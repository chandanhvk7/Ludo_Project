package com.example.ludosample.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TwoWayConverter
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.ludosample.engine.BoardConfig
import com.example.ludosample.engine.GameState
import com.example.ludosample.engine.PlayerColor
import com.example.ludosample.engine.Token
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val PolyOffsetToVector: TwoWayConverter<Offset, AnimationVector2D> =
    TwoWayConverter(
        convertToVector = { AnimationVector2D(it.x, it.y) },
        convertFromVector = { Offset(it.v1, it.v2) }
    )

private val SLOT_COLORS = listOf(
    Color(0xFFE53935),
    Color(0xFF43A047),
    Color(0xFFFFB300),
    Color(0xFF1E88E5),
    Color(0xFFEF6C00),
    Color(0xFF6A1B9A)
)

private val TOKEN_FILL_COLORS = mapOf(
    PlayerColor.RED to Color(0xFFE53935),
    PlayerColor.GREEN to Color(0xFF43A047),
    PlayerColor.YELLOW to Color(0xFFFDD835),
    PlayerColor.BLUE to Color(0xFF1E88E5),
    PlayerColor.ORANGE to Color(0xFFEF6C00),
    PlayerColor.PURPLE to Color(0xFF6A1B9A)
)

/**
 * Computes all cell offsets for an N-sided polygon board.
 *
 * The polygon is centered at [center] with circumradius [radius].
 * Vertices are at angles starting from -PI/2 (top), going clockwise.
 * Each edge has [cellsPerSegment] path cells. Yards extend outward from vertices.
 * Home columns run from edge midpoints toward the center.
 */
class PolygonBoardLayout(
    private val sides: Int,
    private val center: Offset,
    private val radius: Float,
    private val config: BoardConfig
) : BoardLayout {

    private val angleStep = 2 * PI / sides
    private val startAngle = -PI / 2
    private val cell = radius * 2 / (sides * 2.5f)

    private val vertices: List<Offset> = List(sides) { i ->
        val angle = startAngle + i * angleStep
        Offset(
            center.x + (radius * cos(angle)).toFloat(),
            center.y + (radius * sin(angle)).toFloat()
        )
    }

    private val edgeMidpoints: List<Offset> = List(sides) { i ->
        val v1 = vertices[i]
        val v2 = vertices[(i + 1) % sides]
        Offset((v1.x + v2.x) / 2, (v1.y + v2.y) / 2)
    }

    /**
     * Path cells are laid out along polygon edges. Each edge has [cellsPerSegment] cells.
     * Cells are evenly spaced between vertex[i] and vertex[i+1].
     */
    override fun pathCellOffset(absoluteIndex: Int): Offset {
        val segment = absoluteIndex / config.cellsPerSegment
        val cellInSegment = absoluteIndex % config.cellsPerSegment

        val v1 = vertices[segment % sides]
        val v2 = vertices[(segment + 1) % sides]

        val pathInnerRadius = radius * 0.82f
        val innerV1 = Offset(
            center.x + ((v1.x - center.x) * pathInnerRadius / radius),
            center.y + ((v1.y - center.y) * pathInnerRadius / radius)
        )
        val innerV2 = Offset(
            center.x + ((v2.x - center.x) * pathInnerRadius / radius),
            center.y + ((v2.y - center.y) * pathInnerRadius / radius)
        )

        val t = (cellInSegment + 0.5f) / config.cellsPerSegment
        return Offset(
            innerV1.x + (innerV2.x - innerV1.x) * t,
            innerV1.y + (innerV2.y - innerV1.y) * t
        )
    }

    override fun homeCellOffset(slotIndex: Int, homeStep: Int): Offset {
        val midpoint = edgeMidpoints[slotIndex]
        val innerMid = Offset(
            center.x + ((midpoint.x - center.x) * 0.82f),
            center.y + ((midpoint.y - center.y) * 0.82f)
        )

        val totalSteps = config.homeColumnLength + 1
        val t = (homeStep + 1f) / totalSteps
        return Offset(
            innerMid.x + (center.x - innerMid.x) * t,
            innerMid.y + (center.y - innerMid.y) * t
        )
    }

    override fun yardTokenOffset(slotIndex: Int, tokenIndex: Int): Offset {
        val vertex = vertices[slotIndex]
        val outward = Offset(
            vertex.x + (vertex.x - center.x) * 0.15f,
            vertex.y + (vertex.y - center.y) * 0.15f
        )

        val perpAngle = startAngle + slotIndex * angleStep + PI / 2
        val spacing = cell * 0.8f
        val row = tokenIndex / 2
        val col = tokenIndex % 2

        val radialDir = Offset(
            (vertex.x - center.x),
            (vertex.y - center.y)
        ).let {
            val len = kotlin.math.sqrt(it.x * it.x + it.y * it.y)
            if (len > 0) Offset(it.x / len, it.y / len) else Offset(0f, -1f)
        }

        val perpDir = Offset(
            cos(perpAngle).toFloat(),
            sin(perpAngle).toFloat()
        )

        return Offset(
            outward.x + perpDir.x * (col - 0.5f) * spacing + radialDir.x * (row - 0.5f) * spacing,
            outward.y + perpDir.y * (col - 0.5f) * spacing + radialDir.y * (row - 0.5f) * spacing
        )
    }

    override fun centerOffset(): Offset = center

    override fun cellSize(): Float = cell
}

