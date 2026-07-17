package com.worstalarm.clock.ui.scanner

/**
 * Tracks whether the Composable that owns a CameraX `ProcessCameraProvider` binding has
 * been torn down, so the camera gets explicitly released the moment the scanner UI goes
 * away — not whenever its `LifecycleOwner` eventually reaches `DESTROYED`.
 *
 * Regression guard (camera-stuck-open bug): CameraX's `bindToLifecycle` only auto-unbinds
 * on the bound `LifecycleOwner`'s `ON_DESTROY` event. The screen or dialog hosting the
 * scanner (a nav back-stack entry, or `AlarmActivity` for the whole ringing session)
 * usually stays RESUMED long after the scanner Composable itself is dismissed — closing
 * the "scan to fill value" dialog, or tapping "Back to ringing screen" mid-alarm. Without
 * an explicit unbind, the camera capture session — and the system's camera-in-use
 * indicator, which also blocks the flashlight on many devices — stays open indefinitely,
 * until (if ever) the host is actually destroyed.
 *
 * The provider is resolved asynchronously (a `ListenableFuture`), so there's a race where
 * the Composable can be disposed before the provider even arrives; this guard makes sure
 * that case never binds at all rather than binding an orphan nothing will ever unbind.
 *
 * Generic and free of any CameraX/Android import so it's unit-testable as plain JVM code.
 */
class CameraBindingGuard<T> {
    private var boundProvider: T? = null
    private var disposed = false

    /**
     * Call once the async provider future resolves. Binds via [bind] unless this guard
     * was already disposed — in that case [bind] is never invoked, so there is nothing
     * left needing to be unbound later.
     */
    fun bindIfNotDisposed(provider: T, bind: (T) -> Unit) {
        if (disposed) return
        boundProvider = provider
        bind(provider)
    }

    /**
     * Call when the owning Composable leaves composition. Idempotent — safe to call more
     * than once. Invokes [unbind] exactly once, and only if [bindIfNotDisposed] actually
     * bound something.
     */
    fun dispose(unbind: (T) -> Unit) {
        if (disposed) return
        disposed = true
        boundProvider?.let(unbind)
        boundProvider = null
    }
}
