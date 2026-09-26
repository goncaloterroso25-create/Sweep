package dev.sweep.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Sweep's icon family.
 *
 * The rules every glyph follows:
 *  - 24 unit grid, 20 unit live area, drawn at 20dp in rows and 22dp in bars.
 *  - One stroke weight, 1.8, with round caps and round joins. Nothing is filled; a selected or
 *    active state is shown by the control around an icon, never by swapping in a solid glyph.
 *  - Containers take a 2.5 corner. Tighter reads as a Material icon, looser reads as a toy.
 *  - Strokes that carry meaning run horizontally, and where there are several they shorten as
 *    they descend, which is the mark's own geometry: slats in the bin, rows in a document, the
 *    bars left behind by the scan front.
 *  - The mark's detached fragment appears only where "something not yet dealt with" is the idea:
 *    the mark, the scan, an empty folder, an unknown.
 *
 * And the rule above the rules: brand never costs recognition. The bin is still a bin and the eye
 * is still an eye. Only the drawing changes.
 */
object SweepIcons {

    // ---- brand ------------------------------------------------------------------------------

    /** Three bars shortening as they descend, and the fragment the sweep has not reached yet. */
    val Mark: ImageVector = icon("Mark") {
        bar(6.5f, 7f, 18.5f)
        bar(10f, 12f, 18.5f)
        bar(13.5f, 17f, 18.5f)
        dot(6f, 17f)
    }

    // ---- chrome ------------------------------------------------------------------------------

    val Back: ImageVector = icon("Back") {
        moveTo(19f, 12f); lineTo(5.5f, 12f)
        moveTo(11f, 6.5f); lineTo(5.5f, 12f); lineTo(11f, 17.5f)
    }

