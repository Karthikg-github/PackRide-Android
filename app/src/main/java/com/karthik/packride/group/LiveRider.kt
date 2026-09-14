package com.karthik.packride.group

data class LiveRider(
    val id: String,
    val name: String,
    val initials: String,
    val latitude: Double,
    val longitude: Double,
    val speedMph: Double,
    val isLeader: Boolean = false,
    val avatarURL: String = ""
)
