package com.karthik.packride.ads

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** Fixed 320x50 banner footer — same placement philosophy as iOS AdBannerFooter. */
@Composable
fun AdBannerFooter(modifier: Modifier = Modifier) {
    val canRequestAds by AdManager.canRequestAds.collectAsState()
    if (!canRequestAds) return
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(vertical = 8.dp),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdConfig.bannerUnitId
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                loadAd(AdManager.adRequest())
            }
        },
        // AdView owns native/WebView resources. Destroy it when Compose
        // removes Profile/Feed/History instead of leaking one per tab visit.
        onRelease = { adView -> adView.destroy() }
    )
}
