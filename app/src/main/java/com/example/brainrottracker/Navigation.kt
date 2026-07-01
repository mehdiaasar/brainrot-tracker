package com.example.brainrottracker

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.brainrottracker.theme.AppTheme
import com.example.brainrottracker.theme.Motion
import com.example.brainrottracker.ui.components.pressScale
import com.example.brainrottracker.data.local.prefs.AppPreferences
import com.example.brainrottracker.ui.permissions.PermissionBanner
import com.example.brainrottracker.ui.permissions.rememberMissingPermissions
import com.example.brainrottracker.ui.permissions.rememberPermissionRequester
import com.example.brainrottracker.ui.screens.dashboard.DashboardScreen
import com.example.brainrottracker.ui.screens.limits.LimitsScreen
import com.example.brainrottracker.ui.screens.onboarding.OnboardingScreen
import com.example.brainrottracker.ui.screens.permissions.PermissionFixScreen
import com.example.brainrottracker.ui.screens.signin.GoogleSignInScreen
import com.example.brainrottracker.ui.screens.stats.StatsScreen
import com.example.brainrottracker.ui.screens.streaks.StreaksScreen
import kotlinx.coroutines.flow.first

data class BottomNavItem(
    val key: NavKey,
    val label: String,
    val icon: ImageVector,
)

private data class InitState(val accessibilityEnabled: Boolean, val signedIn: Boolean)

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val navBg = colors.background
    val navBorder = colors.border
    val inactiveColor = colors.textSecondary

    // Read startup state asynchronously before building the nav back stack.
    // We only need the first emission — once we know where to start, we stop collecting.
    val initState by produceState<InitState?>(initialValue = null) {
        val accessibilityEnabled = try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabledServices.contains(context.packageName)
        } catch (_: Exception) { false }
        val signedIn = AppPreferences.isSignedInFlow(context).first()
        value = InitState(accessibilityEnabled, signedIn)
    }

    // Show a blank matching-color splash while DataStore emits the first value
    if (initState == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(navBg)
        )
        return
    }

    val startDestination: NavKey = if (initState!!.accessibilityEnabled) Dashboard else Onboarding

    val backStack = rememberNavBackStack(startDestination)
    var currentDestination by remember { mutableStateOf(startDestination) }

    val bottomNavItems = listOf(
        BottomNavItem(Dashboard, "Dashboard", Icons.Filled.Dashboard),
        BottomNavItem(Stats, "Stats", Icons.Filled.BarChart),
        BottomNavItem(Streaks, "Streaks", Icons.Filled.LocalFireDepartment),
    )

    // Switch to a top-level tab, collapsing any pushed sub-screens (e.g. Settings).
    val selectTab: (NavKey) -> Unit = { key ->
        if (currentDestination != key) {
            currentDestination = key
            while (backStack.size > 1) backStack.removeLastOrNull()
            backStack.removeLastOrNull()
            backStack += key
        }
    }
    // Settings is the relocated "Goals"/Limits screen, opened from the dashboard profile photo.
    val openSettings: () -> Unit = { if (backStack.lastOrNull() != Limits) backStack += Limits }
    // Back from Settings always returns to the Streaks & Progress tab, collapsing the
    // pushed Limits screen even when Streaks was already the active tab.
    val backFromSettings: () -> Unit = {
        currentDestination = Streaks
        while (backStack.size > 1) backStack.removeLastOrNull()
        backStack.removeLastOrNull()
        backStack += Streaks
    }

    // Detect missing permissions and surface a persistent banner across the main app.
    // Re-checks on every ON_RESUME so it clears the moment the user grants from settings.
    // Only *required* permissions nag — optional ones (e.g. notifications) are ignored here.
    val missing by rememberMissingPermissions()
    val requiredMissing = missing.filter { it.required }
    val requestPermission = rememberPermissionRequester()

    // Hide the bar on full-screen flows (onboarding, sign-in, permission fix)
    val topKey = backStack.lastOrNull()
    val showBottomBar = topKey != Onboarding && topKey != GoogleSignIn && topKey != PermissionFix
    // Show the banner everywhere except those same full-screen flows.
    val showBanner = requiredMissing.isNotEmpty() &&
        topKey != Onboarding && topKey != GoogleSignIn && topKey != PermissionFix

    Scaffold(
        containerColor = navBg,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = navBorder, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(navBg)
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bottomNavItems.forEach { item ->
                            val isSelected = currentDestination == item.key
                            val itemColor by animateColorAsState(
                                if (isSelected) colors.accent else inactiveColor,
                                Motion.colorSpec, label = "navItemColor"
                            )
                            val interaction = remember { MutableInteractionSource() }
                            Column(
                                modifier = Modifier
                                    .pressScale(interaction)
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = null
                                    ) { selectTab(item.key) }
                                    .padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    tint = itemColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    item.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = itemColor
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            if (showBanner) {
                PermissionBanner(
                    message = "LoopOut can't track reels without these permissions",
                    onAllow = {
                        // One missing → jump straight to it; several → open the fix screen.
                        if (requiredMissing.size == 1) requestPermission(requiredMissing.first())
                        else backStack += PermissionFix
                    }
                )
            }
            // The banner already draws under and clears the status bar, so when it's showing the
            // screens below must not re-apply the top inset (that double-count is what left the
            // empty gap). Keep only the bottom inset in that case.
            val contentPadding = if (showBanner) {
                PaddingValues(bottom = innerPadding.calculateBottomPadding())
            } else {
                innerPadding
            }
            NavDisplay(
                modifier = Modifier.weight(1f).padding(contentPadding),
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
                                // Offer the optional sign-in once after onboarding
                                // (hidden for v1 until Firebase is configured — see CLOUD_SYNC_ENABLED)
                                if (CLOUD_SYNC_ENABLED && initState?.signedIn == false) backStack += GoogleSignIn
                            },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    entry<GoogleSignIn> {
                        GoogleSignInScreen(
                            onSignInSuccess = { backStack.removeLastOrNull() },
                            onSkip = { backStack.removeLastOrNull() }
                        )
                    }
                    entry<Dashboard> {
                        DashboardScreen(
                            onOpenSettings = openSettings,
                            onViewStreaks = { selectTab(Streaks) },
                            onEditPlan = openSettings,
                        )
                    }
                    entry<Stats> {
                        StatsScreen()
                    }
                    // Settings slides in from the right when opened and slides back off to
                    // the right when returning to Streaks & Progress. Scoped to this entry so
                    // bottom-nav tab switches keep their default cross-fade.
                    entry<Limits>(
                        metadata = NavDisplay.transitionSpec {
                            (slideInHorizontally(tween(320)) { it } + fadeIn(tween(320))) togetherWith
                                (slideOutHorizontally(tween(320)) { -it / 5 } + fadeOut(tween(320)))
                        } + NavDisplay.popTransitionSpec {
                            (slideInHorizontally(tween(320)) { -it / 5 } + fadeIn(tween(320))) togetherWith
                                (slideOutHorizontally(tween(320)) { it } + fadeOut(tween(320)))
                        }
                    ) {
                        LimitsScreen(
                            onNavigateToSignIn = { backStack += GoogleSignIn },
                            onBack = backFromSettings
                        )
                    }
                    entry<Streaks> {
                        StreaksScreen(onOpenSettings = openSettings)
                    }
                    entry<PermissionFix> {
                        PermissionFixScreen(
                            missing = requiredMissing,
                            requestPermission = requestPermission,
                            onClose = { backStack.removeLastOrNull() },
                        )
                    }
                }
            )
        }
    }
}
