package com.karthik.packride.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Routes a tapped crash push to the Profile responder inbox. */
object PendingCrashIncident {
    private val _incidentId = MutableStateFlow<String?>(null)
    val incidentId = _incidentId.asStateFlow()

    fun request(id: String) { _incidentId.value = id }
    fun clear() { _incidentId.value = null }
}