    val ChevronRight: ImageVector = icon("ChevronRight") {
        moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f)
    }

    val ChevronDown: ImageVector = icon("ChevronDown") {
        moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f)
    }

    val Close: ImageVector = icon("Close") {
        moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
        moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
    }

    val Check: ImageVector = icon("Check") {
        moveTo(5f, 12.5f); lineTo(9.8f, 17.2f); lineTo(19f, 7f)
    }

    /** Two sliders: the mark's bar language doing an ordinary job. */
    val Settings: ImageVector = icon("Settings") {
        bar(4f, 8f, 11.5f)
        bar(17.5f, 8f, 20f)
        circle(14.5f, 8f, 2.6f)
        bar(4f, 16f, 6.5f)
        bar(12.5f, 16f, 20f)
        circle(9.5f, 16f, 2.6f)
    }

    // ---- actions ------------------------------------------------------------------------------

    /** The front, with sorted bars behind it and one fragment it has not reached. */
    val Scan: ImageVector = icon("Scan") {
        bar(4f, 7.5f, 11.5f)
        bar(6.5f, 12f, 11.5f)
        bar(9f, 16.5f, 11.5f)
        moveTo(15.5f, 4f); lineTo(15.5f, 20f)
        dot(19.5f, 12f)
    }

    /** A bin whose slats run across, shortening toward the base. */
    val Delete: ImageVector = icon("Delete") {
        bar(4.5f, 6.5f, 19.5f)
        moveTo(9.5f, 6.5f); lineTo(9.5f, 4.8f); curveTo(9.5f, 4.4f, 9.8f, 4f, 10.2f, 4f)
        lineTo(13.8f, 4f); curveTo(14.2f, 4f, 14.5f, 4.4f, 14.5f, 4.8f); lineTo(14.5f, 6.5f)
        moveTo(6.5f, 6.5f); lineTo(7.3f, 18.3f)
        curveTo(7.4f, 19.3f, 8.1f, 20f, 9.1f, 20f); lineTo(14.9f, 20f)
        curveTo(15.9f, 20f, 16.6f, 19.3f, 16.7f, 18.3f); lineTo(17.5f, 6.5f)
        bar(10f, 11f, 14f)
        bar(10.7f, 15f, 13.3f)
    }

    /** Circle and slash. Nothing else reads as "never suggest this again". */
    val Exclude: ImageVector = icon("Exclude") {
        circle(12f, 12f, 7.5f)
        moveTo(6.8f, 6.8f); lineTo(17.2f, 17.2f)
    }

    /** Looking at something before deciding. */
    val Preview: ImageVector = icon("Preview") {
        moveTo(3f, 12f)
        curveTo(5.2f, 7.8f, 8.3f, 5.5f, 12f, 5.5f)
        curveTo(15.7f, 5.5f, 18.8f, 7.8f, 21f, 12f)
        curveTo(18.8f, 16.2f, 15.7f, 18.5f, 12f, 18.5f)
        curveTo(8.3f, 18.5f, 5.2f, 16.2f, 3f, 12f)
        close()
        circle(12f, 12f, 2.8f)
    }

    /** Handing a file to another app: content leaving its frame. */
    val OpenExternal: ImageVector = icon("OpenExternal") {
        moveTo(11.5f, 5f); lineTo(7.5f, 5f)
        curveTo(6.1f, 5f, 5f, 6.1f, 5f, 7.5f); lineTo(5f, 16.5f)
        curveTo(5f, 17.9f, 6.1f, 19f, 7.5f, 19f); lineTo(16.5f, 19f)
        curveTo(17.9f, 19f, 19f, 17.9f, 19f, 16.5f); lineTo(19f, 12.5f)
        moveTo(13f, 11f); lineTo(19.5f, 4.5f)
        moveTo(14.5f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 9.5f)
    }

    /** Bars ordered by length, with the direction they are ordered in. */
    val Sort: ImageVector = icon("Sort") {
        bar(4.5f, 7f, 14.5f)
        bar(4.5f, 12f, 11.5f)
        bar(4.5f, 17f, 8.5f)
        moveTo(18.5f, 6f); lineTo(18.5f, 18f)
        moveTo(16f, 15.5f); lineTo(18.5f, 18f); lineTo(21f, 15.5f)
    }

    // ---- categories ---------------------------------------------------------------------------

    /** Two of the same thing, one behind the other. */
    val Duplicates: ImageVector = icon("Duplicates") {
        roundRect(8.5f, 3.5f, 20.5f, 15.5f, 2.5f)
        moveTo(15.5f, 20.5f); lineTo(6f, 20.5f)
        curveTo(4.6f, 20.5f, 3.5f, 19.4f, 3.5f, 18f); lineTo(3.5f, 8.5f)
    }

    /** Arriving from elsewhere and landing. */
    val Downloads: ImageVector = icon("Downloads") {
        moveTo(12f, 4f); lineTo(12f, 14.5f)
        moveTo(7.8f, 10.3f); lineTo(12f, 14.5f); lineTo(16.2f, 10.3f)
        bar(5f, 19.5f, 19f)
    }

    /** A package arriving into a phone: the file whose only job was to install something. */
    val Installers: ImageVector = icon("Installers") {
        roundRect(6f, 3f, 18f, 21f, 2.5f)
        moveTo(12f, 7.5f); lineTo(12f, 14f)
        moveTo(9.2f, 11.2f); lineTo(12f, 14f); lineTo(14.8f, 11.2f)
        bar(10.5f, 17.5f, 13.5f)
    }

    /** A box with its band, kept plain so it still reads as an archive at small sizes. */
    val Archives: ImageVector = icon("Archives") {
        roundRect(3.5f, 4.5f, 20.5f, 9.5f, 2f)
        moveTo(5f, 9.5f); lineTo(5f, 17.5f)
        curveTo(5f, 18.9f, 6.1f, 20f, 7.5f, 20f); lineTo(16.5f, 20f)
        curveTo(17.9f, 20f, 19f, 18.9f, 19f, 17.5f); lineTo(19f, 9.5f)
        bar(10f, 13.5f, 14f)
    }

    /** Corner brackets around a captured frame. */
    val Screenshots: ImageVector = icon("Screenshots") {
        moveTo(4f, 9f); lineTo(4f, 6.5f); curveTo(4f, 5.1f, 5.1f, 4f, 6.5f, 4f); lineTo(9f, 4f)
        moveTo(15f, 4f); lineTo(17.5f, 4f); curveTo(18.9f, 4f, 20f, 5.1f, 20f, 6.5f); lineTo(20f, 9f)
        moveTo(20f, 15f); lineTo(20f, 17.5f); curveTo(20f, 18.9f, 18.9f, 20f, 17.5f, 20f); lineTo(15f, 20f)
        moveTo(9f, 20f); lineTo(6.5f, 20f); curveTo(5.1f, 20f, 4f, 18.9f, 4f, 17.5f); lineTo(4f, 15f)
        bar(8.5f, 10f, 15.5f)
        bar(10f, 14f, 15.5f)
    }

    /** Stacked slabs: bulk. */
    val LargeFiles: ImageVector = icon("LargeFiles") {
        roundRect(3.5f, 4f, 20.5f, 9f, 2f)
        roundRect(3.5f, 11f, 20.5f, 16f, 2f)
        moveTo(3.5f, 18f); lineTo(3.5f, 18.5f)
        curveTo(3.5f, 19.3f, 4.2f, 20f, 5f, 20f); lineTo(19f, 20f)
        curveTo(19.8f, 20f, 20.5f, 19.3f, 20.5f, 18.5f); lineTo(20.5f, 18f)
    }

    /** A folder whose base has come apart: nothing inside is holding it up. */
    val EmptyFolders: ImageVector = icon("EmptyFolders") {
        moveTo(3.5f, 16f); lineTo(3.5f, 6.5f)
        curveTo(3.5f, 5.7f, 4.2f, 5f, 5f, 5f); lineTo(9.2f, 5f); lineTo(11.2f, 7.5f)
        lineTo(19f, 7.5f); curveTo(19.8f, 7.5f, 20.5f, 8.2f, 20.5f, 9f); lineTo(20.5f, 16f)
        bar(6f, 19f, 8.5f)
        bar(12f, 19f, 14.5f)
        dot(18.5f, 19f)
    }

    // ---- apps and storage -----------------------------------------------------------------------

    /** Four apps, one of them left to fade. */
    val Apps: ImageVector = icon("Apps") {
        roundRect(4f, 4f, 10.5f, 10.5f, 2.2f)
        roundRect(13.5f, 4f, 20f, 10.5f, 2.2f)
        roundRect(4f, 13.5f, 10.5f, 20f, 2.2f)
    }.plus(alpha = 0.4f) {
        roundRect(13.5f, 13.5f, 20f, 20f, 2.2f)
    }

    /** Layers of held data, stacked flat. */
    val Cache: ImageVector = icon("Cache") {
        moveTo(12f, 4f); lineTo(20f, 8f); lineTo(12f, 12f); lineTo(4f, 8f); close()
        moveTo(4f, 12f); lineTo(12f, 16f); lineTo(20f, 12f)
        moveTo(4f, 16f); lineTo(12f, 20f); lineTo(20f, 16f)
    }

    val Folder: ImageVector = icon("Folder") {
        moveTo(3.5f, 17.5f); lineTo(3.5f, 6.5f)
        curveTo(3.5f, 5.7f, 4.2f, 5f, 5f, 5f); lineTo(9.2f, 5f); lineTo(11.2f, 7.5f)
        lineTo(19f, 7.5f); curveTo(19.8f, 7.5f, 20.5f, 8.2f, 20.5f, 9f); lineTo(20.5f, 17.5f)
        curveTo(20.5f, 18.3f, 19.8f, 19f, 19f, 19f); lineTo(5f, 19f)
        curveTo(4.2f, 19f, 3.5f, 18.3f, 3.5f, 17.5f)
    }

    /** Time passing, for anything about when something last happened. */
    val Clock: ImageVector = icon("Clock") {
        circle(12f, 12f, 8f)
        moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15f, 14f)
    }

    /** Storage Sweep has not been allowed to read. */
    val Lock: ImageVector = icon("Lock") {
        roundRect(4.5f, 10f, 19.5f, 20f, 2.5f)
        moveTo(8f, 10f); lineTo(8f, 7.5f)
        curveTo(8f, 5.3f, 9.8f, 3.5f, 12f, 3.5f)
        curveTo(14.2f, 3.5f, 16f, 5.3f, 16f, 7.5f); lineTo(16f, 10f)
    }

    val Warning: ImageVector = icon("Warning") {
        moveTo(10.3f, 5.3f)
        curveTo(11.1f, 4f, 12.9f, 4f, 13.7f, 5.3f)
        lineTo(20.2f, 16.8f)
        curveTo(21f, 18.1f, 20.1f, 19.5f, 18.6f, 19.5f)
        lineTo(5.4f, 19.5f)
        curveTo(3.9f, 19.5f, 3f, 18.1f, 3.8f, 16.8f)
        close()
        moveTo(12f, 9.5f); lineTo(12f, 13.5f)
        dot(12f, 16.5f)
    }

    /** Unknown rather than absent: a question that trails off into a fragment. */
    val Unknown: ImageVector = icon("Unknown") {
        circle(12f, 12f, 8f)
        moveTo(9.4f, 9.6f)
        curveTo(9.4f, 8.2f, 10.6f, 7.2f, 12f, 7.2f)
        curveTo(13.5f, 7.2f, 14.7f, 8.3f, 14.7f, 9.7f)
        curveTo(14.7f, 11.4f, 12f, 11.8f, 12f, 13.8f)
        dot(12f, 16.8f)
    }

    val Info: ImageVector = icon("Info") {
        circle(12f, 12f, 8f)
        moveTo(12f, 11f); lineTo(12f, 16f)
        dot(12f, 7.8f)
    }

    val Notification: ImageVector = icon("Notification") {
        moveTo(6f, 16f); lineTo(6f, 10.5f)
        curveTo(6f, 7.2f, 8.7f, 4.5f, 12f, 4.5f)
        curveTo(15.3f, 4.5f, 18f, 7.2f, 18f, 10.5f); lineTo(18f, 16f)
        bar(4.5f, 16f, 19.5f)
        moveTo(10f, 19f)
        curveTo(10.4f, 19.9f, 11.1f, 20.4f, 12f, 20.4f)
        curveTo(12.9f, 20.4f, 13.6f, 19.9f, 14f, 19f)
    }

    /** Kept: the copy of a duplicate Sweep will not touch. A shield, reduced to its outline. */
    val Keep: ImageVector = icon("Keep") {
        moveTo(12f, 3.5f); lineTo(18.5f, 6f); lineTo(18.5f, 11.5f)
        curveTo(18.5f, 15.6f, 15.8f, 18.9f, 12f, 20.5f)
        curveTo(8.2f, 18.9f, 5.5f, 15.6f, 5.5f, 11.5f); lineTo(5.5f, 6f); close()
        moveTo(9f, 12f); lineTo(11.2f, 14.2f); lineTo(15f, 10f)
    }
}

