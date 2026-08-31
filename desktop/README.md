# AutoSkip Desktop

AutoSkip Desktop is a local Windows companion for YouTube in Google Chrome. It activates YouTube's own visible **Skip ad** button when that button becomes available. It does not block ads, bypass non-skippable ads, or alter Chrome.

The existing Android application remains in the repository root and is unchanged. Desktop code lives entirely under `desktop/`.

## Requirements

- Windows 11, or Windows 10 version 1809 or later
- Google Chrome
- No administrator account or development environment is required for the published build

## Install the portable build

1. Open the latest successful **Build AutoSkip Desktop** workflow run on GitHub.
2. Download the artifact named `AutoSkip-Desktop-win-x64-portable`.
3. Extract it to a stable per-user folder, such as `%LOCALAPPDATA%\AutoSkip Desktop`.
4. Run `AutoSkip.Desktop.exe`.

Windows may show a SmartScreen reputation warning because the executable is not code-signed. Verify `SHA256.txt`, choose **More info**, and run the app only if the hash matches the downloaded executable.

Do not move the executable after enabling startup. If you do move it, turn **Start AutoSkip with Windows** off and on again so Windows records the new path.

## Use

- **Enable AutoSkip** starts monitoring Chrome. It is enabled when the app starts.
- **Disable AutoSkip** immediately stops scans and clicks.
- **Start AutoSkip with Windows** adds or removes a value under the current user's normal `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` key. It does not require elevation.
- Closing the window minimizes the app to the notification area. Use the tray menu's **Exit** command to quit.
- **Open setup/help** explains the `Setup required` status.

If Chrome is started or restarted later, AutoSkip discovers its windows automatically. The diagnostics are session-only and are never written to disk.

## How it works

The native .NET 8 WinForms app uses Windows UI Automation (UIA), Chrome's supported accessibility interface:

1. A low-frequency, three-second discovery timer looks only for top-level `chrome.exe` windows. It exists so Chrome can open, close, or restart after AutoSkip.
2. UIA structure events trigger a coalesced scan. AutoSkip does not scan the screen, use OCR, or run a high-frequency page loop.
3. The active Chrome address bar must parse as `youtube.com`, `www.youtube.com`, or `m.youtube.com`, on a `/watch`, `/shorts/`, `/live/`, or `/embed/` video route.
4. Only visible, enabled UIA `Button` elements inside a visible web `Document` are candidates. Browser chrome is outside that document scope.
5. The accessible button name must match a known English, Russian, or Hebrew label. Generic `Skip`, `Пропустить`, and `דלג` labels are exact-match only.
6. AutoSkip invokes the semantic UIA action on the button, with a bounded parent-action fallback. It never uses fixed coordinates.
7. A 1.2-second debounce prevents repeated attempts.

This approach was selected over a Chrome extension because it installs as a portable executable without Web Store publishing or Developer Mode, and over DevTools because it needs no debugging port or special Chrome launch command. It is less coupled to YouTube's HTML/CSS than DOM selectors.

## Supported labels

- English: `Skip ad`, `Skip ads`, `Skip`, `Skip video`
- Russian: `Пропустить рекламу`, `Пропустить`
- Hebrew: `דלג על המודעה`, `דלג`

Longer labels may include an accessibility suffix separated by a space or punctuation, such as `Skip ad, 1 of 2`. Generic one-word labels do not accept suffixes.

## Build and test

Install the .NET 8 SDK, then run from the repository root:

```powershell
dotnet test desktop/AutoSkip.Core.Tests/AutoSkip.Core.Tests.csproj --configuration Release
dotnet publish desktop/AutoSkip.Desktop/AutoSkip.Desktop.csproj `
  --configuration Release --runtime win-x64 --self-contained true `
  --output desktop/artifacts/AutoSkip-Desktop-win-x64
```

The matcher test suite is a safe harness for all supported labels, false positives, URL scope, and debounce behavior. End-to-end UIA tests require a real interactive Windows desktop and Chrome, so they are covered by the manual matrix below rather than headless CI.

## Manual test matrix

| Scenario | Expected result |
|---|---|
| Chrome closed | `Chrome not running`; no activity |
| Chrome open without YouTube | `Active`, YouTube `No`; no click |
| YouTube video without an ad | YouTube `Yes`; no click |
| Skippable ad | One semantic click when the Skip button becomes visible |
| Non-skippable ad | No candidate and no click |
| Multiple Chrome windows | Each window is tracked; only an active supported YouTube video document is eligible |
| Disabled | UIA events are ignored and no click occurs |
| Chrome restarted | Window subscriptions recover within about three seconds |
| AutoSkip restarted | Monitoring and session counters restart cleanly |

Ad delivery is nondeterministic, so the CI test harness validates candidate policy but cannot force a real YouTube ad.

## Troubleshooting

### Setup required

AutoSkip found Chrome but could not read its address bar through UIA. Update Chrome first. If the status remains, open `chrome://accessibility` in Chrome, enable accessibility mode for the active YouTube tab, and restart Chrome. Managed browser policies or security software can deny UIA access.

### A Skip button was not detected

- Confirm the tab URL is a supported YouTube video route.
- Keep the YouTube tab visible; hidden/background tabs are intentionally ignored.
- Open setup/help and check for `Setup required`.
- YouTube may have introduced a new translated accessible label. Add it to `SkipCandidateMatcher` with a regression test.

### Startup stopped working

The startup entry points to the executable's exact path. Disable startup, move/extract the app to its final location, then enable startup again.

### Uninstall

1. Clear **Start AutoSkip with Windows** in the app.
2. Choose **Exit** from the app or tray menu.
3. Delete the extracted folder.

If the executable was already removed, delete the `AutoSkip Desktop` value from `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` using Windows Registry Editor.

## Security and privacy

- No network API is used by the app. There is no server, analytics, telemetry, update checker, or advertising SDK.
- No page contents, diagnostics, cookies, credentials, browsing history, URLs, or labels are persisted.
- The current active address is read transiently only to validate the YouTube host and video route; it is not displayed or logged.
- UIA access is limited in code to top-level Google Chrome processes, visible web documents, and visible enabled buttons.
- No browser extension or browser permission is required.
- No administrator rights, service, driver, global keyboard hook, code injection, Chrome binary modification, debugging port, or profile access is used.
- The only persistent state is the optional per-user Windows startup command.

## Resource expectations

A local disabled/minimized smoke test measured 56.1 MB working set and 21.1 MB private memory. Startup used 0.19 CPU-seconds during the first five seconds. CPU should settle near 0% while idle; the three-second Chrome-window discovery operation is brief, and actual UIA scans are event-coalesced. An enabled process may use somewhat more memory or CPU depending on the Windows build, Chrome accessibility-tree activity, and number of Chrome windows.

## Limitations compared with Android

- Chrome must expose its accessibility/UIA tree and readable active address bar.
- Only visible, active YouTube tabs are eligible; background tabs are intentionally ignored.
- Desktop browser accessibility names can change independently of Android and may require label updates.
- A portable unsigned build can trigger SmartScreen reputation warnings.
- Unlike the Android gesture fallback, desktop AutoSkip intentionally has no coordinate fallback. This is safer but means an unusual button without a UIA action cannot be clicked.

## Repository layout

- `desktop/AutoSkip.Core/` — label matching, YouTube URL scope, debounce
- `desktop/AutoSkip.Core.Tests/` — safe unit test harness
- `desktop/AutoSkip.Desktop/` — Chrome UIA monitor, click execution, state, WinForms/tray UI, startup integration
- `.github/workflows/build-desktop.yml` — Windows build, tests, self-contained publish, artifact upload
