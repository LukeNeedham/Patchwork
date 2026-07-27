package com.tailgunnerx.piccollage

import java.util.UUID

/**
 * A collage layout is a binary tree of splits (like a tiling window manager):
 * every internal node divides its rectangle into two along one axis, at a
 * given fraction. Leaves are picture boxes. This single model drives the
 * live preview, thumbnails, the interactive editor, and the final export -
 * geometry is computed once, not re-implemented per layout.
 */
sealed class LayoutNode {
    abstract val id: String
}

data class BoxNode(override val id: String) : LayoutNode()

enum class SplitDirection { ROW, COLUMN }

data class SplitNode(
    override val id: String,
    val direction: SplitDirection,
    val fraction: Float,
    val first: LayoutNode,
    val second: LayoutNode
) : LayoutNode()

data class QuadrantInsert(val direction: SplitDirection, val newFirst: Boolean)

/** Rectangle in 0..1 fractions of the overall canvas. */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    operator fun contains(point: Pair<Float, Float>): Boolean =
        point.first in left..right && point.second in top..bottom
}

/** Rectangle in absolute pixels, after border insets have been applied. */
data class PixelRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

private val FULL_RECT = NormalizedRect(0f, 0f, 1f, 1f)

const val MIN_SPLIT_FRACTION = 0.12f

private var idCounter = 0

/** Fresh, collision-free node id. Used for boxes/splits created during editing. */
fun newNodeId(prefix: String): String {
    idCounter += 1
    return "$prefix-$idCounter-${UUID.randomUUID().toString().take(8)}"
}

private fun splitChildRects(node: SplitNode, rect: NormalizedRect): Pair<NormalizedRect, NormalizedRect> =
    when (node.direction) {
        SplitDirection.ROW -> {
            val splitX = rect.left + rect.width * node.fraction
            NormalizedRect(rect.left, rect.top, splitX, rect.bottom) to
                NormalizedRect(splitX, rect.top, rect.right, rect.bottom)
        }
        SplitDirection.COLUMN -> {
            val splitY = rect.top + rect.height * node.fraction
            NormalizedRect(rect.left, rect.top, rect.right, splitY) to
                NormalizedRect(rect.left, splitY, rect.right, rect.bottom)
        }
    }

/** Normalized rect for every node in the tree (leaves and internal splits), keyed by id. */
fun LayoutNode.computeAllRects(bounds: NormalizedRect = FULL_RECT): Map<String, NormalizedRect> {
    val result = LinkedHashMap<String, NormalizedRect>()
    fun visit(node: LayoutNode, rect: NormalizedRect) {
        result[node.id] = rect
        if (node is SplitNode) {
            val (firstRect, secondRect) = splitChildRects(node, rect)
            visit(node.first, firstRect)
            visit(node.second, secondRect)
        }
    }
    visit(this, bounds)
    return result
}

/** Normalized rect for every leaf (picture box), keyed by id. */
fun LayoutNode.computeRects(bounds: NormalizedRect = FULL_RECT): Map<String, NormalizedRect> =
    computeAllRects(bounds).filterKeys { id -> this.findNode(id) is BoxNode }

fun LayoutNode.findNode(targetId: String): LayoutNode? {
    if (id == targetId) return this
    return when (this) {
        is BoxNode -> null
        is SplitNode -> first.findNode(targetId) ?: second.findNode(targetId)
    }
}

fun LayoutNode.leafIds(): List<String> {
    val ids = mutableListOf<String>()
    fun visit(node: LayoutNode) {
        when (node) {
            is BoxNode -> ids += node.id
            is SplitNode -> { visit(node.first); visit(node.second) }
        }
    }
    visit(this)
    return ids
}

fun LayoutNode.leafCount(): Int = leafIds().size

/** Every split (divider) in the tree, in root-to-leaf order. */
fun LayoutNode.allSplits(): List<SplitNode> {
    val splits = mutableListOf<SplitNode>()
    fun visit(node: LayoutNode) {
        if (node is SplitNode) {
            splits += node
            visit(node.first)
            visit(node.second)
        }
    }
    visit(this)
    return splits
}

/** Converts a normalized leaf rect to absolute pixels, insetting by the border:
 *  a full border where the rect touches the canvas edge, a half-border where it
 *  borders another box (so two adjoining half-borders sum to one full gap). */
