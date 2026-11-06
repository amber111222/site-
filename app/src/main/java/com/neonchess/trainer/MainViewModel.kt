package com.neonchess.trainer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.neonchess.trainer.data.*
import com.neonchess.trainer.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppUiState(
    val elo: Int = 1200,
    val username: String = "",
    val importing: Boolean = false,
    val message: String? = null,
    val games: List<ImportedGame> = emptyList(),
    val boardFen: String = Board().fen,
    val selected: String? = null,
    val hint: EngineMove? = null,
    val engineThinking: Boolean = false,
    val assessments: List<MoveAssessment> = emptyList(),
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NeonChessApp
    private val settings = SettingsRepository(application)
    private val games = RoomGameRepository(app.database.gameDao())
    private val chessCom = ChessComPublicRepository()
    private val engine: ChessEngineRepository = AdaptiveOfflineEngine()
    private val analysis: AnalysisRepository = LocalAnalysisRepository(engine)
    private val board = Board()
    private val mutable = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = combine(mutable, settings.elo, games.observeGames()) { ui, elo, stored ->
        ui.copy(elo = elo, games = stored)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    fun setElo(value: Int) = viewModelScope.launch { settings.setElo(value) }
    fun setUsername(value: String) { mutable.update { it.copy(username = value, message = null) } }
    fun dismissMessage() { mutable.update { it.copy(message = null) } }
    fun toggleReduceMotion() { mutable.update { it.copy(reduceMotion = !it.reduceMotion) } }
    fun toggleContrast() { mutable.update { it.copy(highContrast = !it.highContrast) } }

    fun newGame() {
        board.loadFromFen(Board().fen)
        mutable.update { it.copy(boardFen = board.fen, selected = null, hint = null, assessments = emptyList(), message = "Новая партия начата") }
    }

    fun tapSquare(square: String) {
        val current = mutable.value.selected
        if (current == null) {
            if (pieceAt(square).isNotBlank()) mutable.update { it.copy(selected = square, hint = null) }
            return
        }
        if (current == square) {
            mutable.update { it.copy(selected = null) }
            return
        }
        val move = runCatching { Move(Square.valueOf(current.uppercase()), Square.valueOf(square.uppercase())) }.getOrNull()
        if (move != null && board.isMoveLegal(move, true)) {
            board.doMove(move)
            mutable.update { it.copy(boardFen = board.fen, selected = null, hint = null, engineThinking = true) }
            requestEngineReply()
        } else {
            mutable.update { it.copy(selected = if (pieceAt(square).isNotBlank()) square else null, message = "Этот ход сейчас невозможен") }
        }
    }

    fun requestHint() = viewModelScope.launch {
        mutable.update { it.copy(engineThinking = true, hint = null) }
        val config = config()
        val best = engine.bestMove(board.fen, config)
        mutable.update { it.copy(engineThinking = false, hint = best, message = best?.let { m -> "Подсказка: ${m.notation}" }) }
    }

    private fun requestEngineReply() = viewModelScope.launch {
        val best = engine.bestMove(board.fen, config())
        if (best != null) {
            val move = Move(Square.valueOf(best.from.uppercase()), Square.valueOf(best.to.uppercase()))
            if (board.isMoveLegal(move, true)) board.doMove(move)
        }
        mutable.update { it.copy(boardFen = board.fen, engineThinking = false, hint = best) }
    }

    private fun config(): EngineConfig {
        val elo = state.value.elo
        return EngineConfig(elo, thinkTimeMs = (120 + elo / 3).toLong(), multiPv = if (elo < 800) 8 else 4, randomness = (2800 - elo) / 2700.0)
    }

    fun importGames() = viewModelScope.launch {
        val username = mutable.value.username.trim()
        if (username.isBlank()) { mutable.update { it.copy(message = "Введите username Chess.com") }; return@launch }
        mutable.update { it.copy(importing = true, message = null) }
        chessCom.importCompletedGames(username).fold(
            onSuccess = { imported ->
                games.saveGames(imported)
                mutable.update { it.copy(importing = false, message = if (imported.isEmpty()) "Завершённых партий не найдено" else "Импортировано партий: ${imported.size}") }
            },
            onFailure = { error -> mutable.update { it.copy(importing = false, message = error.message ?: "Не удалось загрузить партии") } }
        )
    }

    fun analyse(game: ImportedGame) = viewModelScope.launch {
        mutable.update { it.copy(assessments = emptyList(), message = "Анализируем партию…") }
        val report = analysis.analyse(game.pgn, config())
        mutable.update { it.copy(assessments = report, message = "Анализ завершён") }
    }

    fun pieceAt(square: String): String = runCatching {
        val piece = board.getPiece(Square.valueOf(square.uppercase()))
        when (piece.name) {
            "WHITE_KING" -> "♔"; "WHITE_QUEEN" -> "♕"; "WHITE_ROOK" -> "♖"; "WHITE_BISHOP" -> "♗"; "WHITE_KNIGHT" -> "♘"; "WHITE_PAWN" -> "♙"
            "BLACK_KING" -> "♚"; "BLACK_QUEEN" -> "♛"; "BLACK_ROOK" -> "♜"; "BLACK_BISHOP" -> "♝"; "BLACK_KNIGHT" -> "♞"; "BLACK_PAWN" -> "♟"
            else -> ""
        }
    }.getOrDefault("")
}
