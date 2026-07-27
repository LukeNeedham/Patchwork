package com.tailgunnerx.piccollage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class DragKind { MOVE, ADD }

/**
 * The interactive collage canvas: every box can be dragged to a new spot,
 * resized via the handles on shared dividers (the neighbor shrinks/grows to
 * match), dragged out past the edge to remove it (its sibling absorbs the
 * space), or added by dragging the "+" button onto an existing box (which
 * splits to make room).
 */
@Composable
fun CollageEditorCanvas(
    root: LayoutNode,
    photos: Map<String, PhotoState>,
    borderPx: Float,
    borderStyle: BorderStyle,
    onTreeChange: (LayoutNode) -> Unit,
    onRequestPhoto: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentRoot = rememberUpdatedState(root)

    var canvasCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // The FAB is a direct child of the canvas box, so its positionInParent() is
    // already in canvas-local coordinates - no extra conversion needed.
    var fabCenterInOuter by remember { mutableStateOf<Offset?>(null) }

    var dragKind by remember { mutableStateOf<DragKind?>(null) }
    var dragLeafId by remember { mutableStateOf<String?>(null) }
    var ghostCanvasPx by remember { mutableStateOf<Offset?>(null) }
    var hoverTarget by remember { mutableStateOf<Pair<String, QuadrantInsert>?>(null) }
    var isOutOfBounds by remember { mutableStateOf(false) }

    fun updateGhost(posInCanvasPx: Offset) {
        ghostCanvasPx = posInCanvasPx
        val coords = canvasCoords
        val w = coords?.size?.width?.toFloat() ?: 0f
        val h = coords?.size?.height?.toFloat() ?: 0f
        if (w <= 0f || h <= 0f) return
        val outside = posInCanvasPx.x !in 0f..w || posInCanvasPx.y !in 0f..h
        isOutOfBounds = outside
        hoverTarget = if (outside) null else {
            val norm = (posInCanvasPx.x / w) to (posInCanvasPx.y / h)
            findDropTarget(currentRoot.value, norm, excludeLeafId = dragLeafId)
        }
    }

    fun clearDrag() {
        dragKind = null
        dragLeafId = null
        ghostCanvasPx = null
        hoverTarget = null
        isOutOfBounds = false
    }

    fun commitDrag() {
        val kind = dragKind
        val leafId = dragLeafId
        val target = hoverTarget
        when (kind) {
            DragKind.MOVE -> {
                if (leafId != null) {
                    if (isOutOfBounds) {
                        if (currentRoot.value.leafCount() > 1) {
                            currentRoot.value.removingLeaf(leafId)?.let(onTreeChange)
                        }
                    } else if (target != null && target.first != leafId) {
                        onTreeChange(currentRoot.value.movingLeaf(leafId, target.first, target.second))
                    }
                }
            }
            DragKind.ADD -> {
                if (!isOutOfBounds && target != null) {
                    onTreeChange(currentRoot.value.insertingAt(target.first, newNodeId("box"), target.second))
                }
            }
            null -> {}
        }
        clearDrag()
    }

    // The canvas fills the whole area the caller gives us (typically a square).
    // The "add" button and drag ghost float on top of it, like the existing
    // per-photo rotate/remove buttons already do - no separate space needed.
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Dark800)
            .then(
                if (dragKind == DragKind.MOVE && isOutOfBounds)
                    Modifier.border(3.dp, Color(0xFFFF5252), RoundedCornerShape(16.dp))
                else Modifier
            )
            .onGloballyPositioned { canvasCoords = it }
    ) {
        CollageLayoutBody(
            root = root,
            photos = photos,
            borderPx = borderPx,
            borderStyle = borderStyle,
            editable = true,
            movingLeafId = dragLeafId,
            hoverTargetLeafId = hoverTarget?.first,
            onTreeChange = onTreeChange,
            onRequestPhoto = onRequestPhoto,
            onRemovePhoto = onRemovePhoto,
            onMoveDragStart = { leafId, startCenterPx ->
                dragKind = DragKind.MOVE
                dragLeafId = leafId
                updateGhost(startCenterPx)
            },
            onMoveDrag = { delta -> ghostCanvasPx?.let { updateGhost(it + delta) } },
            onMoveDragEnd = { commitDrag() }
        )

        // Floating "add a box" handle - drag it onto an existing box to split it.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(48.dp)
                .onGloballyPositioned {
                    fabCenterInOuter = it.positionInParent() +
                        Offset(it.size.width / 2f, it.size.height / 2f)
                }
                .clip(CircleShape)
                .background(AppGradient)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragKind = DragKind.ADD
                            dragLeafId = null
                            updateGhost(fabCenterInOuter ?: Offset.Zero)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            ghostCanvasPx?.let { updateGhost(it + amount) }
                        },
                        onDragEnd = { commitDrag() },
                        onDragCancel = { clearDrag() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text("＋", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        // Ghost preview following the finger while a move/add drag is active.
        val ghost = ghostCanvasPx
        if (dragKind != null && ghost != null) {
            val density = LocalDensity.current
            val ghostSize = 56.dp
            val ghostSizePx = with(density) { ghostSize.toPx() }
            val pos = ghost - Offset(ghostSizePx / 2f, ghostSizePx / 2f)
            Box(
                Modifier
                    .offset(
                        x = with(density) { pos.x.toDp() },
                        y = with(density) { pos.y.toDp() }
                    )
                    .size(ghostSize)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isOutOfBounds) Color(0xFFFF5252).copy(alpha = 0.85f)
                        else Pink500.copy(alpha = 0.85f)
                    )
            )
        }
    }
}

