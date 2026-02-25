package com.example.ludosample.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ludosample.engine.BoardType
import com.example.ludosample.engine.GameState

@Composable
fun LudoBoard(
    boardType: BoardType,
    gameState: GameState,
    validMoves: List<Int>,
    currentPlayerId: String,
    onTokenTapped: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    when (boardType) {
        BoardType.CLASSIC -> ClassicBoardCanvas(
            gameState = gameState,
            validMoves = validMoves,
            currentPlayerId = currentPlayerId,
            onTokenTapped = onTokenTapped,
            modifier = modifier
        )
        BoardType.PENTA -> PentaBoardCanvas(
            gameState = gameState,
            validMoves = validMoves,
            currentPlayerId = currentPlayerId,
            onTokenTapped = onTokenTapped,
            modifier = modifier
        )
        BoardType.HEX -> HexBoardCanvas(
            gameState = gameState,
            validMoves = validMoves,
            currentPlayerId = currentPlayerId,
            onTokenTapped = onTokenTapped,
            modifier = modifier
        )
    }
}
