package app.zsync

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

// Android cannot change an app icon at runtime: each character has its own launcher
// activity-alias (see AndroidManifest.xml) and only the selected one is enabled.
object AppIcons {
    private fun alias(context: Context, key: String) =
        ComponentName(context, "app.zsync." + if (key == Characters.default.key) "IconDefault" else "Icon_$key")

    fun apply(context: Context, key: String) {
        val pm = context.packageManager
        val target = Characters.byKey(key).key

        pm.setComponentEnabledSetting(alias(context, target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (c in Characters.all) if (c.key != target) {
            pm.setComponentEnabledSetting(alias(context, c.key), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    fun ensure(context: Context, key: String) {
        val state = context.packageManager.getComponentEnabledSetting(alias(context, Characters.byKey(key).key))
        val enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && Characters.byKey(key) == Characters.default)
        if (!enabled) apply(context, key)
    }
}
