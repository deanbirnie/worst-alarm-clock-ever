package com.worstalarm.clock.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worstalarm.clock.ui.AppViewModel

/** Resolve a picked audio file's display name; falls back to a generic label. */
fun audioDisplayName(context: Context, uriString: String?): String {
    if (uriString == null) return "System default alarm sound"
    return try {
        context.contentResolver.query(
            Uri.parse(uriString),
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Custom sound"
    } catch (_: Throwable) {
        "Custom sound (file may have moved)"
    }
}

/**
 * Reusable "pick an audio file as alarm tone" row. [currentUri] null means the
 * fallback described by [defaultLabel]. Picked URIs get a persistable read grant so
 * they still play after reboot.
 */
@Composable
fun AlarmTonePickerRow(
    title: String,
    defaultLabel: String,
    currentUri: String?,
    onPicked: (String?) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onPicked(uri.toString())
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(
            if (currentUri == null) defaultLabel else audioDisplayName(context, currentUri),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { launcher.launch(arrayOf("audio/*")) }) {
                Text("Choose audio file")
            }
            if (currentUri != null) {
                TextButton(onClick = { onPicked(null) }) { Text("Reset") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val globalTone by vm.globalRingtone.collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    AlarmTonePickerRow(
                        title = "Alarm sound",
                        defaultLabel = "System default alarm sound",
                        currentUri = globalTone,
                        onPicked = { vm.setGlobalRingtone(it) }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Used for every alarm unless an alarm sets its own sound.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Welcome message", fontWeight = FontWeight.Bold)
                    Text(
                        "Show the introduction again the next time the app starts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        vm.setIntroSeen(false)
                        Toast.makeText(
                            context, "The welcome message will show on next launch.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) { Text("Show again") }
                }
            }
        }
    }
}
