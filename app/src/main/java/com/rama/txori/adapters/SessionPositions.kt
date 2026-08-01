package com.rama.txori.adapters

import com.rama.txori.SessionItem

/**
 * Pure mapping between a row's index in the full item list ("raw") and its
 * position in the list as actually displayed ("visible"): a collapsed group's
 * rows are hidden, so every item below them shifts up.
 */

private fun SessionItem.isCollapsedRow(collapsed: Set<Long>): Boolean =
    this is SessionItem.Row && collapsed.contains(sessionId)

internal fun computeVisiblePosition(
    items: List<SessionItem>,
    collapsed: Set<Long>,
    rawIndex: Int
): Int {
    val item = items.getOrNull(rawIndex) ?: return -1
    if (item.isCollapsedRow(collapsed)) return -1
    var visible = 0
    for (i in items.indices) {
        if (!items[i].isCollapsedRow(collapsed)) {
            if (i == rawIndex) return visible
            visible++
        }
    }
    return -1
}

internal fun computeRawIndex(
    items: List<SessionItem>,
    collapsed: Set<Long>,
    visiblePosition: Int
): Int {
    var visible = 0
    for (i in items.indices) {
        if (!items[i].isCollapsedRow(collapsed)) {
            if (visible == visiblePosition) return i
            visible++
        }
    }
    return visiblePosition
}
