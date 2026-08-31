namespace AutoSkip.Desktop;

internal sealed class AppController : IDisposable
{
    private readonly ChromeAutomationMonitor _monitor = new();

    public AppController()
    {
        Startup = new StartupManager();
        _monitor.SnapshotChanged += (_, snapshot) => SnapshotChanged?.Invoke(this, snapshot);
        _monitor.Start();
    }

    public StartupManager Startup { get; }
    public bool Enabled => _monitor.Enabled;
    public DiagnosticSnapshot Snapshot => _monitor.Snapshot;
    public event EventHandler<DiagnosticSnapshot>? SnapshotChanged;

    public void SetEnabled(bool enabled) => _monitor.SetEnabled(enabled);

    public void Dispose() => _monitor.Dispose();
}
