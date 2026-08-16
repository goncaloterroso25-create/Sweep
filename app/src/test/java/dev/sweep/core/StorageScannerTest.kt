package dev.sweep.core

import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.CleanupItem
import dev.sweep.core.model.ScanConfig
import dev.sweep.core.model.ScanResult
import dev.sweep.core.model.ScanUpdate
import dev.sweep.core.scan.StorageScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Exercises the whole pipeline against a real directory tree on disk, which is the only
 * way to be confident the walk, the classification and the duplicate promotion agree.
 */
class StorageScannerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var root: File

    private val config = ScanConfig(
        oldFileThresholdDays = 60,
        largeFileThresholdBytes = 100_000,
        oldScreenshotThresholdDays = 180,
        minDuplicateSizeBytes = 4_096,
    )

    @Before
    fun buildTree() {
        root = temp.newFolder("sdcard")

        // Downloads: an old document, old media, two installers, two archives.
        root.writeFile("Download/manual.pdf", 50_000, seed = 1, ageDays = 142)
        root.writeFile("Download/holiday.jpg", 20_000, seed = 2, ageDays = 142)
        root.writeFile("Download/app-v1.apk", 30_000, seed = 3, ageDays = 40)
        root.writeFile("Download/app-v2.apk", 31_000, seed = 4, ageDays = 1)
        root.writeFile("Download/bundle.zip", 10_000, seed = 5, ageDays = 40)
        root.writeFile("Download/bundle/readme.txt", 500, seed = 6, ageDays = 0)
        root.writeFile("Download/loose.zip", 12_000, seed = 7, ageDays = 40)

        // An exact duplicate pair, both old enough to look like plain downloads first.
        root.writeFile("Download/report.pdf", 9_000, seed = 8, ageDays = 100)
        root.writeFile("Download/report(1).pdf", 9_000, seed = 8, ageDays = 90)

        // A large video and an old screenshot.
        root.writeFile("Movies/trip.mp4", 180_000, seed = 9, ageDays = 5)
        root.writeFile("Pictures/Screenshots/Screenshot_001.png", 8_000, seed = 10, ageDays = 400)

        // Nothing inside these.
        root.makeDir("Empty/Nested")

        // App-private storage must never be walked.
        root.writeFile("Android/data/com.example/files/huge.bin", 500_000, seed = 11, ageDays = 900)
    }

    private fun scan(
        cfg: ScanConfig = config,
        exclusions: Set<String> = emptySet(),
    ): ScanResult = runBlocking {
        val updates = StorageScanner(clock = { NOW }).scan(listOf(root), cfg, exclusions).toList()
        (updates.last() as ScanUpdate.Complete).result
    }

    private fun ScanResult.paths(category: CleanupCategory): Set<String> =
        byCategory[category].orEmpty().map { File(it.path).name }.toSet()

    private fun ScanResult.item(name: String): CleanupItem? = items.firstOrNull { it.name == name }

    @Test
    fun `app private storage is never scanned`() {
        val result = scan()
        assertNull(result.item("huge.bin"))
        assertTrue(result.items.none { it.path.contains("Android") })
    }

    @Test
    fun `installers are surfaced and only the settled one is pre-selected`() {
        val result = scan()
        assertEquals(setOf("app-v1.apk", "app-v2.apk"), result.paths(CleanupCategory.INSTALLERS))
        assertTrue(result.item("app-v1.apk")!!.isSafeSuggestion)
        assertFalse("a one-day-old installer may still be mid-install",
            result.item("app-v2.apk")!!.isSafeSuggestion)
    }

    @Test
    fun `archives are pre-selected only when an extracted folder sits beside them`() {
        val result = scan()
        assertEquals(setOf("bundle.zip", "loose.zip"), result.paths(CleanupCategory.ARCHIVES))
        assertTrue(result.item("bundle.zip")!!.isSafeSuggestion)
        assertFalse(result.item("loose.zip")!!.isSafeSuggestion)
    }

    @Test
    fun `old downloads are pre-selected but personal media is not`() {
        val result = scan()
        assertTrue(result.item("manual.pdf")!!.isSafeSuggestion)
        assertFalse("a downloaded photo is still the user's photo",
            result.item("holiday.jpg")!!.isSafeSuggestion)
        // Not old enough to qualify.
        assertNull(result.item("readme.txt"))
    }

    @Test
    fun `a duplicate is promoted out of downloads and its keeper stays behind`() {
        val result = scan()
        val copy = result.item("report(1).pdf")!!
        assertEquals(CleanupCategory.DUPLICATES, copy.category)
        assertTrue(copy.isSafeSuggestion)
        assertEquals(2, copy.duplicate!!.copiesInGroup)
        assertEquals("report.pdf", copy.duplicate!!.keeperName)

        val keeper = result.item("report.pdf")!!
        assertEquals(CleanupCategory.DOWNLOADS, keeper.category)
        assertEquals(1, result.byCategory[CleanupCategory.DUPLICATES]!!.size)
    }

    @Test
    fun `the kept copy is never pre-selected, so select-safe cannot remove every copy`() {
        val result = scan()
        // report.pdf is an old non-media download, which on its own would be a safe suggestion.
        // Being the surviving copy of a duplicate group has to override that.
        val keeper = result.item("report.pdf")!!
        assertFalse(
            "selecting every safe item must never delete all copies of a file",
            keeper.isSafeSuggestion,
        )
        assertTrue(keeper.reasons.any { it is dev.sweep.core.model.Reason.DuplicateCopies })

        val safePaths = result.items.filter { it.isSafeSuggestion }.map { it.path }.toSet()
        val group = listOf("report.pdf", "report(1).pdf").map { result.item(it)!!.path }
        assertTrue(
            "at least one copy of every duplicate group must survive a safe selection",
            group.any { it !in safePaths },
        )
    }

    @Test
    fun `screenshots and large files are shown but never pre-selected`() {
        val result = scan()
        val shot = result.item("Screenshot_001.png")!!
        assertEquals(CleanupCategory.SCREENSHOTS, shot.category)
        assertFalse(shot.isSafeSuggestion)

        val video = result.item("trip.mp4")!!
        assertEquals(CleanupCategory.LARGE_FILES, video.category)
        assertFalse(video.isSafeSuggestion)
    }

    @Test
    fun `only the topmost empty folder is reported`() {
        val result = scan()
        val empties = result.byCategory[CleanupCategory.EMPTY_FOLDERS].orEmpty()
        assertEquals(1, empties.size)
        assertEquals("Empty", empties.single().name)
        assertTrue(empties.single().isDirectory)
    }

    @Test
    fun `Android's own shared folders are never offered for deletion`() {
        // A fresh device has several of these sitting empty. Offering to delete DCIM is noise.
        listOf("DCIM", "Alarms", "Ringtones", "Podcasts", "Notifications").forEach(root::makeDir)

        val empties = scan().byCategory[CleanupCategory.EMPTY_FOLDERS].orEmpty().map { it.name }
        assertEquals(listOf("Empty"), empties)
    }

    @Test
    fun `headline figure counts safe suggestions only and never double-counts`() {
        val result = scan()
        val safe = result.items.filter { it.isSafeSuggestion }
        assertEquals(safe.sumOf { it.size }, result.suggestedBytes)
        assertEquals(result.items.size, result.items.map { it.path }.distinct().size)
        assertTrue(result.suggestedBytes < result.totalFoundBytes)
    }

    @Test
    fun `progress arrives before the result and categories fill in`() = runBlocking {
        val updates: List<ScanUpdate> =
            StorageScanner(clock = { NOW }).scan(listOf(root), config).toList()
        assertTrue(updates.size >= 2)
        assertTrue(updates.dropLast(1).all { it is ScanUpdate.Progress })
        assertTrue(updates.last() is ScanUpdate.Complete)
        val last = updates.dropLast(1).last() as ScanUpdate.Progress
        assertTrue(last.filesSeen > 0)
        assertNotNull(last.partial.firstOrNull { it.category == CleanupCategory.INSTALLERS })
    }

    @Test
    fun `exclusions remove a file and everything under a folder`() {
        val result = scan(
            exclusions = setOf(
                File(root, "Download/manual.pdf").absolutePath,
                File(root, "Pictures").absolutePath,
            )
        )
        assertNull(result.item("manual.pdf"))
        assertNull(result.item("Screenshot_001.png"))
        assertNotNull(result.item("loose.zip"))
    }

    /** Runs a scan whose stop flag flips after [afterChecks] directory checks. */
    private fun scanStoppingAfter(afterChecks: Int): ScanResult = runBlocking {
        var checks = 0
        val updates = StorageScanner(clock = { NOW })
            .scan(listOf(root), config, emptySet()) { checks++ >= afterChecks }
            .toList()
        (updates.last() as ScanUpdate.Complete).result
    }

    @Test
    fun `stopping immediately still completes rather than hanging or throwing`() {
        val result = scanStoppingAfter(0)
        assertTrue("a stopped scan must still emit a result", result.stoppedEarly)
        assertEquals(0, result.filesScanned)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `stopping mid-walk keeps what was already found`() {
        val partial = scanStoppingAfter(2)
        assertTrue(partial.stoppedEarly)
        assertTrue(
            "a stopped scan must report less than a full one",
            partial.filesScanned < scan().filesScanned,
        )
    }

    @Test
    fun `a scan that runs to the end is not marked as stopped`() {
        assertFalse(scan().stoppedEarly)
    }

    @Test
    fun `a clean tree produces an empty result rather than an error`() = runBlocking {
        val clean = temp.newFolder("clean")
        clean.writeFile("notes.txt", 100, seed = 1, ageDays = 2)
        val updates: List<ScanUpdate> =
            StorageScanner(clock = { NOW }).scan(listOf(clean), config).toList()
        val result = (updates.last() as ScanUpdate.Complete).result
        assertTrue(result.items.isEmpty())
        assertEquals(0L, result.suggestedBytes)
        assertEquals(1, result.filesScanned)
        assertTrue(result.summaries().all { it.isEmpty })
    }

    @Test
    fun `a missing root is tolerated`() {
        val result = runBlocking {
            val updates: Flow<ScanUpdate> = StorageScanner(clock = { NOW })
                .scan(listOf(File(root, "does-not-exist")), config)
            (updates.toList().last() as ScanUpdate.Complete).result
        }
        assertEquals(0, result.filesScanned)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `raising the age threshold drops borderline downloads`() {
        val strict = scan(config.copy(oldFileThresholdDays = 180))
        assertNull(strict.item("manual.pdf"))
        // Installers and archives use their own grace period, so they stay.
        assertNotNull(strict.item("app-v1.apk"))
    }
}