fun NormalizedRect.toPixelRectWithBorder(canvasWidth: Float, canvasHeight: Float, borderPx: Float): PixelRect {
    val halfBp = borderPx / 2f
    val insetLeft = if (left <= 0.0001f) borderPx else halfBp
    val insetRight = if (right >= 0.9999f) borderPx else halfBp
    val insetTop = if (top <= 0.0001f) borderPx else halfBp
    val insetBottom = if (bottom >= 0.9999f) borderPx else halfBp
    return PixelRect(
        left = left * canvasWidth + insetLeft,
        top = top * canvasHeight + insetTop,
        right = right * canvasWidth - insetRight,
        bottom = bottom * canvasHeight - insetBottom
    )
}

/** Returns a copy of the tree with the node matching [targetId] replaced by `transform(node)`. */
fun LayoutNode.replacingNode(targetId: String, transform: (LayoutNode) -> LayoutNode): LayoutNode {
    if (id == targetId) return transform(this)
    return when (this) {
        is BoxNode -> this
        is SplitNode -> copy(
            first = first.replacingNode(targetId, transform),
            second = second.replacingNode(targetId, transform)
        )
    }
}

fun LayoutNode.withSplitFraction(splitId: String, fraction: Float): LayoutNode =
    replacingNode(splitId) { node ->
        (node as SplitNode).copy(fraction = fraction.coerceIn(MIN_SPLIT_FRACTION, 1f - MIN_SPLIT_FRACTION))
    }

/**
 * Removes a leaf box from the tree. Its sibling in the split absorbs the freed
 * space (the split node itself collapses away). Returns null if [leafId] was
 * the tree's only box (nothing left to show).
 */
fun LayoutNode.removingLeaf(leafId: String): LayoutNode? = when (this) {
    is BoxNode -> if (id == leafId) null else this
    is SplitNode -> {
        val newFirst = first.removingLeaf(leafId)
        val newSecond = second.removingLeaf(leafId)
        when {
            newFirst == null -> second
            newSecond == null -> first
            newFirst === first && newSecond === second -> this
            else -> copy(first = newFirst, second = newSecond)
        }
    }
}

/**
 * Splits the leaf [targetLeafId] in two, inserting a brand-new leaf [newLeafId]
 * on the requested side. This is how a box is "dragged in": it lands on top of
 * an existing box, which divides to make room.
 */
fun LayoutNode.insertingAt(
    targetLeafId: String,
    newLeafId: String,
    insert: QuadrantInsert,
    fraction: Float = 0.5f
): LayoutNode = replacingNode(targetLeafId) { target ->
    val newLeaf = BoxNode(newLeafId)
    val splitId = newNodeId("split")
    if (insert.newFirst) {
        SplitNode(splitId, insert.direction, fraction, newLeaf, target)
    } else {
        SplitNode(splitId, insert.direction, 1f - fraction, target, newLeaf)
    }
}

/** Moves an existing leaf to a new position: removed from its old spot (sibling absorbs the gap), then inserted at the target. */
fun LayoutNode.movingLeaf(leafId: String, targetLeafId: String, insert: QuadrantInsert): LayoutNode {
    if (leafId == targetLeafId) return this
    val removed = removingLeaf(leafId) ?: return this
    // targetLeafId must still exist in `removed` - it can't, since leafId != targetLeafId and only leafId was removed.
    return removed.insertingAt(targetLeafId, leafId, insert)
}

/** Which leaf (if any) contains [pos] (0..1 canvas-normalized), and where within it, as a drop target for move/insert gestures. */
fun findDropTarget(
    root: LayoutNode,
    pos: Pair<Float, Float>,
    excludeLeafId: String? = null
): Pair<String, QuadrantInsert>? {
    val rects = root.computeRects()
    val targetId = root.leafIds()
        .filter { it != excludeLeafId }
        .firstOrNull { id -> rects[id]?.let { pos in it } == true }
        ?: return null
    val r = rects[targetId] ?: return null
    val relX = if (r.width > 0f) (pos.first - r.left) / r.width else 0.5f
    val relY = if (r.height > 0f) (pos.second - r.top) / r.height else 0.5f
    val distLeft = relX
    val distRight = 1f - relX
    val distTop = relY
    val distBottom = 1f - relY
    val minDist = minOf(distLeft, distRight, distTop, distBottom)
    val insert = when (minDist) {
        distLeft -> QuadrantInsert(SplitDirection.ROW, newFirst = true)
        distRight -> QuadrantInsert(SplitDirection.ROW, newFirst = false)
        distTop -> QuadrantInsert(SplitDirection.COLUMN, newFirst = true)
        else -> QuadrantInsert(SplitDirection.COLUMN, newFirst = false)
    }
    return targetId to insert
}
