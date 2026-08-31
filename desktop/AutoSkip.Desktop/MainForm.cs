using System.Drawing;
using System.IO;

namespace AutoSkip.Desktop;

internal sealed class MainForm : Form
{
    private readonly AppController _controller;
    private readonly Label _statusValue = new();
    private readonly Label _chromeValue = new();
    private readonly Label _youtubeValue = new();
    private readonly Label _lastDetectionValue = new();
    private readonly Label _lastClickValue = new();
    private readonly Label _skipCountValue = new();
    private readonly Button _enableButton = new();
    private readonly Button _disableButton = new();
    private readonly CheckBox _startWithWindows = new();
    private readonly NotifyIcon _trayIcon;
    private readonly ToolStripMenuItem _trayEnable;
    private readonly ToolStripMenuItem _trayDisable;
    private bool _allowExit;

    public MainForm(AppController controller)
    {
        _controller = controller;
        Text = "AutoSkip Desktop";
        ClientSize = new Size(430, 420);
        MinimumSize = new Size(446, 459);
        StartPosition = FormStartPosition.CenterScreen;
        Font = new Font("Segoe UI", 10F);

        var trayMenu = new ContextMenuStrip();
        _trayEnable = new ToolStripMenuItem("Enable", null, (_, _) => SetEnabled(true));
        _trayDisable = new ToolStripMenuItem("Disable", null, (_, _) => SetEnabled(false));
        trayMenu.Items.AddRange(
        [
            _trayEnable,
            _trayDisable,
            new ToolStripSeparator(),
            new ToolStripMenuItem("Open", null, (_, _) => ShowWindow()),
            new ToolStripMenuItem("Exit", null, (_, _) => ExitApplication()),
        ]);
        _trayIcon = new NotifyIcon
        {
            Text = "AutoSkip Desktop",
            Icon = SystemIcons.Application,
            ContextMenuStrip = trayMenu,
            Visible = true,
        };
        _trayIcon.DoubleClick += (_, _) => ShowWindow();

        BuildLayout();
        _startWithWindows.Checked = _controller.Startup.IsEnabled;
        _startWithWindows.CheckedChanged += OnStartupChanged;
        _controller.SnapshotChanged += OnSnapshotChanged;
        UpdateView(_controller.Snapshot);
    }

