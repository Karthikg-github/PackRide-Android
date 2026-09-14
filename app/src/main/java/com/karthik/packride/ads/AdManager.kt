package com.karthik.packride.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob — same placement rules as iOS: Feed, History, Profile, Moto Run only.
 *
 * iOS uses publisher pub-2444709882752151. Android needs its own App ID + banner
 * unit in AdMob. Set USE_TEST_ADS = false after pasting real Android IDs.
 */
object AdConfig {
    /** true = Google sample ads (safe). false = your production units. */
    const val USE_TEST_ADS = false

    private const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
    // AdMob → Apps → Android app (com.karthik.packride) → Banner unit
    private const val PROD_BANNER_UNIT_ID = "ca-app-pub-2444709882752151/4304568865"

    val bannerUnitId: String
        get() = if (USE_TEST_ADS) TEST_BANNER else PROD_BANNER_UNIT_ID
}

object AdManager {
    private val initialized = AtomicBoolean(false)
    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds = _canRequestAds.asStateFlow()
    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired = _privacyOptionsRequired.asStateFlow()

    private fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        MobileAds.initialize(context.applicationContext) {}
    }

    /** Refresh consent on every launch and never request an ad before UMP allows it. */
    fun gatherConsent(activity: Activity) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                _privacyOptionsRequired.value =
                    consent.privacyOptionsRequirementStatus == PrivacyOptionsRequirementStatus.REQUIRED
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updateAdReadiness(activity, consent.canRequestAds())
                }
            },
            {
                // A transient consent-network failure must not override a
                // previously valid decision cached by the UMP SDK.
                updateAdReadiness(activity, consent.canRequestAds())
            }
        )
    }

    fun showPrivacyOptions(activity: Activity, onComplete: (String?) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            val consent = UserMessagingPlatform.getConsentInformation(activity)
            _privacyOptionsRequired.value =
                consent.privacyOptionsRequirementStatus == PrivacyOptionsRequirementStatus.REQUIRED
            updateAdReadiness(activity, consent.canRequestAds())
            onComplete(error?.message)
        }
    }

    private fun updateAdReadiness(context: Context, allowed: Boolean) {
        _canRequestAds.value = allowed
        if (allowed) initialize(context)
    }

    fun adRequest(): AdRequest = AdRequest.Builder().build()
}
