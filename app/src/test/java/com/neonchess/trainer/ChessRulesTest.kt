package com.neonchess.trainer

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.neonchess.trainer.data.AdaptiveOfflineEngine
import com.neonchess.trainer.domain.EngineConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChessRulesTest {
    @Test
    fun openingPositionHasTwentyLegalMoves() {
        assertEquals(20, Board().legalMoves().size)
    }

    @Test
    fun illegalPawnLeapIsRejected() {
        val board = Board()
        assertFalse(board.legalMoves().contains(Move(Square.E2, Square.E5)))
    }

    @Test
    fun engineReturnsLegalMoveAcrossStrengthRange() = runBlocking {
        for (elo in listOf(100, 800, 1320, 2000, 2800)) {
            val board = Board()
            val move = AdaptiveOfflineEngine().bestMove(board.fen, EngineConfig(estimatedElo = elo))
            assertNotNull(move)
            val candidate = Move(Square.valueOf(move!!.from.uppercase()), Square.valueOf(move.to.uppercase()))
            assertTrue("Expected legal move at $elo Elo", board.legalMoves().contains(candidate))
        }
    }
}
