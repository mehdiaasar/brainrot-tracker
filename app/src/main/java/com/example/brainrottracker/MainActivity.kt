package com.example.brainrottracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.notification.NotificationHelper
import com.example.brainrottracker.theme.BrainRotTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database singleton
        AppDatabase.getInstance(applicationContext)

        // Create notification channels
        NotificationHelper(applicationContext).createChannels()

        enableEdgeToEdge()
        setContent {
            BrainRotTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }
}
