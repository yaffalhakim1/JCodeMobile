package dev.jcode.mobile.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * One place that maps intent to a glyph.
 *
 * Most icons come from `material-icons-core`. The few the core set lacks are
 * drawn here on the same 24dp grid and 1.8dp stroke, so the toolbar reads as one
 * family rather than two.
 */
object Glyphs {
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Close: ImageVector = Icons.Filled.Close
    val Add: ImageVector = Icons.Filled.Add
    val Send: ImageVector = Icons.Filled.Send
    val Refresh: ImageVector = Icons.Filled.Refresh
    val Settings: ImageVector = Icons.Filled.Settings
    val Check: ImageVector = Icons.Filled.Check
    val ChevronDown: ImageVector = Icons.Filled.ArrowDropDown
    val ChevronRight: ImageVector = Icons.Filled.KeyboardArrowRight
    val Alert: ImageVector = Icons.Filled.Warning

    val Stop: ImageVector = drawn("stop") {
        filled { addRect(7f, 7f, 17f, 17f) }
    }

    val Terminal: ImageVector = drawn("terminal") {
        stroked {
            moveTo(4f, 6f); lineTo(20f, 6f); lineTo(20f, 18f); lineTo(4f, 18f); close()
        }
        stroked {
            moveTo(8f, 10f); lineTo(11f, 13f); lineTo(8f, 16f)
        }
        stroked {
            moveTo(13f, 16f); lineTo(16f, 16f)
        }
    }

    val Sparkle: ImageVector = drawn("sparkle") {
        stroked {
            moveTo(12f, 4f); lineTo(13.6f, 10.4f); lineTo(20f, 12f); lineTo(13.6f, 13.6f)
            lineTo(12f, 20f); lineTo(10.4f, 13.6f); lineTo(4f, 12f); lineTo(10.4f, 10.4f)
            close()
        }
    }

    /** A plain down arrow: "jump to the newest message". */
    val ArrowDown: ImageVector = drawn("arrow-down") {
        stroked {
            moveTo(12f, 5f); lineTo(12f, 19f)
        }
        stroked {
            moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f)
        }
    }

    /** Sliders: the model and provider sheet. */
    val Tune: ImageVector = drawn("tune") {
        stroked {
            moveTo(4f, 8f); lineTo(20f, 8f)
        }
        stroked {
            moveTo(4f, 16f); lineTo(20f, 16f)
        }
        filled { addCircle(9f, 8f, 2.4f) }
        filled { addCircle(15f, 16f, 2.4f) }
    }

    /** A check in a circle: a finished tool call. */
    val CheckCircle: ImageVector = drawn("check-circle") {
        stroked {
            moveTo(12f, 3.5f)
            curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
            curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
            curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f)
            curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
            close()
        }
        stroked {
            moveTo(8f, 12.2f); lineTo(10.8f, 15f); lineTo(16.2f, 9.2f)
        }
    }

    private fun drawn(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(build).build()

    private fun ImageVector.Builder.stroked(build: PathBuilder.() -> Unit) {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = build,
        )
    }

    private fun ImageVector.Builder.filled(build: PathBuilder.() -> Unit) {
        path(
            fill = SolidColor(Color.Black),
            pathBuilder = build,
        )
    }

    private fun PathBuilder.addRect(left: Float, top: Float, right: Float, bottom: Float) {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(right, bottom)
        lineTo(left, bottom)
        close()
    }

    private fun PathBuilder.addCircle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }
}