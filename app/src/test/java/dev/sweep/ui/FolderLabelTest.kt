package dev.sweep.ui

import dev.sweep.ui.screens.folderLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderLabelTest {

    @Test
    fun `folders are named relative to the user's storage`() {
        assertEquals("Download", folderLabel("/storage/emulated/0/Download/a.pdf"))
        assertEquals("Pictures/Screenshots", folderLabel("/storage/emulated/0/Pictures/Screenshots/s.png"))
        assertEquals("Main storage", folderLabel("/storage/emulated/0/loose.zip"))
    }

    @Test
    fun `removable storage drops the volume id`() {
        assertEquals("DCIM", folderLabel("/storage/1A2B-3C4D/DCIM/IMG_1.jpg"))
    }
}
