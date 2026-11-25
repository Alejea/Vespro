package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

@Composable
fun PatternLockGrid(
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onPatternComplete: (List<Int>) -> Unit
) {
    val selectedNodes = remember { mutableStateListOf<Int>() }
    var activePointer by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val activeColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val activeGlow = activeColor.copy(alpha = 0.24f)
    val idleNodeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val idleRingColor = MaterialTheme.colorScheme.outline

    fun nodeCenters(size: IntSize): List<Offset> {
        if (size.width <= 0 || size.height <= 0) return emptyList()
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val cellW = w / 3f
        val cellH = h / 3f
        return buildList {
            for (row in 0..2) {
                for (col in 0..2) {
                    add(Offset(cellW * col + cellW / 2f, cellH * row + cellH / 2f))
                }
            }
        }
    }

    fun hitNode(pos: Offset, centers: List<Offset>, hitRadius: Float): Int? {
        centers.forEachIndexed { index, center ->
            if (hypot(pos.x - center.x, pos.y - center.y) <= hitRadius) {
                return index
            }
        }
        return null
    }

    fun tryAddNode(index: Int) {
        if (index in selectedNodes) return
        // Auto-bridge intermediate dot when jumping straight across row/col/diagonal
        if (selectedNodes.isNotEmpty()) {
            val prev = selectedNodes.last()
            val prevR = prev / 3
            val prevC = prev % 3
            val curR = index / 3
            val curC = index % 3
            val dr = curR - prevR
            val dc = curC - prevC
            if (dr % 2 == 0 && dc % 2 == 0) {
                val mid = ((prevR + dr / 2) * 3) + (prevC + dc / 2)
                if (mid !in selectedNodes) {
                    selectedNodes.add(mid)
                }
            }
        }
        selectedNodes.add(index)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(maxWidth = 300.dp, maxHeight = 300.dp)
            .aspectRatio(1f)
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        selectedNodes.clear()
                        activePointer = startOffset
                        val centers = nodeCenters(canvasSize)
                        val hitRadius = (canvasSize.width.coerceAtMost(canvasSize.height) / 3f) * 0.34f
                        hitNode(startOffset, centers, hitRadius)?.let { tryAddNode(it) }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        activePointer = change.position
                        val centers = nodeCenters(canvasSize)
                        val hitRadius = (canvasSize.width.coerceAtMost(canvasSize.height) / 3f) * 0.34f
                        hitNode(change.position, centers, hitRadius)?.let { tryAddNode(it) }
                    },
                    onDragEnd = {
                        activePointer = null
                        if (selectedNodes.isNotEmpty()) {
                            onPatternComplete(selectedNodes.toList())
                        }
                        selectedNodes.clear()
                    },
                    onDragCancel = {
                        activePointer = null
                        selectedNodes.clear()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val centers = nodeCenters(canvasSize)
                    val hitRadius = (canvasSize.width.coerceAtMost(canvasSize.height) / 3f) * 0.34f
                    val node = hitNode(tapOffset, centers, hitRadius)
                    if (node != null) {
                        onPatternComplete(listOf(node))
                    }
                }
            }
            .testTag("pattern_lock_grid")
    ) {
        val centers = nodeCenters(IntSize(size.width.toInt(), size.height.toInt()))
        if (centers.isEmpty()) return@Canvas

        val minDim = size.width.coerceAtMost(size.height)
        val dotRadius = minDim * 0.032f
        val ringRadius = minDim * 0.095f
        val strokeWidth = minDim * 0.022f

        // Draw connecting segments between selected nodes
        for (i in 0 until selectedNodes.size - 1) {
            val from = centers[selectedNodes[i]]
            val to = centers[selectedNodes[i + 1]]
            drawLine(
                color = activeColor,
                start = from,
                end = to,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Draw live trailing segment to active finger
        val pointer = activePointer
        if (pointer != null && selectedNodes.isNotEmpty()) {
            val lastCenter = centers[selectedNodes.last()]
            drawLine(
                color = activeColor.copy(alpha = 0.55f),
                start = lastCenter,
                end = pointer,
                strokeWidth = strokeWidth * 0.85f,
                cap = StrokeCap.Round
            )
        }

        // Draw the 9 nodes
        centers.forEachIndexed { idx, center ->
            val isSelected = idx in selectedNodes
            if (isSelected) {
                drawCircle(
                    color = activeGlow,
                    radius = ringRadius * 1.15f,
                    center = center
                )
                drawCircle(
                    color = activeColor,
                    radius = ringRadius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth * 0.6f,
                        join = StrokeJoin.Round
                    )
                )
                drawCircle(
                    color = activeColor,
                    radius = dotRadius * 1.35f,
                    center = center
                )
            } else {
                drawCircle(
                    color = idleRingColor.copy(alpha = 0.45f),
                    radius = ringRadius * 0.72f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth * 0.35f)
                )
                drawCircle(
                    color = idleNodeColor,
                    radius = dotRadius,
                    center = center
                )
            }
        }
    }
}
