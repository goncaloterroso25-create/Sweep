package dev.sweep.core

import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.CleanupItem
import dev.sweep.core.model.DuplicateInfo
import dev.sweep.core.scan.SafetyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastCopyTest {

    private val info = DuplicateInfo(groupId = "g", copiesInGroup = 3, keeperPath = "/s/Docs/a.pdf", keeperName = "a.pdf")

    private fun copy(path: String) = CleanupItem(
        path = path, name = "a.pdf", size = 10, lastModified = 0, category = CleanupCategory.DUPLICATES,
        reasons = emptyList(), isSafeSuggestion = true, duplicate = info,
    )

    /** The kept copy, also listed under Old downloads by coincidence of age and location. */
    private val keeperElsewhere = CleanupItem(
        path = info.keeperPath, name = "a.pdf", size = 10, lastModified = 0, category = CleanupCategory.DOWNLOADS,
        reasons = emptyList(), isSafeSuggestion = false,
    )

    private val items = listOf(copy("/s/Download/a.pdf"), copy("/s/WhatsApp/a.pdf"), keeperElsewhere)

    @Test
    fun `selecting the copies alone always leaves the kept one`() {
        val selected = setOf("/s/Download/a.pdf", "/s/WhatsApp/a.pdf")
        assertTrue(SafetyPolicy.groupsLeftWithoutACopy(items, selected).isEmpty())
    }

    @Test
    fun `selecting the kept copy by hand as well is reported`() {
        val selected = setOf("/s/Download/a.pdf", "/s/WhatsApp/a.pdf", info.keeperPath)
        assertEquals(listOf("a.pdf"), SafetyPolicy.groupsLeftWithoutACopy(items, selected))
    }

    @Test
    fun `a copy left out of the selection is a survivor`() {
        val selected = setOf("/s/Download/a.pdf", info.keeperPath)
        assertTrue(SafetyPolicy.groupsLeftWithoutACopy(items, selected).isEmpty())
    }

    @Test
    fun `a copy excluded from the results still survives on disk`() {
        // Only one of the two removable copies made it into the results; the other was excluded.
        val shown = listOf(copy("/s/Download/a.pdf"), keeperElsewhere)
        val selected = setOf("/s/Download/a.pdf", info.keeperPath)
        assertTrue(SafetyPolicy.groupsLeftWithoutACopy(shown, selected).isEmpty())
    }
}