@Composable
private fun CollageLayoutBody(
    root: LayoutNode,
    photos: Map<String, PhotoState>,
    borderPx: Float,
    borderStyle: BorderStyle,
    editable: Boolean,
    movingLeafId: String?,
    hoverTargetLeafId: String?,
    onTreeChange: (LayoutNode) -> Unit,
    onRequestPhoto: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onMoveDragStart: (String, Offset) -> Unit,
    onMoveDrag: (Offset) -> Unit,
    onMoveDragEnd: () -> Unit
) {
    val density = LocalDensity.current
    val currentRoot = rememberUpdatedState(root)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val borderPxPx = with(density) { borderPx.dp.toPx() }

        BorderBackground(borderStyle, Modifier.fillMaxSize())

        val leafRects = remember(root) { root.computeRects() }
        for ((leafId, rect) in leafRects) {
            val px = remember(rect, wPx, hPx, borderPxPx) {
                rect.toPixelRectWithBorder(wPx, hPx, borderPxPx)
            }
            val isMoving = editable && movingLeafId == leafId
            val isHoverTarget = editable && hoverTargetLeafId == leafId
            val centerPx = Offset(px.left + px.width / 2f, px.top + px.height / 2f)
            val centerPxState = rememberUpdatedState(centerPx)

            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = with(density) { px.left.toDp() },
                        y = with(density) { px.top.toDp() }
                    )
                    .size(
                        width = with(density) { px.width.toDp() },
                        height = with(density) { px.height.toDp() }
                    )
                    .alpha(if (isMoving) 0.25f else 1f)
                    .then(
                        if (isHoverTarget) Modifier.border(3.dp, Pink500, RoundedCornerShape(6.dp))
                        else Modifier
                    )
            ) {
                PhotoCellBox(
                    boxId = leafId,
                    state = photos[leafId],
                    editable = editable,
                    onRequestPhoto = onRequestPhoto,
                    onRemovePhoto = onRemovePhoto,
                    onMoveDragStart = { onMoveDragStart(leafId, centerPxState.value) },
                    onMoveDrag = onMoveDrag,
                    onMoveDragEnd = onMoveDragEnd,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (editable) {
            val allRects = remember(root) { root.computeAllRects() }
            val splits = remember(root) { root.allSplits() }
            for (split in splits) {
                val rect = allRects[split.id] ?: continue
                ResizeHandle(
                    split = split,
                    rect = rect,
                    canvasWidthPx = wPx,
                    canvasHeightPx = hPx,
                    currentRoot = currentRoot,
                    onTreeChange = onTreeChange
                )
            }
        }
    }
}

