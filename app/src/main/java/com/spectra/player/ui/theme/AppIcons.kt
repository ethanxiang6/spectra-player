package com.spectra.player.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.spectra.player.R

/**
 * Launcher icon themes. Each is an <activity-alias> in the manifest; exactly one is enabled.
 * Some launchers take a few seconds to show the new icon, and may move it off the home screen.
 */
object AppIcons {
    /** [alias] names the manifest's `.icon.<alias>` activity-alias and is what the settings store. */
    data class Icon(val alias: String, val background: Int, val foreground: Int)

    val all = listOf(
        Icon("Evening", R.drawable.icon_bg_evening, R.drawable.icon_fg_evening),
        Icon("Midnight", R.drawable.icon_bg_midnight, R.drawable.icon_fg_midnight),
        Icon("Sunset", R.drawable.icon_bg_sunset, R.drawable.icon_fg_sunset),
        Icon("Aurora", R.drawable.icon_bg_aurora, R.drawable.icon_fg_aurora),
        Icon("Nebula", R.drawable.icon_bg_nebula, R.drawable.icon_fg_nebula),
        Icon("Ocean", R.drawable.icon_bg_ocean, R.drawable.icon_fg_ocean),
        Icon("Black", R.drawable.icon_bg_black, R.drawable.icon_fg_black),
        Icon("Monochrome", R.drawable.icon_bg_monochrome, R.drawable.icon_fg_monochrome),
    )

    const val DEFAULT = "Evening"

    /** The icon for a stored value: an alias, or a theme name saved by older versions ("EVENING"). */
    fun find(stored: String?): Icon = all.firstOrNull { it.alias.equals(stored, ignoreCase = true) } ?: all.first()

    fun apply(context: Context, alias: String) {
        val chosen = find(alias)
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
