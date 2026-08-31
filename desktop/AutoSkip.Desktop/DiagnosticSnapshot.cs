namespace AutoSkip.Desktop;

internal enum MonitorStatus
{
    Disabled,
    ChromeNotRunning,
    Active,
    SetupRequired,
}

internal sealed record DiagnosticSnapshot(
    MonitorStatus Status,
    bool ChromeDetected,
    bool YouTubeDetected,
    DateTimeOffset? LastDetection,
    string LastClickResult,
    int SuccessfulSkips);
