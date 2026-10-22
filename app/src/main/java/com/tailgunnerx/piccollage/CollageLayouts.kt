package com.tailgunnerx.piccollage

/**
 * A named starting point for the layout editor. Picking one seeds the editable
 * [tree] - from there every box can be moved, resized, added, or removed, so
 * this is only ever the *initial* shape, not a fixed template.
 */
data class CollageTemplate(
    val id: String,
    val displayName: String,
    val description: String,
    val tree: LayoutNode
) {
    val photoCount: Int get() = tree.leafCount()
}

private fun row(fraction: Float, first: LayoutNode, second: LayoutNode) =
    SplitNode(newNodeId("split"), SplitDirection.ROW, fraction, first, second)

private fun column(fraction: Float, first: LayoutNode, second: LayoutNode) =
    SplitNode(newNodeId("split"), SplitDirection.COLUMN, fraction, first, second)

private fun box() = BoxNode(newNodeId("box"))

/** Even 3-way split across [items], laid out along [direction]. */
private fun evenSplit(direction: SplitDirection, items: List<LayoutNode>): LayoutNode {
    require(items.isNotEmpty())
    if (items.size == 1) return items[0]
    val fraction = 1f / items.size
    val rest = evenSplit(direction, items.drop(1))
    return SplitNode(newNodeId("split"), direction, fraction, items[0], rest)
}

val defaultTemplates: List<CollageTemplate> = listOf(
    // ── 2 photos ─────────────────────────────────────────────────────────
    CollageTemplate(
        id = "split_vertical",
        displayName = "Side by Side",
        description = "Two columns, left & right",
        tree = row(0.5f, box(), box())
    ),
    CollageTemplate(
        id = "split_horizontal",
        displayName = "Top & Bottom",
        description = "Two rows, top & bottom",
        tree = column(0.5f, box(), box())
    ),

    // ── 3 photos ─────────────────────────────────────────────────────────
    CollageTemplate(
        id = "big_left",
        displayName = "Big Left",
        description = "One large left + two stacked right",
        tree = row(0.55f, box(), column(0.5f, box(), box()))
    ),
    CollageTemplate(
        id = "big_right",
        displayName = "Big Right",
        description = "Two stacked left + one large right",
        tree = row(0.45f, column(0.5f, box(), box()), box())
    ),

    // ── 4 photos ─────────────────────────────────────────────────────────
    CollageTemplate(
        id = "grid_2x2",
        displayName = "Grid 2×2",
        description = "Classic four-square grid",
        tree = column(0.5f, row(0.5f, box(), box()), row(0.5f, box(), box()))
    ),
    CollageTemplate(
        id = "big_top",
        displayName = "Big Top",
        description = "One wide top + three below",
        tree = column(0.55f, box(), evenSplit(SplitDirection.ROW, listOf(box(), box(), box())))
    ),
    CollageTemplate(
        id = "big_bottom",
        displayName = "Big Bottom",
        description = "Three on top + one wide bottom",
        tree = column(0.45f, evenSplit(SplitDirection.ROW, listOf(box(), box(), box())), box())
    ),

    // ── 5 photos ─────────────────────────────────────────────────────────
    CollageTemplate(
        id = "grid_asymmetric",
        displayName = "Asymmetric 5",
        description = "Two large + three small",
        tree = column(
            0.55f,
            row(0.5f, box(), box()),
            evenSplit(SplitDirection.ROW, listOf(box(), box(), box()))
        )
    ),

    // ── 6 photos ─────────────────────────────────────────────────────────
    CollageTemplate(
        id = "grid_2x3",
        displayName = "Grid 2×3",
        description = "Six equal cells, 2 columns × 3 rows",
        tree = evenSplit(
            SplitDirection.COLUMN,
            listOf(
                row(0.5f, box(), box()),
                row(0.5f, box(), box()),
                row(0.5f, box(), box())
            )
        )
    )
)
