package com.example.ludosample.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Path
import com.example.ludosample.engine.BoardConfig
import com.example.ludosample.engine.GameState
import com.example.ludosample.engine.PlayerColor
import com.example.ludosample.engine.Token
import com.example.ludosample.ui.theme.BoardDark
import com.example.ludosample.ui.theme.CellBorder
import com.example.ludosample.ui.theme.CellDark
import com.example.ludosample.ui.theme.CellSafe
import com.example.ludosample.ui.theme.GlassBorder
import com.example.ludosample.ui.theme.TokenShadow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun Color.lighten(f: Float) = Color(
    (red + (1f - red) * f).coerceIn(0f, 1f),
    (green + (1f - green) * f).coerceIn(0f, 1f),
    (blue + (1f - blue) * f).coerceIn(0f, 1f), alpha
)

private fun Color.darken(f: Float) = Color(
    (red * (1f - f)).coerceIn(0f, 1f),
    (green * (1f - f)).coerceIn(0f, 1f),
    (blue * (1f - f)).coerceIn(0f, 1f), alpha
)

private val OffsetToVector: TwoWayConverter<Offset, AnimationVector2D> =
    TwoWayConverter(
        convertToVector = { AnimationVector2D(it.x, it.y) },
        convertFromVector = { Offset(it.v1, it.v2) }
    )

private const val GRID_SIZE = 15

private val PLAYER_COLORS = mapOf(
    PlayerColor.RED to Color(0xFFE53935),
    PlayerColor.GREEN to Color(0xFF43A047),
    PlayerColor.YELLOW to Color(0xFFFDD835),
    PlayerColor.BLUE to Color(0xFF1E88E5),
    PlayerColor.ORANGE to Color(0xFFEF6C00),
    PlayerColor.PURPLE to Color(0xFF6A1B9A)
)

