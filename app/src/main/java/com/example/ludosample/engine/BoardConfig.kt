package com.example.ludosample.engine

sealed class BoardConfig(
    val totalSlots: Int,
    val pathLength: Int,
    val cellsPerSegment: Int,
    val homeColumnLength: Int,
    val safeSpotIndices: Set<Int>
) {
    data object Classic : BoardConfig(
        totalSlots = 4,
        pathLength = 52,
        cellsPerSegment = 13,
        homeColumnLength = 5,
        safeSpotIndices = buildSafeSpots(4, 13)
    )

    data object Penta : BoardConfig(
        totalSlots = 5,
        pathLength = 65,
        cellsPerSegment = 13,
        homeColumnLength = 5,
        safeSpotIndices = buildSafeSpots(5, 13)
    )

    data object Hex : BoardConfig(
        totalSlots = 6,
        pathLength = 78,
        cellsPerSegment = 13,
        homeColumnLength = 5,
        safeSpotIndices = buildSafeSpots(6, 13)
    )

    val homePosition: Int get() = pathLength + homeColumnLength - 1

    val slotColors: List<PlayerColor> get() = PlayerColor.forSlotCount(totalSlots)

    fun colorForSlot(slotIndex: Int): PlayerColor = slotColors[slotIndex]

    fun startPosition(slotIndex: Int): Int = slotIndex * cellsPerSegment

    fun homeEntryAbsolute(slotIndex: Int): Int {
        return (startPosition(slotIndex) - 1 + pathLength) % pathLength
    }

    fun toAbsolutePosition(relativePos: Int, slotIndex: Int): Int {
        require(relativePos in 0 until pathLength) {
            "Relative position $relativePos must be on shared path (0..${pathLength - 1})"
        }
        return (relativePos + startPosition(slotIndex)) % pathLength
    }

    fun toRelativePosition(absolutePos: Int, slotIndex: Int): Int {
        return (absolutePos - startPosition(slotIndex) + pathLength) % pathLength
    }

    fun assignSlots(playerCount: Int): List<Int> {
        require(playerCount in 2..totalSlots) {
            "Player count $playerCount not valid for board with $totalSlots slots"
        }
        if (playerCount == totalSlots) return (0 until totalSlots).toList()

        return when (this) {
            Classic -> when (playerCount) {
                2 -> listOf(0, 2)
                3 -> listOf(0, 1, 3)
                else -> (0 until 4).toList()
            }
            Penta -> when (playerCount) {
                2 -> listOf(0, 2)
                3 -> listOf(0, 2, 4)
                4 -> listOf(0, 1, 3, 4)
                else -> (0 until 5).toList()
            }
            Hex -> when (playerCount) {
                2 -> listOf(0, 3)
                3 -> listOf(0, 2, 4)
                4 -> listOf(0, 1, 3, 4)
                5 -> listOf(0, 1, 2, 4, 5)
                else -> (0 until 6).toList()
            }
        }
    }

    companion object {
        fun forPlayerCount(count: Int): BoardConfig = when (count) {
            in 2..4 -> Classic
            5 -> Penta
            6 -> Hex
            else -> throw IllegalArgumentException("Player count must be 2-6, got $count")
        }

        fun forBoardType(type: BoardType): BoardConfig = when (type) {
            BoardType.CLASSIC -> Classic
            BoardType.PENTA -> Penta
            BoardType.HEX -> Hex
        }

        private fun buildSafeSpots(slots: Int, segmentSize: Int): Set<Int> = buildSet {
            for (i in 0 until slots) {
                add(i * segmentSize)
                add(i * segmentSize + 8)
            }
        }
    }
}
