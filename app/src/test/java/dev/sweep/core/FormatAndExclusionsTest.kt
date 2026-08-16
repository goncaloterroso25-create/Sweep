package dev.sweep.core

import dev.sweep.core.model.AgeFormat
import dev.sweep.core.model.ByteFormat
import dev.sweep.core.scan.Exclusions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FormatAndExclusionsTest {

    private val en = Locale.UK

    @Test
    fun `bytes are formatted the way Android Settings reports storage`() {
        assertEquals("0 B", ByteFormat.short(0, en))
        assertEquals("512 B", ByteFormat.short(512, en))
        assertEquals("1.0 KB", ByteFormat.short(1_000, en))
        assertEquals("6.4 MB", ByteFormat.short(6_400_000, en))
        assertEquals("1.8 GB", ByteFormat.short(1_800_000_000, en))
        assertEquals("128 GB", ByteFormat.short(128_000_000_000, en))
    }

    @Test
    fun `value and unit are separable so the UI can typeset them apart`() {
        val formatted = ByteFormat.format(42_800_000_000, en)
        assertEquals("42.8", formatted.value)
        assertEquals("GB", formatted.unit)
    }

    @Test
    fun `negative or nonsense sizes never render as junk`() {
        assertEquals("0 B", ByteFormat.short(-1, en))
    }

    @Test
    fun `age wording stays natural across the ranges`() {
        assertEquals("today", AgeFormat.describe(0))
        assertEquals("yesterday", AgeFormat.describe(1))
        assertEquals("12 days ago", AgeFormat.describe(12))
        assertEquals("4 months ago", AgeFormat.describe(142))
        assertEquals("2 years ago", AgeFormat.describe(800))
        assertEquals("42d", AgeFormat.compact(42))
        assertEquals("4mo", AgeFormat.compact(142))
        assertEquals("2y", AgeFormat.compact(800))
    }

    @Test
    fun `excluding a folder excludes everything beneath it`() {
        val excluded = setOf("/storage/emulated/0/Pictures", "/storage/emulated/0/Download/a.pdf")

        assertTrue(Exclusions.isExcluded("/storage/emulated/0/Pictures", excluded))
        assertTrue(Exclusions.isExcluded("/storage/emulated/0/Pictures/Camera/x.jpg", excluded))
        assertTrue(Exclusions.isExcluded("/storage/emulated/0/Download/a.pdf", excluded))

        assertFalse(Exclusions.isExcluded("/storage/emulated/0/Download/b.pdf", excluded))
        // A sibling with a shared name prefix must not be swallowed.
        assertFalse(Exclusions.isExcluded("/storage/emulated/0/Pictures2/x.jpg", excluded))
        assertFalse(Exclusions.isExcluded("/storage/emulated/0/Music", excluded))
    }

    @Test
    fun `trailing separators and backslashes do not break matching`() {
        val excluded = setOf("/storage/emulated/0/Pictures/")
        assertTrue(Exclusions.isExcluded("/storage/emulated/0/Pictures", excluded))
        assertTrue(Exclusions.isExcluded("\\storage\\emulated\\0\\Pictures\\a.jpg", excluded))
        assertFalse(Exclusions.isExcluded("", excluded))
    }
}
