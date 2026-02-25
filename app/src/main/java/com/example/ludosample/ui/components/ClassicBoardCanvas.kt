package com.example.ludosample.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.ludosample.engine.BoardConfig
import com.example.ludosample.engine.GameState
import com.example.ludosample.engine.PlayerColor
import com.example.ludosample.engine.Token

private const val GRID_SIZE = 15

private val QUADRANT_COLORS = mapOf(
    0 to Color(0xFFE53935),
    1 to Color(0xFF43A047),
    2 to Color(0xFFFFB300),
    3 to Color(0xFF1E88E5)
)

private val TOKEN_COLORS = mapOf(
    PlayerColor.RED to Color(0xFFC62828),
    PlayerColor.GREEN to Color(0xFF2E7D32),
    PlayerColor.YELLOW to Color(0xFFF9A825),
    PlayerColor.BLUE to Color(0xFF1565C0),
    PlayerColor.ORANGE to Color(0xFFEF6C00),
    PlayerColor.PURPLE to Color(0xFF6A1B9A)
)

/**
 * The 52-cell shared path on the classic 15x15 board, specified as (row, col) pairs.
 * Index 0 corresponds to the absolute path position 0 (Red's start).
 * Winds clockwise starting from Red's entry at (6, 1).
 */
private val PATH_COORDS: List<Pair<Int, Int>> = buildList {
    // Segment 0 (Red): upward from (6,1) to (0,6) then right to (0,6)
    // Red start: going up column 6 from row 6
    add(6 to 1)   // 0  - Red start
    add(6 to 2)
    add(6 to 3)
    add(6 to 4)
    add(6 to 5)
    add(5 to 6)
    add(4 to 6)
    add(3 to 6)
    add(2 to 6)   // 8  - safe star
    add(1 to 6)
    add(0 to 6)
    add(0 to 7)
    add(0 to 8)

    // Segment 1 (Green): down from (1,8) to (6,8) then down to (6,13)
    add(1 to 8)   // 13 - Green start
    add(2 to 8)
    add(3 to 8)
    add(4 to 8)
    add(5 to 8)
    add(6 to 9)
    add(6 to 10)
    add(6 to 11)
    add(6 to 12)  // 21 - safe star
    add(6 to 13)
    add(6 to 14)
    add(7 to 14)
    add(8 to 14)

    // Segment 2 (Yellow): left from (8,13) to (8,9) then down to (14,8)
    add(8 to 13)  // 26 - Yellow start
    add(8 to 12)
    add(8 to 11)
    add(8 to 10)
    add(8 to 9)
    add(9 to 8)
    add(10 to 8)
    add(11 to 8)
    add(12 to 8)  // 34 - safe star
    add(13 to 8)
    add(14 to 8)
    add(14 to 7)
    add(14 to 6)

    // Segment 3 (Blue): up from (13,6) to (8,6) then left to (8,0)
    add(13 to 6)  // 39 - Blue start
    add(12 to 6)
    add(11 to 6)
    add(10 to 6)
    add(9 to 6)
    add(8 to 5)
    add(8 to 4)
    add(8 to 3)
    add(8 to 2)   // 47 - safe star
    add(8 to 1)
    add(8 to 0)
    add(7 to 0)
    add(6 to 0)
}

/** Home column coordinates for each slot (5 cells each, from path entry toward center). */
private val HOME_COLUMN_COORDS: List<List<Pair<Int, Int>>> = listOf(
    // Slot 0 (Red): row 7, cols 1-5
    listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5),
    // Slot 1 (Green): col 7 (actually 8 -> 7), rows 1-5
    listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7),
    // Slot 2 (Yellow): row 7, cols 13-9
    listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9),
    // Slot 3 (Blue): col 7, rows 13-9
    listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7)
)

/** Yard token positions (row, col) for each slot – 4 positions inside each corner quadrant. */
private val YARD_COORDS: List<List<Pair<Int, Int>>> = listOf(
    // Slot 0 (Red) – top-left quadrant
    listOf(2 to 2, 2 to 4, 4 to 2, 4 to 4),
    // Slot 1 (Green) – top-right quadrant
    listOf(2 to 10, 2 to 12, 4 to 10, 4 to 12),
    // Slot 2 (Yellow) – bottom-right quadrant
    listOf(10 to 10, 10 to 12, 12 to 10, 12 to 12),
    // Slot 3 (Blue) – bottom-left quadrant
    listOf(10 to 2, 10 to 4, 12 to 2, 12 to 4)
)

class ClassicBoardLayout(private val boardSize: Float) : BoardLayout {
    private val cell = boardSize / GRID_SIZE

    override fun pathCellOffset(absoluteIndex: Int): Offset {
        val (row, col) = PATH_COORDS[absoluteIndex % PATH_COORDS.size]
        return Offset(col * cell + cell / 2, row * cell + cell / 2)
    }

