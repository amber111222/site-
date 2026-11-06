package com.neonchess.trainer.domain

data class EngineConfig(
    val estimatedElo: Int = 1200,
    val thinkTimeMs: Long = 450,
    val multiPv: Int = 4,
    val randomness: Double = 0.2
)

data class ChessPosition(
    val fen: String,
    val sideToMove: String,
    val moveHistory: List<String>
)

data class EngineMove(
    val from: String,
    val to: String,
    val promotion: String? = null,
    val scoreCp: Int = 0,
    val mateIn: Int? = null,
    val principalVariation: List<String> = emptyList()
) { val notation: String get() = "$from → $to" }

data class ImportedGame(
    val id: String,
    val source: String = "Chess.com",
    val white: String,
    val black: String,
    val result: String,
    val playedAt: Long,
    val pgn: String
)

enum class MoveClassification(val label: String) {
    BEST("Точно"), INACCURACY("Неточность"), MISTAKE("Ошибка"), BLUNDER("Зевок")
}

data class MoveAssessment(
    val move: String,
    val bestMove: String,
    val centipawnLoss: Int,
    val classification: MoveClassification
)

interface ChessEngineRepository {
    suspend fun bestMove(fen: String, config: EngineConfig): EngineMove?
}

interface ChessComRepository {
    suspend fun importCompletedGames(username: String): Result<List<ImportedGame>>
}

interface GameRepository {
    suspend fun saveGames(games: List<ImportedGame>)
    fun observeGames(): kotlinx.coroutines.flow.Flow<List<ImportedGame>>
}

interface AnalysisRepository {
    suspend fun analyse(pgn: String, config: EngineConfig): List<MoveAssessment>
}
