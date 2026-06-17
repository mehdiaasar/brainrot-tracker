package com.example.brainrottracker.ui.screens.limits

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.brainrottracker.R
import com.example.brainrottracker.data.local.prefs.AppPreferences
import com.example.brainrottracker.service.BlockingMode
import com.example.brainrottracker.theme.ThemeController
import com.example.brainrottracker.theme.WarmBackground
import com.example.brainrottracker.theme.WarmBorder
import com.example.brainrottracker.theme.WarmLightBackground
import com.example.brainrottracker.theme.WarmLightBorder
import com.example.brainrottracker.theme.WarmLightSurface
import com.example.brainrottracker.theme.WarmLightText
import com.example.brainrottracker.theme.WarmLightTextSecondary
import com.example.brainrottracker.theme.WarmStepDim
import com.example.brainrottracker.theme.WarmSurface
import com.example.brainrottracker.theme.WarmText
import com.example.brainrottracker.theme.WarmTextSecondary
import com.example.brainrottracker.theme.rememberIsDark

// Screen-local accents tuned to the Daily Limits mock.
private val SetOrange = Color(0xFFF26B21)
private val SetPurple = Color(0xFF8B5CF6)
private val SetBlue = Color(0xFF57A6D4)
private val SetGreen = Color(0xFF46A86B)