@Composable
private fun ResizeHandle(
    split: SplitNode,
    rect: NormalizedRect,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    currentRoot: State<LayoutNode>,
    onTreeChange: (LayoutNode) -> Unit
) {
    val density = LocalDensity.current
    val isRow = split.direction == SplitDirection.ROW
    val centerNormX = if (isRow) rect.left + rect.width * split.fraction else (rect.left + rect.right) / 2f
    val centerNormY = if (isRow) (rect.top + rect.bottom) / 2f else rect.top + rect.height * split.fraction
    val centerPx = Offset(centerNormX * canvasWidthPx, centerNormY * canvasHeightPx)
    val axisPx = if (isRow) rect.width * canvasWidthPx else rect.height * canvasHeightPx
    val handleSize = 22.dp
    val handleSizePx = with(density) { handleSize.toPx() }
    val splitState = rememberUpdatedState(split)
    val axisPxState = rememberUpdatedState(axisPx)

    Box(
        Modifier
            .offset(
                x = with(density) { (centerPx.x - handleSizePx / 2f).toDp() },
                y = with(density) { (centerPx.y - handleSizePx / 2f).toDp() }
            )
            .size(handleSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .border(1.5.dp, Pink500, CircleShape)
            .pointerInput(split.id) {
                var startFraction = splitState.value.fraction
                var accumulated = 0f
                detectDragGestures(
                    onDragStart = {
                        startFraction = splitState.value.fraction
                        accumulated = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        accumulated += if (isRow) amount.x else amount.y
                        val axis = axisPxState.value
                        if (axis > 0f) {
                            val newFraction = startFraction + accumulated / axis
                            onTreeChange(currentRoot.value.withSplitFraction(splitState.value.id, newFraction))
                        }
                    }
                )
            }
    )
}

@Composable
private fun BorderBackground(borderStyle: BorderStyle, modifier: Modifier) {
    when (val style = borderStyle) {
        is BorderStyle.SolidColor -> Box(modifier.background(style.color))
        is BorderStyle.GradientPattern -> {
            val brush = when (style.dir) {
                GradientDir.TOP_BOTTOM -> Brush.verticalGradient(style.colors)
                GradientDir.LEFT_RIGHT -> Brush.horizontalGradient(style.colors)
                GradientDir.REVERSE_DIAGONAL -> Brush.linearGradient(
                    style.colors,
                    start = Offset(Float.POSITIVE_INFINITY, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY)
                )
                GradientDir.RADIAL_CENTER -> Brush.radialGradient(style.colors)
                GradientDir.RADIAL_CORNER -> Brush.radialGradient(
                    style.colors,
                    center = Offset(0f, 0f),
                    radius = 800f
                )
                else -> Brush.linearGradient(style.colors)
            }
            Box(modifier.background(brush))
        }
    }
}

@Composable
private fun PhotoCellBox(
    boxId: String,
    state: PhotoState?,
    editable: Boolean,
    onRequestPhoto: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onMoveDragStart: () -> Unit,
    onMoveDrag: (Offset) -> Unit,
    onMoveDragEnd: () -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier.clip(RoundedCornerShape(6.dp)).background(Dark700)) {
        if (state != null) {
            PhotoCell(state = state, modifier = Modifier.fillMaxSize())
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable {
                            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
                            state.bitmap = android.graphics.Bitmap.createBitmap(
                                state.bitmap, 0, 0, state.bitmap.width, state.bitmap.height, matrix, true
                            )
                            state.panX = 0f
                            state.panY = 0f
                            state.zoom = 1f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("↻", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onRemovePhoto(boxId) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().clickable { onRequestPhoto(boxId) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("＋", color = Pink500, fontSize = 24.sp, fontWeight = FontWeight.Normal)
                }
            }
        }

        if (editable) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(boxId) {
                        detectDragGestures(
                            onDragStart = { onMoveDragStart() },
                            onDrag = { change, amount -> change.consume(); onMoveDrag(amount) },
                            onDragEnd = { onMoveDragEnd() },
                            onDragCancel = { onMoveDragEnd() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("✥", color = White, fontSize = 13.sp, fontWeight = FontWeight.Normal)
            }
        }
    }
}

/** Non-interactive miniature of a layout tree - used by the layout picker chips. */
@Composable
fun LayoutTreeThumbnail(node: LayoutNode, cellColor: Color, gap: Dp, modifier: Modifier = Modifier) {
    when (node) {
        is BoxNode -> Box(modifier.background(cellColor, RoundedCornerShape(3.dp)))
        is SplitNode -> when (node.direction) {
            SplitDirection.ROW -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
                LayoutTreeThumbnail(node.first, cellColor, gap, Modifier.weight(node.fraction).fillMaxHeight())
                LayoutTreeThumbnail(node.second, cellColor, gap, Modifier.weight(1f - node.fraction).fillMaxHeight())
            }
            SplitDirection.COLUMN -> Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
                LayoutTreeThumbnail(node.first, cellColor, gap, Modifier.weight(node.fraction).fillMaxWidth())
                LayoutTreeThumbnail(node.second, cellColor, gap, Modifier.weight(1f - node.fraction).fillMaxWidth())
            }
        }
    }
}
