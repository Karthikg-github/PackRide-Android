package com.karthik.packride.motorun

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class MotoRaceState { IDLE, LOBBY, ACTIVE }

data class MotoRacer(
    val id: String,
    val initials: String,
    val colorIndex: Int,
    val isHost: Boolean,
    val jumpCount: Int = 0,
    val score: Int = 0,
    val alive: Boolean = true,
    val finished: Boolean = false
)

object MotoRacePhysics {
    const val BASE_SPEED = 190.0
    const val MAX_SPEED = 660.0
    const val RAMP_PER_SECOND = 10.0
    const val MAX_DURATION = 90.0
    private val capTime = (MAX_SPEED - BASE_SPEED) / RAMP_PER_SECOND

    fun distance(time: Double): Double {
        val t = max(0.0, time)
        if (t <= capTime) return BASE_SPEED * t + 0.5 * RAMP_PER_SECOND * t * t
        val atCap = BASE_SPEED * capTime + 0.5 * RAMP_PER_SECOND * capTime * capTime
        return atCap + MAX_SPEED * (t - capTime)
    }
}

private class SplitMix64(seed: Long) {
    private var state = seed
    fun nextLong(): Long {
        state += -7046029254386353131L
        var z = state
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }
    fun nextDouble(): Double = nextLong().ushr(11).toDouble() / 9007199254740992.0
}

class RaceObstacleSchedule private constructor(private val spawnTimes: List<Double>) {
    fun visibleObstacleX(time: Double, laneWidth: Float): List<Float> {
        if (time < 0) return emptyList()
        val now = MotoRacePhysics.distance(time)
        return spawnTimes.asSequence().takeWhile { it <= time }
            .map { laneWidth + 20.0 - (now - MotoRacePhysics.distance(it)) }
            .filter { it > -40.0 }.map { it.toFloat() }.toList()
    }

    companion object {
        fun generate(seed: Long): RaceObstacleSchedule {
            val random = SplitMix64(seed)
            val times = mutableListOf<Double>()
            var time = 4.0
            while (time < MotoRacePhysics.MAX_DURATION) {
                times += time
                time += 1.7 + random.nextDouble() * 1.2
            }
            return RaceObstacleSchedule(times)
        }
    }
}

/** Firebase protocol-compatible port of iOS MotoRunSessionManager. */
class MotoRunSessionManager(context: Context) {
    private val root = FirebaseDatabase.getInstance().reference.child("motorun")
    private var raceListener: ValueEventListener? = null
    private var offsetListener: ValueEventListener? = null
    private var serverOffsetMs = 0.0
    private val prefs = context.getSharedPreferences("motorun_session", Context.MODE_PRIVATE)

