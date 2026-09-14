package com.karthik.packride.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.ui.theme.Pr

@Composable
fun LoginScreen(auth: AuthManager) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var resetSent by remember { mutableStateOf(false) }
    val error by auth.errorMessage.collectAsState()
    val valid = email.isNotBlank() && password.length >= 6 && (!isRegister || password == confirmPassword)

    Column(Modifier.fillMaxSize().background(Pr.bg).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(210.dp).background(Pr.cover), contentAlignment = Alignment.BottomStart) {
            Icon(Icons.Default.Navigation, null, tint = Color.White.copy(alpha = .05f), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp))
            Column(Modifier.padding(20.dp, 20.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.background(Pr.coral, RoundedCornerShape(14.dp)).padding(12.dp)) {
                        Icon(Icons.Default.Navigation, null, tint = Color.White)
                    }
                    Text("PACKRIDE", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.6.sp)
                }
                Text("Ride together.\nStay connected.", color = Color.White, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 24.dp).background(Color(0xFFF0ECE5), RoundedCornerShape(14.dp)).padding(4.dp)) {
            LoginModeButton("Log In", !isRegister, Modifier.weight(1f)) { isRegister = false; auth.clearError() }
            LoginModeButton("Sign Up", isRegister, Modifier.weight(1f)) { isRegister = true; auth.clearError() }
        }
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LoginField(email, { email = it; auth.clearError(); resetSent = false }, "Email address", KeyboardType.Email)
            LoginField(password, { password = it; auth.clearError() }, "Password", KeyboardType.Password, true)
            AnimatedVisibility(isRegister) {
                LoginField(confirmPassword, { confirmPassword = it; auth.clearError() }, "Confirm password", KeyboardType.Password, true)
            }
        }
        if (!isRegister) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showReset = true }) { Text("Forgot Password?", color = Pr.coral, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (error.isNotEmpty()) {
            Text(error, color = Color(0xFFD33B2C), fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).background(Color(0xFFFCEBE7), RoundedCornerShape(10.dp)).padding(12.dp))
        }
        if (isRegister && confirmPassword.isNotEmpty() && password != confirmPassword) {
            Text("Passwords do not match.", color = Color(0xFFD33B2C), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
        }
        Button(
            onClick = { if (isRegister) auth.register(email.trim(), password) else auth.login(email.trim(), password) },
            enabled = valid,
            colors = ButtonDefaults.buttonColors(containerColor = Pr.coral, disabledContainerColor = Pr.coral.copy(alpha = .4f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp).height(54.dp)
        ) { Text(if (isRegister) "Create Account" else "Log In", color = Color.White, fontWeight = FontWeight.Bold) }

        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(Pr.border))
            Text("OR", color = Pr.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
            Box(Modifier.weight(1f).height(1.dp).background(Pr.border))
        }
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SocialSignInButton("Continue with Apple", "●", Color.Black, Color.White) {
                context.findActivity()?.let(auth::signInWithApple) ?: auth.configurationError("Apple")
            }
            SocialSignInButton("Continue with Google", "G", Pr.ink, Pr.cardBg) {
                // google-services.json currently has no Android/web OAuth client.
                // Keep the parity control visible and fail explicitly until the
                // refreshed Firebase config supplies default_web_client_id.
                auth.configurationError("Google")
            }
            SocialSignInButton("Continue with Facebook", "f", Color.White, Color(0xFF17529C)) {
                // Requires the Android Meta App ID/client token; neither is in
                // this project yet, so do not start an SDK flow that cannot finish.
                auth.configurationError("Facebook")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 40.dp), horizontalArrangement = Arrangement.Center) {
            Text(if (isRegister) "Already have an account? " else "Don't have an account? ", color = Pr.muted, fontSize = 13.sp)
            Text(if (isRegister) "Log In" else "Sign Up", color = Pr.coral, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { isRegister = !isRegister; auth.clearError() })
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(if (resetSent) "Check your email" else "Reset password") },
            text = {
                if (resetSent) Text("A password-reset link was sent to $email.")
                else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter your PackRide account email.")
                    LoginField(email, { email = it }, "Email address", KeyboardType.Email)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (resetSent) showReset = false else auth.resetPassword(email.trim()) { resetSent = it }
                }, enabled = resetSent || email.isNotBlank()) { Text(if (resetSent) "Done" else "Send Link", color = Pr.coral) }
            },
            dismissButton = { if (!resetSent) TextButton(onClick = { showReset = false }) { Text("Cancel") } }
        )
    }
}

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SocialSignInButton(title: String, glyph: String, foreground: Color, background: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).background(background, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(glyph, color = foreground, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(10.dp))
        Text(title, color = foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LoginModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.background(if (selected) Pr.cardBg else Color.Transparent, RoundedCornerShape(11.dp)).clickable(onClick = onClick).padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Pr.ink else Pr.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun LoginField(value: String, onValueChange: (String) -> Unit, label: String, keyboardType: KeyboardType, secret: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label, color = Pr.muted) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth()
    )
}
