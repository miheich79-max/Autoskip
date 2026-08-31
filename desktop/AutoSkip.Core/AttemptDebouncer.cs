namespace AutoSkip.Core;

public sealed class AttemptDebouncer(TimeSpan interval, TimeProvider? timeProvider = null)
{
    private readonly TimeProvider _timeProvider = timeProvider ?? TimeProvider.System;
    private long _lastAttemptTimestamp = long.MinValue;
    private readonly object _gate = new();

    public bool TryBegin()
    {
        lock (_gate)
        {
            var now = _timeProvider.GetTimestamp();
            if (_lastAttemptTimestamp != long.MinValue &&
                _timeProvider.GetElapsedTime(_lastAttemptTimestamp, now) < interval)
            {
                return false;
            }

            _lastAttemptTimestamp = now;
            return true;
        }
    }
}
