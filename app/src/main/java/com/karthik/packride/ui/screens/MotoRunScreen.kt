package com.karthik.packride.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthik.packride.ads.AdBannerFooter
import com.karthik.packride.motorun.*
import com.karthik.packride.ui.theme.PrCoral
import kotlinx.coroutines.delay
import kotlin.math.ceil

private enum class SoloState { READY, PLAYING, COUNTDOWN, GAME_OVER }
private val BikeColors = listOf(PrCoral, Color(0xFF20A99A), Color(0xFF2E9E5B), Color(0xFF8A66B1), Color(0xFF3266CE), Color(0xFFD9518C))

@Composable
fun MotoRunScreen(onExit: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? Activity
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    val prefs = remember { context.getSharedPreferences("packride_prefs", Context.MODE_PRIVATE) }
    val engine = remember { MotoRunEngine() }
    val session = remember { MotoRunSessionManager(context.applicationContext) }
    val raceState by session.raceState.collectAsState()
    val raceCode by session.raceCode.collectAsState()
    val racers by session.racers.collectAsState()
    val seed by session.seed.collectAsState()
    val raceStartAt by session.raceStartAt.collectAsState()
    var soloState by remember { mutableStateOf(SoloState.READY) }
    var highScore by remember { mutableIntStateOf(prefs.getInt(KEY_HIGH, 0)) }
    var countdown by remember { mutableIntStateOf(3) }
    var lastNanos by remember { mutableLongStateOf(0L) }
    var frame by remember { mutableIntStateOf(0) }
    var newBest by remember { mutableStateOf(false) }
    var showLobby by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(prefs.getInt("motoRunColorIndex", 0)) }
    val riderName = prefs.getString("riderName", "Rider").orEmpty()
    val initials = remember(riderName) { riderName.trim().split(Regex("\\s+")).filter(String::isNotBlank).take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "R" } }

    val laneY = remember { mutableStateMapOf<String, Float>() }
    val laneVelocity = remember { mutableStateMapOf<String, Float>() }
    val knownJumps = remember { mutableStateMapOf<String, Int>() }
    var schedule by remember { mutableStateOf<RaceObstacleSchedule?>(null) }
    var obstacleXs by remember { mutableStateOf<List<Float>>(emptyList()) }
    var localScore by remember { mutableIntStateOf(0) }
    var localAlive by remember { mutableStateOf(true) }
    var scoreTicks by remember { mutableIntStateOf(0) }
    var finishReported by remember { mutableStateOf(false) }
    var scoreAccumulator by remember { mutableFloatStateOf(0f) }
    var playfieldWidthDp by remember { mutableFloatStateOf(900f) }

    DisposableEffect(activity) {
        val prior = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            session.leaveRace(); session.dispose()
            activity?.requestedOrientation = prior ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    BackHandler { if (showLobby) showLobby = false else onExit() }

    fun startSolo() { engine.reset(); newBest = false; lastNanos = 0L; soloState = SoloState.PLAYING }
    fun resetRace() {
        laneY.clear(); laneVelocity.clear(); knownJumps.clear(); schedule = null; obstacleXs = emptyList()
        localScore = 0; localAlive = true; scoreTicks = 0; scoreAccumulator = 0f; finishReported = false; lastNanos = 0L
    }
    LaunchedEffect(raceStartAt) { if (raceStartAt > 0) resetRace() }
    LaunchedEffect(raceState) { if (raceState != MotoRaceState.LOBBY) showLobby = false }
    LaunchedEffect(soloState) {
        if (soloState == SoloState.COUNTDOWN) {
            countdown = 3
            while (countdown > 0) { delay(1_000); countdown-- }
            startSolo()
        }
    }
    LaunchedEffect(Unit) {
        engine.reset()
        while (true) withFrameNanos { now ->
            val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, .05f)
            lastNanos = now
            if (raceState == MotoRaceState.IDLE && soloState == SoloState.PLAYING) {
                val alive = engine.alive
                engine.tick(dt)
                if (alive && !engine.alive) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    newBest = engine.score > highScore
                    if (newBest) { highScore = engine.score; prefs.edit().putInt(KEY_HIGH, highScore).apply() }
                    soloState = SoloState.GAME_OVER
                }
            } else if (raceState == MotoRaceState.ACTIVE) {
                val elapsed = session.elapsed()
                if (schedule == null && seed > 0) schedule = RaceObstacleSchedule.generate(seed)
                if (elapsed >= 0 && schedule != null) {
                    obstacleXs = schedule!!.visibleObstacleX(elapsed, playfieldWidthDp)
                    racers.forEach { racer ->
                        var y = laneY[racer.id] ?: 0f
                        var velocity = laneVelocity[racer.id] ?: 0f
                        if (racer.id != session.myId) {
                            val old = knownJumps[racer.id] ?: racer.jumpCount
                            if (racer.jumpCount > old && y == 0f) velocity = -840f
                            knownJumps[racer.id] = racer.jumpCount
                        }
                        if (y != 0f || velocity != 0f) {
                            velocity += 3240f * dt
                            y = (y + velocity * dt).coerceAtMost(0f)
                            if (y >= 0f) { y = 0f; velocity = 0f }
                        }
                        laneY[racer.id] = y; laneVelocity[racer.id] = velocity
                    }
                    if (localAlive && elapsed < MotoRacePhysics.MAX_DURATION) {
                        val crashed = (laneY[session.myId] ?: 0f) > -18f && obstacleXs.any { it in 43f..69f }
                        if (crashed) { localAlive = false; haptic.performHapticFeedback(HapticFeedbackType.LongPress); session.sendCrash(localScore) }
                        else {
                            scoreAccumulator += dt * 60f
                            val earned = scoreAccumulator.toInt()
                            if (earned > 0) {
                                scoreAccumulator -= earned; localScore += earned; scoreTicks += earned
                                if (scoreTicks >= 10) { scoreTicks %= 10; session.sendScore(localScore) }
                            }
                        }
                    }
                    val over = elapsed >= MotoRacePhysics.MAX_DURATION || (racers.isNotEmpty() && racers.all { if (it.id == session.myId) !localAlive else !it.alive })
                    if (over && !finishReported) { finishReported = true; if (localAlive) session.sendTimeoutFinish(localScore) }
                }
            }
            frame++
        }
    }

    Column(Modifier.fillMaxSize().background(GameBg)) {
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { playfieldWidthDp = it.width / density }.pointerInput(raceState, soloState, localAlive) {
            detectTapGestures {
                if (raceState == MotoRaceState.IDLE) when (soloState) {
                    SoloState.READY -> startSolo()
                    SoloState.PLAYING -> { engine.jump(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    else -> Unit
                } else if (raceState == MotoRaceState.ACTIVE && localAlive && session.elapsed() >= 0 && (laneY[session.myId] ?: 0f) == 0f) {
                    laneVelocity[session.myId] = -840f; session.sendJump(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }) {
            @Suppress("UNUSED_EXPRESSION") frame
            if (raceState == MotoRaceState.IDLE) {
                Canvas(Modifier.fillMaxSize()) { drawSolo(engine) }
                if (soloState != SoloState.PLAYING) SoloOverlay(soloState, countdown, engine.score, highScore, newBest) { soloState = SoloState.COUNTDOWN }
            } else {
                MultiplayerField(racers, session.myId, laneY, obstacleXs, localScore, localAlive)
                if (raceState == MotoRaceState.ACTIVE && raceStartAt > 0 && session.elapsed() < 0) CountdownOverlay(maxOf(1, ceil(-session.elapsed()).toInt()))
                val over = raceState == MotoRaceState.ACTIVE && session.elapsed() >= 0 && (session.elapsed() >= MotoRacePhysics.MAX_DURATION || (racers.isNotEmpty() && racers.all { if (it.id == session.myId) !localAlive else !it.alive }))
                if (over) ResultsOverlay(racers, session.myId, localScore, session.isHost, session::startRace, session::leaveRace)
            }
            IconButton(onClick = onExit, modifier = Modifier.padding(18.dp).size(42.dp).background(GameCard, CircleShape)) { Icon(Icons.Default.Close, "Exit Moto Run", tint = GameMuted) }
            if (raceState == MotoRaceState.IDLE) Row(Modifier.align(Alignment.TopEnd).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (soloState == SoloState.READY) Button(onClick = { showLobby = true }, colors = ButtonDefaults.buttonColors(containerColor = PrCoral), shape = CircleShape) { Icon(Icons.Default.Group, null); Spacer(Modifier.width(5.dp)); Text("Invite", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(14.dp)); Column(horizontalAlignment = Alignment.End) { Text("${engine.score}", color = GameInk, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("BEST $highScore", color = GameMuted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) }
            }
        }
        AdBannerFooter()
    }

    if (showLobby) LobbyDialog(raceCode, racers, session.isHost, colorIndex, joinCode, showJoin,
        onJoinCode = { joinCode = it }, onShowJoin = { showJoin = true },
        onColor = { colorIndex = it; prefs.edit().putInt("motoRunColorIndex", it).apply(); session.updateMyColor(it) },
        onCreate = { session.createRace(initials, colorIndex) }, onJoin = { session.joinRace(joinCode, initials, colorIndex) }, onStart = session::startRace,
        onClose = { if (raceState == MotoRaceState.LOBBY) session.leaveRace(); showLobby = false },
        onShare = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Join my PackRide Moto Run race with code $raceCode") }, "Invite a Friend")) })
}

@Composable private fun SoloOverlay(state: SoloState, countdown: Int, score: Int, high: Int, newBest: Boolean, replay: () -> Unit) {
    Surface(Modifier.fillMaxSize().wrapContentSize(Alignment.Center).widthIn(min = 280.dp), color = GameCard, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, GameBorder), shadowElevation = 10.dp) {
        Column(Modifier.padding(horizontal = 36.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) { when (state) {
            SoloState.READY -> { Icon(Icons.Default.TouchApp, null, tint = PrCoral, modifier = Modifier.size(38.dp)); Text("Moto Run", color = GameInk, fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("Tap to jump the speed bumps", color = GameMuted) }
            SoloState.COUNTDOWN -> { Text("GET READY", color = GameMuted, fontWeight = FontWeight.Bold); Text("$countdown", color = PrCoral, fontSize = 58.sp, fontWeight = FontWeight.Black) }
            SoloState.GAME_OVER -> { Icon(Icons.Default.Warning, null, tint = PrCoral, modifier = Modifier.size(38.dp)); Text("Wiped Out", color = GameInk, fontSize = 26.sp, fontWeight = FontWeight.Bold); Text(if (newBest) "New best — $score" else "Score $score · Best $high", color = GameMuted); IconButton(replay, Modifier.padding(top = 6.dp).size(52.dp).background(PrCoral, CircleShape)) { Icon(Icons.Default.Refresh, "Play again", tint = Color.White) } }
            else -> Unit
        } }
    }
}

@Composable private fun CountdownOverlay(seconds: Int) { Surface(Modifier.fillMaxSize().wrapContentSize(Alignment.Center), color = GameCard, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, GameBorder)) { Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Get Ready", color = GameMuted); Text("$seconds", color = PrCoral, fontSize = 56.sp, fontWeight = FontWeight.Bold) } } }

@Composable private fun MultiplayerField(racers: List<MotoRacer>, myId: String, ys: SnapshotStateMap<String, Float>, obstacles: List<Float>, myScore: Int, myAlive: Boolean) {
    val density = LocalDensity.current.density
    Column(Modifier.fillMaxSize()) { racers.forEach { racer ->
        Box(Modifier.weight(1f).fillMaxWidth().background(GameBg)) {
            Canvas(Modifier.fillMaxSize()) { val ground = size.height - 20f*density; drawLine(GameBorder, Offset(0f, ground), Offset(size.width, ground), 2f); obstacles.forEach { drawBump(it*density, ground, 18f*density, 15f*density) }; drawBike(56f*density, ground - 15f*density + (ys[racer.id] ?: 0f)*density, 34f*density, 30f*density, BikeColors[racer.colorIndex.mod(BikeColors.size)], !(if (racer.id == myId) myAlive else racer.alive)) }
            Text("${racer.initials}${if (racer.id == myId) " · YOU" else ""}   ${if (racer.id == myId) myScore else racer.score}", color = GameInk, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp))
            if (!(if (racer.id == myId) myAlive else racer.alive)) Text("Wiped Out", color = PrCoral, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterEnd).padding(18.dp))
        }; HorizontalDivider(color = GameBorder)
    } }
}

@Composable private fun ResultsOverlay(racers: List<MotoRacer>, myId: String, myScore: Int, host: Boolean, replay: () -> Unit, leave: () -> Unit) {
    val ranked = racers.map { it to if (it.id == myId) myScore else it.score }.sortedByDescending { it.second }
    Surface(Modifier.fillMaxSize().wrapContentSize(Alignment.Center).width(300.dp), color = GameCard, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, GameBorder)) { Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(Icons.Default.EmojiEvents, null, tint = PrCoral); Text("Race Over", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        ranked.forEachIndexed { index, entry -> Row(Modifier.fillMaxWidth()) { Text(listOf("🥇", "🥈", "🥉").getOrElse(index) { "" }); Spacer(Modifier.width(8.dp)); Text(if (entry.first.id == myId) "You" else entry.first.initials, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text("${entry.second}", color = GameMuted, fontWeight = FontWeight.Bold) } }
        if (host) Button(replay, colors = ButtonDefaults.buttonColors(containerColor = PrCoral), shape = CircleShape) { Text("Play Again") } else Text("Waiting for host to restart…", color = GameMuted)
        TextButton(leave) { Text("Leave", color = GameMuted) }
    } }
}

@Composable private fun LobbyDialog(code: String, racers: List<MotoRacer>, host: Boolean, color: Int, joinCode: String, showJoin: Boolean, onJoinCode: (String)->Unit, onShowJoin: ()->Unit, onColor: (Int)->Unit, onCreate: ()->Unit, onJoin: ()->Unit, onStart: ()->Unit, onClose: ()->Unit, onShare: ()->Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Moto Run") }, text = { Column(Modifier.widthIn(min = 320.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (code.isBlank()) {
            Icon(Icons.Default.Group, null, tint = PrCoral, modifier = Modifier.size(34.dp)); Text("Race Friends", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Up to 3 riders, same speed bumps, same start.", color = GameMuted)
            Button(onCreate, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PrCoral)) { Text("Start a Race") }
            if (showJoin) { OutlinedTextField(joinCode, onJoinCode, Modifier.fillMaxWidth(), label = { Text("Enter code") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)); Button(onJoin, Modifier.fillMaxWidth(), enabled = joinCode.isNotBlank()) { Text("Join") } } else TextButton(onShowJoin) { Text("Join with a Code", color = PrCoral) }
        } else {
            Text("RACE CODE", color = GameMuted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text(code, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Button(onShare, colors = ButtonDefaults.buttonColors(containerColor = PrCoral)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Invite a Friend") }
            Text("YOUR BIKE COLOR", color = GameMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { BikeColors.forEachIndexed { index, item -> Box(Modifier.size(if (color == index) 34.dp else 28.dp).background(item, CircleShape).clickable { onColor(index) }) } }
            Column(Modifier.fillMaxWidth()) { Text("RIDERS (${racers.size}/3)", color = GameMuted, fontSize = 10.sp, fontWeight = FontWeight.Black); racers.forEach { Text("${it.initials}${if (it.isHost) "   HOST" else ""}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp)) } }
            if (host) Button(onStart, colors = ButtonDefaults.buttonColors(containerColor = PrCoral)) { Text("Start Race") } else Text("Waiting for the host to start the race…", color = GameMuted)
        }
    } }, confirmButton = { TextButton(onClose) { Text("Close") } })
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSolo(engine: MotoRunEngine) { val ground = size.height * (1f - MotoRunEngine.GROUND); drawRect(GameBg, size = size); drawLine(GameBorder, Offset(0f, ground), Offset(size.width, ground), 2f); var x = -size.width * (engine.groundScroll % .16f); while (x < size.width) { drawLine(GameMuted.copy(.22f), Offset(x, ground + 18f), Offset(x + size.width * .055f, ground + 18f), 3f, StrokeCap.Round); x += size.width * .16f }; engine.obstacles.forEach { drawBump(size.width * it.x, ground, size.width * it.width, size.height * it.height) }; drawBike(size.width * .18f, ground - size.height * engine.riderY - size.height * MotoRunEngine.RIDER_H / 2, size.width * .085f, size.height * MotoRunEngine.RIDER_H, PrCoral, false) }
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBump(x: Float, ground: Float, width: Float, height: Float) { drawPath(Path().apply { moveTo(x-width/2, ground); quadraticTo(x, ground-height, x+width/2, ground); close() }, GameTeal) }
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBike(x: Float, y: Float, width: Float, height: Float, color: Color, crashed: Boolean) { val alpha=if(crashed).35f else 1f; val rear=Offset(x-width*.3f,y+height*.3f); val front=Offset(x+width*.3f,y+height*.3f); val seat=Offset(x-width*.1f,y-height*.3f); val nose=Offset(x+width*.28f,y-height*.05f); val stroke=height*.15f; drawLine(GameInk.copy(alpha),seat,rear,stroke,StrokeCap.Round); drawLine(GameInk.copy(alpha),seat,nose,stroke,StrokeCap.Round); drawLine(GameInk.copy(alpha),nose,front,stroke,StrokeCap.Round); drawOval(color.copy(alpha),Offset(seat.x-width*.16f,seat.y-height*.1f),Size(width*.34f,height*.22f)); listOf(rear,front).forEach{drawCircle(GameInk,height*.3f,it);drawCircle(GameBg,height*.114f,it)} }
private val GameBg=Color(0xFFF7F5F2); private val GameCard=Color.White; private val GameInk=Color(0xFF202126); private val GameMuted=Color(0xFF77777E); private val GameBorder=Color(0xFFE2DED8); private val GameTeal=Color(0xFF20A99A); private const val KEY_HIGH="motoRunHighScore"
