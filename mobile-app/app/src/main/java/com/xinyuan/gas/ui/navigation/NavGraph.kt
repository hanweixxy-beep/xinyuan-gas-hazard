package com.xinyuan.gas.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.xinyuan.gas.ui.about.AppGuideScreen
import com.xinyuan.gas.ui.export.ExportScreen
import com.xinyuan.gas.ui.data.DataManagementScreen
import com.xinyuan.gas.ui.home.BottledInspectionHomeScreen
import com.xinyuan.gas.ui.home.HomeScreen
import com.xinyuan.gas.ui.home.TerminalHazardHomeScreen
import com.xinyuan.gas.ui.inspection.*
import com.xinyuan.gas.ui.terminal.TerminalHazardDetailScreen
import com.xinyuan.gas.ui.terminal.TerminalHazardExportScreen
import com.xinyuan.gas.ui.terminal.TerminalHazardFormScreen
import com.xinyuan.gas.ui.terminal.TerminalHazardListScreen
import com.xinyuan.gas.ui.terminal.TerminalHazardRectificationDetailScreen
import com.xinyuan.gas.ui.terminal.TerminalHazardRectificationListScreen

object Routes {
    const val HOME = "home"
    const val BOTTLED_HOME = "bottled_home"
    const val TERMINAL_HOME = "terminal_home"
    const val NEW_INSPECTION = "new_inspection"
    const val EDIT_INSPECTION = "edit_inspection/{recordId}"
    const val INSPECTION_LIST = "inspection_list"
    const val INSPECTION_DETAIL = "inspection_detail/{recordId}"
    const val EXPORT = "export"
    const val TERMINAL_HAZARD_LIST = "terminal_hazard_list"
    const val TERMINAL_HAZARD_DETAIL = "terminal_hazard_detail/{recordId}"
    const val TERMINAL_EXPORT = "terminal_export"
    const val TERMINAL_RECTIFICATION_LIST = "terminal_rectification_list"
    const val TERMINAL_RECTIFICATION_DETAIL = "terminal_rectification_detail/{recordId}"
    const val NEW_TERMINAL_HAZARD = "new_terminal_hazard"
    const val EDIT_TERMINAL_HAZARD = "edit_terminal_hazard/{recordId}"
    const val DATA_MANAGEMENT = "data_management"
    const val APP_GUIDE = "app_guide"

    fun editInspection(id: Long) = "edit_inspection/$id"
    fun inspectionDetail(id: Long) = "inspection_detail/$id"
    fun editTerminalHazard(id: Long) = "edit_terminal_hazard/$id"
    fun terminalHazardDetail(id: Long) = "terminal_hazard_detail/$id"
    fun terminalRectificationDetail(id: Long) = "terminal_rectification_detail/$id"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: InspectionViewModel
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToBottledModule = { navController.navigate(Routes.BOTTLED_HOME) },
                onNavigateToTerminalModule = { navController.navigate(Routes.TERMINAL_HOME) },
                onNavigateToNewTerminal = { navController.navigate(Routes.NEW_TERMINAL_HAZARD) },
                onNavigateToTerminalList = { navController.navigate(Routes.TERMINAL_HAZARD_LIST) },
                onNavigateToDataManagement = { navController.navigate(Routes.DATA_MANAGEMENT) },
                onNavigateToAppGuide = { navController.navigate(Routes.APP_GUIDE) }
            )
        }

        composable(Routes.APP_GUIDE) {
            AppGuideScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.DATA_MANAGEMENT) {
            DataManagementScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BOTTLED_HOME) {
            BottledInspectionHomeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToNew = { navController.navigate(Routes.NEW_INSPECTION) },
                onNavigateToList = { navController.navigate(Routes.INSPECTION_LIST) },
                onNavigateToExport = { navController.navigate(Routes.EXPORT) }
            )
        }

        composable(Routes.TERMINAL_HOME) {
            TerminalHazardHomeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToNew = { navController.navigate(Routes.NEW_TERMINAL_HAZARD) },
                onNavigateToList = { navController.navigate(Routes.TERMINAL_HAZARD_LIST) },
                onNavigateToRectification = { navController.navigate(Routes.TERMINAL_RECTIFICATION_LIST) },
                onNavigateToExport = { navController.navigate(Routes.TERMINAL_EXPORT) }
            )
        }

        composable(Routes.NEW_INSPECTION) {
            NewInspectionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.EDIT_INSPECTION,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
            NewInspectionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                editRecordId = recordId
            )
        }

        composable(Routes.INSPECTION_LIST) {
            InspectionListScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onRecordClick = { id ->
                    navController.navigate(Routes.inspectionDetail(id))
                }
            )
        }

        composable(
            Routes.INSPECTION_DETAIL,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
            InspectionDetailScreen(
                viewModel = viewModel,
                recordId = recordId,
                onBack = { navController.popBackStack() },
                onEdit = { id ->
                    navController.navigate(Routes.editInspection(id))
                }
            )
        }

        composable(Routes.EXPORT) {
            ExportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TERMINAL_HAZARD_LIST) {
            TerminalHazardListScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenRecord = { id -> navController.navigate(Routes.terminalHazardDetail(id)) },
                onExport = { navController.navigate(Routes.TERMINAL_EXPORT) }
            )
        }

        composable(
            Routes.TERMINAL_HAZARD_DETAIL,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
            TerminalHazardDetailScreen(
                viewModel = viewModel,
                recordId = recordId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TERMINAL_EXPORT) {
            TerminalHazardExportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TERMINAL_RECTIFICATION_LIST) {
            TerminalHazardRectificationListScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenRecord = { id -> navController.navigate(Routes.terminalRectificationDetail(id)) }
            )
        }

        composable(
            Routes.TERMINAL_RECTIFICATION_DETAIL,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
            TerminalHazardRectificationDetailScreen(
                viewModel = viewModel,
                recordId = recordId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.NEW_TERMINAL_HAZARD) {
            TerminalHazardFormScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.EDIT_TERMINAL_HAZARD,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
            TerminalHazardFormScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                editRecordId = recordId
            )
        }
    }
}
