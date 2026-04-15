package com.worstalarm.clock.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.worstalarm.clock.ui.alarmedit.AlarmEditScreen
import com.worstalarm.clock.ui.alarmlist.AlarmListScreen
import com.worstalarm.clock.ui.barcodes.BarcodeLibraryScreen

object Routes {
    const val AlarmList = "alarms"
    const val BarcodeLibrary = "barcodes"
    const val AlarmEdit = "alarms/edit"  // ?id=Long (0 == new)
    fun alarmEdit(id: Long) = "$AlarmEdit?id=$id"
}

@Composable
fun Navigation(onRequestOverlayPermission: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.AlarmList) {
        composable(Routes.AlarmList) {
            AlarmListScreen(
                onAdd = { nav.navigate(Routes.alarmEdit(0)) },
                onEdit = { id -> nav.navigate(Routes.alarmEdit(id)) },
                onManageBarcodes = { nav.navigate(Routes.BarcodeLibrary) },
                onRequestOverlayPermission = onRequestOverlayPermission
            )
        }
        composable(Routes.BarcodeLibrary) {
            BarcodeLibraryScreen(onBack = { nav.popBackStack() })
        }
        composable("${Routes.AlarmEdit}?id={id}") { backStack ->
            val idStr = backStack.arguments?.getString("id") ?: "0"
            val id = idStr.toLongOrNull() ?: 0L
            AlarmEditScreen(
                alarmId = id,
                onDone = { nav.popBackStack() }
            )
        }
    }
}
