namespace AutoSkip.Desktop;

internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        using var singleInstance = new Mutex(true, @"Local\AutoSkip.Desktop", out var createdNew);
        if (!createdNew)
        {
            MessageBox.Show("AutoSkip Desktop is already running.", "AutoSkip Desktop",
                MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        ApplicationConfiguration.Initialize();
        using var controller = new AppController();
        if (args.Any(value => value.Equals("--disabled", StringComparison.OrdinalIgnoreCase)))
        {
            controller.SetEnabled(false);
        }
        using var form = new MainForm(controller);
        if (args.Any(value => value.Equals("--minimized", StringComparison.OrdinalIgnoreCase)))
        {
            form.ShowInTaskbar = false;
            form.WindowState = FormWindowState.Minimized;
        }

        Application.Run(form);
    }
}
