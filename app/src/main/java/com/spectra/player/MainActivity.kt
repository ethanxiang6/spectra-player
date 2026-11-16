package com.spectra.player

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.spectra.player.ui.SpectraRoot
import com.spectra.player.ui.theme.SpectraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as SpectraApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // A recreated activity re-delivers the original intent; only act on a fresh launch.
        if (savedInstanceState == null) handleAuthRedirect(intent)

        setContent {
            val appSettings by container.appPrefs.settings.collectAsState()
            androidx.compose.runtime.SideEffect {
                com.spectra.player.ui.nowplaying.Dusk.mono = appSettings.monochrome
                com.spectra.player.ui.nowplaying.Dusk.palette = appSettings.custom
                com.spectra.player.ui.nowplaying.Dusk.appCustom =
                    !appSettings.monochrome && appSettings.theme == com.spectra.player.visualizer.Backdrop.CUSTOM
            }
            // The launch screen (shown while Spectra opens, also right after an update) and the
            // window behind the app follow the chosen look instead of the default icon's colours.
            androidx.compose.runtime.LaunchedEffect(appSettings.monochrome, appSettings.icon) {
                applyLaunchLook(appSettings.monochrome, appSettings.icon)
            }
            // The home screen widget wears the same colours: redraw it when they change.
            androidx.compose.runtime.LaunchedEffect(
                appSettings.monochrome, appSettings.theme, appSettings.custom, appSettings.widgetBackground,
            ) { com.spectra.player.widget.SpectraWidget.drawStill(applicationContext) }
            SpectraTheme(theme = appSettings.theme, monochrome = appSettings.monochrome) {
                SpectraRoot(
                    container = container,
                    onLogin = { container.authManager.launchLogin(this) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bring the home screen widget (if there is one) back to life.
        com.spectra.player.widget.WidgetService.startIfNeeded(this)
    }

    private fun applyLaunchLook(mono: Boolean, icon: String) {
        val alias = if (mono) "Monochrome" else com.spectra.player.ui.theme.AppIcons.find(icon).alias
        if (mono) window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val style = when (alias) {
                "Midnight" -> R.style.Theme_Spectra_Splash_Midnight
                "Sunset" -> R.style.Theme_Spectra_Splash_Sunset
                "Aurora" -> R.style.Theme_Spectra_Splash_Aurora
                "Nebula" -> R.style.Theme_Spectra_Splash_Nebula
                "Ocean" -> R.style.Theme_Spectra_Splash_Ocean
                "Black" -> R.style.Theme_Spectra_Splash_Black
                "Monochrome" -> R.style.Theme_Spectra_Splash_Monochrome
                else -> R.style.Theme_Spectra_Splash_Evening
            }
            try {
                splashScreen.setSplashScreenTheme(style)
            } catch (e: Exception) {
                // Some launchers' splash handling can refuse; the default stays.
            }
        }
    }

    // singleTask: the spectra://callback redirect from the Custom Tab arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthRedirect(intent)
    }

    private fun handleAuthRedirect(intent: Intent?) {
        if (intent == null) return
        val data = intent.data ?: return
        if (data.scheme == "spectra" && data.host == "callback") {
            intent.data = null // consume: never process the same redirect twice
            lifecycleScope.launch { container.authManager.handleRedirect(data) }
        }
    }
}