// ---- construction -------------------------------------------------------------------------------

private const val STROKE = 1.8f

private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = "Sweep.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    ).build()

/**
 * Adds a second path at reduced strength. Rebuilds rather than mutates because an [ImageVector]
 * is immutable once built; these are created once, at class load.
 */
private fun ImageVector.plus(alpha: Float, block: PathBuilder.() -> Unit): ImageVector {
    val base = this
    return ImageVector.Builder(
        name = base.name,
        defaultWidth = base.defaultWidth,
        defaultHeight = base.defaultHeight,
        viewportWidth = base.viewportWidth,
        viewportHeight = base.viewportHeight,
    ).apply {
        base.root.forEach { node ->
            if (node is VectorPath) {
                addPath(
                    pathData = node.pathData,
                    stroke = node.stroke,
                    strokeAlpha = node.strokeAlpha,
                    strokeLineWidth = node.strokeLineWidth,
                    strokeLineCap = node.strokeLineCap,
                    strokeLineJoin = node.strokeLineJoin,
                )
            }
        }
    }.path(
        stroke = SolidColor(Color.Black),
        strokeAlpha = alpha,
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    ).build()
}

/** A horizontal stroke: the unit the family is built from. */
private fun PathBuilder.bar(startX: Float, y: Float, endX: Float) {
    moveTo(startX, y)
    lineTo(endX, y)
}

/** The mark's leftover fragment: a round cap with almost no length. */
private fun PathBuilder.dot(x: Float, y: Float) {
    moveTo(x, y)
    lineTo(x + 0.01f, y)
}

private fun PathBuilder.circle(centreX: Float, centreY: Float, radius: Float) {
    moveTo(centreX - radius, centreY)
    arcToRelative(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = true, radius * 2, 0f)
    arcToRelative(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = true, -radius * 2, 0f)
    close()
}

private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
    moveTo(left + radius, top)
    lineTo(right - radius, top)
    arcToRelative(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, radius, radius)
    lineTo(right, bottom - radius)
    arcToRelative(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, -radius, radius)
    lineTo(left + radius, bottom)
    arcToRelative(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, -radius, -radius)
    lineTo(left, top + radius)
    arcToRelative(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, radius, -radius)
    close()
}
