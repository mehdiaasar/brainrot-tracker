package com.example.brainrottracker

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.brainrottracker.theme.CardBackground
import com.example.brainrottracker.theme.DarkBackground
import com.example.brainrottracker.theme.PrimaryCyan
import com.example.brainrottracker.theme.TextSecondary
import com.example.brainrottracker.ui.screens.dashboard.DashboardScreen
import com.example.brainrottracker.ui.screens.limits.LimitsScreen
import com.example.brainrottracker.ui.screens.onboarding.OnboardingScreen
import com.example.brainrottracker.ui.screens.stats.StatsScreen
import com.example.brainrottracker.ui.screens.streaks.StreaksScreen

data class BottomNavItem(
    val key: NavKey,
    val label: String,
    val icon: ImageVector,
    val activeColor: Color = PrimaryCyan
)

@Composable
fun MainNavigation() {
    val context = LocalContext.current

    val accessibilityEnabled = remember {
        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabledServices.contains(context.packageName)
        } catch (_: Exception) { false }
    }

    val startDestination: NavKey = if (accessibilityEnabled) Dashboard else Onboarding
    val backStack = rememberNavBackStack(startDestination)
    var currentDestination by remember { mutableStateOf(startDestination) }

    val bottomNavItems = listOf(
        BottomNavItem(Dashboard, "Home", Icons.Filled.Psychology, PrimaryCyan),
        BottomNavItem(Stats, "Progress", Icons.Filled.BarChart, Color(0xFF7C3AED)),
        BottomNavItem(Limits, "Goals", Icons.Filled.Timer, Color(0xFFFF6B6B)),
        BottomNavItem(Streaks, "Profile", Icons.Filled.LocalFireDepartment, Color(0xFFFF9F43)),
    )

    val showBottomBar = currentDestination != Onboarding

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                // Floating pill-style bottom nav
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        CardBackground.copy(alpha = 0.95f),
                                        Color(0xFF12122A).copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .drawBehind {
                                // Subtle top highlight line (glass rim)
                                drawLine(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.15f),
                                            Color.Transparent
                                        )
                                    ),
                                    start = Offset(size.width * 0.1f, 1f),
                                    end = Offset(size.width * 0.9f, 1f),
                                    strokeWidth = 1.5f
                                )
                                // Bottom glow shadow
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            PrimaryCyan.copy(alpha = 0.04f)
                                        )
                                    )
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        bottomNavItems.forEach { item ->
                            val isSelected = currentDestination == item.key
                            FloatingNavItem(
                                item = item,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (currentDestination != item.key) {
                                        currentDestination = item.key
                                        while (backStack.size > 1) backStack.removeLastOrNull()
                                        backStack.removeLastOrNull()
                                        backStack += item.key
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<Onboarding> {
                        OnboardingScreen(
                            onComplete = {
                                currentDestination = Dashboard
                                while (backStack.size > 1) backStack.removeLastOrNull()
                                backStack.removeLastOrNull()
                                backStack += Dashboard
                            },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    entry<Dashboard> {
                        DashboardScreen(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    entry<Stats> {
                        StatsScreen(
                            modifier = Modifier.padding(16.dp),
                            onBackClick = {
                                currentDestination = Dashboard
                                while (backStack.size > 1) backStack.removeLastOrNull()
                                backStack.removeLastOrNull()
                                backStack += Dashboard
                            }
                        )
                    }
                    entry<Limits> {
                        LimitsScreen(modifier = Modifier.padding(16.dp))
                    }
                    entry<Streaks> {
                        StreaksScreen(modifier = Modifier.padding(16.dp))
                    }
                }
            )
        }
    }
}

@Composable
private fun FloatingNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "navItemScale"
    )

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected)
                    Brush.verticalGradient(
                        colors = listOf(
                            item.activeColor.copy(alpha = 0.18f),
                            item.activeColor.copy(alpha = 0.08f)
                        )
                    )
                else Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSelected) {
                // Glow behind icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        item.activeColor.copy(alpha = 0.35f),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.minDimension * 0.6f
                            )
                        }
                )
            }
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (isSelected) item.activeColor else TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.height(2.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 10.sp
            ),
            color = if (isSelected) item.activeColor else TextSecondary.copy(alpha = 0.5f)
        )
    }
}
