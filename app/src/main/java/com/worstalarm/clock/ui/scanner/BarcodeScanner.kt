package com.worstalarm.clock.ui.scanner

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class DetectedBarcode(val rawValue: String, val format: Int)

/**
 * Camera preview that runs the ML Kit scanner on every frame. [onDetected] fires on the
 * first barcode seen (caller is responsible for de-duping / stopping).
 */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun BarcodeScanner(
    modifier: Modifier = Modifier,
    onDetected: (DetectedBarcode) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
        )
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                startCamera(ctx, previewView, lifecycleOwner, executor, scanner, onDetected)
                previewView
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner.close()
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun startCamera(
    context: Context,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    executor: ExecutorService,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onDetected: (DetectedBarcode) -> Unit
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val provider = providerFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) { imageProxy.close(); return@setAnalyzer }
            val input = InputImage.fromMediaImage(
                mediaImage, imageProxy.imageInfo.rotationDegrees
            )
            scanner.process(input)
                .addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull()?.rawValue?.let { raw ->
                        onDetected(DetectedBarcode(raw, barcodes.first().format))
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        } catch (_: Throwable) { /* camera unavailable */ }
    }, androidx.core.content.ContextCompat.getMainExecutor(context))
}