    private void BuildLayout()
    {
        var layout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            Padding = new Padding(22),
            ColumnCount = 2,
            RowCount = 12,
        };
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 145));
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));

        var title = new Label
        {
            Text = "AutoSkip Desktop",
            Font = new Font(Font.FontFamily, 21F, FontStyle.Bold),
            AutoSize = true,
            Margin = new Padding(0, 0, 0, 14),
        };
        layout.Controls.Add(title, 0, 0);
        layout.SetColumnSpan(title, 2);

        AddDiagnosticRow(layout, 1, "Status:", _statusValue);
        AddDiagnosticRow(layout, 2, "Chrome detected:", _chromeValue);
        AddDiagnosticRow(layout, 3, "YouTube detected:", _youtubeValue);
        AddDiagnosticRow(layout, 4, "Last detection:", _lastDetectionValue);
        AddDiagnosticRow(layout, 5, "Last click:", _lastClickValue);
        AddDiagnosticRow(layout, 6, "Session skips:", _skipCountValue);

        var actions = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            Margin = new Padding(0, 18, 0, 8),
        };
        _enableButton.Text = "Enable AutoSkip";
        _enableButton.AutoSize = true;
        _enableButton.Click += (_, _) => SetEnabled(true);
        _disableButton.Text = "Disable AutoSkip";
        _disableButton.AutoSize = true;
        _disableButton.Click += (_, _) => SetEnabled(false);
        actions.Controls.AddRange([_enableButton, _disableButton]);
        layout.Controls.Add(actions, 0, 7);
        layout.SetColumnSpan(actions, 2);

        _startWithWindows.Text = "Start AutoSkip with Windows";
        _startWithWindows.AutoSize = true;
        _startWithWindows.Margin = new Padding(3, 6, 0, 10);
        layout.Controls.Add(_startWithWindows, 0, 8);
        layout.SetColumnSpan(_startWithWindows, 2);

        var secondaryActions = new FlowLayoutPanel { AutoSize = true };
        var helpButton = new Button { Text = "Open setup/help", AutoSize = true };
        helpButton.Click += (_, _) => ShowHelp();
        var hideButton = new Button { Text = "Minimize to tray", AutoSize = true };
        hideButton.Click += (_, _) => HideToTray();
        var exitButton = new Button { Text = "Exit", AutoSize = true };
        exitButton.Click += (_, _) => ExitApplication();
        secondaryActions.Controls.AddRange([helpButton, hideButton, exitButton]);
        layout.Controls.Add(secondaryActions, 0, 9);
        layout.SetColumnSpan(secondaryActions, 2);

        var privacy = new Label
        {
            Text = "Local only · No network · No analytics · No browsing data stored",
            AutoSize = true,
            ForeColor = Color.DimGray,
            Margin = new Padding(0, 18, 0, 0),
        };
        layout.Controls.Add(privacy, 0, 10);
        layout.SetColumnSpan(privacy, 2);

        Controls.Add(layout);
    }

    private static void AddDiagnosticRow(TableLayoutPanel layout, int row, string label, Label value)
    {
        var name = new Label
        {
            Text = label,
            AutoSize = true,
            ForeColor = Color.DimGray,
            Margin = new Padding(0, 5, 0, 5),
        };
        value.AutoSize = true;
        value.Margin = new Padding(0, 5, 0, 5);
        layout.Controls.Add(name, 0, row);
        layout.Controls.Add(value, 1, row);
    }

    private void OnSnapshotChanged(object? sender, DiagnosticSnapshot snapshot)
    {
        if (IsDisposed || !IsHandleCreated)
        {
            return;
        }

        BeginInvoke(() => UpdateView(snapshot));
    }

    private void UpdateView(DiagnosticSnapshot snapshot)
    {
        _statusValue.Text = snapshot.Status switch
        {
            MonitorStatus.Disabled => "Disabled",
            MonitorStatus.ChromeNotRunning => "Chrome not running",
            MonitorStatus.SetupRequired => "Setup required",
            _ => "Active",
        };
        _statusValue.ForeColor = snapshot.Status == MonitorStatus.Active
            ? Color.ForestGreen
            : snapshot.Status == MonitorStatus.SetupRequired ? Color.DarkOrange : Color.Firebrick;
        _chromeValue.Text = snapshot.ChromeDetected ? "Yes" : "No";
        _youtubeValue.Text = snapshot.YouTubeDetected ? "Yes" : "No";
        _lastDetectionValue.Text = snapshot.LastDetection?.ToLocalTime().ToString("G") ?? "None";
        _lastClickValue.Text = snapshot.LastClickResult;
        _skipCountValue.Text = snapshot.SuccessfulSkips.ToString();

        _enableButton.Enabled = !_controller.Enabled;
        _disableButton.Enabled = _controller.Enabled;
        _trayEnable.Enabled = !_controller.Enabled;
        _trayDisable.Enabled = _controller.Enabled;
        _trayIcon.Text = $"AutoSkip Desktop — {_statusValue.Text}";
    }

    private void SetEnabled(bool enabled)
    {
        _controller.SetEnabled(enabled);
        UpdateView(_controller.Snapshot);
    }

    private void OnStartupChanged(object? sender, EventArgs args)
    {
        try
        {
            _controller.Startup.SetEnabled(_startWithWindows.Checked);
        }
        catch (Exception exception) when (exception is UnauthorizedAccessException or IOException)
        {
            MessageBox.Show(
                $"Windows could not update your startup setting.\n\n{exception.Message}",
                "AutoSkip Desktop",
                MessageBoxButtons.OK,
                MessageBoxIcon.Warning);
            _startWithWindows.CheckedChanged -= OnStartupChanged;
            _startWithWindows.Checked = _controller.Startup.IsEnabled;
            _startWithWindows.CheckedChanged += OnStartupChanged;
        }
    }

    private void ShowHelp()
    {
        MessageBox.Show(
            "AutoSkip watches only visible web-document buttons in Google Chrome. " +
            "It acts only when the active address is a YouTube video page and the button has a known Skip label.\n\n" +
            "If status says Setup required, update Chrome first. Then open chrome://accessibility " +
            "in Chrome and enable accessibility mode for the active YouTube tab. Restart Chrome if needed.\n\n" +
            "Keep this app running or minimize it to the notification area.",
            "AutoSkip Desktop setup and help",
            MessageBoxButtons.OK,
            MessageBoxIcon.Information);
    }

    private void HideToTray()
    {
        Hide();
        ShowInTaskbar = false;
    }

    private void ShowWindow()
    {
        ShowInTaskbar = true;
        Show();
        WindowState = FormWindowState.Normal;
        Activate();
    }

    private void ExitApplication()
    {
        _allowExit = true;
        Close();
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        if (!_allowExit && e.CloseReason == CloseReason.UserClosing)
        {
            e.Cancel = true;
            HideToTray();
            return;
        }

        _trayIcon.Visible = false;
        base.OnFormClosing(e);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _controller.SnapshotChanged -= OnSnapshotChanged;
            _trayIcon.Dispose();
        }
        base.Dispose(disposing);
    }
}
