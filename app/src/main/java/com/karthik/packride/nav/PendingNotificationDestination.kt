package com.karthik.packride.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Destination requested by a notification tap, consumed once by PackRideNav. */
object PendingNotificationDestination {
    private val _type = MutableStateFlow<String?>(null)
    val type = _type.asStateFlow()

    fun request(type: String) { _type.value = type }
    fun clear() { _type.value = null }
}
