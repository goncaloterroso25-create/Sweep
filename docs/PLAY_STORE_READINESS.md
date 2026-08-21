# Play Store readiness

Where Sweep actually stands against Google Play policy, as of August 2026.

Short version: the build requirements are met, and the permission requirements are the problem. Two of Sweep's permissions need a Permissions Declaration Form and a review decision that nobody can predict from the outside. Meeting policy is not the same as being approved.

---

## Build requirements

| Requirement | Status |
| --- | --- |
| Target API level | **Met.** `targetSdk 36`. Play requires API 36 for new apps and updates from 31 August 2026. |
| Compile SDK | `compileSdk 36`, with AGP 8.10.1 and Gradle 8.11.1. |
| App Bundle | **Met.** `./gradlew releaseBundleInfo` produces a signed `.aab`. Play has required bundles for new apps since August 2021. |
| Play App Signing | Required for new apps. The key in `keystore.properties` becomes the *upload* key; Play holds the app signing key and re-signs every download. |
| 64-bit | **Met.** No native code of Sweep's own. The two bundled `.so` files come from AndroidX and ship all four ABIs. |
| Notification permission | **Met.** `POST_NOTIFICATIONS` is requested at the moment a reminder is switched on, never at launch. |

---

## Permissions

| Permission | Feature that needs it | Narrower option? | Play requirement | Status |
| --- | --- | --- | --- | --- |
| `MANAGE_EXTERNAL_STORAGE` | The entire file scan: duplicates, old downloads, installers, archives, screenshots, large files, empty folders, and deleting any of them | MediaStore and SAF cover media and user-picked trees, but not arbitrary files across shared storage, and cannot detect duplicates spanning directories the user has not individually granted | Permissions Declaration Form. Permitted uses include file management, where accessing and managing files outside app-specific storage is the core purpose | **At risk.** Sweep is a file manager by function, which is a listed permitted use, but reviewers apply this narrowly and cleanup apps are a contested category |
| `QUERY_ALL_PACKAGES` | Listing installed apps for Unused apps and per-app cache sizes | `<queries>` only works when the packages are known ahead of time. Enumerating every installed app is the feature | Permissions Declaration Form. Permitted uses include file managers and antivirus. Device cleanup is **not** an explicitly listed permitted use | **At risk.** The strongest available argument is the file-manager use, which is also what justifies the storage permission |
| `PACKAGE_USAGE_STATS` | Last-opened dates and per-app storage sizes | None. This is the only API that answers "when was this last opened" | Special access, granted by the user in system settings. Prominent disclosure and Data Safety accuracy apply | **Acceptable**, given the in-app explanation of what it is for |
| `REQUEST_DELETE_PACKAGES` | Launching Android's uninstall dialog | None. Without it the system can drop the request silently | Not separately restricted. Sweep never removes anything itself; Android shows its own confirmation | **Acceptable** |
| `READ_/WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="29"`) | The same file access on Android 8 to 10 | Already capped at API 29 | Standard runtime permissions on those versions | **Acceptable** |
| `POST_NOTIFICATIONS` | Optional cleanup and unused-app reminders | None | Standard runtime permission | **Acceptable** |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | Added by WorkManager. Finishing a scheduled reminder, and rescheduling it after a reboot | Only by dropping background reminders entirely | Normal permissions, no declaration | **Acceptable** |

None of these are declared speculatively. Each maps to a feature that stops working without it, which is the test I applied rather than "could this be useful later".

WorkManager would also have contributed `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE`. Both are stripped with `tools:node="remove"`, because the reminder job declares no network constraint and never runs expedited work. An app whose main privacy claim is that it cannot reach the network should not ship a network permission it never calls.

---

## Data Safety

Sweep collects nothing and transmits nothing.

- No `INTERNET` permission, so the app is technically incapable of sending data anywhere. This is verifiable in the merged manifest.
- No analytics, no crash reporting, no advertising SDK, no accounts.
- Settings and the exclusion list are stored locally in DataStore and excluded from cloud backup.
- File names, app names and usage history never leave the device.

The Data Safety form should therefore declare no data collected and no data shared. The one nuance worth stating accurately: when the user opens a file preview, Sweep grants a read-only URI for that single file to whichever app the user picked. That is a user-initiated handoff to another app on the same device, not collection.

---

## If a declaration is rejected

The realistic fallback is a reduced Play variant rather than abandoning the store, but it is worth being honest about what it would cost:

- Without `MANAGE_EXTERNAL_STORAGE`: no duplicate detection across directories, no empty folders, and downloads and installers limited to what MediaStore exposes. That removes most of the product.
- Without `QUERY_ALL_PACKAGES`: no Unused apps and no cache sizes. Two features gone, cleanly.

Dropping package visibility alone leaves a coherent app. Dropping storage access does not. A flavour has deliberately **not** been created yet, because building and maintaining a second variant before a policy decision exists would be work spent on a guess.

---

## What this document does not claim

- That Sweep will be approved. Permitted use and approval are different things, and Play reviewers assess the specific app.
- That any of this has been submitted. Nothing here has been through review.
- That signing an APK affects Play Protect warnings for sideloaded builds. It does not.
