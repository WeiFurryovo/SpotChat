package com.weifurry.spotchat.domain

import android.content.Context

data class ProfileSettings(
    val displayName: String,
    val avatarId: String,
    val about: String,
    val defaultDisappearingMode: String = ProfileStore.DEFAULT_DISAPPEARING_MODE
)

class ProfileStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(defaultDisplayName: String): ProfileSettings =
        ProfileSettings(
            displayName = prefs.getString(KEY_DISPLAY_NAME, null)?.takeUnless { it.isBlank() }
                ?: defaultDisplayName,
            avatarId = prefs.getString(KEY_AVATAR_ID, null)?.takeUnless { it.isBlank() }
                ?: DEFAULT_AVATAR_ID,
            about = prefs.getString(KEY_ABOUT, null)?.takeUnless { it.isBlank() }
                ?: DEFAULT_ABOUT,
            defaultDisappearingMode =
                normalizeDefaultDisappearingMode(
                    prefs.getString(KEY_DEFAULT_DISAPPEARING_MODE, null)
                )
        )

    fun save(profile: ProfileSettings): ProfileSettings {
        val normalized =
            profile.copy(
                displayName = profile.displayName.take(MAX_DISPLAY_NAME_CHARS),
                avatarId = profile.avatarId.ifBlank { DEFAULT_AVATAR_ID },
                about = profile.about.replace("\n", " ").trim().take(MAX_ABOUT_CHARS).ifBlank { DEFAULT_ABOUT },
                defaultDisappearingMode = normalizeDefaultDisappearingMode(profile.defaultDisappearingMode)
            )
        prefs
            .edit()
            .putString(KEY_DISPLAY_NAME, normalized.displayName)
            .putString(KEY_AVATAR_ID, normalized.avatarId)
            .putString(KEY_ABOUT, normalized.about)
            .putString(KEY_DEFAULT_DISAPPEARING_MODE, normalized.defaultDisappearingMode)
            .apply()
        return normalized
    }

    private fun normalizeDefaultDisappearingMode(mode: String?): String =
        if (mode != null && mode in DEFAULT_DISAPPEARING_MODES) {
            mode
        } else {
            DEFAULT_DISAPPEARING_MODE
        }

    companion object {
        const val DEFAULT_AVATAR_ID = "initial"
        const val DEFAULT_ABOUT = "可加密聊天"
        const val DEFAULT_DISAPPEARING_MODE = "off"
        const val MAX_DISPLAY_NAME_CHARS = 18
        const val MAX_ABOUT_CHARS = 32
        private val DEFAULT_DISAPPEARING_MODES =
            setOf(
                DEFAULT_DISAPPEARING_MODE,
                "one_minute",
                "one_hour",
                "one_day"
            )
        private const val PREFS_NAME = "spotchat_profile"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AVATAR_ID = "avatar_id"
        private const val KEY_ABOUT = "about"
        private const val KEY_DEFAULT_DISAPPEARING_MODE = "default_disappearing_mode"
    }
}
