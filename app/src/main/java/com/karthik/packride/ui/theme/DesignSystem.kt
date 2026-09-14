package com.karthik.packride.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Aug 30, 2026 — 1:1 port of iOS's DesignSystem.swift + the Color extension in
// LoginView.swift (the app's actual "REVER-inspired" design language: white
// canvas, ink type, orange accent, charcoal chrome). Android's previous
// Theme.kt had 5 made-up colors, including an accent (#E85A4F) that doesn't
// even match iOS's real one (#FF6A00) — every screen built before this pass
// used stock Material3 defaults instead of this system, which is the root
// cause of the app looking generic next to iOS. This file is the fix:
// exact token values, exact type scale, and the same reusable building
// blocks (cards, primary button, pills, avatar, badges) iOS actually uses,
// named the same way (Pr.ink instead of Color.prInk) so porting a screen
// means a mostly mechanical swap.

/** Adaptive (light/dark) + fixed color tokens — values copied from iOS Color extension. */
object Pr {
    val bg: Color
        @Composable get() = if (prIsDarkTheme()) Color(0xFF121213) else Color(0xFFF8F8F8)
    val coral: Color
        @Composable get() = Color(0xFFFF6A00) // REVER orange — fixed, not adaptive (matches iOS)
    val coralSoft: Color
        @Composable get() = if (prIsDarkTheme()) Color(0xFF472408) else Color(0xFFFFEBDC)
    val ink: Color
        @Composable get() = if (prIsDarkTheme()) Color(0xFFF5F5F5) else Color(0xFF1A1A1C)
    val muted: Color
        @Composable get() = if (prIsDarkTheme()) Color(0xFF9E9EA3) else Color(0xFF737378)
    val border: Color
        @Composable get() = if (prIsDarkTheme()) Color(0xFF38383D) else Color(0xFFE6E6E8)
    val teal: Color = Color(0xFF2B6E85)
    val cardBg: Color
        @Composable get() = if (prIsDarkTheme()) Color(0xFF1F1F21) else Color(0xFFFFFFFF)
    val fieldBg: Color
        @Composable get() = if (prIsDarkTheme()) Color(0xFF29292B) else Color(0xFFF6F6F7)
    val inkFixed: Color = Color(0xFF1A1A1C)
    /** Always-dark bottom tab bar background, in both light and dark mode (matches iOS). */
    val tabBar: Color = Color(0xFF17171A)
    val cover: Color = Color(0xFF25293E)

    // Corner radii — the specific values iOS actually uses at each call site
    // (glassCard default 20, colored-glass-card default 16, primary button 12,
    // bottom sheets/cards 28, small pills/badges 6-12).
    val RadiusSmall = 6.dp
    val RadiusButton = 12.dp
    val RadiusMedium = 16.dp
    val RadiusCard = 20.dp
    val RadiusSheet = 28.dp

    val accentGradient: Brush
        @Composable get() = Brush.horizontalGradient(listOf(coral, Color(0xFFEB4700)))
    val disabledGradient: Brush = Brush.horizontalGradient(listOf(Color(0x4D9E9E9E), Color(0x4D9E9E9E)))
}

/** Type scale — 1:1 port of iOS's AppFont. */
object PrFont {
    val hero: TextStyle @Composable get() = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Pr.ink)
    val title: TextStyle @Composable get() = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Pr.ink)
    val heading: TextStyle @Composable get() = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Pr.ink)
    val subheading: TextStyle @Composable get() = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Pr.ink)
    val body: TextStyle @Composable get() = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = Pr.ink)
    val bodySmall: TextStyle @Composable get() = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = Pr.ink)
    val caption: TextStyle @Composable get() = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Pr.muted)
    val micro: TextStyle @Composable get() = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Pr.muted)
    val sectionHeader: TextStyle @Composable get() = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pr.muted, letterSpacing = 2.sp)
    val stat: TextStyle @Composable get() = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Pr.ink)
    val statLarge: TextStyle @Composable get() = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Pr.ink)
    val button: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    val buttonSmall: TextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
}

