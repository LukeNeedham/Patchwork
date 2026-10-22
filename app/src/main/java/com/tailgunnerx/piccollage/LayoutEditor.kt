package com.tailgunnerx.piccollage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class DragKind { MOVE, ADD_BOX, ASSIGN_PHOTO }

/** A fixed-size margin around the box grid, visually and functionally distinct
 *  from any box's own interior: a drop here always means "new row/column
 *  alongside everything", never "inside whichever box happens to be here". */
private val CanvasOuterPadding = 24.dp

/**
 * The interactive collage canvas: every box can be dragged (long-press, then
 * drag) to a new spot, resized via the handles on shared dividers (the
 * neighbor shrinks/grows to match), dragged out past the edge to remove it
 * (its sibling absorbs the space), or added by dragging the handle below the
 * canvas onto an existing box (which splits to make room). A short tap on a
 * box opens the system photo picker directly; dragging a tile from the photo
 * pool below onto a box assigns that photo to it instead.
 */
@Composable
fun CollageEditorCanvas(
    root: LayoutNode,
    photosByBoxId: Map<String, PhotoState>,
    photoPool: List<PoolPhoto>,
    borderPx: Float,
    borderStyle: BorderStyle,
    onTreeChange: (LayoutNode) -> Unit,
    onRequestPhoto: (String) -> Unit,
    onAssignPhoto: (boxId: String, poolPhotoId: String) -> Unit,
    onRequestAddToPool: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentRoot = rememberUpdatedState(root)
    val density = LocalDensity.current

    var outerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var canvasCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // The box grid's own coordinates - inset within canvasCoords by CanvasOuterPadding,
    // so a position reported relative to it (e.g. a box's center) needs this, not
    // canvasCoords, to land in the right place once converted to outer-space.
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var addHandleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var dragKind by remember { mutableStateOf<DragKind?>(null) }
    var dragLeafId by remember { mutableStateOf<String?>(null) }
    var dragPoolPhotoId by remember { mutableStateOf<String?>(null) }
    // Always in the outer Box's coordinate space, since the ghost it drives can
    // travel from the add-handle or photo pool (below the canvas) onto the canvas.
    var ghostPos by remember { mutableStateOf<Offset?>(null) }
    var hoverTarget by remember { mutableStateOf<DropTarget?>(null) }
    var isOutOfBounds by remember { mutableStateOf(false) }

    fun canvasOriginInOuter(): Offset {
        val oc = outerCoords ?: return Offset.Zero
        val cc = canvasCoords ?: return Offset.Zero
        return oc.localPositionOf(cc, Offset.Zero)
    }

    fun centerInOuter(coords: LayoutCoordinates?): Offset? {
        val oc = outerCoords ?: return null
        val c = coords ?: return null
        return oc.localPositionOf(c, Offset(c.size.width / 2f, c.size.height / 2f))
    }

    fun updateGhost(posInOuterPx: Offset) {
        ghostPos = posInOuterPx
        val cCoords = canvasCoords ?: return
        val fullW = cCoords.size.width.toFloat()
        val fullH = cCoords.size.height.toFloat()
        if (fullW <= 0f || fullH <= 0f) return
        val local = posInOuterPx - canvasOriginInOuter()
        val outside = local.x !in 0f..fullW || local.y !in 0f..fullH
        isOutOfBounds = outside
        if (outside) {
            hoverTarget = null
            return
        }

        // The box grid itself is inset by a fixed padding from the canvas's own
        // bounds (see CanvasOuterPadding) - a drop in that padding ring always
        // means "new row/column alongside everything", with no ambiguity about
        // whether it's inside whichever box happens to be near that edge.
        val padPx = with(density) { CanvasOuterPadding.toPx() }
        val gridLeft = padPx
        val gridTop = padPx
        val gridRight = (fullW - padPx).coerceAtLeast(gridLeft)
        val gridBottom = (fullH - padPx).coerceAtLeast(gridTop)
        val inGrid = local.x in gridLeft..gridRight && local.y in gridTop..gridBottom

        hoverTarget = if (!inGrid) {
            // Assigning a photo only ever fills an existing box - there's no
            // "outer edge" meaning for it, unlike moving or adding a box.
            if (dragKind == DragKind.ASSIGN_PHOTO) null
            else {
                val overLeft = (gridLeft - local.x).coerceAtLeast(0f)
                val overRight = (local.x - gridRight).coerceAtLeast(0f)
                val overTop = (gridTop - local.y).coerceAtLeast(0f)
                val overBottom = (local.y - gridBottom).coerceAtLeast(0f)
                val maxOver = maxOf(overLeft, overRight, overTop, overBottom)
                val insert = when (maxOver) {
                    overLeft -> QuadrantInsert(SplitDirection.ROW, newFirst = true)
                    overRight -> QuadrantInsert(SplitDirection.ROW, newFirst = false)
                    overTop -> QuadrantInsert(SplitDirection.COLUMN, newFirst = true)
                    else -> QuadrantInsert(SplitDirection.COLUMN, newFirst = false)
                }
                DropTarget.OuterEdge(insert)
            }
        } else {
            val gridW = gridRight - gridLeft
            val gridH = gridBottom - gridTop
            if (gridW <= 0f || gridH <= 0f) null
            else {
                val norm = ((local.x - gridLeft) / gridW) to ((local.y - gridTop) / gridH)
                findDropTarget(currentRoot.value, norm, excludeLeafId = dragLeafId)
            }
        }
    }

    fun clearDrag() {
        dragKind = null
        dragLeafId = null
        dragPoolPhotoId = null
        ghostPos = null
        hoverTarget = null
        isOutOfBounds = false
    }

    fun commitDrag() {
        val kind = dragKind
        val leafId = dragLeafId
        val photoId = dragPoolPhotoId
        val target = hoverTarget
        when (kind) {
            DragKind.MOVE -> {
                if (leafId != null) {
                    if (isOutOfBounds) {
                        if (currentRoot.value.leafCount() > 1) {
                            currentRoot.value.removingLeaf(leafId)?.let(onTreeChange)
                        }
                    } else when (target) {
                        is DropTarget.OnLeaf -> if (target.leafId != leafId) {
                            onTreeChange(currentRoot.value.movingLeaf(leafId, target.leafId, target.insert))
                        }
                        is DropTarget.OuterEdge -> {
                            currentRoot.value.removingLeaf(leafId)?.let { removed ->
                                onTreeChange(removed.insertingAsOuterSibling(leafId, target.insert))
                            }
                        }
                        null -> {}
                    }
                }
            }
            DragKind.ADD_BOX -> {
                if (!isOutOfBounds) {
                    when (target) {
                        is DropTarget.OnLeaf ->
                            onTreeChange(currentRoot.value.insertingAt(target.leafId, newNodeId("box"), target.insert))
                        is DropTarget.OuterEdge ->
                            onTreeChange(currentRoot.value.insertingAsOuterSibling(newNodeId("box"), target.insert))
                        null -> {}
                    }
                }
            }
            DragKind.ASSIGN_PHOTO -> {
                if (!isOutOfBounds && photoId != null) {
                    (target as? DropTarget.OnLeaf)?.let { onAssignPhoto(it.leafId, photoId) }
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
                    photosByBoxId = photosByBoxId,
                    borderPx = borderPx,
                    borderStyle = borderStyle,
                    editable = true,
                    movingLeafId = dragLeafId,
                    dragKind = dragKind,
                    hoverTarget = hoverTarget,
                    modifier = Modifier
                        .padding(CanvasOuterPadding)
                        .onGloballyPositioned { gridCoords = it },
                    onTreeChange = onTreeChange,
                    onRequestPhoto = onRequestPhoto,
                    onMoveDragStart = { leafId, startCenterPxGridLocal ->
                        dragKind = DragKind.MOVE
                        dragLeafId = leafId
                        val oc = outerCoords
                        val gc = gridCoords
                        if (oc != null && gc != null) {
                            updateGhost(oc.localPositionOf(gc, startCenterPxGridLocal))
                        }
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
                                    dragKind = DragKind.ADD_BOX
                                    dragLeafId = null
                                    centerInOuter(addHandleCoords)?.let { updateGhost(it) }
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

            Spacer(Modifier.height(18.dp))

            Text("Your Photos", color = Subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))

            // The photo pool: independent of the layout. Drag a tile onto a box
            // to put that photo there; the last tile adds a new photo to the pool.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (photo in photoPool) {
                    key(photo.id) {
                        PoolPhotoTile(
                            photo = photo,
                            onDragStart = { tileCoords ->
                                dragKind = DragKind.ASSIGN_PHOTO
                                dragPoolPhotoId = photo.id
                                centerInOuter(tileCoords)?.let { updateGhost(it) }
                            },
                            onDrag = { delta -> ghostPos?.let { updateGhost(it + delta) } },
                            onDragEnd = { commitDrag() },
                            onDragCancel = { clearDrag() }
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Dark700)
                        .clickable(onClick = onRequestAddToPool),
                    contentAlignment = Alignment.Center
                ) {
                    Text("＋", color = Pink500, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Ghost preview following the finger while a move/add/assign drag is active.
        val ghost = ghostPos
        if (dragKind != null && ghost != null) {
            val ghostSize = 56.dp
            val ghostSizePx = with(density) { ghostSize.toPx() }
            val pos = ghost - Offset(ghostSizePx / 2f, ghostSizePx / 2f)
            val draggedPhotoBitmap = if (dragKind == DragKind.ASSIGN_PHOTO) {
                photoPool.firstOrNull { it.id == dragPoolPhotoId }?.state?.bitmap
            } else null
            Box(
                Modifier
                    .offset(
                        x = with(density) { pos.x.toDp() },
                        y = with(density) { pos.y.toDp() }
                    )
                    .size(ghostSize)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (draggedPhotoBitmap == null) {
                            Modifier.background(
                                if (isOutOfBounds) Color(0xFFFF5252).copy(alpha = 0.85f)
                                else Pink500.copy(alpha = 0.85f)
                            )
                        } else Modifier.background(Dark700)
                    )
            ) {
                if (draggedPhotoBitmap != null) {
                    Image(
                        bitmap = draggedPhotoBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().alpha(0.9f),
                        contentScale = ContentScale.Crop
                    )
                    if (isOutOfBounds) {
                        Box(Modifier.fillMaxSize().background(Color(0xFFFF5252).copy(alpha = 0.45f)))
                    }
                }
            }
        }
    }
}

/** A single draggable thumbnail in the photo pool. No pan/zoom here - that's
 *  only for a photo once it's placed in a box; this is just a static preview. */
@Composable
private fun PoolPhotoTile(
    photo: PoolPhoto,
    onDragStart: (LayoutCoordinates) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        Modifier
            .size(64.dp)
            .onGloballyPositioned { coords = it }
            .clip(RoundedCornerShape(8.dp))
            .background(Dark700)
            .pointerInput(photo.id) {
                detectDragGestures(
                    onDragStart = { coords?.let(onDragStart) },
                    onDrag = { change, amount -> change.consume(); onDrag(amount) },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() }
                )
            }
    ) {
        Image(
            bitmap = photo.state.bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
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
    photosByBoxId: Map<String, PhotoState>,
    borderPx: Float,
    borderStyle: BorderStyle,
    editable: Boolean,
    movingLeafId: String?,
    dragKind: DragKind?,
    hoverTarget: DropTarget?,
    onTreeChange: (LayoutNode) -> Unit,
    onRequestPhoto: (String) -> Unit,
    onMoveDragStart: (String, Offset) -> Unit,
    onMoveDrag: (Offset) -> Unit,
    onMoveDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val currentRoot = rememberUpdatedState(root)

    BoxWithConstraints(modifier.fillMaxSize()) {
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
                    state = photosByBoxId[leafId],
                    editable = editable,
                    onRequestPhoto = onRequestPhoto,
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

        if (editable) {
            when (val target = hoverTarget) {
                null -> {}
                is DropTarget.OnLeaf -> {
                    val targetRect = leafRects[target.leafId]
                    if (targetRect != null) {
                        if (dragKind == DragKind.ASSIGN_PHOTO) {
                            // Assigning a photo doesn't reshape the layout - just highlight
                            // the whole box it will land in.
                            val targetPx = targetRect.toPixelRectWithBorder(wPx, hPx, borderPxPx)
                            PixelRectOverlay(
                                targetPx,
                                density,
                                Modifier.border(3.dp, Pink500, RoundedCornerShape(6.dp))
                            )
                        } else {
                            // Ghost split preview: shows exactly how the hovered box will divide -
                            // the new box's landing spot in pink, the existing box's new, smaller
                            // footprint outlined - before the drag is dropped.
                            val (firstNorm, secondNorm) = targetRect.splitBy(target.insert.direction, 0.5f)
                            val newBoxNorm = if (target.insert.newFirst) firstNorm else secondNorm
                            val remainingNorm = if (target.insert.newFirst) secondNorm else firstNorm
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
                is DropTarget.OuterEdge -> {
                    // A new row/column alongside everything else, not nested inside
                    // any single box - shown spanning the whole relevant edge.
                    val fraction = OUTER_EDGE_INSERT_FRACTION
                    val (newBoxNorm, remainingNorm) = when (target.insert.direction) {
                        SplitDirection.ROW -> if (target.insert.newFirst) {
                            NormalizedRect(0f, 0f, fraction, 1f) to NormalizedRect(fraction, 0f, 1f, 1f)
                        } else {
                            NormalizedRect(1f - fraction, 0f, 1f, 1f) to NormalizedRect(0f, 0f, 1f - fraction, 1f)
                        }
                        SplitDirection.COLUMN -> if (target.insert.newFirst) {
                            NormalizedRect(0f, 0f, 1f, fraction) to NormalizedRect(0f, fraction, 1f, 1f)
                        } else {
                            NormalizedRect(0f, 1f - fraction, 1f, 1f) to NormalizedRect(0f, 0f, 1f, 1f - fraction)
                        }
                    }
                    PixelRectOverlay(
                        remainingNorm.toPixelRectWithBorder(wPx, hPx, borderPxPx),
                        density,
                        Modifier.border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    )
                    PixelRectOverlay(
                        newBoxNorm.toPixelRectWithBorder(wPx, hPx, borderPxPx),
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
}

@Composable
private fun BoxScope.PixelRectOverlay(px: PixelRect, density: Density, modifier: Modifier) {
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
    val handleSize = 40.dp
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
    ) {
        // Drawn geometrically (not with a text glyph) so it is always exactly
        // centered, regardless of font metrics - a double-headed arrow showing
        // which way this handle drags: left-right for a vertical divider,
        // up-down for a horizontal one.
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val armLength = size.minDimension * 0.24f
            val headSize = size.minDimension * 0.14f
            val c = Offset(size.width / 2f, size.height / 2f)
            if (isRow) {
                drawLine(Pink500, Offset(c.x - armLength, c.y), Offset(c.x + armLength, c.y), strokeWidth, cap = StrokeCap.Round)
                drawLine(Pink500, Offset(c.x - armLength, c.y), Offset(c.x - armLength + headSize, c.y - headSize), strokeWidth, cap = StrokeCap.Round)
                drawLine(Pink500, Offset(c.x - armLength, c.y), Offset(c.x - armLength + headSize, c.y + headSize), strokeWidth, cap = StrokeCap.Round)
                drawLine(Pink500, Offset(c.x + armLength, c.y), Offset(c.x + armLength - headSize, c.y - headSize), strokeWidth, cap = StrokeCap.Round)
                drawLine(Pink500, Offset(c.x + armLength, c.y), Offset(c.x + armLength - headSize, c.y + headSize), strokeWidth, cap = StrokeCap.Round)
            } else {
                drawLine(Pink500, Offset(c.x, c.y - armLength), Offset(c.x, c.y + armLength), strokeWidth, cap = StrokeCap.Round)
                drawLine(Pink500, Offset(c.x, c.y - armLength), Offset(c.x - headSize, c.y - armLength + headSize), strokeWidth, cap = StrokeCap.Round)
                drawLine(Pink500, Offset(c.x, c.y - armLength), Offset(c.x + headSize, c.y - armLength + headSize), strokeWidth, cap = StrokeCap.Round)
                drawLine(Pink500, Offset(c.x, c.y + armLength), Offset(c.x - headSize, c.y + armLength - headSize), strokeWidth, cap = StrokeCap.Round)
                drawLine(Pink500, Offset(c.x, c.y + armLength), Offset(c.x + headSize, c.y + armLength - headSize), strokeWidth, cap = StrokeCap.Round)
            }
        }
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
    onMoveDragStart: () -> Unit,
    onMoveDrag: (Offset) -> Unit,
    onMoveDragEnd: () -> Unit,
    modifier: Modifier
) {
    // Long-pressing anywhere on the box starts a move; a quick tap (released
    // before the long-press fires) opens the photo picker instead. The flag
    // stops that same release from ALSO registering as a tap once a long
    // press has already kicked off a move (e.g. a long-press-then-release
    // with no movement in between).
    var suppressNextTap by remember(boxId) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Dark700)
            .then(
                if (editable) {
                    Modifier
                        .pointerInput(boxId) {
                            detectTapGestures(
                                onTap = {
                                    if (suppressNextTap) suppressNextTap = false
                                    else onRequestPhoto(boxId)
                                }
                            )
                        }
                        .pointerInput(boxId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    suppressNextTap = true
                                    onMoveDragStart()
                                },
                                onDrag = { change, amount -> change.consume(); onMoveDrag(amount) },
                                onDragEnd = { onMoveDragEnd() },
                                onDragCancel = { onMoveDragEnd() }
                            )
                        }
                } else {
                    Modifier.clickable { onRequestPhoto(boxId) }
                }
            )
    ) {
        if (state != null) {
            PhotoCell(state = state, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("＋", color = Pink500, fontSize = 24.sp, fontWeight = FontWeight.Normal)
                }
            }
        }
    }
}

/**
 * Like [androidx.compose.foundation.gestures.detectTransformGestures], but only
 * reacts once a second finger is down. A single finger is left completely
 * alone (never consumed), so it falls through to a sibling gesture on the
 * parent - here, long-press-and-drag to move the whole box. Framing a photo
 * (pinch to zoom, two-finger drag to pan) and moving the box it's in would
 * otherwise both be "drag on the photo", with no way to tell them apart.
 */
suspend fun PointerInputScope.detectTwoFingerTransformGestures(
    onGesture: (pan: Offset, zoom: Float) -> Unit
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled && event.changes.size >= 2) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = kotlin.math.abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()
                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop && (zoomChange != 1f || panChange != Offset.Zero)) {
                    onGesture(panChange, zoomChange)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
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
