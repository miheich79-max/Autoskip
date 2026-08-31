using AutoSkip.Core;

namespace AutoSkip.Core.Tests;

public sealed class YouTubeUrlMatcherTests
{
    private readonly YouTubeUrlMatcher _matcher = new();

    [Theory]
    [InlineData("https://www.youtube.com/watch?v=abc")]
    [InlineData("youtube.com/shorts/abc")]
    [InlineData("https://m.youtube.com/live/abc")]
    [InlineData("https://www.youtube.com/embed/abc")]
    public void AcceptsSupportedVideoPages(string url) => Assert.True(_matcher.IsSupportedPage(url));

    [Theory]
    [InlineData(null)]
    [InlineData("https://example.com/watch?v=abc")]
    [InlineData("https://youtube.example.com/watch?v=abc")]
    [InlineData("https://www.youtube.com/")]
    [InlineData("https://www.youtube.com/watchlater")]
    [InlineData("chrome://settings/accessibility")]
    public void RejectsOtherPages(string? url) => Assert.False(_matcher.IsSupportedPage(url));
}
