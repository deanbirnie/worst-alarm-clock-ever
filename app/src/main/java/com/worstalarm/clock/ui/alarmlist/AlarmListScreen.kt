package com.worstalarm.clock.ui.alarmlist

import android.content.Context
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worstalarm.clock.data.dao.AlarmWithSteps
import com.worstalarm.clock.ui.AppViewModel
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
    vm: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val alarms by vm.alarms().collectAsStateWithLifecycle(initialValue = emptyList())
    // Re-checked on every ON_RESUME so the banner disappears when the user returns from
    // the "Display over other apps" settings page after granting.
    var canDrawOverlays by remember { mutableStateOf(SystemSettings.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = SystemSettings.canDrawOverlays(context)
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
                if (!canDrawOverlays) {
                    OverlayPermissionCard(onRequest = onRequestOverlayPermission)
                    Spacer(Modifier.height(12.dp))
                }
                if (alarms.isEmpty()) {
                    Text(
                        "No alarms yet. Tap + to set your first routine.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(items = alarms, key = { it.alarm.id }) { a ->
                            AlarmRow(
                                alarm = a,
                                context = context,
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
                        "quiet break, walking you through your whole morning routine.\n\n" +
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
private fun AlarmRow(
    alarm: AlarmWithSteps,
    context: Context,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "%02d:%02d".format(alarm.alarm.hour, alarm.alarm.minute),
                    style = MaterialTheme.typography.headlineMedium
                )
                if (alarm.alarm.label.isNotBlank()) Text(alarm.alarm.label)
                Text(
                    formatDays(alarm.alarm.daysMask),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${alarm.orderedSteps.size} location" +
                        if (alarm.orderedSteps.size == 1) "" else "s",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = alarm.alarm.enabled, onCheckedChange = onToggle)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            OutlinedButton(onClick = onClick) { Text("Edit") }
        }
    }
}

private fun formatDays(mask: Int): String {
    if (mask == 0) return "One-time"
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return days.withIndex().filter { (i, _) -> (mask shr i) and 1 == 1 }
        .joinToString(" ") { it.value }
}
