package com.weifurry.spotchat.domain

import android.content.Context

data class ProfileSettings(
    val displayName: String,
    val avatarId: String
)

class ProfileStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(defaultDisplayName: String): ProfileSettings =
        ProfileSettings(
            displayName = prefs.getString(KEY_DISPLAY_NAME, null)?.takeUnless { it.isBlank() }
                ?: defaultDisplayName,
            avatarId = prefs.getString(KEY_AVATAR_ID, null)?.takeUnless { it.isBlank() }
                ?: DEFAULT_AVATAR_ID
        )

    fun save(profile: ProfileSettings): ProfileSettings {
        val normalized =
            profile.copy(
                displayName = profile.displayName.take(MAX_DISPLAY_NAME_CHARS),
                avatarId = profile.avatarId.ifBlank { DEFAULT_AVATAR_ID }
            )
        prefs
            .edit()
            .putString(KEY_DISPLAY_NAME, normalized.displayName)
            .putString(KEY_AVATAR_ID, normalized.avatarId)
            .apply()
        return normalized
    }

    companion object {
        const val DEFAULT_AVATAR_ID = "mint"
        const val MAX_DISPLAY_NAME_CHARS = 18
        private const val PREFS_NAME = "spotchat_profile"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AVATAR_ID = "avatar_id"
    }
}
