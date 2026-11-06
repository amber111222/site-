package com.neonchess.trainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import coil3.compose.AsyncImage
import com.neonchess.trainer.domain.*
import java.text.DateFormat
import java.util.Date

private val Night = Color(0xFF080A10)
private val Panel = Color(0xFF121522)
private val Violet = Color(0xFF8A5CFF)
private val Cyan = Color(0xFF4EE7F2)
private val Gold = Color(0xFFFFCC68)
private val Muted = Color(0xFF969AAF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NeonTheme { NeonChessRoot() } }
    }
}

@Composable
private fun NeonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Violet, secondary = Cyan, tertiary = Gold, background = Night, surface = Panel, onBackground = Color.White),
        typography = Typography(), content = content
    )
}

@Composable
private fun NeonChessRoot(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Night,
        bottomBar = { if (nav.currentBackStackEntryAsState().value?.destination?.route != "onboarding") NeonBottomBar(nav) },
        snackbarHost = {
            state.message?.let { message ->
                LaunchedEffect(message) { kotlinx.coroutines.delay(2600); vm.dismissMessage() }
                Snackbar(modifier = Modifier.padding(16.dp), containerColor = Panel) { Text(message) }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "onboarding", modifier = Modifier.padding(padding)) {
            composable("onboarding") { OnboardingScreen { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } } }
            composable("home") { HomeScreen(state, nav) }
            composable("play") { PlayScreen(state, vm) }
            composable("import") { ImportScreen(state, vm, nav) }
            composable("games") { GamesScreen(state, vm, nav) }
            composable("analysis") { AnalysisScreen(state) }
            composable("settings") { SettingsScreen(state, vm) }
        }
    }
}

@Composable
private fun OnboardingScreen(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Violet.copy(.35f), Night), radius = 1100f))) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.padding(top = 42.dp)) {
                Text("NEONCHESS", color = Gold, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Spacer(Modifier.height(20.dp))
                Text("Думай глубже.\nИграй ярче.", fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(18.dp))
                Text("Офлайн-соперник, разбор завершённых партий и тренировка ключевых решений.", color = Muted, fontSize = 17.sp, lineHeight = 25.sp)
            }
            AsyncImage(
                model = R.drawable.neon_knight_hero,
                contentDescription = "Неоновая шахматная фигура коня",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(28.dp))
            )
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) {
                Text("НАЧАТЬ ТРЕНИРОВКУ", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.ArrowForward, null)
            }
        }
    }
}

@Composable
private fun NeonKnight() {
    val transition = rememberInfiniteTransition(label = "glow")
    val alpha by transition.animateFloat(.35f, .8f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "alpha")
    Canvas(Modifier.size(220.dp)) {
        drawCircle(Violet.copy(alpha), radius = size.minDimension * .42f)
        drawCircle(Night, radius = size.minDimension * .34f)
        drawCircle(Cyan.copy(.7f), radius = size.minDimension * .31f, style = Stroke(4.dp.toPx()))
    }
    Text("♞", fontSize = 132.sp, color = Color.White, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun HomeScreen(state: AppUiState, nav: NavHostController) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("NEONCHESS", color = Gold, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text("Твоя шахматная лаборатория", fontSize = 29.sp, fontWeight = FontWeight.Bold)
        }
        item { EloCard(state.elo) { nav.navigate("play") } }
        item { StrategyBanner() }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("Играть", "Против движка", Icons.Rounded.SportsEsports, Violet, Modifier.weight(1f)) { nav.navigate("play") }
                ActionCard("Импорт", "Chess.com", Icons.Rounded.CloudDownload, Cyan, Modifier.weight(1f)) { nav.navigate("import") }
            }
        }
        item { ActionCard("Разбор партий", "${state.games.size} сохранено", Icons.Rounded.Analytics, Gold, Modifier.fillMaxWidth()) { nav.navigate("games") } }
        item { SafetyCard() }
    }
}

