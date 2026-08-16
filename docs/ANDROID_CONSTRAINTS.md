# What Android actually allows

This document records what a third-party storage cleaner can and cannot do on modern Android, the
choice Sweep made in each case, and what would have to change before a Play Store release.

It exists because most of the interesting decisions in this project are *platform* decisions, and
because "we can't do that, so here's the honest alternative" is a design constraint worth writing
down rather than quietly working around.

---

## 1. Scoped storage and `MANAGE_EXTERNAL_STORAGE`

**The constraint.** From Android 10 (API 29), apps no longer get free rein over shared storage.
From Android 11 (API 30), broad access requires `MANAGE_EXTERNAL_STORAGE` ("All files access"),
granted from a Settings screen rather than a runtime dialog. Even with it, `Android/data` and
`Android/obb` remain unreadable.

**Why Sweep needs it.** Duplicate detection and stale-download analysis are meaningless over a
handful of MediaStore collections. You cannot find that a PDF in `Documents/` is byte-identical to
one in `Download/` through MediaStore, and you cannot enumerate arbitrary archives at all.

**What Sweep does.**

| Android version | Mechanism |
|---|---|
| 11+ (API 30+) | `Environment.isExternalStorageManager()`; opens `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` with a fallback chain |
| 10 (API 29) | `READ`/`WRITE_EXTERNAL_STORAGE` + `requestLegacyExternalStorage="true"` |
| 8–9 (API 26–28) | `READ`/`WRITE_EXTERNAL_STORAGE` runtime prompt |

Because the permission is granted in Settings and not by a dialog, `MainActivity.onResume()`
re-reads it every time. Sweep never assumes it still has access.

If it is denied, the app opens normally, the scan button is disabled, and a card explains what is
unavailable. `Android/`, `.thumbnails`, `LOST.DIR` and any folder containing `.nomedia` are skipped
by the scanner regardless of permission — those belong to other apps.

**Before a Play Store release.** `MANAGE_EXTERNAL_STORAGE` is a restricted permission. Google
grants it only to a narrow set of categories (file managers, backup/restore, antivirus, on-device
search). A store submission would need either an approved declaration or the MediaStore fallback
described in roadmap item 5 — which would genuinely reduce what the app can find, and should be
described as such rather than papered over.

---

## 2. Deleting files

**The constraint.** With All Files Access, `File.delete()` works across primary shared storage.
Without it, deleting media requires `MediaStore.createDeleteRequest()` and a system confirmation.

**What Sweep does.** Direct `File.delete()`, then `MediaScannerConnection.scanFile()` on the
deleted paths so MediaStore drops its stale rows — otherwise the gallery keeps showing thumbnails
for pictures that are gone, and the app looks broken even though it worked.

**Honesty rules baked into `FileDeleter`:**

- File size is re-read immediately before the delete, so a stale scan figure can never inflate the
  result.
- Bytes are counted only after `!file.exists()` confirms the file is actually gone.
- Files that vanished on their own are reported as `alreadyGone` — not as successes, and worth
  zero bytes.
- Failures carry a reason and are shown on the completion screen.
- Items are deleted deepest-path-first so an empty parent folder can still go after its children.

---

## 3. Package visibility

**The constraint.** From Android 11, `getInstalledApplications()` returns a filtered list unless
the app declares `<queries>` or holds `QUERY_ALL_PACKAGES`.

**What Sweep does.** Declares `QUERY_ALL_PACKAGES`. A `<queries>` element cannot express "every app
the user installed", which is precisely the question this feature asks.

**Before a Play Store release.** Also a restricted permission requiring justification. An app that
only offered per-app cache navigation could avoid it; an "unused apps" feature cannot.

---

## 4. Usage Access and `UsageStatsManager`

**The constraint.** `PACKAGE_USAGE_STATS` is a signature-level permission granted through
Settings → Special app access → Usage access. It cannot be requested with a runtime dialog. Data
retention is roughly: daily buckets ~7 days, weekly ~4 weeks, monthly ~6 months, yearly ~2 years.

**What Sweep does.** Checks the grant via `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS)`
(with `MODE_DEFAULT` falling back to a permission check), and queries
`queryAndAggregateUsageStats()` over a 730-day window. `lastTimeVisible` is folded in on API 29+.

**The honesty point.** Because retention is finite, an app with no entry has not necessarily never
been opened — it may simply not have been opened *recently enough to still be on record*. Sweep
reports this as **"No usage on record"** and never as "never opened", and
`UnusedAppPolicy` additionally refuses to flag such an app unless it has also been *installed*
longer than the threshold, so a fresh install never appears as unused.

