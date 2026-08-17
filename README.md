# Sweep

An experimental Android storage cleanup app built as a personal portfolio project.

Sweep scans accessible device storage and helps identify files and apps that may be taking up unnecessary space.

This is currently an **early functional version**, not a production-ready application.

The project is being developed and tested locally before further UX, reliability and feature improvements.

---

## Current status

Sweep has been tested on **four physical Android devices**.

Most of the core storage-scanning functionality is working, although some features still need improvement.

### Working

#### Storage scan

The main storage scan works as intended.

Sweep currently detects:

- Large files
- Duplicate files
- Installed/downloaded APK files
- Old downloads
- Archives
- Old screenshots
- Empty folders
- Potentially unused apps

Files are grouped into categories so the user can review them before deleting anything.

#### Duplicate files

Duplicate files can be detected and presented for cleanup.

The app keeps one copy instead of automatically selecting every duplicate for deletion.

#### Cache information

Sweep can display the amount of cache associated with installed applications.

Android does not allow Sweep to directly clear another application's cache, so the app instead opens the relevant Android app-storage page where the user can clear it manually.

This behavior works correctly.

The old **Open Android Cache Management** button has been removed. It did not work reliably on either test device, and the per-app storage page already covers the same need.

#### File preview

Files can be inspected before they are deleted.

Tapping a row opens a details sheet with a thumbnail for images and videos, plus the filename, folder, size and modified date. For duplicates it also names the copy that will be kept.

From there, **Open** hands the file to whichever app the device already uses for that type, through a read-only permission granted for that one file. If nothing installed can open it, Sweep says so.

Selecting and opening are separate actions. The checkbox marks a file for deletion, tapping the row inspects it.

#### Haptic feedback

Haptic feedback now works on the test devices.

It is used sparingly: selecting a file, excluding one, confirming a deletion, finishing a scan and finishing a cleanup.

The earlier problem was that `performHapticFeedback` is a request Android can silently ignore, and the return value was being discarded. Sweep now asks in a way the platform accepts and checks the result.

Settings includes a **Test haptic** action that triggers the real confirmation feedback and reports what happened. Android's own touch-feedback setting still takes priority, and Sweep does not claim to override it.

#### Unused apps

An app is listed as unused only when Android reported a real last-opened date older than the chosen threshold. Nothing is inferred from install dates or package metadata.

Sweep reads two sources and takes the newest date from either:

- raw foreground events, which show exactly when the user last opened an app, but only cover recent history;
- all four aggregated usage buckets, daily through yearly, which reach back much further.

Earlier versions relied on `queryAndAggregateUsageStats` over a long history window. That produced very uneven results across devices. v0.3 now combines explicit usage-stat intervals with relevant foreground events and takes the newest plausible timestamp Android provides. This has improved usage-history coverage across the phones tested so far, but availability still varies by device.

Testing so far does not indicate that Samsung's Sleeping or Deep Sleeping states are the cause of missing history. One device also returned dates from before Sweep was installed, confirming that Sweep is not limited to usage recorded after its own installation.

**Usage unknown** is still a separate state, because some apps genuinely have no history. Those apps are excluded from the unused count and from the reclaimable total, and they get an app-info action instead of an uninstall button.

#### Uninstalling

Uninstalling opens Android's own confirmation dialog. Sweep never removes an app itself.

This did not work on one test device. Sweep was sending the older `ACTION_DELETE` intent, which some Android builds ignore, and it lacked the `REQUEST_DELETE_PACKAGES` permission that `ACTION_UNINSTALL_PACKAGE` requires. Both are fixed, with fallbacks for devices that answer only the older action.

Afterwards Sweep re-reads the package list and asks Android whether the package is still installed. It reports a removal only when the package is actually gone, reports a refusal when Android says the removal failed, and says nothing at all when the dialog was cancelled. The app list and storage figures refresh either way.

---

## Known issues

### Usage history is uneven across devices

Sweep now reads every usage source Android exposes, but it cannot create history that a device never recorded. Some apps will still show as Usage unknown, and how many depends on the device.

This is a platform limitation rather than a bug, and the interface states it plainly instead of guessing.

---

## Planned improvements

### More device coverage

Four phones is enough to find real problems and still not enough to call anything solved, particularly on older Android versions where the storage rules differ.

### Malware scanning

Potential future feature.

This still needs research before deciding whether it belongs in Sweep and whether it can be implemented meaningfully without unnecessarily increasing the scope of the project.

---

## Project goals

Sweep is currently intended as a **small functional Android project for my GitHub portfolio**.

The priorities are:

1. Real functionality
2. Safe file deletion
3. Clear information about what is being removed
4. Fast and enjoyable UX
5. Clean animations and transitions
6. Honest handling of Android limitations

The goal is not to create a commercial Android cleaner or pretend to perform operations that Android does not allow.

---

## Interface

The visual direction has not changed: near-black background, one lime accent, strong type, and a block field that represents the device's storage.

What v0.3 added is motion with a consistent idea behind it. Things arrive along the same axis the scan front travels, so scanning, discovering, selecting and clearing feel like one gesture rather than separate effects:

- during a scan a front moves through the block field, blocks respond as it passes and leave a short wake behind it, and a faint pool of light follows it;
- categories reveal once as they are discovered, then update quietly instead of re-animating on every file;
- when a scan ends the field resolves with a final pass rather than stopping;
- selection tints the row and settles it by a fraction of a percent, and the toolbar rises from the edge with its figures counting up;
- deleting drains the reclaimable blocks, and the free-space figure only moves once Android has been asked for the real number.

The block field is a picture of activity and discovery, not a progress bar. The scanner cannot know how far through it is, so nothing pretends otherwise.

The Home header now carries the Sweep mark next to the wordmark. It draws itself in once when the screen opens and once when a scan starts, and is otherwise still.

Everything above respects the **Reduced motion** setting, which removes the sweep effects, stagger, spring overshoot and counting numbers while keeping state changes legible. Android's own animation setting is honoured too. No animation blocks an action, and nothing animates while the app is idle.

Sweep has no sound effects, by choice.

---

## Privacy

Sweep currently works locally on the device.

File scanning and storage analysis do not require an account or cloud service.

---

## Tech stack

- Kotlin
- Jetpack Compose
- Material 3
- Android Storage APIs
- UsageStatsManager
- StorageStatsManager
- FileProvider, for file preview
- Coroutines / Flow
- DataStore

---

## Development status

**Early functional MVP**

Core storage scanning and cleanup functionality works, but several areas still require testing and refinement.

The next development phase will focus mainly on:

- testing on more devices and older Android versions;
- verifying unused-app coverage across those devices;
- edge cases and reliability.

---

## Licence

MIT. See [LICENSE](LICENSE).
