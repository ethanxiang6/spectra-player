package com.spectra.player.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.spectra.player.R
import com.spectra.player.visualizer.Backdrop

/**
 * Launcher icon themes. Each is an <activity-alias> in the manifest; exactly one is enabled.
 * Some launchers take a few seconds to show the new icon, and may move it off the home screen.
 */
object AppIcons {
    data class Icon(val theme: Backdrop, val alias: String, val background: Int, val foreground: Int)

    val all = listOf(
        Icon(Backdrop.EVENING, "Evening", R.drawable.icon_bg_evening, R.drawable.icon_fg_evening),
        Icon(Backdrop.MIDNIGHT, "Midnight", R.drawable.icon_bg_midnight, R.drawable.icon_fg_midnight),
        Icon(Backdrop.SUNSET, "Sunset", R.drawable.icon_bg_sunset, R.drawable.icon_fg_sunset),
        Icon(Backdrop.AURORA, "Aurora", R.drawable.icon_bg_aurora, R.drawable.icon_fg_aurora),
        Icon(Backdrop.NEBULA, "Nebula", R.drawable.icon_bg_nebula, R.drawable.icon_fg_nebula),
        Icon(Backdrop.OCEAN, "Ocean", R.drawable.icon_bg_ocean, R.drawable.icon_fg_ocean),
        Icon(Backdrop.BLACK, "Black", R.drawable.icon_bg_black, R.drawable.icon_fg_black),
    )

    fun apply(context: Context, theme: Backdrop) {
        val chosen = all.firstOrNull { it.theme == theme } ?: all.first()
        val pm = context.packageManager
        // Enable the new one first so the app always has a launcher entry.
        set(context, pm, chosen.alias, enabled = true)
        all.filter { it != chosen }.forEach { set(context, pm, it.alias, enabled = false) }
    }

    private fun set(context: Context, pm: PackageManager, alias: String, enabled: Boolean) {
        val component = ComponentName(context, "com.spectra.player.icon.$alias")
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (pm.getComponentEnabledSetting(component) != state) {
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}
