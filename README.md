# Sweep

An Android storage cleanup app built as a personal portfolio project.

Sweep scans the storage it is allowed to see, groups what it finds into categories, and lets you review each file before anything is deleted. Everything happens on the device.

This is a **functional personal project**, not a commercial release. It works, it has been used on real phones, and it is not on any app store.

Tested on **four physical Android devices**.

---

## Quick install APK

If you only want to try Sweep, a prebuilt **v0.4.0 debug APK** is already included in the repository:

`apk/Sweep-v0.4.0-debug.apk`

You can copy that file to an Android phone and install it directly. Because it is a manually installed debug build, Android, Play Protect or third-party antivirus software may show an unknown-app warning. The source is included in the same repository if you prefer to build it yourself.

SHA-256: `9568c065c5f180e7ee08aac8f1ed4ce7183829d283bde0c08e49db885304f5e8`

---

## What it does

| Feature | What it does | Status |
| --- | --- | --- |
| Storage scan | Walks accessible storage and groups cleanup candidates | Working |
| Duplicate files | Finds byte-identical copies and always keeps one | Working |
| Large files | Lists unusually large files for manual review | Working, never pre-selected |
| Old downloads | Finds older files sitting in Downloads | Working |
| Installers | Finds leftover APK and install files | Working |
| Archives | Finds ZIP, RAR, 7z and similar archives | Working |
| Old screenshots | Shows older screenshots for review | Working, never pre-selected |
| Empty folders | Finds folders with nothing in them | Working |
| File preview | Opens a file in an installed app before you delete it | Working |
| Review and delete | Itemised confirmation, reports what actually got deleted | Working |
| App cache | Shows cache sizes and opens each app's Android storage page | Working, Android does the clearing |
| Unused apps | Uses Android's usage history to find apps you stopped opening | Device-dependent |
| Uninstall | Opens Android's uninstall dialog, then verifies the result | Working |
| Haptics | Short feedback on selection, confirmation and completion | Working |

Unused apps is marked device-dependent on purpose. Sweep reads every usage source Android exposes, but some devices report very little history. Apps with no usable history are listed separately under **Usage unknown** rather than being guessed at.

---

## Running Sweep

You need [Android Studio](https://developer.android.com/studio) (Ladybug or newer) and **JDK 17**. Android Studio bundles a suitable JDK, so a separate install is usually unnecessary.

1. Clone or download the repository.
2. Open the project **root folder** in Android Studio, not the `app` folder.
3. Wait for the Gradle sync to finish. Studio will offer to install the Android SDK components it needs, including **SDK Platform 35**.
4. Connect a phone by USB, or create an emulator in Device Manager.
5. On a physical phone, enable **Developer options** by tapping Build number in Settings seven times, then turn on **USB debugging**.
6. Pick the device in the toolbar dropdown.
7. Run the `app` configuration.
8. Grant the permissions Sweep asks for. Both are optional, and the app explains what is unavailable without them.

A physical device is much better than an emulator here. Real storage, real installed apps, real usage history, real cache sizes and the actual uninstall flow are all things an emulator either fakes or does not have.

**If Usage Access will not turn on:** Android restricts sensitive settings for apps installed manually rather than from a store, and the switch can appear greyed out or say the app was denied access. Open Sweep's App info, open the menu in the top right, choose **Allow restricted settings**, then try again. The wording varies between manufacturers. Sweep shows these steps in the app when it detects this.

---

## Building a tester APK

**Debug build**, for your own development:

```bash
./gradlew assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/`. A prebuilt v0.4.0 debug APK is also included at `apk/Sweep-v0.4.0-debug.apk`. Debug builds install alongside a release build, since they use the `dev.sweep.debug` application ID.

**Signed release build**, for sending to someone else. You need your own signing key.

1. Create a key if you do not have one. In Android Studio: **Build > Generate Signed App Bundle / APK > APK > Create new**. Keep the `.jks` file somewhere outside the repository, and back it up. Losing it means you cannot update an installed app.

2. Copy `keystore.properties.example` to `keystore.properties` in the project root and fill in your values:

   ```properties
   storeFile=C:/path/to/sweep-release.jks
   storePassword=...
   keyAlias=sweep
   keyPassword=...
   ```

   The same four values can come from the `SWEEP_STORE_FILE`, `SWEEP_STORE_PASSWORD`, `SWEEP_KEY_ALIAS` and `SWEEP_KEY_PASSWORD` environment variables instead.

3. Build it:

   ```bash
   ./gradlew releaseApkInfo
   ```

   This assembles the release APK and prints its name, size, signing status and SHA-256 checksum. `./gradlew assembleRelease` also works if you only want the file.

The result is `app/build/outputs/apk/release/Sweep-v0.4.0-release.apk`. Without a keystore configured the build still succeeds and produces `Sweep-v0.4.0-release-unsigned.apk`, which Android will refuse to install.

Send the checksum along with the APK so the person receiving it can confirm the file arrived intact.

**Never commit** the `.jks` keystore, `keystore.properties`, or any password. Both are already in `.gitignore`. For normal releases, publish APKs through GitHub Releases. This repository keeps one prebuilt debug APK in `apk/` only for quick testing.

A note on installing: sideloaded apps are unknown to Play Protect, and Android will warn about them. Signing the APK does not change that, and neither does anything else Sweep can do. Some antivirus apps are also suspicious of anything that requests broad storage access, which Sweep genuinely needs in order to work at all.

---

## Permissions and privacy

Sweep ships with six permissions and no networking.

| Permission | Why | Without it |
| --- | --- | --- |
| `MANAGE_EXTERNAL_STORAGE` | Read and delete files across shared storage on Android 11+ | No file scanning |
| `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` | The same thing on Android 8 to 10, where they still apply | No file scanning |
| `PACKAGE_USAGE_STATS` | Last-opened dates and per-app storage sizes | No unused apps, no cache sizes |
| `QUERY_ALL_PACKAGES` | Seeing the installed app list at all, under Android 11+ package visibility | The app list is nearly empty |
| `REQUEST_DELETE_PACKAGES` | Launching Android's uninstall dialog | Uninstall silently does nothing |

There is no `INTERNET` permission, so file lists and app names cannot leave the device even by accident. No account, no analytics, no crash reporting. Settings and the exclusion list live in local storage.

The two storage permissions and Usage Access are genuinely powerful, and Sweep does not pretend otherwise. Both are granted from Android's own settings screens, and Sweep can only open those screens for you.

---

## Known limitations

- **Usage history varies by device.** Sweep reads foreground events and all four usage buckets, but it cannot invent history a device never recorded. Apps without it stay under Usage unknown.
- **Restricted Settings.** Manually installed builds can be blocked from enabling Usage Access until you allow restricted settings for the app.
- **Sweep cannot clear another app's cache.** No third-party app can. It shows the sizes and opens the relevant Android page.
- **Sweep cannot uninstall anything itself.** It opens Android's dialog and checks afterwards whether the package is gone.
- **Deletions are permanent.** There is no recycle bin, and the confirmation sheet says so.
- **Play Store distribution would be difficult.** `MANAGE_EXTERNAL_STORAGE` and `QUERY_ALL_PACKAGES` are both restricted permissions requiring justification.
- **Exact duplicates only.** No perceptual or similar-image matching.

---

## Development status

**Functional MVP.** Core scanning, review, deletion, preview, cache reporting and unused-app detection all work on real hardware.

Next up:

- testing on more devices and older Android versions;
- checking how much usage history different devices actually expose;
- edge cases and reliability.

---

## Licence

MIT. See [LICENSE](LICENSE).
