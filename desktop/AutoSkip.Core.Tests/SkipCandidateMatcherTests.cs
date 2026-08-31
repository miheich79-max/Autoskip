using AutoSkip.Core;

namespace AutoSkip.Core.Tests;

public sealed class SkipCandidateMatcherTests
{
    private readonly SkipCandidateMatcher _matcher = new();

    [Theory]
    [InlineData("Skip ad")]
    [InlineData("SKIP ADS")]
    [InlineData("Skip")]
    [InlineData("Skip video")]
    [InlineData("Пропустить рекламу")]
    [InlineData("Пропустить")]
    [InlineData("דלג על המודעה")]
    [InlineData("דלג")]
    [InlineData("Skip ad, 1 of 2")]
    public void AcceptsKnownLabels(string label) => Assert.True(_matcher.IsMatch(label));

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("Skip navigation")]
    [InlineData("Skip trial")]
    [InlineData("Skip to content")]
    [InlineData("Subscribe")]
    [InlineData("Пропустить вступление")]
    public void RejectsUnrelatedLabels(string? label) => Assert.False(_matcher.IsMatch(label));
}
