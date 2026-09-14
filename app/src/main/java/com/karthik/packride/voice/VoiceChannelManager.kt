package com.karthik.packride.voice

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceMember(val id: String, val name: String, val role: String)
data class VoiceJoinRequest(val id: String, val name: String)

/** Firebase control plane for temporary, cross-platform Ride Comms rooms. */
class VoiceChannelManager(context: Context) {
    private val db = FirebaseDatabase.getInstance().reference
    private val functions = FirebaseFunctions.getInstance()
    private val voice = VoiceChatManager.get()
    private val _code = MutableStateFlow(""); val code: StateFlow<String> = _code.asStateFlow()
    private val _title = MutableStateFlow("Ride Comms"); val title: StateFlow<String> = _title.asStateFlow()
    private val _status = MutableStateFlow("idle"); val status: StateFlow<String> = _status.asStateFlow()
    private val _host = MutableStateFlow(false); val isHost: StateFlow<Boolean> = _host.asStateFlow()
    private val _members = MutableStateFlow<List<VoiceMember>>(emptyList()); val members: StateFlow<List<VoiceMember>> = _members.asStateFlow()
    private val _requests = MutableStateFlow<List<VoiceJoinRequest>>(emptyList()); val requests: StateFlow<List<VoiceJoinRequest>> = _requests.asStateFlow()
    private val _error = MutableStateFlow<String?>(null); val error: StateFlow<String?> = _error.asStateFlow()
    private var listener: ValueEventListener? = null

    fun create(name: String, title: String) {
        _status.value = "working"
        functions.getHttpsCallable("createVoiceRoom").call(mapOf("name" to name, "title" to title))
            .addOnSuccessListener { result ->
                val value = result.data as? Map<*, *> ?: return@addOnSuccessListener fail("Invalid voice-server response.")
                open(value["code"] as? String ?: return@addOnSuccessListener fail("No room code was returned."))
            }.addOnFailureListener { fail(it.localizedMessage ?: "Couldn't create Ride Comms.") }
    }

    fun requestJoin(roomCode: String, name: String) {
        val cleaned = roomCode.uppercase().filter { it.isLetterOrDigit() }.take(6)
        _status.value = "working"
        functions.getHttpsCallable("requestVoiceRoomJoin").call(mapOf("code" to cleaned, "name" to name))
            .addOnSuccessListener { result ->
                _code.value = cleaned
                _status.value = ((result.data as? Map<*, *>)?.get("status") as? String) ?: "pending"
                listen(cleaned)
            }.addOnFailureListener { fail(it.localizedMessage ?: "Couldn't request access.") }
    }

    fun respond(uid: String, approve: Boolean) {
        functions.getHttpsCallable("respondVoiceRoomJoin").call(mapOf("code" to _code.value, "requesterUID" to uid, "approve" to approve))
            .addOnFailureListener { fail(it.localizedMessage ?: "Couldn't update that request.") }
    }

    fun connectAudio() { if (_status.value == "accepted") voice.join("VC${_code.value}") }
    fun dismissError() { _error.value = null }

    fun leave() {
        voice.leave(); val room = _code.value; stopListen()
        if (room.isNotEmpty()) functions.getHttpsCallable("leaveVoiceRoom").call(mapOf("code" to room))
        reset()
    }

    private fun open(roomCode: String) { _code.value = roomCode; _status.value = "accepted"; _host.value = true; listen(roomCode) }

    private fun listen(roomCode: String) {
        stopListen(); val ref = db.child("voiceRooms").child(roomCode)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val me = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                _title.value = s.child("title").getValue(String::class.java) ?: "Ride Comms"
                _host.value = s.child("hostUID").getValue(String::class.java) == me
                val roomState = s.child("status").getValue(String::class.java) ?: "ended"
                val myState = s.child("members").child(me).child("status").getValue(String::class.java)
                _status.value = if (roomState != "active") "ended" else myState ?: "pending"
                _members.value = s.child("members").children.mapNotNull { c ->
                    if (c.child("status").getValue(String::class.java) != "accepted") null else VoiceMember(c.key ?: return@mapNotNull null, c.child("name").getValue(String::class.java) ?: "Rider", c.child("role").getValue(String::class.java) ?: "rider")
                }
                _requests.value = if (_host.value) s.child("joinRequests").children.mapNotNull { c -> VoiceJoinRequest(c.key ?: return@mapNotNull null, c.child("name").getValue(String::class.java) ?: "Rider") } else emptyList()
                if (_status.value == "ended") voice.leave()
            }
            override fun onCancelled(e: DatabaseError) { if (_status.value != "pending") fail(e.message) }
        }
        ref.addValueEventListener(l); listener = l
    }

    private fun stopListen() { listener?.let { if (_code.value.isNotEmpty()) db.child("voiceRooms").child(_code.value).removeEventListener(it) }; listener = null }
    private fun fail(message: String) { _status.value = "idle"; _error.value = message }
    private fun reset() { _code.value = ""; _title.value = "Ride Comms"; _status.value = "idle"; _host.value = false; _members.value = emptyList(); _requests.value = emptyList() }
}
