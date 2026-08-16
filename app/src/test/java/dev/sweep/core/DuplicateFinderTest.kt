package dev.sweep.core

import dev.sweep.core.scan.DuplicateFinder
import dev.sweep.core.scan.Hashing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DuplicateFinderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun scan(root: File, minSize: Long = 4L * 1024) =
        DuplicateFinder.find(root.walkTopDown().filter { it.isFile }.toList(), minSize)

    @Test
    fun `same size but different content is not a duplicate`() {
        val root = temp.newFolder()
        root.writeFile("a.bin", 8_192, seed = 1, ageDays = 10)
        root.writeFile("b.bin", 8_192, seed = 2, ageDays = 10)
        assertTrue(scan(root).isEmpty())
    }

    @Test
    fun `identical files are grouped and exactly one copy is kept`() {
        val root = temp.newFolder()
        root.writeFile("photos/holiday.jpg", 20_000, seed = 7, ageDays = 300)
        root.writeFile("photos/holiday copy.jpg", 20_000, seed = 7, ageDays = 100)
        root.writeFile("Download/holiday.jpg", 20_000, seed = 7, ageDays = 50)

        val groups = scan(root)
        assertEquals(1, groups.size)
        val group = groups.single()
        assertEquals(3, group.totalCopies)
        assertEquals(2, group.copies.size)
        assertEquals(20_000L * 2, group.reclaimableBytes)
        // Oldest, outside Downloads, wins.
        assertEquals("holiday.jpg", group.keeper.name)
        assertTrue(group.keeper.absolutePath.contains("photos"))
    }

    @Test
    fun `a file with a copy suffix is never the keeper`() {
        val root = temp.newFolder()
        // The suffixed one is deliberately the older file, so only the suffix rule can save it.
        root.writeFile("Download/report(1).pdf", 9_000, seed = 3, ageDays = 400)
        root.writeFile("Download/report.pdf", 9_000, seed = 3, ageDays = 10)

        val group = scan(root).single()
        assertEquals("report.pdf", group.keeper.name)
        assertEquals("report(1).pdf", group.copies.single().name)
    }

    @Test
    fun `files below the minimum size are ignored`() {
        val root = temp.newFolder()
        root.writeFile("tiny-a.txt", 200, seed = 5, ageDays = 10)
        root.writeFile("tiny-b.txt", 200, seed = 5, ageDays = 10)
        assertTrue(scan(root).isEmpty())
        assertEquals(1, scan(root, minSize = 100).size)
    }

    @Test
    fun `duplicates larger than the prefix window still match on full hash`() {
        val root = temp.newFolder()
        val size = Hashing.PREFIX_BYTES * 2
        root.writeFile("big-a.bin", size, seed = 11, ageDays = 30)
        root.writeFile("big-b.bin", size, seed = 11, ageDays = 30)
        assertEquals(1, scan(root).single().copies.size)
    }

    @Test
    fun `files sharing a prefix but differing later are not duplicates`() {
        val root = temp.newFolder()
        val size = Hashing.PREFIX_BYTES + 4_096
        val shared = ByteArray(size) { i -> (i % 251).toByte() }
        File(root, "x.bin").writeBytes(shared)
        File(root, "y.bin").writeBytes(shared.copyOf().also { it[size - 1] = 99 })

        assertEquals(
            Hashing.prefixHash(File(root, "x.bin")),
            Hashing.prefixHash(File(root, "y.bin")),
        )
        assertTrue(scan(root).isEmpty())
    }

    @Test
    fun `hash progress is reported and cancellation stops the work`() {
        val root = temp.newFolder()
        repeat(6) { i -> root.writeFile("dup$i.bin", 8_192, seed = 4, ageDays = 10) }

        var ticks = 0
        DuplicateFinder.find(
            files = root.listFiles()!!.toList(),
            minSize = 4L * 1024,
            onHashProgress = { _, _ -> ticks++ },
        )
        assertEquals(6, ticks)

        val stopped = DuplicateFinder.find(
            files = root.listFiles()!!.toList(),
            minSize = 4L * 1024,
            isActive = { false },
        )
        assertTrue(stopped.isEmpty())
    }
}
