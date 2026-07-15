package com.worstalarm.clock.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.worstalarm.clock.ui.about.AboutScreen
import com.worstalarm.clock.ui.alarmedit.AlarmEditScreen
import com.worstalarm.clock.ui.alarmlist.AlarmListScreen
import com.worstalarm.clock.ui.barcodes.BarcodeLibraryScreen
import com.worstalarm.clock.ui.qr.QrGeneratorScreen
import com.worstalarm.clock.ui.settings.SettingsScreen

object Routes {
    const val AlarmList = "alarms"
    const val BarcodeLibrary = "barcodes"
    const val AlarmEdit = "alarms/edit"  // ?id=Long (0 == new)
    const val Settings = "settings"
    const val About = "about"
    const val QrGenerator = "qr-generator"
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
                onOpenQrGenerator = { nav.navigate(Routes.QrGenerator) },
                onOpenSettings = { nav.navigate(Routes.Settings) },
                onOpenAbout = { nav.navigate(Routes.About) },
                onRequestOverlayPermission = onRequestOverlayPermission
            )
        }
        composable(Routes.BarcodeLibrary) {
            BarcodeLibraryScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.Settings) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.About) {
            AboutScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.QrGenerator) {
            QrGeneratorScreen(onBack = { nav.popBackStack() })
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
