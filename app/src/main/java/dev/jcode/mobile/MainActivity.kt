package dev.jcode.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jcode.mobile.ui.AppRoot
import dev.jcode.mobile.ui.theme.JCodeTheme
import dev.jcode.mobile.ui.theme.ThemeFamily

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as JCodeApp).repository
        setContent {
            // The palette and the mode are separate choices: one decides the
            // colours, the other decides whether they are the light or dark set.
            val settings by repository.settings.collectAsStateWithLifecycle()
            val dark = settings.themeMode.isDark(isSystemInDarkTheme())

            // Bar icons follow the app theme, not the system: a pinned light
            // theme on a dark system would leave white icons on white.
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            JCodeTheme(
                darkTheme = dark,
                family = ThemeFamily.fromStored(settings.themeFamily),
            ) {
                AppRoot(repository)
            }
        }
    }
}
