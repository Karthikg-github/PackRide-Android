package com.karthik.packride.safety

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** iOS EmergencyContact parity — up to 3 contacts, optional PackRide linkedUserID. */
data class EmergencyContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val relationship: String = "",
    val linkedUserID: String? = null
)

class EmergencyContactStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("packride_prefs", Context.MODE_PRIVATE)

    fun load(): List<EmergencyContact> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                EmergencyContact(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.getString("name"),
                    phone = o.optString("phone", ""),
                    relationship = o.optString("relationship", ""),
                    linkedUserID = if (o.isNull("linkedUserID")) null else o.optString("linkedUserID").takeIf { it.isNotBlank() }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(list: List<EmergencyContact>) {
        val arr = JSONArray()
        list.take(MAX).forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("phone", c.phone)
                    .put("relationship", c.relationship)
                    .put("linkedUserID", c.linkedUserID ?: JSONObject.NULL)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun linkedUserIds(): List<String> =
        load().mapNotNull { it.linkedUserID }.filter { it.isNotBlank() }

    companion object {
        private const val KEY = "emergencyContacts"
        const val MAX = 3
    }
}
