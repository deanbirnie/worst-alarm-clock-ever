package com.worstalarm.clock.ui.barcodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.common.Barcode
import com.worstalarm.clock.data.entity.BarcodeEntity
import com.worstalarm.clock.ui.AppViewModel
import com.worstalarm.clock.ui.scanner.BarcodeScanner
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeLibraryScreen(
    onBack: () -> Unit,
    vm: AppViewModel = viewModel()
) {
    val items by vm.barcodes().collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    var showEditor by remember { mutableStateOf<BarcodeEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Barcode library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                showEditor = BarcodeEntity(name = "", rawValue = "", format = Barcode.FORMAT_UNKNOWN)
            }) { Icon(Icons.Default.Add, contentDescription = "Add barcode") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (items.isEmpty()) {
                Text(
                    "No saved barcodes. Tap + to add one — scan a real barcode or type a value manually.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items = items, key = { it.id }) { b ->
                        BarcodeRow(
                            barcode = b,
                            onEdit = { showEditor = b },
                            onDelete = {
                                vm.deleteBarcode(b) { ok ->
                                    if (!ok) Toast.makeText(
                                        context,
                                        "Can't delete — barcode is in use by an alarm.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    showEditor?.let { initial ->
        BarcodeEditorDialog(
            initial = initial,
            onDismiss = { showEditor = null },
            onSave = { updated ->
                vm.saveBarcode(updated)
                showEditor = null
            }
        )
    }
}

@Composable
private fun BarcodeRow(
    barcode: BarcodeEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(barcode.name.ifBlank { "(unnamed)" }, style = MaterialTheme.typography.titleMedium)
                Text(
                    barcode.rawValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Format: ${formatName(barcode.format)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onEdit) { Text("Edit") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun BarcodeEditorDialog(
    initial: BarcodeEntity,
    onDismiss: () -> Unit,
    onSave: (BarcodeEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var rawValue by remember { mutableStateOf(initial.rawValue) }
    var format by remember { mutableStateOf(initial.format) }
    var scanning by remember { mutableStateOf(false) }

    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && rawValue.isNotBlank(),
                onClick = { onSave(initial.copy(name = name, rawValue = rawValue, format = format)) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(if (initial.id == 0L) "New barcode" else "Edit barcode") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. \"Fridge QR\")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = rawValue,
                    onValueChange = { rawValue = it },
                    label = { Text("Value") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Format: ${formatName(format)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (cameraPermission.status.isGranted) scanning = true
                        else cameraPermission.launchPermissionRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  Scan to fill value")
                }
                if (scanning) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(240.dp)) {
                        BarcodeScanner { detected ->
                            rawValue = detected.rawValue
                            format = detected.format
                            scanning = false
                        }
                    }
                }
            }
        }
    )
}

internal fun formatName(format: Int): String = when (format) {
    Barcode.FORMAT_QR_CODE -> "QR"
    Barcode.FORMAT_EAN_13 -> "EAN-13"
    Barcode.FORMAT_EAN_8 -> "EAN-8"
    Barcode.FORMAT_UPC_A -> "UPC-A"
    Barcode.FORMAT_UPC_E -> "UPC-E"
    Barcode.FORMAT_CODE_128 -> "Code 128"
    Barcode.FORMAT_CODE_39 -> "Code 39"
    Barcode.FORMAT_CODE_93 -> "Code 93"
    Barcode.FORMAT_CODABAR -> "Codabar"
    Barcode.FORMAT_ITF -> "ITF"
    Barcode.FORMAT_PDF417 -> "PDF417"
    Barcode.FORMAT_AZTEC -> "Aztec"
    Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
    Barcode.FORMAT_UNKNOWN -> "Any"
    else -> "Other"
}
