using AutoSkip.Core;

namespace AutoSkip.Core.Tests;

public sealed class AttemptDebouncerTests
{
    [Fact]
    public void AllowsFirstAttemptAndBlocksImmediateRepeat()
    {
        var debouncer = new AttemptDebouncer(TimeSpan.FromMilliseconds(1200));

        Assert.True(debouncer.TryBegin());
        Assert.False(debouncer.TryBegin());
    }
}
