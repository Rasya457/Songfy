package com.lagu.songfy.presentation.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GoogleLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    containerColor: Color = Color(0xFF181A24)
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        // Red segment (#EA4335) - Top Arc
        val redPath = Path().apply {
            moveTo(cx, cy)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                startAngleDegrees = -180f,
                sweepAngleDegrees = 110f,
                forceMoveTo = false
            )
            lineTo(cx, cy)
            close()
        }
        drawPath(path = redPath, color = Color(0xFFEA4335))

        // Yellow segment (#FBBC05) - Bottom Left Arc
        val yellowPath = Path().apply {
            moveTo(cx, cy)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                startAngleDegrees = 135f,
                sweepAngleDegrees = 45f,
                forceMoveTo = false
            )
            lineTo(cx, cy)
            close()
        }
        drawPath(path = yellowPath, color = Color(0xFFFBBC05))

        // Green segment (#34A853) - Bottom Arc
        val greenPath = Path().apply {
            moveTo(cx, cy)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                startAngleDegrees = 45f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(cx, cy)
            close()
        }
        drawPath(path = greenPath, color = Color(0xFF34A853))

        // Blue segment (#4285F4) - Right Arc
        val bluePath = Path().apply {
            moveTo(cx, cy)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                startAngleDegrees = -70f,
                sweepAngleDegrees = 115f,
                forceMoveTo = false
            )
            lineTo(cx, cy)
            close()
        }
        drawPath(path = bluePath, color = Color(0xFF4285F4))

        // Horizontal G Bar in Blue
        drawRect(
            color = Color(0xFF4285F4),
            topLeft = Offset(cx - w * 0.05f, cy - h * 0.14f),
            size = Size(w * 0.52f, h * 0.28f)
        )

        // Center hole cutout
        drawCircle(
            color = containerColor,
            radius = w * 0.28f,
            center = Offset(cx, cy)
        )
    }
}
