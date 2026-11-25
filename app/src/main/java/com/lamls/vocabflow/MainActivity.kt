package com.lamls.vocabflow

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.lamls.vocabflow.app.AppRoot
import com.lamls.vocabflow.app.ProvideStores
import com.lamls.vocabflow.designsystem.theme.VocabFlowTheme

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
        setContent {
            VocabFlowTheme {
                ProvideStores(container) {
                    AppRoot()
                }
            }
        }
    }
}
