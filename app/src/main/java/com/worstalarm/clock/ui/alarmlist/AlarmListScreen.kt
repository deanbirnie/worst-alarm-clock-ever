package com.worstalarm.clock.ui.alarmlist

import android.app.NotificationManager
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worstalarm.clock.data.dao.AlarmWithSteps
import com.worstalarm.clock.ui.AppViewModel
import com.worstalarm.clock.ui.components.DaySummaryFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onManageBarcodes: () -> Unit,
    onOpenQrGenerator: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestFullScreenIntentPermission: () -> Unit,
    vm: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val alarms by vm.alarms().collectAsStateWithLifecycle(initialValue = emptyList())
    // Both permissions are re-checked on every ON_RESUME so the banners disappear when the
    // user returns from the relevant settings page after granting.
    var canDrawOverlays by remember { mutableStateOf(SystemSettings.canDrawOverlays(context)) }
    var canUseFullScreenIntent by remember { mutableStateOf(deviceCanUseFullScreenIntent(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = SystemSettings.canDrawOverlays(context)
                canUseFullScreenIntent = deviceCanUseFullScreenIntent(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // First-launch welcome. Initial value `true` (= already seen) avoids a one-frame
    // flash of the dialog while DataStore loads. If the user unchecks "do not show
    // again", nothing is persisted — the dialog just stays away for this session and
    // returns on the next launch.
    val introSeen by vm.introSeen.collectAsStateWithLifecycle(initialValue = true)
    var introDismissedThisSession by remember { mutableStateOf(false) }
    if (!introSeen && !introDismissedThisSession) {
        IntroDialog(onDone = { dontShowAgain ->
            introDismissedThisSession = true
            if (dontShowAgain) vm.setIntroSeen(true)
        })
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    fun closeDrawerThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Worst Alarm Clock Ever",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Barcode library") },
                    icon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                    selected = false,
                    onClick = { closeDrawerThen(onManageBarcodes) }
                )
                NavigationDrawerItem(
                    label = { Text("QR generator") },
                    icon = { Icon(Icons.Default.QrCode2, contentDescription = null) },
                    selected = false,
                    onClick = { closeDrawerThen(onOpenQrGenerator) }
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    selected = false,
                    onClick = { closeDrawerThen(onOpenSettings) }
                )
                NavigationDrawerItem(
                    label = { Text("About") },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    selected = false,
                    onClick = { closeDrawerThen(onOpenAbout) }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Worst Alarm Clock Ever") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = onManageBarcodes) {
                            Icon(Icons.Default.QrCode, contentDescription = "Barcodes")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "Add alarm")
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                if (!canUseFullScreenIntent) {
                    FullScreenIntentPermissionCard(onRequest = onRequestFullScreenIntentPermission)
                    Spacer(Modifier.height(12.dp))
                }
                if (!canDrawOverlays) {
                    OverlayPermissionCard(onRequest = onRequestOverlayPermission)
                    Spacer(Modifier.height(12.dp))
                }
                if (alarms.isEmpty()) {
                    // Friendly sunrise empty state instead of a bare caption.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("☀️", fontSize = 44.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("No alarms yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap + to build your first wake-up routine.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(items = alarms, key = { it.alarm.id }) { a ->
                            AlarmRow(
                                alarm = a,
                                onClick = { onEdit(a.alarm.id) },
                                onToggle = { on -> vm.setEnabled(a.alarm, on) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroDialog(onDone: (dontShowAgain: Boolean) -> Unit) {
    var dontShowAgain by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = { onDone(dontShowAgain) },
        confirmButton = {
            Button(onClick = { onDone(dontShowAgain) }) { Text("Got it") }
        },
        title = { Text("Welcome to the worst alarm clock ever") },
        text = {
            Column {
                Text(
                    "This alarm has no snooze and no stop button. The only way to " +
                        "silence it is to physically walk to a barcode or QR code you've " +
                        "registered and scan it with the camera.\n\n" +
                        "The real magic is the path: give one alarm several locations — " +
                        "bathroom, kitchen, desk — and it re-rings at each one after a " +
                        "quiet break, walking you through your whole morning routine. " +
                        "Each location only counts once it rings, so you can't dash round " +
                        "and scan them all at once.\n\n" +
                        "Start in the menu: add barcodes to your library (or generate " +
                        "printable QR codes), then create an alarm."
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Text("Do not show this again")
                }
            }
        }
    )
}

@Composable
private fun OverlayPermissionCard(onRequest: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Grant 'Display over other apps'", fontWeight = FontWeight.Bold)
            Text(
                "Needed so the alarm re-asserts itself if you navigate away while it's ringing.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRequest) { Text("Open settings") }
        }
    }
}

@Composable
private fun FullScreenIntentPermissionCard(onRequest: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Allow full-screen alarms", fontWeight = FontWeight.Bold)
            Text(
                "Without this, the alarm can ring while the screen stays dark and you'd have to " +
                    "open the app by hand. Turn on \"full-screen notifications\" so the alarm " +
                    "shows over the lock screen.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRequest) { Text("Open settings") }
        }
    }
}

/**
 * Whether the app may launch its full-screen alarm UI from the background. Below Android 14
 * `USE_FULL_SCREEN_INTENT` is auto-granted (always true here); on 14+ it can be revoked/ungranted,
 * so ask the NotificationManager and, if it says no, surface [FullScreenIntentPermissionCard].
 */
private fun deviceCanUseFullScreenIntent(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    val nm = context.getSystemService(NotificationManager::class.java)
    return nm?.canUseFullScreenIntent() ?: true
}

/**
 * v0.4 redesign: the whole card is tappable (no separate "Edit" button), the
 * time anchors the card at display size, and a single quiet summary line
 * ("Weekdays · 3 locations") replaces the old stack of captions. Disabled
 * alarms dim their content so the list reads at a glance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmRow(
    alarm: AlarmWithSteps,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val enabled = alarm.alarm.enabled
    val contentAlpha = if (enabled) 1f else 0.45f
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "%02d:%02d".format(alarm.alarm.hour, alarm.alarm.minute),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                if (alarm.alarm.label.isNotBlank()) {
                    Text(
                        alarm.alarm.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
                    )
                }
                Spacer(Modifier.height(4.dp))
                val locations = alarm.orderedSteps.size
                Text(
                    DaySummaryFormatter.format(alarm.alarm.daysMask) +
                        " · $locations location" + (if (locations == 1) "" else "s"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}