private val QUADRANT_COLORS = mapOf(
    0 to PLAYER_COLORS[PlayerColor.RED]!!,
    1 to PLAYER_COLORS[PlayerColor.GREEN]!!,
    2 to PLAYER_COLORS[PlayerColor.YELLOW]!!,
    3 to PLAYER_COLORS[PlayerColor.BLUE]!!
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

/** Quadrant centers as cell-center indices (midpoint of each 6-cell span). */
private val QUADRANT_CENTERS: List<Pair<Float, Float>> = listOf(
    2.5f to 2.5f,    // Slot 0 (Red) – top-left
    2.5f to 11.5f,   // Slot 1 (Green) – top-right
    11.5f to 11.5f,  // Slot 2 (Yellow) – bottom-right
    11.5f to 2.5f    // Slot 3 (Blue) – bottom-left
)

/** Centroids of the 4 physical triangles in the center 3x3 home area (row, col). */
private val PHYS_TRIANGLE_CENTERS: List<Pair<Float, Float>> = listOf(
    7.5f to 6.5f,  // Physical left
    6.5f to 7.5f,  // Physical top
    7.5f to 8.5f,  // Physical right
    8.5f to 7.5f   // Physical bottom
)

class ClassicBoardLayout(private val boardSize: Float, val rotation: Int = 0) : BoardLayout {
    private val cell = boardSize / GRID_SIZE

    private fun rotateGrid(row: Float, col: Float): Pair<Float, Float> {
        val max = GRID_SIZE - 1f
        return when (rotation % 4) {
            1 -> col to (max - row)
            2 -> (max - row) to (max - col)
            3 -> (max - col) to row
            else -> row to col
        }
    }

    fun slotAtPhysicalPosition(physPos: Int): Int = (physPos - rotation + 4) % 4

    fun yardCenterOffset(slotIndex: Int): Offset {
        val (centerRow, centerCol) = QUADRANT_CENTERS[slotIndex.coerceIn(0, 3)]
        val (rRow, rCol) = rotateGrid(centerRow, centerCol)
        return Offset(rCol * cell + cell / 2, rRow * cell + cell / 2)
    }

    override fun pathCellOffset(absoluteIndex: Int): Offset {
        val (row, col) = PATH_COORDS[absoluteIndex % PATH_COORDS.size]
        val (rRow, rCol) = rotateGrid(row.toFloat(), col.toFloat())
        return Offset(rCol * cell + cell / 2, rRow * cell + cell / 2)
    }

    override fun homeCellOffset(slotIndex: Int, homeStep: Int): Offset {
        val coords = HOME_COLUMN_COORDS[slotIndex]
        val (row, col) = coords[homeStep.coerceIn(0, coords.lastIndex)]
        val (rRow, rCol) = rotateGrid(row.toFloat(), col.toFloat())
        return Offset(rCol * cell + cell / 2, rRow * cell + cell / 2)
    }

    override fun yardTokenOffset(slotIndex: Int, tokenIndex: Int): Offset {
        val (centerRow, centerCol) = QUADRANT_CENTERS[slotIndex.coerceIn(0, 3)]
        val (rRow, rCol) = rotateGrid(centerRow, centerCol)
        val cx = rCol * cell + cell / 2
        val cy = rRow * cell + cell / 2
        val spacing = cell * 1.4f
        val dx = if (tokenIndex % 2 == 0) -spacing / 2 else spacing / 2
        val dy = if (tokenIndex < 2) -spacing / 2 else spacing / 2
        return Offset(cx + dx, cy + dy)
    }

    fun homeTriangleCenterOffset(slotIndex: Int, tokenIndex: Int): Offset {
        val physTriangle = (slotIndex + rotation) % 4
        val (row, col) = PHYS_TRIANGLE_CENTERS[physTriangle]
        val d = cell * 0.18f
        val dx = if (tokenIndex % 2 == 0) -d else d
        val dy = if (tokenIndex < 2) -d else d
        return Offset(col * cell + dx, row * cell + dy)
    }

    override fun centerOffset(): Offset = Offset(boardSize / 2, boardSize / 2)

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
    val mySlot = gameState.players[currentPlayerId]?.slotIndex ?: 3
    val boardRotation = (3 - mySlot + 4) % 4

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    var lastDice by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    androidx.compose.runtime.LaunchedEffect(gameState.diceValue) {
        gameState.diceValue?.let { lastDice = it }
    }

    var boardSize by remember { mutableFloatStateOf(0f) }
    val animatedOffsets = remember {
        mutableStateMapOf<String, Animatable<Offset, AnimationVector2D>>()
    }
    val prevPositions = remember { mutableStateMapOf<String, Pair<Int, Boolean>>() }
    val tokenTrails = remember { mutableStateMapOf<String, List<Offset>>() }

    if (boardSize > 0f) {
        val layout = ClassicBoardLayout(boardSize, boardRotation)
        for ((playerId, player) in gameState.players) {
            if (player.isEliminated) continue
            player.tokens.forEachIndexed { index, token ->
                val key = "${playerId}_$index"
                val target = tokenOffset(config, token, player.slotIndex, index, layout)
                val anim = animatedOffsets.getOrPut(key) {
                    Animatable(target, OffsetToVector)
                }
                val slotIndex = player.slotIndex
                LaunchedEffect(key, token.position, token.isHome, boardSize, boardRotation) {
                    val prev = prevPositions[key]
                    if (prev == null || (prev.first == token.position && prev.second == token.isHome)) {
                        anim.snapTo(target)
                        tokenTrails.remove(key)
                    } else {
                        val wasCaptured = prev.first >= 0 && token.position == -1 && !token.isHome
                        if (wasCaptured) {
                            val capturerSteps = lastDice.coerceIn(1, 6)
                            kotlinx.coroutines.delay((capturerSteps * 300 + 150).toLong())
                        }
                        val trail = mutableListOf(anim.value)
                        tokenTrails[key] = trail.toList()
                        val waypoints = if (wasCaptured) {
                            buildReverseCaptureWaypoints(config, prev.first, slotIndex)
                        } else {
                            buildPathWaypoints(
                                config, prev.first, prev.second,
                                token.position, token.isHome
                            )
                        }
                        for ((pos, isHome) in waypoints) {
                            val wp = tokenOffset(config, Token(pos, isHome), slotIndex, index, layout)
                            anim.animateTo(wp, animationSpec = tween(if (wasCaptured) 80 else 300))
                            trail.add(wp)
                            tokenTrails[key] = trail.toList()
                        }
                        kotlinx.coroutines.delay(350)
                        tokenTrails.remove(key)
                    }
                    prevPositions[key] = token.position to token.isHome
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { boardSize = it.width.toFloat() }
            .pointerInput(gameState, validMoves, boardRotation) {
                detectTapGestures { tapOffset ->
                    val layout = ClassicBoardLayout(size.width.toFloat(), boardRotation)
                    handleTokenTap(
                        config, gameState, currentPlayerId, validMoves,
                        layout, tapOffset, onTokenTapped
                    )
                }
            }
    ) {
        val layout = ClassicBoardLayout(size.width, boardRotation)
        val cell = layout.cellSize()

        drawBoard(layout, cell)
        drawPath(layout, cell, config)
        drawHomeColumns(layout, cell)
        drawCenter(layout, cell)
        drawTokenTrails(tokenTrails, gameState, cell)
        drawAllTokensAnimated(
            config, gameState, layout, cell, currentPlayerId, validMoves,
            textMeasurer, animatedOffsets, pulseScale, pulseAlpha
        )
        drawFinishedCrowns(gameState, layout, cell, textMeasurer)
    }
}

private fun DrawScope.drawBoard(layout: ClassicBoardLayout, cell: Float) {
    drawRect(BoardDark, size = size)

    val quadrantSize = cell * 6
    val quadrants = listOf(
        Offset(0f, 0f) to layout.slotAtPhysicalPosition(0),
        Offset(cell * 9, 0f) to layout.slotAtPhysicalPosition(1),
        Offset(cell * 9, cell * 9) to layout.slotAtPhysicalPosition(2),
        Offset(0f, cell * 9) to layout.slotAtPhysicalPosition(3)
    )
    for ((offset, slot) in quadrants) {
        val qColor = QUADRANT_COLORS[slot]!!
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    qColor.copy(alpha = 0.45f),
                    qColor.copy(alpha = 0.22f)
                ),
                startY = offset.y,
                endY = offset.y + quadrantSize
            ),
            topLeft = offset,
            size = Size(quadrantSize, quadrantSize),
            cornerRadius = CornerRadius(cell * 0.3f)
        )
        // Glass sheen gradient overlay
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                startY = offset.y,
                endY = offset.y + quadrantSize * 0.45f
            ),
            topLeft = offset,
            size = Size(quadrantSize, quadrantSize),
            cornerRadius = CornerRadius(cell * 0.3f)
        )
        val inset = cell * 0.8f
        drawRoundRect(
            color = qColor.copy(alpha = 0.5f),
            topLeft = Offset(offset.x + inset, offset.y + inset),
            size = Size(quadrantSize - inset * 2, quadrantSize - inset * 2),
            cornerRadius = CornerRadius(cell * 0.4f),
            style = Stroke(width = cell * 0.08f)
        )
    }

    val gridColor = Color.White.copy(alpha = 0.06f)
    val strokeWidth = 1f
    for (i in 0..GRID_SIZE) {
        val pos = i * cell
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
            isStart -> QUADRANT_COLORS[slotForCell]!!.copy(alpha = 0.70f)
            isSafe -> Color(0xFFD8DAE0)
            else -> CellDark
        }

        if (isSafe && !isStart) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFEEEFF2), Color(0xFFCACDD4)),
                    startY = topLeft.y,
                    endY = topLeft.y + pathCellSize.height
                ),
                topLeft = topLeft,
                size = pathCellSize,
                cornerRadius = CornerRadius(cell * 0.1f)
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                    startY = topLeft.y,
                    endY = topLeft.y + pathCellSize.height * 0.45f
                ),
                topLeft = topLeft,
                size = pathCellSize,
                cornerRadius = CornerRadius(cell * 0.1f)
            )
        } else {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(fillColor.lighten(0.15f), fillColor),
                    startY = topLeft.y,
                    endY = topLeft.y + pathCellSize.height
                ),
                topLeft = topLeft,
                size = pathCellSize,
                cornerRadius = CornerRadius(cell * 0.1f)
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                    startY = topLeft.y,
                    endY = topLeft.y + pathCellSize.height * 0.5f
                ),
                topLeft = topLeft,
                size = pathCellSize,
                cornerRadius = CornerRadius(cell * 0.1f)
            )
        }
        drawRoundRect(
            color = CellBorder,
            topLeft = topLeft,
            size = pathCellSize,
            cornerRadius = CornerRadius(cell * 0.1f),
            style = Stroke(width = 1f)
        )

        if (isStart) {
            drawStar(offset, cell * 0.2f, Color.White.copy(alpha = 0.6f))
        } else if (isSafe) {
            drawStar(offset, cell * 0.25f, Color(0xFF1B2838).copy(alpha = 0.4f))
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
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.70f),
                        color.copy(alpha = 0.40f)
                    ),
                    startY = topLeft.y,
                    endY = topLeft.y + cellSize.height
                ),
                topLeft = topLeft,
                size = cellSize,
                cornerRadius = CornerRadius(cell * 0.1f)
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                    startY = topLeft.y,
                    endY = topLeft.y + cellSize.height * 0.5f
                ),
                topLeft = topLeft,
                size = cellSize,
                cornerRadius = CornerRadius(cell * 0.1f)
            )
            drawRoundRect(
                color = color.copy(alpha = 0.75f),
                topLeft = topLeft,
                size = cellSize,
                cornerRadius = CornerRadius(cell * 0.1f),
                style = Stroke(width = 1.5f)
            )
        }
    }
}

