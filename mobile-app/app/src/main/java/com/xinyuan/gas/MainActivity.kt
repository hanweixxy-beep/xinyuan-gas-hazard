package com.xinyuan.gas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xinyuan.gas.ui.cloud.CloudAppRoot
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.theme.GasHazardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GasHazardTheme {
                val viewModel: InspectionViewModel = viewModel()
                CloudAppRoot(viewModel = viewModel)
            }
        }
    }
}
