package com.example.brainrottracker

/**
 * Cloud sync (Google sign-in + Firestore backup) is hidden for the v1 Play release
 * because no Firebase project is configured (no app/google-services.json, placeholder
 * google_web_client_id). Flip this to true once Firebase is set up to re-expose the
 * sign-in flow and the Account card. The underlying sign-in/sync code is left intact.
 */
const val CLOUD_SYNC_ENABLED = false
