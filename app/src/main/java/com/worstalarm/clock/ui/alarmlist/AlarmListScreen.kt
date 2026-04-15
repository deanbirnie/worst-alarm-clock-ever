package com.worstalarm.clock.ui.alarmlist

import android.content.Context
import android.provider.Settings
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
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worstalarm.clock.data.dao.AlarmWithSteps
import com.worstalarm.clock.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onManageBarcodes: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    vm: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val alarms by vm.alarms().collectAsStateWithLifecycle(initialValue = emptyList())
    // Re-evaluated on every recomposition so the banner disappears once the user grants it.
    val canDrawOverlays = Settings.canDrawOverlays(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Worst Alarm Clock Ever") },
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
