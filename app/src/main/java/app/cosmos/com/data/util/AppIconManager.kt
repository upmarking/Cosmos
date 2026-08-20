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
    val aliasName: String?,
    val requiredTier: MembershipTier,
    val emoji: String,
    val previewBgColor: Long,
    val previewBorderColor: Long
) {
    DEFAULT(
        id = "default",
        title = "Cosmos Classic",
        subtitle = "Original Celestial Core (Default)",
        aliasName = null,
        requiredTier = MembershipTier.ASTEROID,
        emoji = "🪐",
        previewBgColor = 0xFF0A0A1E,
        previewBorderColor = 0xFF8083FF
    ),
    ASTEROID(
        id = "asteroid",
        title = "Asteroid",
        subtitle = "Electric Indigo & Orbital Ring",
        aliasName = "app.cosmos.com.MainActivityAliasAsteroid",
        requiredTier = MembershipTier.ASTEROID,
        emoji = "☄️",
        previewBgColor = 0xFF111418,
        previewBorderColor = 0xFF8083FF
    ),
    MOON(
        id = "moon",
        title = "Lunar Moon",
        subtitle = "Crescent & Glowing Ring",
        aliasName = "app.cosmos.com.MainActivityAliasMoon",
        requiredTier = MembershipTier.ASTEROID,
        emoji = "🌙",
        previewBgColor = 0xFF0D111A,
        previewBorderColor = 0xFFADC6FF
    ),
    EARTH(
        id = "earth",
        title = "Terra Earth",
        subtitle = "Atmosphere & Continents",
        aliasName = "app.cosmos.com.MainActivityAliasEarth",
        requiredTier = MembershipTier.ASTEROID,
        emoji = "🌍",
        previewBgColor = 0xFF08101A,
        previewBorderColor = 0xFF34A853
    ),
    SUN(
        id = "sun",
        title = "Solar Sun",
        subtitle = "Radiant Corona & Amber Core",
        aliasName = "app.cosmos.com.MainActivityAliasSun",
        requiredTier = MembershipTier.ASTEROID,
        emoji = "☀️",
        previewBgColor = 0xFF0D0B08,
        previewBorderColor = 0xFFFFB300
    );

    companion object {
        fun fromId(id: String): CosmosAppIcon {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
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
        val savedId = prefs.getString(KEY_ACTIVE_ICON, CosmosAppIcon.DEFAULT.id) ?: CosmosAppIcon.DEFAULT.id
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
            val defaultActivity = ComponentName(packageName, "app.cosmos.com.MainActivity")

            if (newIcon.aliasName == null) {
                // ── Switch back to DEFAULT (normal app logo) ────────────────
                // 1. Enable MainActivity
                pm.setComponentEnabledSetting(
                    defaultActivity,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

                // 2. Disable all activity aliases
                CosmosAppIcon.values().forEach { icon ->
                    icon.aliasName?.let { alias ->
                        val comp = ComponentName(packageName, alias)
                        pm.setComponentEnabledSetting(
                            comp,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }
            } else {
                // ── Switch to selected Alias ─────────────────────────────────
                // 1. Enable target icon alias
                val targetComponent = ComponentName(packageName, newIcon.aliasName)
                pm.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

                // 2. Disable all other icon aliases
                CosmosAppIcon.values().forEach { icon ->
                    if (icon != newIcon && icon.aliasName != null) {
                        val comp = ComponentName(packageName, icon.aliasName)
                        pm.setComponentEnabledSetting(
                            comp,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }

                // 3. Disable the default unaliased MainActivity launcher to avoid duplicates
                try {
                    pm.setComponentEnabledSetting(
                        defaultActivity,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } catch (e: Exception) {
                    Log.d(TAG, "Default activity already disabled: ${e.message}")
                }
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