@Composable
private fun StrategyBanner() {
    Box(Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(24.dp))) {
        AsyncImage(
            model = R.drawable.strategy_banner,
            contentDescription = "Неоновая траектория шахматного хода",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Night.copy(.9f), Night.copy(.25f)))))
        Column(Modifier.align(Alignment.CenterStart).padding(20.dp)) {
            Text("РАЗБОР РЕШЕНИЙ", color = Gold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
            Text("Найди ход,\nкоторый меняет всё", fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
private fun EloCard(elo: Int, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(26.dp)) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Violet.copy(.3f), Color.Transparent))).padding(22.dp)) {
            Column {
                Text("ТЕКУЩАЯ СИЛА", color = Muted, fontSize = 12.sp, letterSpacing = 2.sp)
                Text("$elo", fontSize = 52.sp, fontWeight = FontWeight.Black)
                Text("примерный Elo · настраивается от 100 до 2800", color = Muted)
            }
            Icon(Icons.Rounded.AutoAwesome, null, tint = Gold, modifier = Modifier.align(Alignment.TopEnd).size(34.dp))
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp)) {
            Surface(color = color.copy(.16f), shape = RoundedCornerShape(14.dp)) { Icon(icon, null, tint = color, modifier = Modifier.padding(10.dp)) }
            Spacer(Modifier.height(18.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SafetyCard() {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Cyan.copy(.08f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VerifiedUser, null, tint = Cyan); Spacer(Modifier.width(12.dp))
        Text("Только честная тренировка: анализ завершённых партий, без live-подсказок и оверлеев.", color = Color.White.copy(.82f), fontSize = 13.sp)
    }
}

@Composable
private fun PlayScreen(state: AppUiState, vm: MainViewModel) {
    val compact = LocalConfiguration.current.screenWidthDp < 520
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Против движка", fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text(if (state.engineThinking) "Движок думает…" else "Сила ${state.elo} Elo", color = if (state.engineThinking) Cyan else Muted)
            Spacer(Modifier.height(14.dp)); ChessBoard(state, vm); Spacer(Modifier.height(14.dp))
            state.hint?.let { Text(it.notation, color = Gold, fontWeight = FontWeight.Black, fontSize = 22.sp) }
        }
    }
    if (compact) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) { content(); Spacer(Modifier.height(16.dp)); GameControls(state, vm) }
    else Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { content() }; Box(Modifier.width(330.dp)) { GameControls(state, vm) } }
}

@Composable
private fun GameControls(state: AppUiState, vm: MainViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Сила соперника", fontWeight = FontWeight.Bold); Text("${state.elo}", color = Gold, fontWeight = FontWeight.Black) }
            Slider(value = state.elo.toFloat(), onValueChange = { vm.setElo(it.toInt()) }, valueRange = 100f..2800f, steps = 26)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = vm::newGame, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Refresh, null); Text(" Новая") }
                Button(onClick = vm::requestHint, modifier = Modifier.weight(1f), enabled = !state.engineThinking) { Icon(Icons.Rounded.Lightbulb, null); Text(" Ход") }
            }
            Spacer(Modifier.height(10.dp)); Text("Подсказка показывает лучший ход только в партии внутри приложения.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChessBoard(state: AppUiState, vm: MainViewModel) {
    val boardSize = minOf(LocalConfiguration.current.screenWidthDp - 28, 560).dp
    Column(Modifier.size(boardSize).clip(RoundedCornerShape(10.dp)).border(2.dp, Violet.copy(.7f), RoundedCornerShape(10.dp))) {
        for (rank in 8 downTo 1) Row(Modifier.weight(1f)) {
            for (fileIndex in 0..7) {
                val file = ('a'.code + fileIndex).toChar()
                val square = "$file$rank"
                val selected = state.selected == square
                val hinted = state.hint?.from == square || state.hint?.to == square
                val base = if ((fileIndex + rank) % 2 == 0) Color(0xFF23283A) else Color(0xFF424A67)
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(if (selected) Violet else if (hinted) Gold.copy(.75f) else base)
                        .clickable { vm.tapSquare(square) }.semantics { contentDescription = "$square ${vm.pieceAt(square)}" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(vm.pieceAt(square), fontSize = (boardSize.value / 11).sp, color = Color.White, textAlign = TextAlign.Center)
                    if (fileIndex == 0) Text(rank.toString(), fontSize = 9.sp, color = Color.White.copy(.55f), modifier = Modifier.align(Alignment.TopStart).padding(2.dp))
                    if (rank == 1) Text(file.toString(), fontSize = 9.sp, color = Color.White.copy(.55f), modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp))
                }
            }
        }
    }
}

