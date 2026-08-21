package dev.sweep.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Sweep's own icons.
 *
 * The app was using stock Material icons, which is the single thing that made a carefully built
 * interface still look like a Compose sample. These are drawn from the same geometry as the brand
 * mark: round-capped strokes of one weight, horizontal emphasis, and lines that shorten as they
 * descend. Several icons carry the mark's small detached fragment, the piece not yet swept.
 *
 * The rule applied throughout: brand expression never costs recognition. Where the conventional
 * shape is the only shape a user will read instantly, such as the trash can or the back arrow,
 * the convention wins and only the drawing style changes.
 *
 * All of them are 24dp stroke vectors on a 24 unit grid, so `Icon` tints them like any other
 * vector asset and they stay sharp at every density.
 */
object SweepIcons {

    // ---- brand ------------------------------------------------------------------------------

    /** The mark itself: three bars shortening as they descend, with one fragment left behind. */
    val Mark: ImageVector = icon("Mark") {
        bar(6.5f, 7f, 18.5f)
        bar(10f, 12f, 18.5f)
        bar(13.5f, 17f, 18.5f)
        dot(6f, 17f)
    }

    // ---- navigation and chrome --------------------------------------------------------------

    val Back: ImageVector = icon("Back") {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }

    val ChevronRight: ImageVector = icon("ChevronRight") {
        moveTo(9.5f, 5.5f); lineTo(16f, 12f); lineTo(9.5f, 18.5f)
    }

    val Close: ImageVector = icon("Close") {
        moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
        moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
    }

