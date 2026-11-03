package com.xinyuan.gas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.xinyuan.gas.reminder.RectificationReminder
import com.xinyuan.gas.ui.cloud.CloudAppRoot
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.theme.GasHazardTheme

class MainActivity : ComponentActivity() {
    private var openPendingRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPendingRequest = savedInstanceState?.getInt("pendingRequest") ?: 0
        if (savedInstanceState == null) consumeReminderIntent(intent)
        enableEdgeToEdge()
        setContent {
            GasHazardTheme {
                val viewModel: InspectionViewModel = viewModel()
                CloudAppRoot(viewModel = viewModel, openPendingRequest = openPendingRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeReminderIntent(intent)
    }

    private fun consumeReminderIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(RectificationReminder.OPEN_PENDING, false) == true) {
            openPendingRequest++
            intent.removeExtra(RectificationReminder.OPEN_PENDING)
        }
    }

    override fun onResume() {
        super.onResume()
        RectificationReminder.ensureScheduled(this, checkNow = true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("pendingRequest", openPendingRequest)
        super.onSaveInstanceState(outState)
    }
}
