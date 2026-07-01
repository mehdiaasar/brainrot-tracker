package com.example.brainrottracker

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object GoogleSignIn : NavKey
@Serializable data object Onboarding : NavKey
@Serializable data object Dashboard : NavKey
@Serializable data object Stats : NavKey
@Serializable data object Limits : NavKey
@Serializable data object Streaks : NavKey
@Serializable data object PermissionFix : NavKey
