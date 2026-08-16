# Sweep

An experimental Android storage cleanup app built as a personal portfolio project.

Sweep scans accessible device storage and helps identify files and apps that may be taking up unnecessary space.

This is currently an **early functional version**, not a production-ready application.

The project is being developed and tested locally before further UX, reliability and feature improvements.

---

## Current status

Sweep has been tested on **two physical Android devices**.

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

It is used sparingly: selecting a file, excluding one, confirming a deletion and finishing a cleanup.

The earlier problem was that `performHapticFeedback` is a request Android can silently ignore, and the return value was being discarded. Sweep now asks in a way the platform accepts and checks the result.

Settings includes a **Test haptic** action that triggers the real confirmation feedback and reports what happened. Android's own touch-feedback setting still takes priority, and Sweep does not claim to override it.

---

## Known issues

### Unused apps

This is still the weakest feature, and it remains **experimental**.

An earlier version treated "Android has no usage record" as evidence that an app was unused, which meant apps opened that same morning could be listed as forgotten. That is fixed. An app is now only called unused when Android reported a real last-opened date older than the chosen threshold.

Everything else goes into a separate **Usage unknown** section, which is excluded from the unused count, excluded from the reclaimable total, and offered an app-info action instead of an uninstall button.

The remaining problem is coverage rather than honesty. On a Samsung test device almost every installed app ends up under Usage unknown, so the feature is accurate but not yet useful there.

Still to investigate:

- whether reading raw usage events gives better results than aggregated `UsageStats`;
- whether device-specific app sleeping behavior affects the data Sweep receives;
- whether the feature is worth keeping if Android cannot supply usable history.

---

## Planned improvements

### Improve unused-app detection

The unused-app system needs more work before it can be considered reliable.

Sweep already tells the user when Android has no usage history for an app. The open question is whether a different data source produces enough real last-opened dates to make the feature worth keeping at all.

### Interface polish

Motion, scanning feedback and the app's visual identity are the next area of work.

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

- unused-app accuracy, or removing the feature;
- UI/UX and animation polish;
- the app's visual identity;
- additional physical-device testing;
- edge cases and reliability.

---

## Licence

MIT. See [LICENSE](LICENSE).
