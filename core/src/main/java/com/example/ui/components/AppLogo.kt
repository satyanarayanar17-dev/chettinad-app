package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.secondary
) {
    Canvas(modifier = modifier.size(48.dp)) {
        val strokeWidth = size.width * 0.12f
        val padding = strokeWidth / 2f
        
        // Draw the "C" curve (arc) - starting at bottom right, sweeping clockwise to top right
        drawArc(
            color = primaryColor,
            startAngle = 60f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(padding, padding),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // Draw the central medical cross
        val crossSize = size.width * 0.35f
        val center = Offset(size.width / 2, size.height / 2)
        val crossStrokeWidth = strokeWidth * 0.85f
        
        // Vertical line
        drawLine(
            color = accentColor,
            start = Offset(center.x, center.y - crossSize / 2),
            end = Offset(center.x, center.y + crossSize / 2),
            strokeWidth = crossStrokeWidth,
            cap = StrokeCap.Round
        )
        
        // Horizontal line
        drawLine(
            color = accentColor,
            start = Offset(center.x - crossSize / 2, center.y),
            end = Offset(center.x + crossSize / 2, center.y),
            strokeWidth = crossStrokeWidth,
            cap = StrokeCap.Round
        )
    }
}
