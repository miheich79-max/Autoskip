using System.Diagnostics;
using System.Windows.Automation;
using AutoSkip.Core;

namespace AutoSkip.Desktop;

internal sealed class ChromeAutomationMonitor : IDisposable
{
    private static readonly TimeSpan WindowDiscoveryInterval = TimeSpan.FromSeconds(3);
    private readonly SkipCandidateMatcher _labelMatcher = new();
    private readonly YouTubeUrlMatcher _urlMatcher = new();
    private readonly AttemptDebouncer _clickDebouncer = new(TimeSpan.FromMilliseconds(1200));
    private readonly Dictionary<IntPtr, AutomationElement> _windows = [];
    private readonly object _gate = new();
    private readonly System.Threading.Timer _discoveryTimer;
    private readonly StructureChangedEventHandler _structureChangedHandler;
    private int _scanQueued;
    private volatile bool _enabled = true;
    private volatile bool _disposed;

    public ChromeAutomationMonitor()
    {
        _structureChangedHandler = OnAutomationChanged;
        _discoveryTimer = new System.Threading.Timer(
            _ => DiscoverWindows(), null, Timeout.InfiniteTimeSpan, Timeout.InfiniteTimeSpan);
        Snapshot = NewSnapshot(MonitorStatus.ChromeNotRunning);
    }

    public bool Enabled => _enabled;
    public DiagnosticSnapshot Snapshot { get; private set; }
    public event EventHandler<DiagnosticSnapshot>? SnapshotChanged;

    public void Start() =>
        _discoveryTimer.Change(TimeSpan.Zero, WindowDiscoveryInterval);

    public void SetEnabled(bool enabled)
    {
        _enabled = enabled;
        if (enabled)
        {
            DiscoverWindows();
            QueueScan();
        }
        else
        {
            Publish(Snapshot with { Status = MonitorStatus.Disabled, YouTubeDetected = false });
        }
    }

    private void DiscoverWindows()
    {
        if (_disposed)
        {
            return;
        }

        try
        {
            var chromeProcesses = Process.GetProcessesByName("chrome");
            HashSet<IntPtr> handles;
            try
            {
                handles = chromeProcesses
                    .Select(process => process.MainWindowHandle)
                    .Where(handle => handle != IntPtr.Zero)
                    .Distinct()
                    .ToHashSet();
            }
            finally
            {
                foreach (var process in chromeProcesses)
                {
                    process.Dispose();
                }
            }

            var addedWindow = false;
            lock (_gate)
            {
                foreach (var staleHandle in _windows.Keys.Except(handles).ToArray())
                {
                    Unsubscribe(_windows[staleHandle]);
                    _windows.Remove(staleHandle);
                }

                foreach (var handle in handles.Except(_windows.Keys).ToArray())
                {
                    try
                    {
                        var window = AutomationElement.FromHandle(handle);
                        Subscribe(window);
                        _windows.Add(handle, window);
                        addedWindow = true;
                    }
                    catch (ElementNotAvailableException)
                    {
                        // Chrome changed between process enumeration and UIA attachment.
                    }
                }
            }

            if (!Enabled)
            {
                Publish(Snapshot with { Status = MonitorStatus.Disabled, ChromeDetected = handles.Count > 0 });
                return;
            }

            if (handles.Count == 0)
            {
                Publish(Snapshot with
                {
                    Status = MonitorStatus.ChromeNotRunning,
                    ChromeDetected = false,
                    YouTubeDetected = false,
                });
                return;
            }

            Publish(Snapshot with { Status = MonitorStatus.Active, ChromeDetected = true });
            if (addedWindow)
            {
                QueueScan();
            }
        }
        catch (InvalidOperationException)
        {
            Publish(Snapshot with { Status = MonitorStatus.SetupRequired });
        }
    }

    private void Subscribe(AutomationElement window)
    {
        Automation.AddStructureChangedEventHandler(
            window, TreeScope.Descendants, _structureChangedHandler);
    }

    private void Unsubscribe(AutomationElement window)
    {
        try
        {
            Automation.RemoveStructureChangedEventHandler(window, _structureChangedHandler);
        }
        catch (ElementNotAvailableException)
        {
            // The Chrome window has already closed.
        }
    }

    private void OnAutomationChanged(object sender, StructureChangedEventArgs args) => QueueScan();

