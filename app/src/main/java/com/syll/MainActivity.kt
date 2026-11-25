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
        setContent {
            VocabFlowTheme {
                ProvideStores(container) {
                    AppRoot()
                }
            }
        }
    }

    override fun onDestroy() {
        (application as VocabFlowApplication).container.microphone.detach(this)
        super.onDestroy()
    }
}