/** Supplied by the navigation shell for iOS-style pushed destinations. */
val LocalPrBackAction = compositionLocalOf<(() -> Unit)?> { null }

/**
 * "Glass card" surface — port of iOS's .glassCard() modifier: card-colored
 * background, rounded corners, a subtle 1dp border. Use as the default
 * replacement for a plain Material3 `Card` everywhere in this app.
 */
@Composable
fun PrCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Pr.RadiusCard,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Pr.cardBg)
            .border(1.dp, Pr.border, RoundedCornerShape(cornerRadius))
    ) {
        content()
    }
}

/** Primary CTA button — port of iOS's .primaryButton() modifier (gradient fill, white text, shadow). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape: Shape = RoundedCornerShape(Pr.RadiusButton)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (enabled) 10.dp else 0.dp, shape, ambientColor = Pr.coral.copy(alpha = 0.28f), spotColor = Pr.coral.copy(alpha = 0.28f))
            .clip(shape)
            .background(if (enabled) Pr.accentGradient else Pr.disabledGradient)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = PrFont.button, textAlign = TextAlign.Center)
    }
}

/** Small rounded pill, filled coral when selected — port of iOS's FilterChip. */
@Composable
fun PrPill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Pr.coral else Pr.fieldBg)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Pr.ink),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/**
 * Gradient circle avatar with initials — port of iOS's AvatarCircle.
 *
 * Aug 31, 2026 — added the optional [photoUrl] param (real profile/post photo
 * support, see storage/ImageCloudUpload.kt). When it's a non-blank URL, this renders
 * the photo via Coil's AsyncImage instead of the initials gradient. Every
 * pre-existing call site across the app (HomeScreen, FeedScreen, CommunityScreen,
 * ScheduleRideScreen, OnboardingScreen...) omits this param entirely, so it
 * defaults to null and those sites are unaffected — same initials-circle
 * rendering as before.
 */
@Composable
fun PrAvatar(initials: String, size: Dp, modifier: Modifier = Modifier, photoUrl: String? = null) {
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(
                    Brush.linearGradient(listOf(Pr.coral, Color(0xFFFF5100))),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = TextStyle(fontSize = (size.value * 0.32).sp, fontWeight = FontWeight.Bold, color = Color.White)
            )
        }
    }
}

/** Uppercase, letter-spaced small header — port of iOS's SectionLabel/.sectionHeader(). */
@Composable
fun PrSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = PrFont.sectionHeader, modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

/** Dark rounded pill over map/photo content — port of iOS's RoadNamePill. */
@Composable
fun PrDarkPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}


/**
 * Page header — port of iOS's PRWebPageHeader (used by Garage, Weather,
 * Badges, RidingDigest, LapCompare and more; see PRWebDesign.swift). No
 * A pushed destination receives [LocalPrBackAction] from the navigation shell
 * and displays the same circular chevron used by iOS PRWebPageHeader.
 */
@Composable
fun PrPageHeader(eyebrow: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val onBack = LocalPrBackAction.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Pr.cardBg)
                    .border(1.dp, Pr.border, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = Pr.ink, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.6.sp, color = Pr.coral))
            Spacer(Modifier.height(5.dp))
            Text(title, style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Pr.ink), maxLines = 1)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Pr.muted))
        }
    }
}

/** Row of stat/label pairs divided by hairlines — port of iOS's PRWebMetricStrip. */
@Composable
fun PrMetricStrip(metrics: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Pr.cardBg)
            .border(width = 1.dp, color = Pr.border)
    ) {
        metrics.forEachIndexed { index, (value, label) ->
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(value, style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Pr.ink), maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Text(label.uppercase(), style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp, color = Pr.muted))
            }
            if (index < metrics.size - 1) {
                Box(Modifier.width(1.dp).height(34.dp).background(Pr.border))
            }
        }
    }
}

/** Uppercase section label with an optional right-aligned detail — port of iOS's PRWebSectionLabel. */
@Composable
fun PrWebSectionLabel(title: String, detail: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title.uppercase(), style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Pr.muted))
        if (detail != null) {
            Text(detail, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Pr.muted.copy(alpha = 0.72f)))
        }
    }
}
