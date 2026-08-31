using System.Globalization;

namespace AutoSkip.Core;

public sealed class SkipCandidateMatcher
{
    private static readonly string[] Terms =
    [
        "пропустить рекламу",
        "דלג על המודעה",
        "skip video",
        "skip ads",
        "skip ad",
        "пропустить",
        "דלג",
        "skip",
    ];

    public bool IsMatch(string? accessibleName)
    {
        var value = Normalize(accessibleName);
        if (value.Length == 0)
        {
            return false;
        }

        foreach (var term in Terms)
        {
            if (value.Equals(term, StringComparison.Ordinal) ||
                (!IsGenericTerm(term) && HasSafeSuffix(value, term)))
            {
                return true;
            }
        }

        return false;
    }

    private static bool IsGenericTerm(string term) =>
        term is "skip" or "пропустить" or "דלג";

    private static bool HasSafeSuffix(string value, string term)
    {
        if (!value.StartsWith(term, StringComparison.Ordinal) || value.Length == term.Length)
        {
            return false;
        }

        var separator = value[term.Length];
        return separator is ' ' or ',' or '.' or ':' or '–' or '-' or '·';
    }

    private static string Normalize(string? value) =>
        (value ?? string.Empty).Trim().ToLower(CultureInfo.InvariantCulture);
}
