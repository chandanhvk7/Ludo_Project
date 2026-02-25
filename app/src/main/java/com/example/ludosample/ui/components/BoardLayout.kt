package com.example.ludosample.ui.components

import androidx.compose.ui.geometry.Offset

/**
 * Maps logical board positions to pixel coordinates for rendering.
 * Each board type (Classic, Penta, Hex) provides its own implementation.
 */
interface BoardLayout {
    /** Pixel offset for a shared-path cell at the given absolute index. */
    fun pathCellOffset(absoluteIndex: Int): Offset

    /** Pixel offset for a home-column cell (0-based step from path entry). */
    fun homeCellOffset(slotIndex: Int, homeStep: Int): Offset

    /** Pixel offset for a token sitting in the yard (0-3). */
    fun yardTokenOffset(slotIndex: Int, tokenIndex: Int): Offset

    /** Pixel offset for the center/home finish area. */
    fun centerOffset(): Offset

    /** Size of a single cell in pixels. */
    fun cellSize(): Float
}
