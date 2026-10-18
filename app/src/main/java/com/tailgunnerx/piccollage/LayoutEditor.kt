package com.tailgunnerx.piccollage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class DragKind { MOVE, ADD }

/**
 * The interactive collage canvas: every box can be dragged to a new spot,
 * resized via the handles on shared dividers (the neighbor shrinks/grows to
 * match), dragged out past the edge to remove it (its sibling absorbs the
 * space), or added by dragging the handle below the canvas onto an existing
 * box (which splits to make room).
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

    var outerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var canvasCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var addHandleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var dragKind by remember { mutableStateOf<DragKind?>(null) }
    var dragLeafId by remember { mutableStateOf<String?>(null) }
    // Always in the outer Box's coordinate space, since the ghost it drives can
    // travel from the add-handle (below the canvas) onto the canvas itself.
    var ghostPos by remember { mutableStateOf<Offset?>(null) }
    var hoverTarget by remember { mutableStateOf<Pair<String, QuadrantInsert>?>(null) }
    var isOutOfBounds by remember { mutableStateOf(false) }

    fun canvasOriginInOuter(): Offset {
        val oc = outerCoords ?: return Offset.Zero
        val cc = canvasCoords ?: return Offset.Zero
        return oc.localPositionOf(cc, Offset.Zero)
    }

    fun addHandleCenterInOuter(): Offset? {
        val oc = outerCoords ?: return null
        val hc = addHandleCoords ?: return null
        return oc.localPositionOf(hc, Offset(hc.size.width / 2f, hc.size.height / 2f))
    }

    fun updateGhost(posInOuterPx: Offset) {
        ghostPos = posInOuterPx
        val cCoords = canvasCoords ?: return
        val w = cCoords.size.width.toFloat()
        val h = cCoords.size.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val local = posInOuterPx - canvasOriginInOuter()
        val outside = local.x !in 0f..w || local.y !in 0f..h
        isOutOfBounds = outside
        hoverTarget = if (outside) null else {
            val norm = (local.x / w) to (local.y / h)
            findDropTarget(currentRoot.value, norm, excludeLeafId = dragLeafId)
        }
    }

    fun clearDrag() {
        dragKind = null
        dragLeafId = null
        ghostPos = null
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

    Box(modifier.onGloballyPositioned { outerCoords = it }) {
        Column(Modifier.fillMaxWidth()) {
            // The square collage canvas.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
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
                    hoverTarget = hoverTarget,
                    onTreeChange = onTreeChange,
                    onRequestPhoto = onRequestPhoto,
                    onRemovePhoto = onRemovePhoto,
                    onMoveDragStart = { leafId, startCenterPxCanvasLocal ->
                        dragKind = DragKind.MOVE
                        dragLeafId = leafId
                        updateGhost(canvasOriginInOuter() + startCenterPxCanvasLocal)
                    },
                    onMoveDrag = { delta -> ghostPos?.let { updateGhost(it + delta) } },
                    onMoveDragEnd = { commitDrag() }
                )
            }

            Spacer(Modifier.height(14.dp))

            // The "add a box" handle - outside the canvas entirely, so it never
            // sits on top of a photo. Dashed and labelled, not a filled button,
            // since tapping it does nothing: it only works by dragging onto a box.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DraggableAddHandle(
                    modifier = Modifier
                        .onGloballyPositioned { addHandleCoords = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    dragKind = DragKind.ADD
                                    dragLeafId = null
                                    addHandleCenterInOuter()?.let { updateGhost(it) }
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    ghostPos?.let { updateGhost(it + amount) }
                                },
                                onDragEnd = { commitDrag() },
                                onDragCancel = { clearDrag() }
                            )
                        }
                )
            }
        }

        // Ghost preview following the finger while a move/add drag is active.
        val ghost = ghostPos
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

/** A drag-only affordance for adding a box: dashed and labelled so it reads as
 *  "drop target to pick up", not a button that does something on tap. */
@Composable
private fun DraggableAddHandle(modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Pink500,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
                    ),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
            }
            Text("＋", color = Pink500, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text("Drag to add", color = Subtle, fontSize = 11.sp)
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
    hoverTarget: Pair<String, QuadrantInsert>?,
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

        // Ghost split preview: shows exactly how the hovered box will divide -
        // the new box's landing spot in pink, the existing box's new, smaller
        // footprint outlined - before the drag is dropped.
        if (editable && hoverTarget != null) {
            val (targetId, insert) = hoverTarget
            val targetRect = leafRects[targetId]
            if (targetRect != null) {
                val (firstNorm, secondNorm) = targetRect.splitBy(insert.direction, 0.5f)
                val newBoxNorm = if (insert.newFirst) firstNorm else secondNorm
                val remainingNorm = if (insert.newFirst) secondNorm else firstNorm
                val newBoxPx = newBoxNorm.toPixelRectWithBorder(wPx, hPx, borderPxPx)
                val remainingPx = remainingNorm.toPixelRectWithBorder(wPx, hPx, borderPxPx)

                PixelRectOverlay(
                    remainingPx,
                    density,
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                )
                PixelRectOverlay(
                    newBoxPx,
                    density,
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Pink500.copy(alpha = 0.45f))
                        .border(2.dp, Pink500, RoundedCornerShape(6.dp))
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PixelRectOverlay(px: PixelRect, density: androidx.compose.ui.unit.Density, modifier: Modifier) {
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
            .then(modifier)
    )
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
    val handleSize = 38.dp
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
            },
        contentAlignment = Alignment.Center
    ) {
        // Shows which way this handle drags, so it's clear at a glance - a
        // left-right arrow for a vertical divider, up-down for a horizontal one.
        // includeFontPadding = false strips the glyph's default top/bottom padding,
        // which otherwise throws off centering inside a tight circle like this.
        Text(
            text = if (isRow) "↔" else "↕",
            color = Pink500,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
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
