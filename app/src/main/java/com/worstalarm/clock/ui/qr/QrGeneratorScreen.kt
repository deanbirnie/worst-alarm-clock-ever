package com.worstalarm.clock.ui.qr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.worstalarm.clock.data.entity.BarcodeEntity
import com.worstalarm.clock.ui.AppViewModel
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

private const val QR_PX = 512

private val CODE_CHARS = ('A'..'Z') + ('0'..'9')

private fun randomCodeValue(): String =
    "WACE-" + (1..10).map { CODE_CHARS.random(Random) }.joinToString("")

/** Render a QR code for [value] as a bitmap (offline, via ZXing). */
private fun qrBitmap(value: String, sizePx: Int = QR_PX): Bitmap {
    val matrix = QRCodeWriter().encode(
        value, BarcodeFormat.QR_CODE, sizePx, sizePx,
        mapOf(EncodeHintType.MARGIN to 1)
    )
    val pixels = IntArray(sizePx * sizePx) { i ->
        if (matrix[i % sizePx, i / sizePx]) AndroidColor.BLACK else AndroidColor.WHITE
    }
    return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).apply {
        setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    }
}

/** Write the QR to cache and open the system share sheet (save, print, send, …). */
private fun shareQr(context: Context, value: String, name: String) {
    try {
        val dir = File(context.cacheDir, "qr").apply { mkdirs() }
        val safeName = name.ifBlank { value }.replace(Regex("[^A-Za-z0-9-_ ]"), "_")
        val file = File(dir, "$safeName.png")
        FileOutputStream(file).use { out ->
            qrBitmap(value).compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share QR code"))
    } catch (_: Throwable) {
        Toast.makeText(context, "Couldn't share the QR code.", Toast.LENGTH_SHORT).show()
    }
}

private data class GeneratedCode(val value: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrGeneratorScreen(
    onBack: () -> Unit,
    vm: AppViewModel = viewModel()
) {
    val codes = remember { mutableStateListOf<GeneratedCode>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR generator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Generate random QR codes to print and stick around the house — or set " +
                    "one as your desktop wallpaper so you must log in to your PC to scan it. " +
                    "Add each code to your library, then share/print it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { codes.add(GeneratedCode(randomCodeValue())) }) {
                    Text("Generate code")
                }
                OutlinedButton(onClick = {
                    repeat(3) { codes.add(GeneratedCode(randomCodeValue())) }
                }) { Text("Generate 3") }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items = codes, key = { it.value }) { code ->
                    GeneratedCodeCard(code = code, vm = vm)
                }
            }
        }
    }
}

@Composable
private fun GeneratedCodeCard(code: GeneratedCode, vm: AppViewModel) {
    val context = LocalContext.current
    val bitmap = remember(code.value) { qrBitmap(code.value, 256) }
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var added by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR code ${code.value}",
                    modifier = Modifier.size(96.dp).background(Color.White)
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(code.value, fontWeight = FontWeight.Bold)
                    Text(
                        if (added) "Saved to library" else "Not in library yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { shareQr(context, code.value, name) }) {
                    Icon(Icons.Default.Share, contentDescription = "Share QR code")
                }
            }
            if (!added) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. \"Desk QR\")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (optional, e.g. \"Desk\")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.saveBarcode(
                            BarcodeEntity(
                                name = name,
                                rawValue = code.value,
                                format = Barcode.FORMAT_QR_CODE,
                                location = location.trim()
                            )
                        )
                        added = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add to barcode library") }
            }
        }
    }
}
