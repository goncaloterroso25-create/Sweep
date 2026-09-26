# Sweep v0.6: design exploration

Why v0.6 looks and moves the way it does, for whoever works on it next.

---

## What was weak in v0.5

v0.5 worked, and its honesty rules were right. The interface around them had accumulated.

- **Home was a stack of equal cards.** Seven category cards, two utility cards and a meter, each with a tinted icon tile, a border and a chevron. Nothing outranked anything else.
- **Six category colours.** Blue installers, purple archives, teal screenshots, amber downloads. The lime signal had to compete with a rainbow, so it stopped meaning anything in particular.
- **The block field implied a precision it did not have.** A hundred blocks read as "each block is 1%", and at scan time the pressure wave was a second effect layered on a picture that was already busy.
- **Glass without a reason.** A translucent selection bar with a lit edge and a scrim, and real window blur behind sheets. It said "premium" rather than anything about storage.
- **Every file was a bordered card.** Long lists were a column of boxes, and at a large font scale the name, reason and size fought for one line.
- **The same idea, animated differently everywhere.** Cards staggered up, rows slid in, the meter swelled, the mark redrew itself every time Home appeared, and figures counted up through values the device never had.
- **Onboarding was a wall.** Two permission cards before the user had seen anything the permissions were for.
- **One real defect in reminders.** After a clean, the "last scan found" figure was never updated, so a weekly reminder could quote gigabytes the user had already deleted.

## Research, briefly

Looked at for what they do well, not for how they look:

- **Apple's Storage settings, Files by Google.** One bar with a legend is instantly understood. Suggestion cards compete for attention; a list sorted by size does not.
- **Nothing OS.** Monochrome plus one signal colour gives a strong identity cheaply. Restraint is the recognisable part.
- **Linear, Raycast.** Dense rows with right-aligned figures, hierarchy from type rather than boxes, transitions around 150 to 250 ms that confirm rather than perform.
- **Material 3 motion.** Springs for spatial change and short tweens for colour and opacity: effects never overshoot, and springs can be interrupted without jumping.
- **Android's SplashScreen API.** The platform already owns cold launch. The right move is to use it well, not to add a second intro screen.

## Chosen direction

**Storage as a tally, and one axis of motion.**

Storage is drawn as a row of round-capped strokes, the mark's own stroke stood upright. Tall strokes are space in use, short stubs are free, and whatever a scan finds collects in lime at the edge of the used region, where it would become free if it went. A tally is visibly counted in steps, which is exactly how precise it is.

Everything moves along one horizontal axis, the direction the mark points. Things arrive from the left and settle; things that go are cleared to the right. The scan is a front passing through the tally: the strokes lean in its wake and settle back with one damped swing, like bristles after a brush. The front's speed follows how busy the scanner actually is, so it slows on large files and never loops at a fixed rate. The first pass reads the strokes from dim to full, and later passes are calmer. None of it is a percentage.

Colour has two jobs. Lime means space you could get back: found, selected, the action that reviews it. Red means permanent. Everything else is ink on paper, or paper on ink. Light mode is designed separately: on paper, lime is read through an olive "signal ink", because lime text on paper fails contrast.

## Principles

1. **Calm until something happens.** No ambient motion. The tally animates only while it is unsettled, and asks for no frames at rest.
2. **Motion is evidence.** The scan front moves because the scanner is working; strokes drop to stubs because a file was confirmed gone; free space changes only when Android has been asked again.
3. **Hierarchy from type, not boxes.** One pill per screen. Rows separated by hairlines. The only rounded containers are groups of settings that genuinely belong together.
4. **Show the rule, not just the result.** Each category states its own pre-selection rule. Duplicate groups list the copy that is kept, with no select mark. The delete sheet calls out anything picked from categories Sweep never pre-selects.
5. **Nothing may disappear because the screen is narrow.** Rows restack rather than squeeze once the width per unit of font scale drops below 260dp. Layout tests enforce it at 320dp and 200% text.

## Systems

- **Motion.** Arrive (from the left, settle), Clear (to the right, accelerate), Settle (one spring, 0.86 damping), Compress (presses, the scan front). Selection is the only place overshoot is allowed. Reduced motion keeps every state change as a short cross-fade and removes all travel, the scan front, leaning and springs. Every helper in `Motion.kt` decides that itself.
- **Icons.** A 24 grid with a 20 live area, one 1.8 stroke, round caps and joins, 2.5 container corners, never filled. Strokes that carry meaning run horizontally and shorten as they descend. The mark's fragment appears only where "not yet dealt with" is the point. Convention wins over brand wherever recognition would suffer.
- **Type.** Space Grotesk for figures and titles, Inter for reading, kept from v0.5 because nothing beat them. Figures that change use tabular numerals. The hero is set in Medium, not Bold, and shrinks to fit rather than wrap.
- **Launch.** The system splash, held only until Home can draw its real first frame (700 ms at most), then lifted in about 250 ms with the mark swept to the right.

## What was deliberately removed

- The block storage field and its pressure-wave scan, replaced by the tally.
- The six category tint colours. Categories are now monochrome icons.
- Card borders around every category, file and app.
- The glass selection bar, `GlassSurface`, the scrim beneath it, and window blur behind sheets.
- The full-screen completion overlay. The receipt appears in place on Home, above the tally that just changed.
- The onboarding screen. Storage access is asked for on Home, where the scan button would be; Usage Access on the Apps screen; notifications when a reminder is switched on.
- Separate Unused apps and App cache screens, merged into one Apps destination with two tabs.
- Counting-up numbers, staggered card entrances, and the mark redrawing itself on Home.
- The `material-icons-extended` dependency, unused since v0.5.
