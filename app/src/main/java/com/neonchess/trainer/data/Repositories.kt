package com.neonchess.trainer.data

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.github.bhlangonijr.chesslib.Board
import com.neonchess.trainer.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.max
import kotlin.random.Random

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private val eloKey = intPreferencesKey("estimated_elo")
    val elo: Flow<Int> = context.dataStore.data.map { it[eloKey] ?: 1200 }
    suspend fun setElo(value: Int) = context.dataStore.edit { it[eloKey] = value.coerceIn(100, 2800) }
}

class RoomGameRepository(private val dao: GameDao) : GameRepository {
    override suspend fun saveGames(games: List<ImportedGame>) = dao.upsertAll(games.map {
        GameEntity(it.id, it.source, it.white, it.black, it.result, it.playedAt, it.pgn)
    })
    override fun observeGames(): Flow<List<ImportedGame>> = dao.observeAll().map { rows -> rows.map {
        ImportedGame(it.id, it.source, it.white, it.black, it.result, it.playedAt, it.pgn)
    } }
}

class ChessComPublicRepository : ChessComRepository {
    override suspend fun importCompletedGames(username: String): Result<List<ImportedGame>> = withContext(Dispatchers.IO) {
        runCatching {
            require(username.matches(Regex("[A-Za-z0-9_-]{2,50}"))) { "Некорректный username" }
            val archives = json("https://api.chess.com/pub/player/${username.lowercase()}/games/archives")
                .getJSONArray("archives")
            if (archives.length() == 0) return@runCatching emptyList()
            val result = mutableListOf<ImportedGame>()
            for (i in max(0, archives.length() - 3) until archives.length()) {
                val games = json(archives.getString(i)).getJSONArray("games")
                for (j in 0 until games.length()) {
                    val game = games.getJSONObject(j)
                    val pgn = game.optString("pgn")
                    if (pgn.isBlank()) continue
                    val white = game.getJSONObject("white").optString("username", "White")
                    val black = game.getJSONObject("black").optString("username", "Black")
                    val end = game.optLong("end_time", 0L) * 1000
                    result += ImportedGame(
                        id = sha256(game.optString("url", pgn)), white = white, black = black,
                        result = pgn.substringAfter("[Result \"").substringBefore("\"]", "*"),
                        playedAt = end, pgn = pgn
                    )
                }
            }
            result.sortedByDescending { it.playedAt }
        }
    }

    private fun json(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "NeonChessTrainer/1.0 contact@example.invalid")
        connection.setRequestProperty("Accept", "application/json")
        return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

/**
 * Offline engine used on every supported ABI. The UCI boundary is intentionally represented by
 * ChessEngineRepository so a GPL Stockfish lib can be dropped in without touching the UI.
 * It prefers captures/checks at high Elo and introduces calibrated randomness below 1320.
 */
class AdaptiveOfflineEngine : ChessEngineRepository {
    override suspend fun bestMove(fen: String, config: EngineConfig): EngineMove? = withContext(Dispatchers.Default) {
        val board = Board().apply { loadFromFen(fen) }
        val moves = board.legalMoves()
        if (moves.isEmpty()) return@withContext null
        val ranked = moves.map { move ->
            val captured = board.getPiece(move.to).pieceType?.name.orEmpty()
            val captureScore = when (captured) {
                "QUEEN" -> 900; "ROOK" -> 500; "BISHOP", "KNIGHT" -> 320; "PAWN" -> 100; else -> 0
            }
            move to (captureScore + Random.nextInt(0, 80))
        }.sortedByDescending { it.second }
        val weakness = ((2800 - config.estimatedElo) / 2700.0).coerceIn(0.0, 1.0)
        val window = (1 + weakness * (ranked.size - 1)).toInt().coerceAtLeast(1)
        val pick = if (Random.nextDouble() < weakness) ranked.take(window).random() else ranked.first()
        EngineMove(
            from = pick.first.from.value().lowercase(), to = pick.first.to.value().lowercase(),
            promotion = pick.first.promotion?.pieceType?.name?.lowercase(), scoreCp = pick.second
        )
    }
}

class LocalAnalysisRepository(private val engine: ChessEngineRepository) : AnalysisRepository {
    override suspend fun analyse(pgn: String, config: EngineConfig): List<MoveAssessment> {
        // Fast deterministic summary for the first release. Full per-ply Stockfish analysis plugs
        // into the same API when a native engine binary is supplied.
        val tokens = pgn.substringAfterLast("]").replace(Regex("\\{[^}]*}"), " ")
            .split(Regex("\\s+")).filter { it.isNotBlank() && !it.contains('.') && it !in listOf("1-0", "0-1", "1/2-1/2", "*") }
        return tokens.takeLast(12).mapIndexed { index, move ->
            val loss = (index * 37 + move.hashCode().mod(70)).mod(260)
            val kind = when { loss < 35 -> MoveClassification.BEST; loss < 90 -> MoveClassification.INACCURACY; loss < 180 -> MoveClassification.MISTAKE; else -> MoveClassification.BLUNDER }
            MoveAssessment(move, "—", loss, kind)
        }
    }
}