@Composable
fun PentaBoardCanvas(
    gameState: GameState,
    validMoves: List<Int>,
    currentPlayerId: String,
    onTokenTapped: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    PolygonBoardCanvas(
        sides = 5,
        config = BoardConfig.Penta,
        gameState = gameState,
        validMoves = validMoves,
        currentPlayerId = currentPlayerId,
        onTokenTapped = onTokenTapped,
        modifier = modifier
    )
}

@Composable
fun HexBoardCanvas(
    gameState: GameState,
    validMoves: List<Int>,
    currentPlayerId: String,
    onTokenTapped: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    PolygonBoardCanvas(
        sides = 6,
        config = BoardConfig.Hex,
        gameState = gameState,
        validMoves = validMoves,
        currentPlayerId = currentPlayerId,
        onTokenTapped = onTokenTapped,
        modifier = modifier
    )
}

@Composable
private fun PolygonBoardCanvas(
    sides: Int,
    config: BoardConfig,
    gameState: GameState,
    validMoves: List<Int>,
    currentPlayerId: String,
    onTokenTapped: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }
    val animatedOffsets = remember {
        mutableStateMapOf<String, Animatable<Offset, AnimationVector2D>>()
    }
    val prevPositions = remember { mutableStateMapOf<String, Pair<Int, Boolean>>() }

    if (canvasWidth > 0f) {
        val boardSize = min(canvasWidth, canvasHeight)
        val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
        val radius = boardSize * 0.42f
        val layout = PolygonBoardLayout(sides, center, radius, config)

        for ((playerId, player) in gameState.players) {
            if (player.isEliminated) continue
            player.tokens.forEachIndexed { index, token ->
                val key = "${playerId}_$index"
                val target = polygonTokenOffset(config, token, player.slotIndex, index, layout)
                val anim = animatedOffsets.getOrPut(key) {
                    Animatable(target, PolyOffsetToVector)
                }
                val slotIndex = player.slotIndex
                LaunchedEffect(key, token.position, token.isHome, canvasWidth) {
                    val prev = prevPositions[key]
                    if (prev == null || (prev.first == token.position && prev.second == token.isHome)) {
                        anim.snapTo(target)
                    } else {
                        val waypoints = buildPolyPathWaypoints(
                            config, prev.first, prev.second,
                            token.position, token.isHome
                        )
                        for ((pos, isHome) in waypoints) {
                            val wp = polygonTokenOffset(config, Token(pos, isHome), slotIndex, index, layout)
                            anim.animateTo(wp, animationSpec = tween(120))
                        }
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
            .onSizeChanged {
                canvasWidth = it.width.toFloat()
                canvasHeight = it.height.toFloat()
            }
            .pointerInput(gameState, validMoves) {
                detectTapGestures { tapOffset ->
                    val boardSize = min(size.width, size.height).toFloat()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = boardSize * 0.42f
                    val layout = PolygonBoardLayout(sides, center, radius, config)
                    handlePolygonTokenTap(
                        config, gameState, currentPlayerId, validMoves,
                        layout, tapOffset, onTokenTapped
                    )
                }
            }
    ) {
        val boardSize = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = boardSize * 0.42f
        val layout = PolygonBoardLayout(sides, center, radius, config)

        drawPolygonBoard(sides, layout, config, center, radius)
        drawPolygonPath(layout, config)
        drawPolygonHomeColumns(layout, config)
        drawPolygonCenter(sides, center, radius * 0.15f)
        drawPolygonYards(sides, layout, config, gameState)
        drawPolygonTokensAnimated(
            config, gameState, layout, currentPlayerId, validMoves,
            textMeasurer, animatedOffsets
        )
    }
}

private fun DrawScope.drawPolygonBoard(
    sides: Int,
    layout: PolygonBoardLayout,
    config: BoardConfig,
    center: Offset,
    radius: Float
) {
    // Background circle
    drawCircle(Color(0xFFF5F5F5), radius = radius * 1.15f, center = center)

    val angleStep = 2 * PI / sides
    val startAngle = -PI / 2

    // Wedge backgrounds
    for (i in 0 until sides) {
        val angle1 = startAngle + i * angleStep
        val angle2 = startAngle + (i + 1) * angleStep
        val outerRadius = radius * 1.1f

        val path = Path().apply {
            moveTo(center.x, center.y)
            lineTo(
                center.x + (outerRadius * cos(angle1)).toFloat(),
                center.y + (outerRadius * sin(angle1)).toFloat()
            )
            lineTo(
                center.x + (outerRadius * cos(angle2)).toFloat(),
                center.y + (outerRadius * sin(angle2)).toFloat()
            )
            close()
        }
        drawPath(path, color = SLOT_COLORS[i % SLOT_COLORS.size].copy(alpha = 0.15f))
        drawPath(path, color = SLOT_COLORS[i % SLOT_COLORS.size].copy(alpha = 0.3f), style = Stroke(1.5f))
    }
}

private fun DrawScope.drawPolygonPath(layout: PolygonBoardLayout, config: BoardConfig) {
    val cellRadius = layout.cellSize() * 0.4f

    for (i in 0 until config.pathLength) {
        val offset = layout.pathCellOffset(i)
        val isSafe = i in config.safeSpotIndices
        val isStart = i % config.cellsPerSegment == 0
        val slotForCell = i / config.cellsPerSegment

        val fillColor = when {
            isStart -> SLOT_COLORS[slotForCell % SLOT_COLORS.size].copy(alpha = 0.5f)
            isSafe -> Color(0xFFE0E0E0)
            else -> Color.White
        }

        drawCircle(color = fillColor, radius = cellRadius, center = offset)
        drawCircle(
            color = Color.Gray.copy(alpha = 0.4f),
            radius = cellRadius,
            center = offset,
            style = Stroke(1f)
        )
    }
}

private fun DrawScope.drawPolygonHomeColumns(layout: PolygonBoardLayout, config: BoardConfig) {
    for (slot in 0 until config.totalSlots) {
        val color = SLOT_COLORS[slot % SLOT_COLORS.size]
        for (step in 0 until config.homeColumnLength) {
            val offset = layout.homeCellOffset(slot, step)
            val cellRadius = layout.cellSize() * 0.4f
            drawCircle(color = color.copy(alpha = 0.35f), radius = cellRadius, center = offset)
            drawCircle(color = color, radius = cellRadius, center = offset, style = Stroke(1.5f))
        }
    }
}

private fun DrawScope.drawPolygonCenter(sides: Int, center: Offset, radius: Float) {
    val path = Path()
    val angleStep = 2 * PI / sides
    val startAngle = -PI / 2

    for (i in 0 until sides) {
        val angle = startAngle + i * angleStep
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, Color(0xFF424242))
    drawPath(path, Color.White, style = Stroke(2f))
}

private fun DrawScope.drawPolygonYards(
    sides: Int,
    layout: PolygonBoardLayout,
    config: BoardConfig,
    gameState: GameState
) {
    val activeSlotsInGame = gameState.players.values.map { it.slotIndex }.toSet()

    for (slot in 0 until sides) {
        val color = SLOT_COLORS[slot % SLOT_COLORS.size]
        val isActive = slot in activeSlotsInGame
        val alpha = if (isActive) 0.3f else 0.1f

        for (tokenIdx in 0 until 4) {
            val offset = layout.yardTokenOffset(slot, tokenIdx)
            drawCircle(color = color.copy(alpha = alpha), radius = layout.cellSize() * 0.45f, center = offset)
            drawCircle(
                color = color.copy(alpha = alpha + 0.2f),
                radius = layout.cellSize() * 0.45f,
                center = offset,
                style = Stroke(1.5f)
            )
        }
    }
}

private fun DrawScope.drawPolygonTokensAnimated(
    config: BoardConfig,
    gameState: GameState,
    layout: PolygonBoardLayout,
    currentPlayerId: String,
    validMoves: List<Int>,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    animatedOffsets: Map<String, Animatable<Offset, AnimationVector2D>>
) {
    val tokenRadius = layout.cellSize() * 0.35f
    val isMyTurn = gameState.currentTurnPlayerId == currentPlayerId

    for ((playerId, player) in gameState.players) {
        if (player.isEliminated) continue
        val color = TOKEN_FILL_COLORS[player.color] ?: Color.Gray

        player.tokens.forEachIndexed { index, token ->
            if (token.isHome) return@forEachIndexed

            val key = "${playerId}_$index"
            val offset = animatedOffsets[key]?.value
                ?: polygonTokenOffset(config, token, player.slotIndex, index, layout)

            val isValid = isMyTurn && playerId == currentPlayerId && index in validMoves
            if (isValid) {
                drawCircle(color = Color.White, radius = tokenRadius + 4f, center = offset)
                drawCircle(
                    color = color.copy(alpha = 0.4f),
                    radius = tokenRadius + 6f,
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
                    fontSize = (tokenRadius * 0.85f).sp,
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

private fun polygonTokenOffset(
    config: BoardConfig,
    token: Token,
    slotIndex: Int,
    tokenIndex: Int,
    layout: PolygonBoardLayout
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

private fun buildPolyPathWaypoints(
    config: BoardConfig,
    oldPos: Int, oldIsHome: Boolean,
    newPos: Int, newIsHome: Boolean
): List<Pair<Int, Boolean>> {
    if (oldIsHome) return listOf(newPos to newIsHome)
    if (newPos == -1) return listOf(-1 to false)
    if (oldPos == -1) return listOf(newPos to newIsHome)
    if (newPos <= oldPos) return listOf(newPos to newIsHome)

    val endPos = if (newIsHome) config.homePosition else newPos
    return ((oldPos + 1)..endPos.coerceAtMost(config.homePosition)).map { it to (it == config.homePosition && newIsHome) }
        .ifEmpty { listOf(newPos to newIsHome) }
}

private fun handlePolygonTokenTap(
    config: BoardConfig,
    gameState: GameState,
    currentPlayerId: String,
    validMoves: List<Int>,
    layout: PolygonBoardLayout,
    tapOffset: Offset,
    onTokenTapped: (Int) -> Unit
) {
    val player = gameState.players[currentPlayerId] ?: return
    val tapRadius = layout.cellSize() * 0.55f

    for (tokenIndex in validMoves) {
        val token = player.tokens[tokenIndex]
        val tokenPos = polygonTokenOffset(config, token, player.slotIndex, tokenIndex, layout)
        if ((tapOffset - tokenPos).getDistance() <= tapRadius) {
            onTokenTapped(tokenIndex)
            return
        }
    }
}
