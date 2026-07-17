package com.worstalarm.clock.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the camera-stuck-open bug: [BarcodeScanner] never explicitly
 * released its CameraX binding when the scanner UI was dismissed — closing the "scan to
 * fill value" dialog in the barcode library, or leaving the alarm's scanning panel —
 * because CameraX only auto-unbinds on the *LifecycleOwner's* ON_DESTROY, and that
 * owner (a nav back-stack entry, or the whole AlarmActivity ringing session) routinely
 * outlives the scanner composable itself. The camera — and with it the flashlight, which
 * many devices refuse to share with an open camera session — stayed locked until the
 * entire host screen/activity was destroyed.
 */
class CameraBindingGuardTest {

    private class FakeProvider {
        var unbindCount = 0
        fun unbindAll() { unbindCount++ }
    }

    @Test
    fun `normal lifecycle - bind then dispose unbinds exactly once`() {
        val guard = CameraBindingGuard<FakeProvider>()
        val provider = FakeProvider()
        var boundCount = 0

        guard.bindIfNotDisposed(provider) { boundCount++ }
        assertEquals(1, boundCount)
        assertEquals(0, provider.unbindCount)

        guard.dispose { it.unbindAll() }
        assertEquals(1, provider.unbindCount)
    }

    @Test
    fun `dispose before the provider future resolves prevents binding entirely`() {
        // This is the exact race that made the original bug possible: the async
        // ProcessCameraProvider.getInstance() future can resolve AFTER the Composable
        // (and its DisposableEffect) has already been torn down. If bind() ran anyway,
        // the resulting camera session would be a permanent orphan — nothing left to
        // ever call unbindAll() on it again.
        val guard = CameraBindingGuard<FakeProvider>()
        val provider = FakeProvider()
        var boundCount = 0

        guard.dispose { it.unbindAll() } // composable torn down first
        guard.bindIfNotDisposed(provider) { boundCount++ } // provider resolves after

        assertEquals("bind() must never run once disposed", 0, boundCount)
        // Nothing was ever bound, so the dispose-time unbind (already run above) had
        // nothing to release — this must NOT retroactively call unbind again.
        assertEquals(0, provider.unbindCount)
    }

    @Test
    fun `dispose without ever binding is a no-op, not a crash`() {
        val guard = CameraBindingGuard<FakeProvider>()
        // No bindIfNotDisposed call at all — e.g. the future never resolves before the
        // user backs out (permission denied, camera unavailable, near-instant dismiss).
        guard.dispose { it.unbindAll() }
        // No assertion needed beyond "this didn't throw" — dispose() must tolerate
        // nothing having been bound.
    }

    @Test
    fun `dispose is idempotent - a second dispose call does not double-unbind`() {
        val guard = CameraBindingGuard<FakeProvider>()
        val provider = FakeProvider()
        guard.bindIfNotDisposed(provider) {}

        guard.dispose { it.unbindAll() }
        guard.dispose { it.unbindAll() }

        assertEquals(1, provider.unbindCount)
    }

    @Test
    fun `rebinding a fresh guard after a previous scan session is independent`() {
        // Simulates scanning twice in a row (e.g. two separate "scan to fill value"
        // dialogs) — each BarcodeScanner composable instance gets its own guard, so one
        // session's dispose must not affect the other's.
        val firstProvider = FakeProvider()
        val firstGuard = CameraBindingGuard<FakeProvider>()
        firstGuard.bindIfNotDisposed(firstProvider) {}
        firstGuard.dispose { it.unbindAll() }
        assertEquals(1, firstProvider.unbindCount)

        val secondProvider = FakeProvider()
        val secondGuard = CameraBindingGuard<FakeProvider>()
        var secondBoundCount = 0
        secondGuard.bindIfNotDisposed(secondProvider) { secondBoundCount++ }

        assertEquals(1, secondBoundCount)
        assertEquals(0, secondProvider.unbindCount)
    }
}