    val myId: String = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("deviceId", it).apply()
    }

    private val _raceCode = MutableStateFlow("")
    val raceCode: StateFlow<String> = _raceCode.asStateFlow()
    private val _raceState = MutableStateFlow(MotoRaceState.IDLE)
    val raceState: StateFlow<MotoRaceState> = _raceState.asStateFlow()
    private val _racers = MutableStateFlow<List<MotoRacer>>(emptyList())
    val racers: StateFlow<List<MotoRacer>> = _racers.asStateFlow()
    private val _seed = MutableStateFlow(0L)
    val seed: StateFlow<Long> = _seed.asStateFlow()
    private val _raceStartAt = MutableStateFlow(0.0)
    val raceStartAt: StateFlow<Double> = _raceStartAt.asStateFlow()

    init {
        val offsetRef = FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset")
        offsetListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { serverOffsetMs = snapshot.getValue(Double::class.java) ?: 0.0 }
            override fun onCancelled(error: DatabaseError) = Unit
        }.also { offsetRef.addValueEventListener(it) }
    }

    val isHost get() = _racers.value.firstOrNull { it.id == myId }?.isHost == true
    val me get() = _racers.value.firstOrNull { it.id == myId }
    fun elapsed(): Double = if (_raceStartAt.value <= 0) -Double.MAX_VALUE
        else ((System.currentTimeMillis() + serverOffsetMs) / 1000.0) - _raceStartAt.value

    fun createRace(initials: String, colorIndex: Int) {
        val code = buildString { repeat(6) { append("ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random()) } }
        val ref = root.child(code)
        ref.child("riders").child(myId).setValue(racerPayload(initials, colorIndex, true))
        ref.child("state").setValue("lobby")
        _raceCode.value = code
        listen(code)
    }

    fun joinRace(code: String, initials: String, colorIndex: Int) {
        val clean = code.trim().uppercase()
        if (clean.isBlank()) return
        root.child(clean).child("riders").child(myId).setValue(racerPayload(initials, colorIndex, false))
        _raceCode.value = clean
        listen(clean)
    }

    private fun racerPayload(initials: String, color: Int, host: Boolean) = mapOf(
        "initials" to initials, "colorIndex" to color, "isHost" to host,
        "jumpCount" to 0, "score" to 0, "alive" to true, "finished" to false
    )

    private fun listen(code: String) {
        stopListening()
        val ref = root.child(code)
        raceListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _racers.value = snapshot.child("riders").children.mapNotNull { child ->
                    val initials = child.child("initials").getValue(String::class.java) ?: return@mapNotNull null
                    MotoRacer(child.key ?: return@mapNotNull null, initials,
                        child.child("colorIndex").getValue(Long::class.java)?.toInt() ?: 0,
                        child.child("isHost").getValue(Boolean::class.java) ?: false,
                        child.child("jumpCount").getValue(Long::class.java)?.toInt() ?: 0,
                        child.child("score").getValue(Long::class.java)?.toInt() ?: 0,
                        child.child("alive").getValue(Boolean::class.java) ?: true,
                        child.child("finished").getValue(Boolean::class.java) ?: false)
                }.sortedByDescending { it.isHost }
                _raceState.value = when (snapshot.child("state").getValue(String::class.java)) {
                    "lobby" -> MotoRaceState.LOBBY; "active" -> MotoRaceState.ACTIVE; else -> MotoRaceState.IDLE
                }
                _seed.value = snapshot.child("seed").getValue(Double::class.java)?.toLong()
                    ?: snapshot.child("seed").getValue(Long::class.java) ?: 0L
                _raceStartAt.value = snapshot.child("raceStartAt").getValue(Double::class.java) ?: 0.0
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }.also { ref.addValueEventListener(it) }
    }

    fun updateMyColor(index: Int) = update("colorIndex", index)
    fun startRace() {
        if (!isHost || _raceCode.value.isBlank()) return
        val ref = root.child(_raceCode.value)
        val newSeed = (Math.random() * (1L shl 52)).toLong()
        val start = ((System.currentTimeMillis() + serverOffsetMs) / 1000.0) + 3.0
        _racers.value.forEach { ref.child("riders").child(it.id).updateChildren(mapOf("alive" to true, "score" to 0, "finished" to false, "jumpCount" to 0)) }
        ref.child("seed").setValue(newSeed.toDouble())
        ref.child("raceStartAt").setValue(start)
        ref.child("state").setValue("active")
    }
    fun sendJump() = update("jumpCount", (me?.jumpCount ?: 0) + 1)
    fun sendScore(score: Int) = update("score", score)
    fun sendCrash(score: Int) = updateChildren(mapOf("alive" to false, "finished" to true, "score" to score))
    fun sendTimeoutFinish(score: Int) = updateChildren(mapOf("finished" to true, "score" to score))
    private fun update(key: String, value: Any) { if (_raceCode.value.isNotBlank()) root.child(_raceCode.value).child("riders").child(myId).child(key).setValue(value) }
    private fun updateChildren(values: Map<String, Any>) { if (_raceCode.value.isNotBlank()) root.child(_raceCode.value).child("riders").child(myId).updateChildren(values) }

    fun leaveRace() {
        val code = _raceCode.value
        stopListening()
        if (code.isNotBlank()) root.child(code).child("riders").child(myId).removeValue()
        _raceCode.value = ""; _raceState.value = MotoRaceState.IDLE; _racers.value = emptyList(); _seed.value = 0; _raceStartAt.value = 0.0
    }

    fun dispose() {
        stopListening()
        offsetListener?.let {
            FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset").removeEventListener(it)
        }
        offsetListener = null
    }
    private fun stopListening() { raceListener?.let { listener -> if (_raceCode.value.isNotBlank()) root.child(_raceCode.value).removeEventListener(listener) }; raceListener = null }
}
