package com.karthik.packride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Motorcycle
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.auth.rideInitials
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrAvatar
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrPill
import com.karthik.packride.ui.theme.PrimaryButton

// Aug 30, 2026 — full port of iOS's OnboardingView.swift (319 lines). The
// previous Android version was a single flat form on the old design system
// (no multi-page flow, no progress indicator, no avatar preview, no
// experience-level picker, free-text experience field). This rebuild
// matches iOS's 4-page flow (Welcome / Name / Bike / Ready) with the same
// copy, the same Continue/"Start Riding" + Back button behavior, and the
// same disabled-until-named gate on the Name page, restyled onto Pr/PrFont.
//
// One deliberate simplification: iOS's page icon is the SF Symbol
// "arrowtriangle.up.fill" (a generic up-arrow glyph); Android has no 1:1
// equivalent bundled, so this uses Icons.Default.Motorcycle instead — a
// closer fit for a riding app and already proven elsewhere in this codebase
// (GarageScreen, RideHistoryScreen, bottom nav).
@Composable
fun OnboardingScreen(auth: AuthManager) {
    var currentPage by remember { mutableStateOf(0) }
    var tempName by remember { mutableStateOf("") }
    var tempBike by remember { mutableStateOf("") }
    var tempCity by remember { mutableStateOf("") }
    var selectedExperience by remember { mutableStateOf("Intermediate") }
    val experienceLevels = listOf("Beginner", "Intermediate", "Advanced", "Expert")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pr.bg)
            .padding(top = 60.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (index in 0 until 4) {
                Box(
                    modifier = Modifier
                        .width(if (currentPage == index) 24.dp else 8.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (currentPage == index) Pr.coral else Pr.border)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        when (currentPage) {
            0 -> OnboardingWelcomePage()
            1 -> OnboardingNamePage(tempName = tempName, onNameChange = { tempName = it })
            2 -> OnboardingBikePage(
                tempBike = tempBike,
                onBikeChange = { tempBike = it },
                tempCity = tempCity,
                onCityChange = { tempCity = it },
                selectedExperience = selectedExperience,
                onExperienceChange = { selectedExperience = it },
                experienceLevels = experienceLevels
            )
            else -> OnboardingReadyPage(name = tempName)
        }

        Spacer(modifier = Modifier.weight(1f))

        val blockedOnNamePage = currentPage == 1 && tempName.isBlank()
        PrimaryButton(
            text = if (currentPage == 3) "Start Riding" else "Continue",
            onClick = {
                if (currentPage < 3) {
                    currentPage += 1
                } else {
                    auth.completeOnboarding(tempName.trim(), tempBike.trim(), tempCity.trim(), selectedExperience)
                }
            },
            enabled = !blockedOnNamePage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        if (currentPage > 0) {
            TextButton(onClick = { currentPage -= 1 }) {
                Text("Back", style = TextStyle(fontSize = 14.sp, color = Pr.muted))
            }
        }
    }
}

@Composable
private fun OnboardingWelcomePage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Pr.coralSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Motorcycle, contentDescription = null, tint = Pr.coral, modifier = Modifier.size(36.dp))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Welcome to\nPackRide",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Pr.ink),
                textAlign = TextAlign.Center
            )
            Text(
                text = "The riding companion built for finding great roads, riding with your pack, and staying safe out there.",
                style = TextStyle(fontSize = 14.sp, color = Pr.muted),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        PrCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            cornerRadius = 16.dp
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OnboardingFeatureRow(color = Pr.coral, text = "Curvy-road route planning")
                OnboardingFeatureRow(color = Pr.teal, text = "Automatic crash detection")
                OnboardingFeatureRow(color = Pr.coral, text = "Live pack tracking on group rides")
            }
        }
    }
}

@Composable
private fun OnboardingFeatureRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = text, style = TextStyle(fontSize = 13.sp, color = Pr.ink))
    }
}

@Composable
private fun OnboardingNamePage(tempName: String, onNameChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        PrAvatar(initials = if (tempName.isBlank()) "?" else tempName.rideInitials(), size = 80.dp)

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What's your name?", style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Pr.ink))
            Text(
                text = "This is how other riders will\nsee you on the map.",
                style = TextStyle(fontSize = 14.sp, color = Pr.muted),
                textAlign = TextAlign.Center
            )
        }

        OutlinedTextField(
            value = tempName,
            onValueChange = onNameChange,
            placeholder = { Text("Your first name", color = Pr.muted) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 18.sp, color = Pr.ink, textAlign = TextAlign.Center),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun OnboardingBikePage(
    tempBike: String,
    onBikeChange: (String) -> Unit,
    tempCity: String,
    onCityChange: (String) -> Unit,
    selectedExperience: String,
    onExperienceChange: (String) -> Unit,
    experienceLevels: List<String>
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Motorcycle, contentDescription = null, tint = Pr.coral, modifier = Modifier.size(40.dp))
            Text(
                text = "Tell us about\nyour ride",
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Pr.ink),
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = tempBike,
                onValueChange = onBikeChange,
                placeholder = { Text("Your bike (e.g. Yamaha R1)", color = Pr.muted) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = Pr.ink),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tempCity,
                onValueChange = onCityChange,
                placeholder = { Text("Your city", color = Pr.muted) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = Pr.ink),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Experience Level",
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pr.muted),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                experienceLevels.forEach { level ->
                    PrPill(title = level, selected = selectedExperience == level, onClick = { onExperienceChange(level) })
                }
            }
        }
    }
}

@Composable
private fun OnboardingReadyPage(name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Pr.coralSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Pr.coral, modifier = Modifier.size(36.dp))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "You're all set, ${name.ifBlank { "Rider" }}",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Pr.ink),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Your PackRide profile is ready.\nTime to hit the road!",
                style = TextStyle(fontSize = 14.sp, color = Pr.muted),
                textAlign = TextAlign.Center
            )
        }

        PrCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            cornerRadius = 16.dp
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OnboardingTipRow(number = "1", text = "Create or join a group ride")
                OnboardingTipRow(number = "2", text = "Share your ride code with friends")
                OnboardingTipRow(number = "3", text = "Ride together, stay connected!")
            }
        }
    }
}

@Composable
private fun OnboardingTipRow(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Pr.coralSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(number, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Pr.coral))
        }
        Text(text, style = TextStyle(fontSize = 13.sp, color = Pr.ink))
    }
}
