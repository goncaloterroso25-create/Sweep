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

---

## Known issues

### Unused apps

This feature currently needs more testing and adjustment.

Sweep uses Android usage information to estimate when an application was last opened, but testing on physical devices showed that some results were inaccurate or incomplete.

For now, I consider the **unused apps feature experimental** rather than fully functional.

Possible solutions include:

- improving how `UsageStats` data is interpreted;
- distinguishing between reliable and unavailable usage history;
- clearly informing the user when Android does not provide enough historical information.

### Haptic feedback

Haptic feedback is currently implemented in the interface, but **does not appear to work during physical-device testing**.

This affects interactions that are intended to provide tactile feedback, such as selections, confirmations and cleanup completion.

The implementation needs to be investigated to determine whether the issue is related to the current haptic implementation, device compatibility, Android settings or vibration permissions.

For now, haptic feedback should be considered **non-functional**.

### Android cache management shortcut

The **Open Android Cache Management** button currently does not work reliably.

Since users can already open the storage page of individual applications directly from Sweep, this button may simply be removed.

---

## Planned improvements

### File preview

Before deleting a file, the user should be able to inspect it.

Planned support includes opening or previewing:

- Images
- Videos
- Documents
- APK files
- Archives
- Other detected files

This should make it easier to verify that something is actually unnecessary before deleting it.

### Improve unused-app detection

The unused-app system needs additional work before it can be considered reliable.

The UI should also clearly communicate when Android does not have enough usage history to determine when an application was last opened.

### Haptics

Investigate and fix the current haptic-feedback implementation across supported devices.

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
- Coroutines / Flow
- DataStore

---

## Development status

**Early functional MVP**

Core storage scanning and cleanup functionality works, but several areas still require testing and refinement.

The next development phase will focus mainly on:

- file previews;
- unused-app accuracy;
- haptic feedback;
- additional physical-device testing;
- UI/UX and animation polish;
- edge cases and reliability.

---

## Licence

MIT. See [LICENSE](LICENSE).
