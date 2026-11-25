package com.example.projeto_grupo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.projeto_grupo.ui.MathExpressionEvaluator

@Composable
fun EquationGraphPreview(
    expression: String,
    params: Map<String, Double> = emptyMap(),
    a: Double = 1.0,
    b: Double = 1.0,
    c: Double = 0.0,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    val gridSize = 12
    val range = 2.0

    val effectiveParams = remember(params, a, b, c) {
        if (params.isNotEmpty()) params else mapOf("a" to a, "b" to b, "c" to c)
    }

    val heights = remember(expression, effectiveParams) {
        Array(gridSize + 1) { i ->
            DoubleArray(gridSize + 1) { j ->
                val x = -range + (2 * range * i / gridSize)
                val y = -range + (2 * range * j / gridSize)
                val valZ = runCatching {
                    MathExpressionEvaluator.evaluate(
                        expression = expression,
                        x = x,
                        y = y,
                        params = effectiveParams
                    )
                }.getOrDefault(0.0)
                if (valZ.isNaN() || valZ.isInfinite()) 0.0 else valZ.coerceIn(-3.0, 3.0)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val centerX = width / 2f
            val centerY = height * 0.55f

            val scaleX = width * 0.20f
            val scaleY = width * 0.20f
            val scaleZ = height * 0.14f

            val cos30 = 0.866f
            val sin30 = 0.5f

            fun project(i: Int, j: Int): Offset {
                val normX = (i - gridSize / 2f) / (gridSize / 2f)
                val normY = (j - gridSize / 2f) / (gridSize / 2f)
                val z = heights[i][j].toFloat()

                val px = centerX + (normX - normY) * scaleX * cos30
                val py = centerY + (normX + normY) * scaleY * sin30 - z * scaleZ
                return Offset(px, py)
            }

            // Draw grid lines along X direction
            for (j in 0..gridSize) {
                val path = Path()
                val start = project(0, j)
                path.moveTo(start.x, start.y)
                for (i in 1..gridSize) {
                    val pt = project(i, j)
                    path.lineTo(pt.x, pt.y)
                }
                drawPath(
                    path = path,
                    color = lineColor.copy(alpha = 0.7f),
                    style = Stroke(width = 2.0f)
                )
            }

            // Draw grid lines along Y direction
            for (i in 0..gridSize) {
                val path = Path()
                val start = project(i, 0)
                path.moveTo(start.x, start.y)
                for (j in 1..gridSize) {
                    val pt = project(i, j)
                    path.lineTo(pt.x, pt.y)
                }
                drawPath(
                    path = path,
                    color = lineColor.copy(alpha = 0.7f),
                    style = Stroke(width = 2.0f)
                )
            }
        }
    }
}