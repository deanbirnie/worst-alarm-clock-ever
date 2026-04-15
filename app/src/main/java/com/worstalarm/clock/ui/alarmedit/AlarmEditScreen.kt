package com.worstalarm.clock.ui.alarmedit

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worstalarm.clock.data.entity.AlarmEntity
import com.worstalarm.clock.data.entity.BarcodeEntity
import com.worstalarm.clock.data.entity.RoutineStepEntity
import com.worstalarm.clock.ui.AppViewModel

private data class UiStep(
    val locationLabel: String,
    val barcodeId: Long,
    val barcodeName: String,
    val timeToNextSeconds: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarmId: Long,
    onDone: () -> Unit,
    vm: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val barcodes by vm.barcodes().collectAsStateWithLifecycle(initialValue = emptyList())

    var label by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf(7) }
    var minute by remember { mutableStateOf(0) }
    var daysMask by remember { mutableStateOf(0b0011111) /* Mon-Fri */ }
    var enabled by remember { mutableStateOf(true) }
    var steps by remember { mutableStateOf(listOf<UiStep>()) }
    var loaded by remember { mutableStateOf(alarmId == 0L) }

    LaunchedEffect(alarmId) {
        if (alarmId != 0L) {
            val existing = vm.loadAlarm(alarmId)
            if (existing != null) {
                label = existing.alarm.label
                hour = existing.alarm.hour
                minute = existing.alarm.minute
                daysMask = existing.alarm.daysMask
                enabled = existing.alarm.enabled
                steps = existing.orderedSteps.map {
                    UiStep(
                        locationLabel = it.step.locationLabel,
                        barcodeId = it.barcode.id,
                        barcodeName = it.barcode.name,
                        timeToNextSeconds = it.step.timeToNextRingSeconds
                    )
                }
            }
            loaded = true
        }
    }

    val canSave = loaded && steps.isNotEmpty() && steps.all { it.barcodeId != 0L && it.locationLabel.isNotBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (alarmId == 0L) "New alarm" else "Edit alarm") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            val alarm = AlarmEntity(
                                id = alarmId,
                                label = label,
                                hour = hour,
                                minute = minute,
                                daysMask = daysMask,
                                enabled = enabled
                            )
                            val entities = steps.mapIndexed { idx, s ->
                                RoutineStepEntity(
                                    alarmId = alarmId,
                                    stepIndex = idx,
                                    locationLabel = s.locationLabel,
                                    barcodeId = s.barcodeId,
                                    timeToNextRingSeconds = s.timeToNextSeconds
                                )
                            }
                            vm.saveAlarm(alarm, entities) { onDone() }
                        }
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m -> hour = h; minute = m },
                        hour, minute, true
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Time: %02d:%02d".format(hour, minute)) }

            Text("Days")
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                names.forEachIndexed { i, d ->
                    FilterChip(
                        selected = (daysMask shr i) and 1 == 1,
                        onClick = { daysMask = daysMask xor (1 shl i) },
                        label = { Text(d) }
                    )
                }
            }
            if (daysMask == 0) {
                Text(
                    "One-time: will fire at the next occurrence of this time, then disable itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("Routine locations (in order)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Each ring stops when you scan that location's barcode. After a scan, the alarm waits the configured duration, then rings the next location.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            steps.forEachIndexed { i, step ->
                StepCard(
                    index = i,
                    step = step,
                    barcodes = barcodes,
                    isLast = i == steps.size - 1,
                    onChange = { updated ->
                        steps = steps.toMutableList().also { it[i] = updated }
                    },
                    onDelete = { steps = steps.toMutableList().also { it.removeAt(i) } }
                )
            }

            OutlinedButton(
                onClick = {
                    if (barcodes.isEmpty()) {
                        // Can't add a step without any barcodes in the library — nudge user.
                    } else {
                        steps = steps + UiStep(
                            locationLabel = "Location ${steps.size + 1}",
                            barcodeId = 0L,
                            barcodeName = "",
                            timeToNextSeconds = 180
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  Add location")
            }
            if (barcodes.isEmpty()) {
                Text(
                    "Add barcodes to your library first (QR icon on the home screen).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepCard(
    index: Int,
    step: UiStep,
    barcodes: List<BarcodeEntity>,
    isLast: Boolean,
    onChange: (UiStep) -> Unit,
    onDelete: () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Step ${index + 1}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove step")
                }
            }
            OutlinedTextField(
                value = step.locationLabel,
                onValueChange = { onChange(step.copy(locationLabel = it)) },
                label = { Text("Location name (e.g. Kitchen)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row {
                AssistChip(
                    onClick = { showPicker = true },
                    label = {
                        Text(
                            if (step.barcodeId == 0L) "Choose barcode"
                            else "Barcode: ${step.barcodeName}"
                        )
                    }
                )
                DropdownMenu(
                    expanded = showPicker,
                    onDismissRequest = { showPicker = false }
                ) {
                    barcodes.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b.name.ifBlank { b.rawValue }) },
                            onClick = {
                                onChange(step.copy(barcodeId = b.id, barcodeName = b.name))
                                showPicker = false
                            }
                        )
                    }
                }
            }

            if (!isLast) {
                var minutesText by remember(step) {
                    mutableStateOf((step.timeToNextSeconds / 60).toString())
                }
                var secondsText by remember(step) {
                    mutableStateOf((step.timeToNextSeconds % 60).toString())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = {
                            minutesText = it.filter(Char::isDigit)
                            val total = (minutesText.toIntOrNull() ?: 0) * 60 +
                                (secondsText.toIntOrNull() ?: 0)
                            onChange(step.copy(timeToNextSeconds = total))
                        },
                        label = { Text("Min") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = {
                            secondsText = it.filter(Char::isDigit)
                            val total = (minutesText.toIntOrNull() ?: 0) * 60 +
                                (secondsText.toIntOrNull() ?: 0)
                            onChange(step.copy(timeToNextSeconds = total))
                        },
                        label = { Text("Sec") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                Text(
                    "…then rings the next location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Final step — scanning this disarms the alarm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
