package com.syll.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.syll.data.KeyValueStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The `RECORD_AUDIO` runtime permission (iOS `AVAudioApplication.recordPermission`). The system dialog needs an
 * activity, which `MainActivity` attaches while it is alive; the stores that ask live longer than it.
 *
 * iOS asks once and then only Settings can change it. Android may ask again after one denial, so the mic counts
 * as denied ([isDenied]) only once the system stops showing the dialog ("Don't allow" twice, or "Don't ask again").
 */
class MicrophonePermission(private val context: Context, private val storage: KeyValueStore) {
    private var activity: ComponentActivity? = null
    private var requestCount = 0
    /** Answers the request in flight with false if its activity goes away before the dialog answers. */
    private var pending: ((Boolean) -> Unit)? = null

    fun attach(activity: ComponentActivity) {
        this.activity = activity
    }

    fun detach(activity: ComponentActivity) {
        if (this.activity !== activity) return
        this.activity = null
        pending?.invoke(false)
    }

    val isGranted: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Turned on again in Settings clears it. */
    val isDenied: Boolean
        get() {
            if (isGranted) {
                if (storage.getBoolean(DENIED_KEY)) storage.remove(DENIED_KEY)
                return false
            }
            return storage.getBoolean(DENIED_KEY)
        }

    suspend fun request(): Boolean {
        if (isGranted) return true
        val activity = activity ?: return false
        return suspendCancellableCoroutine { continuation ->
            var launcher: ActivityResultLauncher<String>? = null
            val finish = { granted: Boolean ->
                pending = null
                launcher?.unregister()
                if (continuation.isActive) continuation.resume(granted)
            }
            pending = finish
            launcher = activity.activityResultRegistry.register(
                "microphone-${requestCount++}",
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    storage.remove(DENIED_KEY)
                } else if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    storage.putBoolean(DENIED_KEY, true)
                }
                finish(granted)
            }
            continuation.invokeOnCancellation {
                pending = null
                launcher?.unregister()
            }
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private companion object {
        const val DENIED_KEY = "microphonePermanentlyDenied"
    }
}