@Composable
fun LimitsScreen(
    modifier: Modifier = Modifier,
    onNavigateToSignIn: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: LimitsViewModel = viewModel()
) {
    val dark = rememberIsDark()
    val bg = if (dark) WarmBackground else WarmLightBackground
    val surface = if (dark) WarmSurface else WarmLightSurface
    val cardBorder = if (dark) WarmBorder else WarmLightBorder
    val trackInactive = if (dark) WarmStepDim else Color(0xFFE7E2DA)
    val pillUnselectedBg = if (dark) WarmStepDim else Color(0xFFF1EFEB)
    val textPrimary = if (dark) WarmText else WarmLightText
    val textSecondary = if (dark) WarmTextSecondary else WarmLightTextSecondary
    val videoCardBg = if (dark) Color(0xFF272320) else Color(0xFFFDF6F0)
    val purpleSoftBg = if (dark) Color(0xFF272233) else Color(0xFFF2ECFD)
    val balanceCardBg = if (dark) Color(0xFF1F261E) else Color(0xFFEDF4EC)
    val themeSelectedBg = if (dark) Color(0xFF34302B) else Color.White

    val limits by viewModel.limits.collectAsState()
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("brainrot_prefs", Context.MODE_PRIVATE) }

    val initialReelLimit = limits.firstOrNull()?.dailyReelLimit?.toFloat() ?: 50f
    val initialMinuteLimit = limits.firstOrNull()?.dailyMinuteLimit?.toFloat() ?: 60f

    var reelLimit by remember(initialReelLimit) { mutableFloatStateOf(initialReelLimit) }
    var minuteLimit by remember(initialMinuteLimit) { mutableFloatStateOf(initialMinuteLimit) }
    var blockingEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("blocking_enabled", false)) }
    var blockingMode by remember {
        mutableStateOf(BlockingMode.fromPref(sharedPrefs.getString(BlockingMode.PREF_KEY, null)))
    }
    var hudScale by remember { mutableFloatStateOf(sharedPrefs.getFloat("hud_scale", 1.2f)) }

    val signedInUser by remember { AppPreferences.userFlow(context) }
        .collectAsState(initial = null)

    val sliderColors = SliderDefaults.colors(
        thumbColor = SetOrange,
        activeTrackColor = SetOrange,
        inactiveTrackColor = trackInactive,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {

            // Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 32.dp, bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(surface)
                            .border(1.dp, cardBorder, CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Streaks & Progress",
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Settings",
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontSize = 36.sp,
                        lineHeight = 41.sp,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Set healthy boundaries. Protect your focus. 🛡️",
                        color = textSecondary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
            }

            // Account: signed-in users get a centered profile header (avatar + name + email);
            // signed-out users keep the "Sign in with Google" card.
            item {
                val user = signedInUser
                if (user != null) {
                    val initial = user.name.ifEmpty { user.email }
                        .firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(SetOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                initial,
                                color = Color.White,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                user.name.ifEmpty { user.email.substringBefore("@") },
                                color = textPrimary,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 30.sp,
                                textAlign = TextAlign.Center
                            )
                            if (user.email.isNotEmpty()) {
                                Text(
                                    user.email,
                                    color = textSecondary,
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(surface)
                            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                    ) {
                        // Brain is drawn first (behind) so the button paints over its feet —
                        // it reads as sitting on the button rather than floating over it.
                        Image(
                            painterResource(R.drawable.setting_google),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 63.dp, end = 14.dp)
                                .width(90.dp)
                                .height(63.dp)
                        )
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 96.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = SetGreen,
                                    modifier = Modifier.size(26.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Account",
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 18.sp,
                                        lineHeight = 22.sp
                                    )
                                    Text(
                                        "Sign in to back up your stats and settings",
                                        color = textSecondary,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SetOrange)
                                    .clickable { onNavigateToSignIn() }
                                    .padding(vertical = 13.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "G",
                                        color = SetOrange,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Sign in with Google",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Daily Video Limit card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(videoCardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SetOrange.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.TrackChanges,
                                    contentDescription = null,
                                    tint = SetOrange,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    "Daily Video Limit",
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 18.sp,
                                    lineHeight = 22.sp
                                )
                                Text(
                                    "Applies to all platforms",
                                    color = textSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        LimitSliderRow(
                            label = "Videos per day",
                            value = reelLimit,
                            valueUnit = "videos",
                            recommended = "Recommended: 30",
                            onValueChange = { reelLimit = it },
                            onValueChangeFinished = {
                                viewModel.updateGlobalLimits(reelLimit.toInt(), minuteLimit.toInt())
                            },
                            valueRange = 0f..200f,
                            sliderColors = sliderColors,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )

                        LimitSliderRow(
                            label = "Time per day",
                            value = minuteLimit,
                            valueUnit = "min",
                            recommended = "Recommended: 45 min",
                            onValueChange = { minuteLimit = it },
                            onValueChangeFinished = {
                                viewModel.updateGlobalLimits(reelLimit.toInt(), minuteLimit.toInt())
                            },
                            valueRange = 0f..240f,
                            sliderColors = sliderColors,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }
                }
            }

            // App Blocking card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(surface)
                        .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = SetPurple,
                                    modifier = Modifier.size(26.dp)
                                )
                                Column {
                                    Text(
                                        "App Blocking",
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 18.sp,
                                        lineHeight = 22.sp
                                    )
                                    Text(
                                        "Show overlay when daily limit is reached",
                                        color = textSecondary,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                            WarmToggle(
                                checked = blockingEnabled,
                                onCheckedChange = {
                                    blockingEnabled = it
                                    sharedPrefs.edit().putBoolean("blocking_enabled", it).apply()
                                },
                                offColor = cardBorder
                            )
                        }
                        if (blockingEnabled) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PillToggleButton(
                                        icon = Icons.Outlined.Lock,
                                        label = "Lock Hard",
                                        selected = blockingMode == BlockingMode.HARD,
                                        accent = SetPurple,
                                        selectedBg = purpleSoftBg,
                                        unselectedBg = pillUnselectedBg,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        onClick = {
                                            blockingMode = BlockingMode.HARD
                                            sharedPrefs.edit().putString(BlockingMode.PREF_KEY, BlockingMode.HARD.name).apply()
                                        }
                                    )
                                    PillToggleButton(
                                        icon = Icons.Outlined.HourglassEmpty,
                                        label = "Snooze",
                                        selected = blockingMode == BlockingMode.SNOOZE,
                                        accent = SetPurple,
                                        selectedBg = purpleSoftBg,
                                        unselectedBg = pillUnselectedBg,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        onClick = {
                                            blockingMode = BlockingMode.SNOOZE
                                            sharedPrefs.edit().putString(BlockingMode.PREF_KEY, BlockingMode.SNOOZE.name).apply()
                                        }
                                    )
                                    PillToggleButton(
                                        icon = Icons.Outlined.NotificationsNone,
                                        label = "Remind Me",
                                        selected = blockingMode == BlockingMode.REMIND,
                                        accent = SetPurple,
                                        selectedBg = purpleSoftBg,
                                        unselectedBg = pillUnselectedBg,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        onClick = {
                                            blockingMode = BlockingMode.REMIND
                                            sharedPrefs.edit().putString(BlockingMode.PREF_KEY, BlockingMode.REMIND.name).apply()
                                        }
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 76.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(purpleSoftBg)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = SetPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        when (blockingMode) {
                                            BlockingMode.HARD -> "Blocks the app until midnight — the only way out is closing it."
                                            BlockingMode.SNOOZE -> "Dismissing gives you 5 more minutes, then the block returns."
                                            BlockingMode.REMIND -> "You'll be reminded each time you open the app."
                                        },
                                        color = textPrimary,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                    if (blockingEnabled) {
                        Image(
                            painterResource(R.drawable.setting_appblocking),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 6.dp, bottom = 6.dp)
                                .size(108.dp)
                        )
                    }
                }
            }

            // Appearance card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(surface)
                        .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 104.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.Palette,
                                contentDescription = null,
                                tint = SetBlue,
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    "Appearance",
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 18.sp,
                                    lineHeight = 22.sp
                                )
                                Text(
                                    "Choose your theme",
                                    color = textSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PillToggleButton(
                                icon = Icons.Outlined.WbSunny,
                                label = "Light",
                                selected = ThemeController.mode == ThemeController.Mode.LIGHT,
                                accent = SetOrange,
                                selectedBg = themeSelectedBg,
                                unselectedBg = pillUnselectedBg,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                modifier = Modifier.weight(1f),
                                onClick = { ThemeController.selectMode(ThemeController.Mode.LIGHT) }
                            )
                            PillToggleButton(
                                icon = Icons.Outlined.DarkMode,
                                label = "Dark",
                                selected = ThemeController.mode == ThemeController.Mode.DARK,
                                accent = SetOrange,
                                selectedBg = themeSelectedBg,
                                unselectedBg = pillUnselectedBg,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                modifier = Modifier.weight(1f),
                                onClick = { ThemeController.selectMode(ThemeController.Mode.DARK) }
                            )
                            PillToggleButton(
                                icon = Icons.Outlined.PhoneAndroid,
                                label = "System",
                                selected = ThemeController.mode == ThemeController.Mode.SYSTEM,
                                accent = SetOrange,
                                selectedBg = themeSelectedBg,
                                unselectedBg = pillUnselectedBg,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                modifier = Modifier.weight(1f),
                                onClick = { ThemeController.selectMode(ThemeController.Mode.SYSTEM) }
                            )
                        }
                    }
                    Image(
                        painterResource(R.drawable.setting_appearance),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 10.dp, y = (-14).dp)
                            .size(132.dp)
                    )
                }
            }

            // Floating counter (HUD) size
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(surface)
                        .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Straighten,
                                contentDescription = null,
                                tint = SetOrange,
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    "Floating Counter",
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 18.sp,
                                    lineHeight = 22.sp
                                )
                                Text(
                                    "Adjust the on-screen counter size",
                                    color = textSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Size", color = textSecondary, fontSize = 14.sp)
                                Text(
                                    "${(hudScale * 100).toInt()}%",
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary,
                                    fontSize = 13.sp
                                )
                            }
                            Slider(
                                value = hudScale,
                                onValueChange = { hudScale = it },
                                onValueChangeFinished = {
                                    sharedPrefs.edit().putFloat("hud_scale", hudScale).apply()
                                },
                                valueRange = 0.8f..1.8f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = sliderColors
                            )
                        }
                    }
                }
            }

            // Balance footer card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                        .height(124.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(balanceCardBg)
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 140.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Eco,
                            contentDescription = null,
                            tint = SetGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Balance today, better tomorrow!",
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 21.sp
                            )
                            Text(
                                "Small limits create big freedom. 🌱",
                                color = SetGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 19.sp
                            )
                        }
                    }
                    Image(
                        painterResource(R.drawable.setting_balance),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 12.dp)
                            .width(136.dp)
                            .height(98.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LimitSliderRow(
    label: String,
    value: Float,
    valueUnit: String,
    recommended: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    sliderColors: androidx.compose.material3.SliderColors,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                modifier = Modifier.fillMaxWidth(),
                colors = sliderColors
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${value.toInt()}",
                    color = SetOrange,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    " $valueUnit",
                    color = SetOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Text(recommended, color = textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PillToggleButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    selectedBg: Color,
    unselectedBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) selectedBg else unselectedBg)
            .then(
                if (selected) Modifier.border(1.5.dp, accent, shape) else Modifier
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) accent else textSecondary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            color = if (selected) textPrimary else textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun WarmToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    offColor: Color = WarmLightBorder
) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) SetOrange else offColor)
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
