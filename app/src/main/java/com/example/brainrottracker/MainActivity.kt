package com.example.brainrottracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.repository.UsageRepository
import com.example.brainrottracker.data.sync.UsageSyncManager
import com.example.brainrottracker.notification.NotificationHelper
import com.example.brainrottracker.theme.BrainRotTrackerTheme
import com.example.brainrottracker.theme.ThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database singleton
        val db = AppDatabase.getInstance(applicationContext)

        // Catch up streak evaluation in case the tracking service wasn't running at midnight,
        // then push any unsynced days to the cloud (no-op unless signed in with backup on)
        lifecycleScope.launch(Dispatchers.IO) {
            val repository = UsageRepository(db)
            repository.evaluateStreaksUpTo(LocalDate.now().minusDays(1))
            try {
                UsageSyncManager(applicationContext, repository).syncIfEnabled()
            } catch (_: Exception) {
            }
        }

        // Load saved theme preference
        ThemeController.init(applicationContext)

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
