# Design notes

The app uses the shared design tokens of this portfolio (the reasoning, and the rules taken from
[Hallmark](https://github.com/nutlope/hallmark), are written up in `SIH-Big-Data-Searching/web/DESIGN.md`):
a warm paper and a green-black one chosen by the system setting, one vermilion accent, small radii, and a
book face for titles and large figures.

## What changed

| Before | Now |
| --- | --- |
| Teal with an orange second accent | One accent (vermilion) on a neutral paper; light and dark both retuned |
| A gradient hero card for "Total Spent" with white text | A flat card on the panel colour with a hairline, text from the theme |
| Accent-coloured status bar | Status bar matches the paper |
| 12 to 24dp corners | 3dp and 6dp, set once in the theme `Shapes` |
| Credit and debit hard-coded green and red, the same on both papers | `creditColor()` and `debitColor()` follow the theme and keep their contrast |
| Hard-coded white text on accent surfaces | `onPrimary` / `onError` from the theme |

Category colours stay distinct on purpose (they tell chart series apart) but were muted to sit with the
palette.

## Limits

- Verified by compile, spotless, detekt, lint and the unit tests. **Not** looked at on a device or
  emulator, so contrast on every surface, the charts and spacing have not been seen.
- The Gradle wrapper in this repository was broken (missing a class, so `./gradlew` failed before
  running anything). It was regenerated in a separate commit; the checks above were run with it.
