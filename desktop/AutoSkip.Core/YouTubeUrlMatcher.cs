namespace AutoSkip.Core;

public sealed class YouTubeUrlMatcher
{
    private static readonly string[] SupportedPathPrefixes =
    [
        "/shorts/",
        "/live/",
        "/embed/",
    ];

    public bool IsSupportedPage(string? addressBarValue)
    {
        if (string.IsNullOrWhiteSpace(addressBarValue))
        {
            return false;
        }

        var candidate = addressBarValue.Trim();
        if (!candidate.Contains("://", StringComparison.Ordinal))
        {
            candidate = $"https://{candidate}";
        }

        if (!Uri.TryCreate(candidate, UriKind.Absolute, out var uri))
        {
            return false;
        }

        if (uri.Scheme is not ("http" or "https"))
        {
            return false;
        }

        if (!uri.Host.Equals("youtube.com", StringComparison.OrdinalIgnoreCase) &&
            !uri.Host.Equals("www.youtube.com", StringComparison.OrdinalIgnoreCase) &&
            !uri.Host.Equals("m.youtube.com", StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        return uri.AbsolutePath.Equals("/watch", StringComparison.OrdinalIgnoreCase) ||
            SupportedPathPrefixes.Any(prefix =>
            uri.AbsolutePath.StartsWith(prefix, StringComparison.OrdinalIgnoreCase));
    }
}