private fun DrawScope.drawCenter(layout: ClassicBoardLayout, cell: Float) {
    val left = cell * 6
    val top = cell * 6
    val right = cell * 9
    val bottom = cell * 9
    val cx = (left + right) / 2
    val cy = (top + bottom) / 2
    val centerSize = right - left

    drawRect(BoardDark, topLeft = Offset(left, top), size = Size(centerSize, centerSize))

    val triangleData = listOf(
        layout.slotAtPhysicalPosition(0) to listOf(Offset(left, top), Offset(left, bottom), Offset(cx, cy)),
        layout.slotAtPhysicalPosition(1) to listOf(Offset(left, top), Offset(right, top), Offset(cx, cy)),
        layout.slotAtPhysicalPosition(2) to listOf(Offset(right, top), Offset(right, bottom), Offset(cx, cy)),
        layout.slotAtPhysicalPosition(3) to listOf(Offset(left, bottom), Offset(right, bottom), Offset(cx, cy))
    )

    for ((slot, vertices) in triangleData) {
        val color = QUADRANT_COLORS[slot]!!
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(vertices[0].x, vertices[0].y)
            lineTo(vertices[1].x, vertices[1].y)
            lineTo(vertices[2].x, vertices[2].y)
            close()
        }
        drawPath(path, color = color.copy(alpha = 0.70f))
        drawPath(path, color = GlassBorder, style = Stroke(width = 2f))
    }

    // Glassy dome overlay
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
            center = Offset(cx, cy),
            radius = centerSize / 2
        ),
        radius = centerSize / 2,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawFinishedCrowns(
    gameState: GameState,
    layout: ClassicBoardLayout,
    cell: Float,
    textMeasurer: TextMeasurer
) {
    val rankLabels = listOf("1st", "2nd", "3rd", "4th", "5th", "6th")
    for ((playerId, player) in gameState.players) {
        if (!player.isFinished) continue
        val rank = gameState.finishOrder.indexOf(playerId)
        if (rank < 0) continue

        val center = layout.yardCenterOffset(player.slotIndex)
        val color = PLAYER_COLORS[player.color] ?: Color.Gray

        // Crown shape
        val crownW = cell * 2.2f
        val crownH = cell * 1.4f
        val crownTop = center.y - crownH * 0.7f
        val crownPath = Path().apply {
            moveTo(center.x - crownW / 2, center.y - crownH * 0.1f)
            lineTo(center.x - crownW / 2, crownTop + crownH * 0.35f)
            lineTo(center.x - crownW * 0.25f, crownTop + crownH * 0.55f)
            lineTo(center.x, crownTop)
            lineTo(center.x + crownW * 0.25f, crownTop + crownH * 0.55f)
            lineTo(center.x + crownW / 2, crownTop + crownH * 0.35f)
            lineTo(center.x + crownW / 2, center.y - crownH * 0.1f)
            close()
        }
        drawPath(crownPath, color = Color(0xFFE8B931).copy(alpha = 0.85f))
        drawPath(crownPath, color = Color.White.copy(alpha = 0.3f), style = Stroke(width = 2f))

        // Rank text below crown
        val label = rankLabels.getOrElse(rank) { "${rank + 1}th" }
        val rankResult = textMeasurer.measure(
            label,
            style = TextStyle(
                color = color,
                fontSize = (cell * 0.7f).sp,
                fontWeight = FontWeight.Bold
            )
        )
        drawText(
            rankResult,
            topLeft = Offset(
                center.x - rankResult.size.width / 2,
                center.y + crownH * 0.05f
            )
        )
    }
}

