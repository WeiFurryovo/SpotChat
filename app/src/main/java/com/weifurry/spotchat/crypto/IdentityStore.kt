package com.weifurry.spotchat.crypto

import android.content.Context
import java.security.KeyPair

class IdentityStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreateIdentity(): KeyPair {
        val publicKey = prefs.getString(KEY_PUBLIC, null)
        val privateKey = prefs.getString(KEY_PRIVATE, null)
        if (!publicKey.isNullOrBlank() && !privateKey.isNullOrBlank()) {
            runCatching {
                KeyPair(
                    SpotChatCrypto.decodePublicKey(publicKey),
                    SpotChatCrypto.decodePrivateKey(privateKey)
                )
            }.onSuccess { identity ->
                return identity
            }
        }

        val identity = SpotChatCrypto.generateIdentity()
        prefs
            .edit()
            .putString(KEY_PUBLIC, SpotChatCrypto.encodePublicKey(identity.public))
            .putString(KEY_PRIVATE, SpotChatCrypto.encodePrivateKey(identity.private))
            .apply()
        return identity
    }

    companion object {
        private const val PREFS_NAME = "spotchat_identity"
        private const val KEY_PUBLIC = "public_key"
        private const val KEY_PRIVATE = "private_key"
    }
}
