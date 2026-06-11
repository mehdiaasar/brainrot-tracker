package com.example.brainrottracker.ui.screens.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.brainrottracker.R
import com.example.brainrottracker.theme.WarmAccent
import com.example.brainrottracker.theme.WarmBackground
import com.example.brainrottracker.theme.WarmBorder
import com.example.brainrottracker.theme.WarmError
import com.example.brainrottracker.theme.WarmLightBackground
import com.example.brainrottracker.theme.WarmLightBorder
import com.example.brainrottracker.theme.WarmLightText
import com.example.brainrottracker.theme.WarmLightTextSecondary
import com.example.brainrottracker.theme.WarmSurface
import com.example.brainrottracker.theme.WarmText
import com.example.brainrottracker.theme.WarmTextSecondary
import com.example.brainrottracker.theme.rememberIsDark

@Composable
fun GoogleSignInScreen(
    onSignInSuccess: () -> Unit,
    onSkip: (() -> Unit)? = null,
    viewModel: GoogleSignInViewModel = viewModel()
) {
    val context = LocalContext.current
    val webClientId = stringResource(R.string.google_web_client_id)
    val dark = rememberIsDark()

    val bg = if (dark) WarmBackground else WarmLightBackground
    val textPrimary = if (dark) WarmText else WarmLightText
    val textSecondary = if (dark) WarmTextSecondary else WarmLightTextSecondary
    val surface = if (dark) WarmSurface else Color(0xFFEFE9DE)
    val border = if (dark) WarmBorder else WarmLightBorder

    val state by viewModel.state.collectAsState()
    var showSyncConsent by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (state) {
            // Cloud upload of usage data needs explicit consent (Play accessibility policy)
            is SignInState.Success -> showSyncConsent = true
            is SignInState.Cancelled -> viewModel.resetError()
            else -> Unit
        }
    }

    if (showSyncConsent) {
        val prefs = remember {
            context.getSharedPreferences("brainrot_prefs", android.content.Context.MODE_PRIVATE)
        }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Back up your stats?") },
            text = {
                Text(
                    "Upload your daily usage totals (video counts, screen time, streaks) to " +
                        "your private account for backup and cross-device access?\n\n" +
                        "Only daily totals are uploaded — never screen content. You can turn " +
                        "this off anytime in Settings."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("cloud_sync_enabled", true).apply()
                    showSyncConsent = false
                    onSignInSuccess()
                }) { Text("Enable backup") }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("cloud_sync_enabled", false).apply()
                    showSyncConsent = false
                    onSignInSuccess()
                }) { Text("Not now") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 32.dp)
            .padding(top = 96.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Logo
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✳", fontSize = 22.sp, color = textPrimary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "BrainRot Tracker",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                letterSpacing = (-0.3).sp,
                color = textPrimary
            )
        }

        Spacer(Modifier.height(48.dp))

        Text(
            text = "Sign In",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 36.sp,
            letterSpacing = (-0.5).sp,
            color = textPrimary
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Track your scroll habits and protect your focus.",
            fontSize = 15.sp,
            color = textSecondary,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(48.dp))

        when (state) {
            is SignInState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = WarmAccent,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            }
            else -> {
                Button(
                    onClick = { viewModel.signIn(context, webClientId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, border, RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = surface,
                        contentColor = textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "G",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4285F4)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary
                    )
                }
            }
        }

        if (state is SignInState.Error) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = (state as SignInState.Error).message,
                fontSize = 13.sp,
                color = WarmError
            )
        }

        if (onSkip != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Skip for now",
                    color = textSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}