@Composable
private fun ImportScreen(state: AppUiState, vm: MainViewModel, nav: NavHostController) {
    Column(Modifier.fillMaxSize().padding(22.dp).verticalScroll(rememberScrollState())) {
        Text("Импорт Chess.com", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Загружаются только завершённые партии из публичного API.", color = Muted)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(value = state.username, onValueChange = vm::setUsername, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Username") }, leadingIcon = { Icon(Icons.Rounded.Person, null) })
        Spacer(Modifier.height(14.dp))
        Button(onClick = vm::importGames, enabled = !state.importing, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            if (state.importing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.CloudDownload, null)
            Text(if (state.importing) "  Загрузка…" else "  Импортировать завершённые партии")
        }
        Spacer(Modifier.height(24.dp)); SafetyCard()
        if (state.games.isNotEmpty()) {
            Spacer(Modifier.height(24.dp)); Text("Уже сохранено: ${state.games.size}", fontWeight = FontWeight.Bold)
            TextButton(onClick = { nav.navigate("games") }) { Text("Открыть библиотеку →") }
        }
    }
}

@Composable
private fun GamesScreen(state: AppUiState, vm: MainViewModel, nav: NavHostController) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Библиотека партий", fontSize = 30.sp, fontWeight = FontWeight.Black); Text("Импортированные завершённые партии", color = Muted) }
        if (state.games.isEmpty()) item { EmptyLibrary() }
        items(state.games, key = { it.id }) { game ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), onClick = { vm.analyse(game); nav.navigate("analysis") }) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Violet.copy(.17f), shape = RoundedCornerShape(12.dp)) { Text("♟", fontSize = 28.sp, modifier = Modifier.padding(10.dp)) }
                    Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("${game.white} — ${game.black}", fontWeight = FontWeight.Bold); Text("${game.result} · ${DateFormat.getDateInstance().format(Date(game.playedAt))}", color = Muted, fontSize = 13.sp) }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column(Modifier.fillMaxWidth().padding(vertical = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("♙", fontSize = 76.sp, color = Violet); Text("Здесь появятся ваши партии", fontWeight = FontWeight.Bold); Text("Импортируйте завершённые игры Chess.com", color = Muted)
    }
}

@Composable
private fun AnalysisScreen(state: AppUiState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Отчёт анализа", fontSize = 30.sp, fontWeight = FontWeight.Black); Text("Последние критические решения", color = Muted) }
        if (state.assessments.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(70.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        items(state.assessments) { item ->
            val color = when (item.classification) { MoveClassification.BEST -> Cyan; MoveClassification.INACCURACY -> Gold; MoveClassification.MISTAKE -> Color(0xFFFF8A65); MoveClassification.BLUNDER -> Color(0xFFFF4D73) }
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color)); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(item.move, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(item.classification.label, color = color) }
                    Text("−${item.centipawnLoss / 100f}", color = Muted)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: AppUiState, vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Text("Настройки", fontSize = 30.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(22.dp))
        SettingSwitch("Высокий контраст", "Усилить различие клеток и текста", state.highContrast, vm::toggleContrast)
        SettingSwitch("Меньше анимаций", "Подходит при чувствительности к движению", state.reduceMotion, vm::toggleReduceMotion)
        Spacer(Modifier.height(22.dp)); Text("О приложении", fontWeight = FontWeight.Bold)
        Text("NeonChess Trainer 1.0\nЛокальный тренажёр. Elo является приблизительной оценкой.", color = Muted)
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange() }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 13.sp) }
        Switch(checked = checked, onCheckedChange = { onChange() })
    }
}

@Composable
private fun NeonBottomBar(nav: NavHostController) {
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar(containerColor = Panel.copy(.98f)) {
        listOf(Triple("home", "Главная", Icons.Rounded.Home), Triple("play", "Играть", Icons.Rounded.GridOn), Triple("games", "Партии", Icons.Rounded.History), Triple("settings", "Настройки", Icons.Rounded.Settings)).forEach { (target, label, icon) ->
            NavigationBarItem(selected = route == target, onClick = { nav.navigate(target) { launchSingleTop = true; popUpTo("home") } }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 10.sp) })
        }
    }
}