    override fun homeCellOffset(slotIndex: Int, homeStep: Int): Offset {
        val coords = HOME_COLUMN_COORDS[slotIndex]
        val (row, col) = coords[homeStep.coerceIn(0, coords.lastIndex)]
        return Offset(col * cell + cell / 2, row * cell + cell / 2)
    }

    override fun yardTokenOffset(slotIndex: Int, tokenIndex: Int): Offset {
        val coords = YARD_COORDS[slotIndex]
        val (row, col) = coords[tokenIndex.coerceIn(0, coords.lastIndex)]
        return Offset(col * cell + cell / 2, row * cell + cell / 2)
    }

    override fun centerOffset(): Offset {
        val center = boardSize / 2
        return Offset(center, center)
    }

    override fun cellSize(): Float = cell
}

@Composable
fun ClassicBoardCanvas(
    gameState: GameState,
    validMoves: List<Int>,
    currentPlayerId: String,
    onTokenTapped: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val config = BoardConfig.Classic
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(gameState, validMoves) {
                detectTapGestures { tapOffset ->
                    val layout = ClassicBoardLayout(size.width.toFloat())
                    handleTokenTap(
                        config, gameState, currentPlayerId, validMoves,
                        layout, tapOffset, onTokenTapped
                    )
                }
            }
    ) {
        val boardSize = size.width
        val layout = ClassicBoardLayout(boardSize)
        val cell = layout.cellSize()

        drawBoard(cell)
        drawPath(layout, cell, config)
        drawHomeColumns(layout, cell)
        drawCenter(cell)
        drawAllTokens(config, gameState, layout, cell, currentPlayerId, validMoves, textMeasurer)
    }
}

private fun DrawScope.drawBoard(cell: Float) {
    // Background
    drawRect(Color.White, size = size)

    // Quadrant backgrounds
    val quadrantSize = cell * 6
    val quadrants = listOf(
        Offset(0f, 0f) to 0,
        Offset(cell * 9, 0f) to 1,
        Offset(cell * 9, cell * 9) to 2,
        Offset(0f, cell * 9) to 3
    )
    for ((offset, slot) in quadrants) {
        drawRoundRect(
            color = QUADRANT_COLORS[slot]!!.copy(alpha = 0.3f),
            topLeft = offset,
            size = Size(quadrantSize, quadrantSize),
            cornerRadius = CornerRadius(cell * 0.3f)
        )
        // Yard border
        val inset = cell * 0.8f
        drawRoundRect(
            color = QUADRANT_COLORS[slot]!!,
            topLeft = Offset(offset.x + inset, offset.y + inset),
            size = Size(quadrantSize - inset * 2, quadrantSize - inset * 2),
            cornerRadius = CornerRadius(cell * 0.4f),
            style = Stroke(width = cell * 0.08f)
        )
    }

    // Grid lines for the cross path area
    val gridColor = Color.Gray.copy(alpha = 0.3f)
    val strokeWidth = 1f
    for (i in 0..GRID_SIZE) {
        val pos = i * cell
        // Only draw in the cross area (columns 6-8, rows 6-8)
        // Vertical lines in middle rows
        if (i in 6..9) {
            drawLine(gridColor, Offset(pos, 0f), Offset(pos, size.height), strokeWidth)
        }
        if (i in 6..9) {
            drawLine(gridColor, Offset(0f, pos), Offset(size.width, pos), strokeWidth)
        }
    }
}

private fun DrawScope.drawPath(layout: ClassicBoardLayout, cell: Float, config: BoardConfig) {
    val pathCellSize = Size(cell * 0.85f, cell * 0.85f)

    for (i in PATH_COORDS.indices) {
        val offset = layout.pathCellOffset(i)
        val topLeft = Offset(offset.x - pathCellSize.width / 2, offset.y - pathCellSize.height / 2)

        val isSafe = i in config.safeSpotIndices
        val isStart = i % config.cellsPerSegment == 0
        val slotForCell = i / config.cellsPerSegment

        val fillColor = when {
            isStart -> QUADRANT_COLORS[slotForCell]!!.copy(alpha = 0.6f)
            isSafe -> Color(0xFFE0E0E0)
            else -> Color.White
        }

        drawRoundRect(
            color = fillColor,
            topLeft = topLeft,
            size = pathCellSize,
            cornerRadius = CornerRadius(cell * 0.1f)
        )
        drawRoundRect(
            color = Color.Gray.copy(alpha = 0.5f),
            topLeft = topLeft,
            size = pathCellSize,
            cornerRadius = CornerRadius(cell * 0.1f),
            style = Stroke(width = 1f)
        )

        if (isSafe && !isStart) {
            drawCircle(
                color = Color.Gray.copy(alpha = 0.4f),
                radius = cell * 0.12f,
                center = offset,
                style = Stroke(width = 1.5f)
            )
        }
    }
}