Usage Access is also what unlocks `StorageStatsManager.queryStatsForPackage()`, which is where
per-app app/data/cache sizes come from. `dataBytes` already includes `cacheBytes`, so Sweep
subtracts it rather than counting those bytes twice.

---

## 5. Uninstalling apps

**The constraint.** No third-party app can silently uninstall another. `PackageInstaller.uninstall()`
requires `REQUEST_DELETE_PACKAGES` and still shows a system dialog.

**What Sweep does.** Launches `Intent.ACTION_DELETE` with a `package:` URI — which needs no extra
permission at all — through an activity-result launcher, then reloads the package list on return
and reports what actually happened. The result code from the uninstall dialog is unreliable across
OEMs; re-reading the package list is not.

The UI says so in as many words: _"Android runs the uninstall. Sweep opens the system dialog and
checks afterwards whether the app is really gone."_

---

## 6. Cache management

**The constraint.** This is the one cleaner apps lie about most. An app **cannot** delete another
app's cache. `PackageManager.deleteApplicationCacheFiles()` is `@hide` and system-only.
`freeStorageAndNotify()` requires the privileged `CLEAR_APP_CACHE` permission.

**What Sweep does — all four of these are real:**

1. Reads per-app `cacheBytes` via `StorageStatsManager` (needs Usage Access) so you can see where
   the space is.
2. On Android 12+ (API 31), opens `StorageManager.ACTION_CLEAR_APP_CACHE` — a genuine system
   dialog that clears cached data across all apps. **Android** performs and confirms the work.
3. Opens a specific app's storage page via `ACTION_APPLICATION_DETAILS_SETTINGS`.
4. Clears its own cache — the only cache it owns — and shows the figure alongside the others
   precisely so the difference between "Sweep did this" and "Android did this" stays visible.

Below API 31 there is no bulk system dialog, so Sweep opens storage settings and says why.

At no point does Sweep report another app's cache as space *it* recovered.

---

## 7. Storage figures

`StatFs` under-reports total capacity because it does not account for filesystem reserve, which is
why a device that Settings calls 128 GB reads as ~119 GB through `StatFs`. Sweep uses
`StorageStatsManager.getTotalBytes()/getFreeBytes()` (API 26+) so its numbers agree with the
Settings app, and falls back to `StatFs` only if the stats service refuses.

Byte formatting is base 1000, matching Android's own convention. Using base 1024 would make every
figure in the app disagree with the system.

Secondary volumes are enumerated via `StorageManager.storageVolumes`. `StorageVolume.getDirectory()`
only exists from API 30, so on older releases Sweep reports the primary volume rather than guessing
at paths.

---

## 8. Blur and translucency

**The constraint.** Android has no backdrop-blur primitive for an ordinary composable inside the
view tree. `RenderEffect` blurs a view's *own* content. Real backdrop blur exists only at the
window level, from API 31, and the system may disable it for battery saver or low-end devices.

**What Sweep does.** Floating surfaces use layered translucency with a lit top edge and a scrim
beneath — the optics of glass at zero per-frame cost. Modal sheets, which are real windows and
short-lived, request genuine `FLAG_BLUR_BEHIND` and check
`WindowManager.isCrossWindowBlurEnabled` first, degrading silently to the translucent surface.

This is a deliberate trade: a blur library would give truer glass and cost a full-screen render
pass on every frame of every scroll, which is the wrong bet for an app whose main promise is that
it feels fast.

---

## 9. Version differences at a glance

| Feature | 8.0–9 (26–28) | 10 (29) | 11 (30) | 12+ (31+) |
|---|---|---|---|---|
| File access | runtime R/W | legacy flag | All files access | All files access |
| `Android/data` readable | yes | yes | **no** | **no** |
| Package list | full | full | needs `QUERY_ALL_PACKAGES` | same |
| Bulk cache dialog | — | — | — | ✅ `ACTION_CLEAR_APP_CACHE` |
| Volume directories | primary only | primary only | ✅ `getDirectory()` | ✅ |
| Window blur | — | — | — | ✅ if enabled |
| `lastTimeVisible` | — | ✅ (29+) | ✅ | ✅ |

---

## 10. Summary: what a Play Store release would need

1. A declared-permissions justification for `MANAGE_EXTERNAL_STORAGE`, or the MediaStore fallback
   variant, with the reduced capability described plainly to users.
2. A declared-permissions justification for `QUERY_ALL_PACKAGES`, or dropping the unused-apps
   feature.
3. A signing config and a real release keystore.
4. A privacy policy URL (the content is trivial — nothing leaves the device — but the listing
   requires one).
5. On-device validation across at least API 26, 29, 30 and 34+, since the storage rules differ
   materially at each of those boundaries.

None of that is blocking for a portfolio project, but none of it should be hand-waved either.
