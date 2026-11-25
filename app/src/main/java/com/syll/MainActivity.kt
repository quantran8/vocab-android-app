package com.syll

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.syll.app.AppRoot
import com.syll.app.ProvideStores
import com.syll.designsystem.theme.VocabFlowTheme
import com.syll.features.auth.AppleSignIn

/** AppCompat so per-app language (AppCompatDelegate.setApplicationLocales) works back to API 26. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Light bars on a white canvas, whatever the system theme (the app is light only).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as VocabFlowApplication).container
        container.microphone.attach(this)
        container.notifications.attach(this)
        setContent {
            VocabFlowTheme {
                ProvideStores(container) {
                    AppRoot()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from Sign in with Apple's Custom Tab without a callback: the tab was closed.
        AppleSignIn.hostResumed()
    }

    override fun onStop() {
        AppleSignIn.hostStopped()
        super.onStop()
    }

    override fun onDestroy() {
        val container = (application as VocabFlowApplication).container
        container.microphone.detach(this)
        container.notifications.detach(this)
        super.onDestroy()
    }
}
