package com.karthik.packride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthik.packride.badges.Badge
import com.karthik.packride.badges.BadgeCategory
import com.karthik.packride.badges.BadgeEngine
import com.karthik.packride.ride.RideHistoryManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrMetricStrip
import com.karthik.packride.ui.theme.PrPageHeader
import com.karthik.packride.ui.theme.PrWebSectionLabel

// Aug 30, 2026 — real functional parity with iOS BadgesView.swift, built on
// the already-real BadgeEngine (badges/BadgeEngine.kt): earned-vs-locked
// treatment and a progress readout toward the next unearned badge — see
// BadgeEngine.kt's header comment for where each stat comes from. iOS's
// BadgesView has no tap-to-detail view on a badge card (no onTapGesture/
// sheet in the source), so this doesn't add one either — straight parity,
// not a gap.
//
// Aug 31, 2026 — all 12 of iOS's badges now render, "Smooth Operator" (Ride
// Score 95+) included: BadgeEngine.computeAsync scans road rides' GPX files
// for real, off the main thread — see its doc comment. This screen shows
// the plain synchronous compute() result immediately (every badge except
// Smooth Operator's real value, which reads as 0/95 until the scan lands)
// and swaps in the fully-resolved list once computeAsync finishes, so the
// screen never blocks on GPX parsing and never shows a loading spinner.
@Composable
fun BadgesScreen() {
    val context = LocalContext.current
    val history = remember { RideHistoryManager(context) }
    val rides by history.rides.collectAsState()
    var badges by remember { mutableStateOf(BadgeEngine.compute(rides)) }
    LaunchedEffect(rides) {
        badges = BadgeEngine.computeAsync(context.filesDir, rides)
    }
    val earnedCount = badges.count { it.earned }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pr.bg)
            .verticalScroll(rememberScrollState())
    ) {
        PrPageHeader(
            eyebrow = "Badges",
            title = "Your Achievements",
            subtitle = "$earnedCount of ${badges.size} badges earned"
        )

        PrMetricStrip(
            metrics = BadgeCategory.entries.map { category ->
                val inCategory = badges.filter { it.category == category }
                "${inCategory.count { it.earned }}/${inCategory.size}" to category.displayName
            }
        )

        Column(modifier = Modifier.padding(top = 8.dp, bottom = 30.dp)) {
            BadgeCategory.entries.forEach { category ->
                val categoryBadges = badges.filter { it.category == category }
                if (categoryBadges.isEmpty()) return@forEach

                val earnedInCategory = categoryBadges.count { it.earned }
                PrWebSectionLabel(title = category.displayName, detail = "$earnedInCategory/${categoryBadges.size}")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    categoryBadges.chunked(2).forEach { rowBadges ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowBadges.forEach { badge ->
                                BadgeCardView(
                                    badge = badge,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                            if (rowBadges.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

// Fixed literal, not an adaptive Pr token — mirrors iOS BadgeCard's own
// goldColor (Color(red: 0.788, green: 0.565, blue: 0.180) / #C9902E), which
// iOS deliberately keeps as a one-off non-adaptive literal ("should read as
// 'gold' the same way in both light and dark mode") rather than a new pr*
// design-system token, same pattern as MaintenanceRow's statusColor.
private val BadgeGold = Color(0xFFC9902E)

@Composable
private fun BadgeCardView(badge: Badge, modifier: Modifier = Modifier) {
    val earned = badge.earned
    val ringColor = if (earned) BadgeGold else Pr.border
    val fillColor = if (earned) BadgeGold.copy(alpha = 0.15f) else Pr.fieldBg
    val glyphColor = if (earned) BadgeGold else Pr.muted
    val borderColor = if (earned) BadgeGold.copy(alpha = 0.4f) else Pr.border

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Pr.cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp)
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Icon glyph — no icon library asset is verified for badge-specific
        // symbols in this codebase (medal/lock/trophy icons are unconfirmed
        // here), so the badge marker is rendered via color/shape/typography:
        // an initial-letter monogram in a ring, exactly the same
        // earned-vs-locked color treatment iOS applies to its SF Symbol.
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(if (earned) 2.dp else 1.dp, ringColor, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(fillColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge.name.take(1).uppercase(),
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = glyphColor)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = badge.name,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Pr.ink),
                textAlign = TextAlign.Center
            )
            Text(
                text = badge.description,
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal, color = Pr.muted),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (earned) {
            Text(
                text = "EARNED",
                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = BadgeGold),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BadgeGold.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Pr.border)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(badge.progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(Pr.coral)
                    )
                }
                Text(
                    text = badge.readout,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Pr.muted,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
    }
}