private fun DrawScope.drawHomeColumns(layout: ClassicBoardLayout, cell: Float) {
    for (slot in 0 until 4) {
        val color = QUADRANT_COLORS[slot]!!
        for (step in 0 until 5) {
            val offset = layout.homeCellOffset(slot, step)
            val cellSize = Size(cell * 0.85f, cell * 0.85f)
            val topLeft = Offset(offset.x - cellSize.width / 2, offset.y - cellSize.height / 2)
            drawRoundRect(
                color = color.copy(alpha = 0.4f),
                topLeft = topLeft,
                size = cellSize,
                cornerRadius = CornerRadius(cell * 0.1f)
            )
            drawRoundRect(
                color = color,
                topLeft = topLeft,
                size = cellSize,
                cornerRadius = CornerRadius(cell * 0.1f),
                style = Stroke(width = 1.5f)
            )
        }
    }
}

private fun DrawScope.drawCenter(cell: Float) {
    val center = Offset(size.width / 2, size.height / 2)
    val triangleRadius = cell * 1.2f

    for (slot in 0 until 4) {
        val color = QUADRANT_COLORS[slot]!!
        val angle1 = Math.toRadians((slot * 90 + 45).toDouble())
        val angle2 = Math.toRadians((slot * 90 + 135).toDouble())

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(center.x, center.y)
            lineTo(
                center.x + (triangleRadius * kotlin.math.cos(angle1)).toFloat(),
                center.y + (triangleRadius * kotlin.math.sin(angle1)).toFloat()
            )
            lineTo(
                center.x + (triangleRadius * kotlin.math.cos(angle2)).toFloat(),
                center.y + (triangleRadius * kotlin.math.sin(angle2)).toFloat()
            )
            close()
        }
        drawPath(path, color = color.copy(alpha = 0.7f))
        drawPath(path, color = Color.White, style = Stroke(width = 1.5f))
    }
}

private fun DrawScope.drawAllTokens(
    config: BoardConfig,
    gameState: GameState,
    layout: ClassicBoardLayout,
    cell: Float,
    currentPlayerId: String,
    validMoves: List<Int>,
    textMeasurer: TextMeasurer
) {
    val tokenRadius = cell * 0.32f
    val isMyTurn = gameState.currentTurnPlayerId == currentPlayerId

    for ((playerId, player) in gameState.players) {
        if (player.isEliminated) continue
        val color = TOKEN_COLORS[player.color] ?: Color.Gray

        player.tokens.forEachIndexed { index, token ->
            if (token.isHome) return@forEachIndexed

            val offset = tokenOffset(config, token, player.slotIndex, index, layout)

            val isValid = isMyTurn && playerId == currentPlayerId && index in validMoves
            if (isValid) {
                drawCircle(
                    color = Color.White,
                    radius = tokenRadius + cell * 0.08f,
                    center = offset
                )
                drawCircle(
                    color = color.copy(alpha = 0.4f),
                    radius = tokenRadius + cell * 0.12f,
                    center = offset
                )
            }

            drawCircle(color = Color.White, radius = tokenRadius + 2f, center = offset)
            drawCircle(color = color, radius = tokenRadius, center = offset)

            val initial = player.name.firstOrNull()?.uppercase() ?: "?"
            val textResult = textMeasurer.measure(
                initial,
                style = TextStyle(
                    color = Color.White,
                    fontSize = (tokenRadius * 0.9f).sp,
                    fontWeight = FontWeight.Bold
                )
            )
            drawText(
                textResult,
                topLeft = Offset(
                    offset.x - textResult.size.width / 2,
                    offset.y - textResult.size.height / 2
                )
            )
        }
    }
}

private fun tokenOffset(
    config: BoardConfig,
    token: Token,
    slotIndex: Int,
    tokenIndex: Int,
    layout: ClassicBoardLayout
): Offset {
    if (token.position == -1) {
        return layout.yardTokenOffset(slotIndex, tokenIndex)
    }
    if (token.position < config.pathLength) {
        val absolutePos = config.toAbsolutePosition(token.position, slotIndex)
        return layout.pathCellOffset(absolutePos)
    }
    val homeStep = token.position - config.pathLength
    return layout.homeCellOffset(slotIndex, homeStep)
}

private fun handleTokenTap(
    config: BoardConfig,
    gameState: GameState,
    currentPlayerId: String,
    validMoves: List<Int>,
    layout: ClassicBoardLayout,
    tapOffset: Offset,
    onTokenTapped: (Int) -> Unit
) {
    val player = gameState.players[currentPlayerId] ?: return
    val tapRadius = layout.cellSize() * 0.5f

    for (tokenIndex in validMoves) {
        val token = player.tokens[tokenIndex]
        val tokenPos = tokenOffset(config, token, player.slotIndex, tokenIndex, layout)
        if ((tapOffset - tokenPos).getDistance() <= tapRadius) {
            onTokenTapped(tokenIndex)
            return
        }
    }
}