    private void QueueScan()
    {
        if (!Enabled || Interlocked.Exchange(ref _scanQueued, 1) != 0)
        {
            return;
        }

        _ = Task.Delay(100).ContinueWith(
            _ =>
            {
                Interlocked.Exchange(ref _scanQueued, 0);
                ScanWindows();
            },
            CancellationToken.None,
            TaskContinuationOptions.None,
            TaskScheduler.Default);
    }

    private void ScanWindows()
    {
        if (!Enabled || _disposed)
        {
            return;
        }

        AutomationElement[] windows;
        lock (_gate)
        {
            windows = _windows.Values.ToArray();
        }

        var foundReadableAddress = false;
        var foundYouTube = false;
        foreach (var window in windows)
        {
            try
            {
                var address = ReadAddressBar(window);
                foundReadableAddress |= !string.IsNullOrWhiteSpace(address);
                if (!_urlMatcher.IsSupportedPage(address))
                {
                    continue;
                }

                foundYouTube = true;
                if (TryClickSkipButton(window))
                {
                    return;
                }
            }
            catch (ElementNotAvailableException)
            {
                // Discovery removes stale windows on the next low-frequency tick.
            }
            catch (InvalidOperationException)
            {
                // A transient UIA provider failure should not terminate monitoring.
            }
        }

        Publish(Snapshot with
        {
            Status = windows.Length > 0 && !foundReadableAddress
                ? MonitorStatus.SetupRequired
                : MonitorStatus.Active,
            ChromeDetected = windows.Length > 0,
            YouTubeDetected = foundYouTube,
        });
    }

    private static string? ReadAddressBar(AutomationElement window)
    {
        var addressBar = window.FindFirst(
            TreeScope.Descendants,
            new PropertyCondition(AutomationElement.AutomationIdProperty, "view_omnibox"));
        if (addressBar is null)
        {
            return null;
        }

        if (addressBar.TryGetCurrentPattern(ValuePattern.Pattern, out var valuePattern))
        {
            return ((ValuePattern)valuePattern).Current.Value;
        }

        return addressBar.Current.Name;
    }

    private bool TryClickSkipButton(AutomationElement window)
    {
        var documents = window.FindAll(
            TreeScope.Descendants,
            new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Document));

        foreach (AutomationElement document in documents)
        {
            if (document.Current.IsOffscreen)
            {
                continue;
            }

            var buttons = document.FindAll(
                TreeScope.Descendants,
                new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Button));
            foreach (AutomationElement button in buttons)
            {
                if (button.Current.IsOffscreen || !button.Current.IsEnabled ||
                    !_labelMatcher.IsMatch(button.Current.Name))
                {
                    continue;
                }

                var detectedAt = DateTimeOffset.Now;
                Publish(Snapshot with
                {
                    YouTubeDetected = true,
                    LastDetection = detectedAt,
                    LastClickResult = "Candidate detected",
                });

                if (!_clickDebouncer.TryBegin())
                {
                    return true;
                }

                if (!Enabled || _disposed)
                {
                    return true;
                }

                var clicked = InvokeButtonOrParent(button);
                Publish(Snapshot with
                {
                    LastClickResult = clicked ? "Clicked with Windows UI Automation" : "Click action unavailable",
                    SuccessfulSkips = Snapshot.SuccessfulSkips + (clicked ? 1 : 0),
                });
                return true;
            }
        }

        return false;
    }

    private static bool InvokeButtonOrParent(AutomationElement candidate)
    {
        var current = candidate;
        for (var depth = 0; depth <= 5; depth++)
        {
            if (current.TryGetCurrentPattern(InvokePattern.Pattern, out var invokePattern))
            {
                ((InvokePattern)invokePattern).Invoke();
                return true;
            }

            var parent = TreeWalker.ControlViewWalker.GetParent(current);
            if (parent is null || parent.Current.ControlType == ControlType.Document)
            {
                break;
            }

            current = parent;
        }

        return false;
    }

    private DiagnosticSnapshot NewSnapshot(MonitorStatus status) =>
        new(status, false, false, null, "No click attempted", 0);

    private void Publish(DiagnosticSnapshot snapshot)
    {
        Snapshot = snapshot;
        SnapshotChanged?.Invoke(this, snapshot);
    }

    public void Dispose()
    {
        _disposed = true;
        _discoveryTimer.Dispose();
        lock (_gate)
        {
            foreach (var window in _windows.Values)
            {
                Unsubscribe(window);
            }
            _windows.Clear();
        }
    }
}
