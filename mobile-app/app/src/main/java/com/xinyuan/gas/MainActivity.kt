package com.xinyuan.gas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.xinyuan.gas.ui.inspection.InspectionViewModel
import com.xinyuan.gas.ui.navigation.NavGraph
import com.xinyuan.gas.ui.theme.GasHazardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GasHazardTheme {
                val navController = rememberNavController()
                val viewModel: InspectionViewModel = viewModel()
                NavGraph(navController = navController, viewModel = viewModel)
            }
        }
    }
}