    val Check: ImageVector = icon("Check") {
        moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 6.5f)
    }

    /** Sliders, which is the mark's bar language doing an ordinary job. */
    val Settings: ImageVector = icon("Settings") {
        bar(4f, 8f, 20f)
        bar(4f, 16f, 20f)
    }.plus {
        circle(14.5f, 8f, 2.4f)
        circle(9.5f, 16f, 2.4f)
    }

    // ---- actions ----------------------------------------------------------------------------

    /** A sweep that comes back round: the field gets read again. */
    val Rescan: ImageVector = icon("Rescan") {
        moveTo(20f, 12f)
        arcToRelative(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = false, -2.4f, -5.7f)
        moveTo(20f, 5.5f); lineTo(20f, 11.5f); lineTo(14f, 11.5f)
    }

    val Delete: ImageVector = icon("Delete") {
        moveTo(4.5f, 7f); lineTo(19.5f, 7f)
        moveTo(9.5f, 7f); lineTo(10f, 4.5f); lineTo(14f, 4.5f); lineTo(14.5f, 7f)
        moveTo(6.5f, 7f); lineTo(7.4f, 19f); curveTo(7.4f, 19.6f, 8f, 20f, 8.6f, 20f)
        lineTo(15.4f, 20f); curveTo(16f, 20f, 16.6f, 19.6f, 16.6f, 19f); lineTo(17.5f, 7f)
    }

    /** Circle and slash. Nothing else reads as "never suggest this again". */
    val Exclude: ImageVector = icon("Exclude") {
        circle(12f, 12f, 7.5f)
        moveTo(7f, 7f); lineTo(17f, 17f)
    }

    /** Content leaving its container: the file opens somewhere else. */
    val Preview: ImageVector = icon("Preview") {
        moveTo(12.5f, 5f); lineTo(6f, 5f)
        curveTo(5.4f, 5f, 5f, 5.4f, 5f, 6f); lineTo(5f, 18f)
        curveTo(5f, 18.6f, 5.4f, 19f, 6f, 19f); lineTo(18f, 19f)
        curveTo(18.6f, 19f, 19f, 18.6f, 19f, 18f); lineTo(19f, 11.5f)
        moveTo(13.5f, 10.5f); lineTo(19.5f, 4.5f)
        moveTo(14.5f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 9.5f)
    }

    /** Bars with a direction. Sorting is the same gesture as sweeping, pointed differently. */
    val Sort: ImageVector = icon("Sort") {
        bar(4.5f, 7f, 15f)
        bar(4.5f, 12f, 11.5f)
        bar(4.5f, 17f, 8f)
        moveTo(19f, 8f); lineTo(19f, 17f)
        moveTo(16.5f, 14.5f); lineTo(19f, 17f); lineTo(21.5f, 14.5f)
    }

    // ---- categories -------------------------------------------------------------------------

    /** Two of the same thing, one behind the other. */
    val Duplicates: ImageVector = icon("Duplicates") {
        roundRect(8.5f, 3.5f, 20.5f, 15.5f, 2.5f)
        moveTo(15.5f, 20.5f); lineTo(6f, 20.5f)
        curveTo(4.6f, 20.5f, 3.5f, 19.4f, 3.5f, 18f); lineTo(3.5f, 8.5f)
    }

    /** An installer: something arriving into a container. */
    val Installers: ImageVector = icon("Installers") {
        roundRect(4f, 4f, 20f, 20f, 3f)
        moveTo(12f, 8f); lineTo(12f, 15f)
        moveTo(8.8f, 11.8f); lineTo(12f, 15f); lineTo(15.2f, 11.8f)
    }

    /** A box with its band, kept plain so it still reads as an archive at 18dp. */
    val Archives: ImageVector = icon("Archives") {
        roundRect(3.5f, 4.5f, 20.5f, 10f, 1.8f)
        moveTo(5f, 10f); lineTo(5f, 18f)
        curveTo(5f, 18.8f, 5.7f, 19.5f, 6.5f, 19.5f); lineTo(17.5f, 19.5f)
        curveTo(18.3f, 19.5f, 19f, 18.8f, 19f, 18f); lineTo(19f, 10f)
        moveTo(10f, 13.5f); lineTo(14f, 13.5f)
    }

    /** Corner brackets: a captured frame. */
    val Screenshots: ImageVector = icon("Screenshots") {
        moveTo(4f, 9f); lineTo(4f, 6f); curveTo(4f, 4.9f, 4.9f, 4f, 6f, 4f); lineTo(9f, 4f)
        moveTo(15f, 4f); lineTo(18f, 4f); curveTo(19.1f, 4f, 20f, 4.9f, 20f, 6f); lineTo(20f, 9f)
        moveTo(20f, 15f); lineTo(20f, 18f); curveTo(20f, 19.1f, 19.1f, 20f, 18f, 20f); lineTo(15f, 20f)
        moveTo(9f, 20f); lineTo(6f, 20f); curveTo(4.9f, 20f, 4f, 19.1f, 4f, 18f); lineTo(4f, 15f)
        bar(9f, 12f, 15f)
    }

    /** Arriving from elsewhere and landing. */
    val Downloads: ImageVector = icon("Downloads") {
        moveTo(12f, 4f); lineTo(12f, 14.5f)
        moveTo(7.8f, 10.3f); lineTo(12f, 14.5f); lineTo(16.2f, 10.3f)
        moveTo(5f, 19.5f); lineTo(19f, 19.5f)
    }

    /** Stacked slabs. The same field the storage meter draws, at icon size. */
    val LargeFiles: ImageVector = icon("LargeFiles") {
        roundRect(3.5f, 4f, 20.5f, 9f, 1.6f)
        roundRect(3.5f, 10.5f, 20.5f, 15.5f, 1.6f)
        moveTo(3.5f, 17f); lineTo(3.5f, 18.5f)
        curveTo(3.5f, 19.3f, 4.2f, 20f, 5f, 20f); lineTo(19f, 20f)
        curveTo(19.8f, 20f, 20.5f, 19.3f, 20.5f, 18.5f); lineTo(20.5f, 17f)
    }

    /** A folder whose base has gone: nothing is holding it up. */
    val EmptyFolders: ImageVector = icon("EmptyFolders") {
        moveTo(3.5f, 16f); lineTo(3.5f, 6.5f)
        curveTo(3.5f, 5.7f, 4.2f, 5f, 5f, 5f); lineTo(9.2f, 5f); lineTo(11.2f, 7.5f)
        lineTo(19f, 7.5f); curveTo(19.8f, 7.5f, 20.5f, 8.2f, 20.5f, 9f); lineTo(20.5f, 16f)
        moveTo(6f, 19f); lineTo(8.5f, 19f)
        moveTo(12.5f, 19f); lineTo(15f, 19f)
        dot(18.5f, 19f)
    }

    // ---- apps and storage -------------------------------------------------------------------

    /** Four apps, one of them faded out of use. */
    val Apps: ImageVector = icon("Apps") {
        roundRect(4f, 4f, 10.5f, 10.5f, 2f)
        roundRect(13.5f, 4f, 20f, 10.5f, 2f)
        roundRect(4f, 13.5f, 10.5f, 20f, 2f)
    }.plus(alpha = 0.45f) {
        roundRect(13.5f, 13.5f, 20f, 20f, 2f)
    }

    /** Layers of held data. */
    val Cache: ImageVector = icon("Cache") {
        moveTo(4f, 7f)
        curveTo(4f, 5.6f, 7.6f, 4.5f, 12f, 4.5f)
        curveTo(16.4f, 4.5f, 20f, 5.6f, 20f, 7f)
        curveTo(20f, 8.4f, 16.4f, 9.5f, 12f, 9.5f)
        curveTo(7.6f, 9.5f, 4f, 8.4f, 4f, 7f)
        moveTo(4f, 7f); lineTo(4f, 17f)
        curveTo(4f, 18.4f, 7.6f, 19.5f, 12f, 19.5f)
        curveTo(16.4f, 19.5f, 20f, 18.4f, 20f, 17f); lineTo(20f, 7f)
        moveTo(4f, 12f)
        curveTo(4f, 13.4f, 7.6f, 14.5f, 12f, 14.5f)
        curveTo(16.4f, 14.5f, 20f, 13.4f, 20f, 12f)
    }

    /** Time passing, for anything about when something last happened. */
    val Clock: ImageVector = icon("Clock") {
        circle(12f, 12f, 8f)
        moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15f, 14f)
    }

    // ---- states -----------------------------------------------------------------------------

    /** Storage access: a container that stays shut. */
    val Lock: ImageVector = icon("Lock") {
        roundRect(4.5f, 10f, 19.5f, 20f, 2.5f)
        moveTo(8f, 10f); lineTo(8f, 7.5f)
        curveTo(8f, 5.3f, 9.8f, 3.5f, 12f, 3.5f)
        curveTo(14.2f, 3.5f, 16f, 5.3f, 16f, 7.5f); lineTo(16f, 10f)
    }

    val Warning: ImageVector = icon("Warning") {
        moveTo(12f, 4.5f); lineTo(21f, 19.5f); lineTo(3f, 19.5f); close()
        moveTo(12f, 10f); lineTo(12f, 14f)
        dot(12f, 17f)
    }

    /** Unknown rather than absent: a mark that trails off. */
    val Unknown: ImageVector = icon("Unknown") {
        circle(12f, 12f, 8f)
        moveTo(9.4f, 9.6f)
        curveTo(9.4f, 8.2f, 10.6f, 7.2f, 12f, 7.2f)
        curveTo(13.5f, 7.2f, 14.7f, 8.3f, 14.7f, 9.7f)
        curveTo(14.7f, 11.4f, 12f, 11.8f, 12f, 14f)
        dot(12f, 16.8f)
    }

    val Notification: ImageVector = icon("Notification") {
        moveTo(6f, 16f); lineTo(6f, 10.5f)
        curveTo(6f, 7.2f, 8.7f, 4.5f, 12f, 4.5f)
        curveTo(15.3f, 4.5f, 18f, 7.2f, 18f, 10.5f); lineTo(18f, 16f)
        moveTo(4.5f, 16f); lineTo(19.5f, 16f)
        moveTo(10f, 19f)
        curveTo(10.4f, 19.9f, 11.1f, 20.4f, 12f, 20.4f)
        curveTo(12.9f, 20.4f, 13.6f, 19.9f, 14f, 19f)
    }
}

// ---- drawing helpers ------------------------------------------------------------------------

private const val STROKE = 1.9f

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
 * Adds a second path to an icon, optionally at reduced strength.
 *
 * Rebuilds rather than mutates because [ImageVector] is immutable once built. These are created
 * once at class load, so the cost is irrelevant and the call sites stay readable.
 */
private fun ImageVector.plus(alpha: Float = 1f, block: PathBuilder.() -> Unit): ImageVector {
    val base = this
    return ImageVector.Builder(
        name = base.name,
        defaultWidth = base.defaultWidth,
        defaultHeight = base.defaultHeight,
        viewportWidth = base.viewportWidth,
        viewportHeight = base.viewportHeight,
    ).apply {
        base.root.forEach { node ->
            if (node is androidx.compose.ui.graphics.vector.VectorPath) {
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

/** A horizontal stroke, the unit the whole family is built from. */
private fun PathBuilder.bar(startX: Float, y: Float, endX: Float) {
    moveTo(startX, y)
    lineTo(endX, y)
}

/** The mark's leftover fragment, drawn as a round cap with almost no length. */
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

private fun PathBuilder.roundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
) {
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
