package dev.sweep.core

import dev.sweep.core.scan.FileClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileClassifierTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `extension is lowercased and safe on odd names`() {
        assertEquals("apk", FileClassifier.extensionOf("Telegram.APK"))
        assertEquals("", FileClassifier.extensionOf("Makefile"))
        assertEquals("", FileClassifier.extensionOf(".gitignore"))
        assertEquals("", FileClassifier.extensionOf("trailing."))
        assertEquals("gz", FileClassifier.extensionOf("archive.tar.gz"))
    }

    @Test
    fun `installers and archives are detected`() {
        assertTrue(FileClassifier.isInstaller("app-release.apk"))
        assertTrue(FileClassifier.isInstaller("game.xapk"))
        assertFalse(FileClassifier.isInstaller("notes.txt"))

        assertTrue(FileClassifier.isArchive("photos.zip"))
        assertTrue(FileClassifier.isArchive("backup.7z"))
        assertTrue(FileClassifier.isArchive("linux.iso"))
        assertFalse(FileClassifier.isArchive("song.mp3"))
    }

    @Test
    fun `screenshots detected by folder or filename, images only`() {
        assertTrue(FileClassifier.isScreenshot("/sdcard/Pictures/Screenshots/a.png", "a.png"))
        assertTrue(FileClassifier.isScreenshot("/sdcard/DCIM/Screenshot_2021.jpg", "Screenshot_2021.jpg"))
        assertFalse(FileClassifier.isScreenshot("/sdcard/Movies/Screenshot.mp4", "Screenshot.mp4"))
        assertFalse(FileClassifier.isScreenshot("/sdcard/DCIM/IMG_0001.jpg", "IMG_0001.jpg"))
    }

    @Test
    fun `downloads folder detected under either spelling`() {
        assertTrue(FileClassifier.isInDownloads("/storage/emulated/0/Download/a.pdf"))
        assertTrue(FileClassifier.isInDownloads("/storage/emulated/0/Downloads/sub/a.pdf"))
        assertFalse(FileClassifier.isInDownloads("/storage/emulated/0/Documents/a.pdf"))
    }

    @Test
    fun `copy suffix detected only at the end of the base name`() {
        assertEquals("(1)", FileClassifier.copySuffixOf("report(1).pdf"))
        assertEquals("(2)", FileClassifier.copySuffixOf("holiday photo (2).jpg"))
        assertNull(FileClassifier.copySuffixOf("report.pdf"))
        assertNull(FileClassifier.copySuffixOf("song (live) remix.mp3"))
    }

    @Test
    fun `age in days is clamped at zero for future timestamps`() {
        assertEquals(0, FileClassifier.ageDays(NOW, NOW + 5_000))
        assertEquals(0, FileClassifier.ageDays(NOW, 0L))
        assertEquals(142, FileClassifier.ageDays(NOW, daysAgo(142)))
    }

    @Test
    fun `extracted sibling folder is recognised`() {
        val root = temp.newFolder()
        root.writeFile("bundle.zip", 128, seed = 1, ageDays = 30)
        assertFalse(FileClassifier.hasExtractedSibling(java.io.File(root, "bundle.zip")))
        root.makeDir("bundle")
        assertTrue(FileClassifier.hasExtractedSibling(java.io.File(root, "bundle.zip")))
    }

    @Test
    fun `directories that belong to apps are skipped`() {
        val root = temp.newFolder()
        assertTrue(FileClassifier.shouldSkipDirectory(root.makeDir("Android")))
        val quiet = root.makeDir("SomeAppCache")
        assertFalse(FileClassifier.shouldSkipDirectory(quiet))
        quiet.writeFile(".nomedia", 0, seed = 0, ageDays = 0)
        assertTrue(FileClassifier.shouldSkipDirectory(quiet))
    }
}