private fun DrawScope.drawStar(center: Offset, outerRadius: Float, color: Color) {
    val path = Path()
    val innerRadius = outerRadius * 0.45f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val angle = -PI / 2 + i * PI / 5
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawTokenTrails(
    trails: Map<String, List<Offset>>,
    gameState: GameState,
    cell: Float
) {
    for ((key, trail) in trails) {
        val playerId = key.substringBefore("_")
        val player = gameState.players[playerId] ?: continue
        val color = PLAYER_COLORS[player.color] ?: Color.Gray
        val trailSize = trail.size
        trail.forEachIndexed { i, point ->
            val alpha = (i + 1).toFloat() / (trailSize + 1) * 0.35f
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = cell * 0.18f,
                center = point
            )
        }
    }
}

private fun DrawScope.drawAllTokensAnimated(
    config: BoardConfig,
    gameState: GameState,
    layout: ClassicBoardLayout,
    cell: Float,
    @Suppress("UNUSED_PARAMETER") currentPlayerId: String,
    validMoves: List<Int>,
    textMeasurer: TextMeasurer,
    animatedOffsets: Map<String, Animatable<Offset, AnimationVector2D>>,
    pulseScale: Float,
    pulseAlpha: Float = 1f
) {
    val tokenRadius = cell * 0.32f
    val homeTokenRadius = cell * 0.25f
    val turnPlayerId = gameState.currentTurnPlayerId

    // Pre-compute stacking groups for tokens sharing the same cell
    val positionGroups = mutableMapOf<String, MutableList<Pair<String, Int>>>()
    for ((playerId, player) in gameState.players) {
        if (player.isEliminated) continue
        player.tokens.forEachIndexed { index, token ->
            if (token.position == -1 || token.isHome) return@forEachIndexed
            val posKey = if (token.position < config.pathLength - 1) {
                "p${config.toAbsolutePosition(token.position, player.slotIndex)}"
            } else {
                "h${player.slotIndex}_${token.position - (config.pathLength - 1)}"
            }
            positionGroups.getOrPut(posKey) { mutableListOf() }.add(playerId to index)
        }
    }

    for ((playerId, player) in gameState.players) {
        if (player.isEliminated) continue
        val color = PLAYER_COLORS[player.color] ?: Color.Gray

        player.tokens.forEachIndexed { index, token ->
            val key = "${playerId}_$index"
            val baseOffset = animatedOffsets[key]?.value
                ?: tokenOffset(config, token, player.slotIndex, index, layout)

            // Compute stacking nudge for path/home-column tokens
            val nudge = if (token.position >= 0 && !token.isHome) {
                val posKey = if (token.position < config.pathLength - 1) {
                    "p${config.toAbsolutePosition(token.position, player.slotIndex)}"
                } else {
                    "h${player.slotIndex}_${token.position - (config.pathLength - 1)}"
                }
                val group = positionGroups[posKey] ?: emptyList()
                if (group.size > 1) {
                    val idx = group.indexOfFirst { it.first == playerId && it.second == index }
                    stackNudge(idx, cell)
                } else Offset.Zero
            } else Offset.Zero

            val offset = baseOffset + nudge
            val radius = if (token.isHome) homeTokenRadius else tokenRadius

            val isValid = playerId == turnPlayerId && index in validMoves
            if (isValid) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.7f * pulseAlpha), Color.Transparent),
                        center = offset,
                        radius = (radius + cell * 0.30f) * pulseScale
                    ),
                    radius = (radius + cell * 0.30f) * pulseScale,
                    center = offset
                )
                drawCircle(
                    color = color.copy(alpha = 0.85f * pulseAlpha),
                    radius = radius + cell * 0.12f,
                    center = offset,
                    style = Stroke(cell * 0.07f * pulseScale)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f * pulseAlpha),
                    radius = radius + cell * 0.06f,
                    center = offset,
                    style = Stroke(cell * 0.03f * pulseScale)
                )
            }

            // Drop shadow
            drawCircle(
                color = TokenShadow,
                radius = radius * 0.95f,
                center = offset + Offset(cell * 0.04f, cell * 0.06f)
            )
            // White outer ring
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = radius + cell * 0.03f,
                center = offset
            )
            // Base with radial gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.lighten(0.3f), color, color.darken(0.3f)),
                    center = Offset(offset.x - radius * 0.25f, offset.y - radius * 0.3f),
                    radius = radius * 1.2f
                ),
                radius = radius,
                center = offset
            )
            // Rim highlight arc
            val arcRect = Size(radius * 2f, radius * 2f)
            val arcTopLeft = Offset(offset.x - radius, offset.y - radius)
            drawArc(
                color = Color.White.copy(alpha = 0.35f),
                startAngle = 200f, sweepAngle = 140f,
                useCenter = false,
                style = Stroke(radius * 0.12f),
                topLeft = arcTopLeft, size = arcRect
            )
            // Specular highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = radius * 0.22f,
                center = Offset(offset.x - radius * 0.25f, offset.y - radius * 0.35f)
            )

            if (token.isHome) {
                val checkResult = textMeasurer.measure(
                    "\u2713",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = (radius * 1.1f).sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                drawText(
                    checkResult,
                    topLeft = Offset(
                        offset.x - checkResult.size.width / 2,
                        offset.y - checkResult.size.height / 2
                    )
                )
            } else {
                val initial = player.name.firstOrNull()?.uppercase() ?: "?"
                val textResult = textMeasurer.measure(
                    initial,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = (radius * 0.6f).sp,
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
}

private fun stackNudge(indexInGroup: Int, cell: Float): Offset {
    val d = cell * 0.16f
    return when (indexInGroup % 4) {
        0 -> Offset(-d, -d)
        1 -> Offset(d, d)
        2 -> Offset(d, -d)
        3 -> Offset(-d, d)
        else -> Offset.Zero
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
    if (token.isHome) {
        return layout.homeTriangleCenterOffset(slotIndex, tokenIndex)
    }
    if (token.position < config.pathLength - 1) {
        val absolutePos = config.toAbsolutePosition(token.position, slotIndex)
        return layout.pathCellOffset(absolutePos)
    }
    val homeStep = token.position - (config.pathLength - 1)
    return layout.homeCellOffset(slotIndex, homeStep)
}

private fun buildReverseCaptureWaypoints(
    @Suppress("UNUSED_PARAMETER") config: BoardConfig,
    capturedRelativePos: Int,
    @Suppress("UNUSED_PARAMETER") slotIndex: Int
): List<Pair<Int, Boolean>> {
    val waypoints = mutableListOf<Pair<Int, Boolean>>()
    var pos = capturedRelativePos - 1
    while (pos >= 0) {
        waypoints.add(pos to false)
        pos--
    }
    waypoints.add(-1 to false)
    return waypoints
}

private fun buildPathWaypoints(
    config: BoardConfig,
    oldPos: Int, oldIsHome: Boolean,
    newPos: Int, newIsHome: Boolean
): List<Pair<Int, Boolean>> {
    if (oldIsHome) return listOf(newPos to newIsHome)
    if (newPos == -1) return listOf(-1 to false)
    if (oldPos == -1) return listOf(newPos to newIsHome)
    if (newPos <= oldPos) return listOf(newPos to newIsHome)

    val endPos = if (newIsHome) config.homePosition else newPos
    return ((oldPos + 1)..endPos.coerceAtMost(config.homePosition))
        .map { it to (it == config.homePosition && newIsHome) }
        .ifEmpty { listOf(newPos to newIsHome) }
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
