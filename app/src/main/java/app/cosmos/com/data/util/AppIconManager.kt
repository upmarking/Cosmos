package app.cosmos.com.data.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.cosmos.com.data.model.MembershipTier
import app.cosmos.com.data.payment.FeatureGate

/**
 * Available COSMOS App Icons for dynamic customization.
 */
enum class CosmosAppIcon(
    val id: String,
    val title: String,
    val subtitle: String,
    val aliasName: String,
    val requiredTier: MembershipTier,
    val emoji: String,
    val previewBgColor: Long,
    val previewBorderColor: Long
) {
    ASTEROID(
        id = "asteroid",
        title = "Light Multi-Color",
        subtitle = "Classic White & Rainbow Accent",
        aliasName = "app.cosmos.com.MainActivityAliasAsteroid",
        requiredTier = MembershipTier.ASTEROID,
        emoji = "✨",
        previewBgColor = 0xFFFFFFFF,
        previewBorderColor = 0xFFD0D5DD
    ),
    MOON(
        id = "moon",
        title = "Dark Multi-Color",
        subtitle = "Slate Dark & Rainbow Accent",
        aliasName = "app.cosmos.com.MainActivityAliasMoon",
        requiredTier = MembershipTier.ASTEROID,
        emoji = "🌙",
        previewBgColor = 0xFF1B1E26,
        previewBorderColor = 0xFF4A5568
    ),
    EARTH(
        id = "earth",
        title = "Monochrome Light",
        subtitle = "Pure White & Obsidian Accent",
        aliasName = "app.cosmos.com.MainActivityAliasEarth",
        requiredTier = MembershipTier.ASTEROID,
        emoji = "⚪",
        previewBgColor = 0xFFFFFFFF,
        previewBorderColor = 0xFFD0D5DD
    ),
    SUN(
        id = "sun",
        title = "Stealth Dark",
        subtitle = "OLED Black & Crisp White",
        aliasName = "app.cosmos.com.MainActivityAliasSun",
        requiredTier = MembershipTier.ASTEROID,
        emoji = "⚫",
        previewBgColor = 0xFF0B0C10,
        previewBorderColor = 0xFF333842
    );

    companion object {
        fun fromId(id: String): CosmosAppIcon {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ASTEROID
        }
    }
}

/**
 * Manages dynamic runtime app icon switching via Android activity-alias.
 */
object AppIconManager {
    private const val TAG = "AppIconManager"
    private const val PREFS_NAME = "cosmos_appearance_prefs"
    private const val KEY_ACTIVE_ICON = "active_app_icon"

    /**
     * Retrieves the currently active app icon.
     */
    fun getActiveIcon(context: Context): CosmosAppIcon {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_ACTIVE_ICON, CosmosAppIcon.ASTEROID.id) ?: CosmosAppIcon.ASTEROID.id
        return CosmosAppIcon.fromId(savedId)
    }

    /**
     * Switches the launcher icon to the specified [newIcon].
     * Uses [PackageManager.setComponentEnabledSetting] with [PackageManager.DONT_KILL_APP].
     */
    fun setAppIcon(context: Context, newIcon: CosmosAppIcon): Boolean {
        return try {
            val pm = context.packageManager
            val packageName = context.packageName

            // Enable target icon alias
            val targetComponent = ComponentName(packageName, newIcon.aliasName)
            pm.setComponentEnabledSetting(
                targetComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // Disable all other icon aliases
            CosmosAppIcon.values().forEach { icon ->
                if (icon != newIcon) {
                    val comp = ComponentName(packageName, icon.aliasName)
                    pm.setComponentEnabledSetting(
                        comp,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }

            // Also disable the default unaliased MainActivity launcher if alias is active
            val defaultActivity = ComponentName(packageName, "app.cosmos.com.MainActivity")
            try {
                pm.setComponentEnabledSetting(
                    defaultActivity,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                Log.d(TAG, "Default activity already handled: ${e.message}")
            }

            // Persist choice
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVE_ICON, newIcon.id)
                .apply()

            Log.i(TAG, "Successfully changed app icon to ${newIcon.title} (${newIcon.id})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch app icon: ${e.message}", e)
            false
        }
    }

    /**
     * Checks if the user's tier permits selecting this icon.
     */
    fun isIconUnlocked(userTier: MembershipTier, icon: CosmosAppIcon): Boolean {
        return FeatureGate.hasAccess(userTier, icon.requiredTier)
    }
}
