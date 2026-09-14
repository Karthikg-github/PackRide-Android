package com.karthik.packride.data

import android.content.Context
import android.content.SharedPreferences

/** Local profile flags — mirrors iOS UserDefaults keys used by Auth / Onboarding. */
class UserPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("packride_prefs", Context.MODE_PRIVATE)

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    var riderName: String
        get() = prefs.getString(KEY_NAME, "Rider") ?: "Rider"
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var riderBike: String
        get() = prefs.getString(KEY_BIKE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BIKE, value).apply()

    var riderCity: String
        get() = prefs.getString(KEY_CITY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CITY, value).apply()

    var riderExperience: String
        get() = prefs.getString(KEY_EXPERIENCE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EXPERIENCE, value).apply()

    var avatarURL: String
        get() = prefs.getString(KEY_AVATAR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AVATAR, value).apply()

    var bannerURL: String
        get() = prefs.getString(KEY_BANNER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BANNER, value).apply()

    // Aug 30, 2026 — Profile screen's Emergency & Safety section (iOS
    // ProfileView's @AppStorage bloodType/allergies). Local-only, same as
    // the rest of UserPrefs — not pushed to Firebase (iOS doesn't either).
    var bloodType: String
        get() = prefs.getString(KEY_BLOOD_TYPE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BLOOD_TYPE, value).apply()

    var allergies: String
        get() = prefs.getString(KEY_ALLERGIES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ALLERGIES, value).apply()

    fun applyServerProfile(
        name: String,
        bike: String?,
        city: String?,
        experience: String?,
        avatar: String?,
        banner: String?
    ) {
        prefs.edit()
            .putString(KEY_NAME, name)
            .putString(KEY_BIKE, bike.orEmpty())
            .putString(KEY_CITY, city.orEmpty())
            .putString(KEY_EXPERIENCE, experience.orEmpty())
            .apply {
                if (!avatar.isNullOrEmpty()) putString(KEY_AVATAR, avatar)
                if (!banner.isNullOrEmpty()) putString(KEY_BANNER, banner)
                putBoolean(KEY_ONBOARDING, true)
            }
            .apply()
    }

    companion object {
        private const val KEY_ONBOARDING = "hasCompletedOnboarding"
        private const val KEY_NAME = "riderName"
        private const val KEY_BIKE = "riderBike"
        private const val KEY_CITY = "riderCity"
        private const val KEY_EXPERIENCE = "riderExperience"
        private const val KEY_AVATAR = "avatarURL"
        private const val KEY_BANNER = "bannerURL"
        private const val KEY_BLOOD_TYPE = "bloodType"
        private const val KEY_ALLERGIES = "allergies"
    }
}
