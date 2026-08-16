package dev.sweep.core

import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.CleanupItem
import dev.sweep.core.scan.FileDeleter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileDeleterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun item(file: File, size: Long = file.length(), isDir: Boolean = file.isDirectory) =
        CleanupItem(
            path = file.absolutePath,
            name = file.name,
            size = size,
            lastModified = file.lastModified(),
            category = if (isDir) CleanupCategory.EMPTY_FOLDERS else CleanupCategory.DOWNLOADS,
            reasons = emptyList(),
            isSafeSuggestion = true,
            isDirectory = isDir,
        )

    @Test
    fun `deletes files and reports the bytes that actually left`() {
        val root = temp.newFolder()
        val a = root.writeFile("a.bin", 4_000, seed = 1, ageDays = 100)
        val b = root.writeFile("b.bin", 6_000, seed = 2, ageDays = 100)

        val outcome = FileDeleter.delete(listOf(item(a), item(b)))

        assertEquals(2, outcome.deletedCount)
        assertEquals(10_000L, outcome.bytesRecovered)
        assertFalse(outcome.hasFailures)
        assertFalse(a.exists())
        assertFalse(b.exists())
    }

    @Test
    fun `a file that vanished first is not counted as recovered`() {
        val root = temp.newFolder()
        val ghost = File(root, "gone.bin")

        val outcome = FileDeleter.delete(listOf(item(ghost, size = 5_000, isDir = false)))

        assertEquals(0, outcome.deletedCount)
        assertEquals(0L, outcome.bytesRecovered)
        assertEquals(1, outcome.alreadyGone)
        assertFalse(outcome.hasFailures)
    }

    @Test
    fun `a failed delete is reported honestly and contributes no bytes`() {
        val root = temp.newFolder()
        val stubborn = root.makeDir("not-empty")
        stubborn.writeFile("child.txt", 100, seed = 1, ageDays = 1)
        val ok = root.writeFile("ok.bin", 2_000, seed = 2, ageDays = 1)

        val outcome = FileDeleter.delete(listOf(item(stubborn), item(ok)))

        assertEquals(1, outcome.deletedCount)
        assertEquals(2_000L, outcome.bytesRecovered)
        assertEquals(1, outcome.failed.size)
        assertEquals("not-empty", outcome.failed.single().name)
        assertTrue(outcome.failed.single().reason.contains("added"))
        assertTrue(stubborn.exists())
        assertTrue("the file inside must survive", File(stubborn, "child.txt").exists())
    }

    @Test
    fun `a stale size is not trusted, the size at delete time is used`() {
        val root = temp.newFolder()
        val file = root.writeFile("shrunk.bin", 1_000, seed = 1, ageDays = 1)

        // The scan said 9 MB; by the time we delete it, it is 1 KB.
        val outcome = FileDeleter.delete(listOf(item(file, size = 9_000_000)))

        assertEquals(1_000L, outcome.bytesRecovered)
    }

    @Test
    fun `nested empty folders are removed deepest first`() {
        val root = temp.newFolder()
        val outer = root.makeDir("outer")
        val inner = File(outer, "inner").apply { mkdirs() }

        val outcome = FileDeleter.delete(listOf(item(outer), item(inner)))

        assertEquals(2, outcome.deletedCount)
        assertFalse(outer.exists())
    }

    @Test
    fun `an empty folder containing only empty folders is removed as a tree`() {
        // The scanner reports only the topmost folder of an empty chain, so this is the shape
        // the deleter actually receives. A plain delete() would fail on it.
        val root = temp.newFolder()
        val outer = root.makeDir("outer")
        outer.makeDir("a/b/c")

        val outcome = FileDeleter.delete(listOf(item(outer)))

        assertEquals(1, outcome.deletedCount)
        assertFalse(outcome.hasFailures)
        assertFalse(outer.exists())
    }

    @Test
    fun `a folder that gained a file since the scan is refused, not wiped`() {
        val root = temp.newFolder()
        val outer = root.makeDir("outer")
        outer.makeDir("nested")
        val appeared = outer.writeFile("nested/surprise.txt", 500, seed = 1, ageDays = 0)

        val outcome = FileDeleter.delete(listOf(item(outer)))

        assertEquals(0, outcome.deletedCount)
        assertEquals(1, outcome.failed.size)
        assertTrue("the file must survive", appeared.exists())
        assertTrue(outcome.failed.single().reason.contains("added"))
    }

    @Test
    fun `progress is reported for every item and cancellation stops early`() {
        val root = temp.newFolder()
        val files = (1..4).map { root.writeFile("f$it.bin", 1_000, seed = it, ageDays = 1) }

        val seen = mutableListOf<Pair<Int, Int>>()
        FileDeleter.delete(files.map { item(it) }, onProgress = { d, t -> seen += d to t })
        assertEquals(listOf(1 to 4, 2 to 4, 3 to 4, 4 to 4), seen)

        val more = (1..4).map { root.writeFile("g$it.bin", 1_000, seed = it, ageDays = 1) }
        val stopped = FileDeleter.delete(more.map { item(it) }, isActive = { false })
        assertEquals(0, stopped.deletedCount)
        assertTrue(more.all { it.exists() })
    }
}
