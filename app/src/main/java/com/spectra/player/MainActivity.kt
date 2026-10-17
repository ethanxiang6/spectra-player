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
            SpectraTheme(theme = appSettings.theme) {
                SpectraRoot(
                    container = container,
                    onLogin = { container.authManager.launchLogin(this) },
                )
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
