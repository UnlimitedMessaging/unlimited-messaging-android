package app.unlimitedmessaging.android.system

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * The system settings that decide whether the gateway survives in the
 * background. See docs/RELIABILITY.md for why each one matters.
 */
object BackgroundSettings {

    /** Whether the app is exempt from battery optimization (Doze, App Standby). */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /**
     * The system dialog that asks the user to exempt this app in one tap.
     *
     * Google Play restricts this permission to a short list of app types;
     * the app is not distributed there, and an SMS gateway is exactly the
     * case the exemption exists for.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizationsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /**
     * The manufacturer's own background-app screen when we know where it is,
     * else the app's system settings page. Several manufacturers kill
     * background apps beyond what stock Android does, regardless of the
     * battery exemption.
     */
    fun manufacturerSettingsIntent(context: Context): Intent {
        val candidates = MANUFACTURER_SCREENS[Build.MANUFACTURER.lowercase()].orEmpty()
            .map { Intent().setComponent(it) }
        return candidates.firstOrNull { it.resolveActivity(context.packageManager) != null }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }

    /** Whether this manufacturer is known to need an extra setting (shown as a hint). */
    val hasAggressiveManufacturer: Boolean
        get() = Build.MANUFACTURER.lowercase() in AGGRESSIVE_MANUFACTURERS

    /** dontkillmyapp.com documents each manufacturer's settings, maintained by the community. */
    val manufacturerGuideUrl: String
        get() = "https://dontkillmyapp.com/${Build.MANUFACTURER.lowercase()}"

    private val AGGRESSIVE_MANUFACTURERS = setOf(
        "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "oneplus", "vivo", "samsung", "meizu", "asus",
    )

    /** Known component names; they move between OS versions, hence several per brand. */
    private val MANUFACTURER_SCREENS: Map<String, List<ComponentName>> = mapOf(
        "xiaomi" to listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ),
        "huawei" to listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ),
        "honor" to listOf(
            ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ),
        "oppo" to listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ),
        "vivo" to listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ),
        "samsung" to listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
        ),
        "asus" to listOf(
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        ),
    )
}
