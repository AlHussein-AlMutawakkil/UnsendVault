package com.riyadm.unsendvault.crypto.message

import com.riyadm.unsendvault.core.model.EncryptionMode
import com.riyadm.unsendvault.data.SettingsRepository
import java.security.SecureRandom
import kotlin.random.asKotlinRandom

/**
 * Manages user message passphrases (global key and per-conversation keys).
 * Automatically encrypts all keys using [LocalKeyCipher] before persisting them to [SettingsRepository].
 */
class UserMessageKeyManager(
    private val settingsRepository: SettingsRepository,
    private val localKeyCipher: LocalKeyCipher = LocalKeyCipher(),
) {
    private val random = SecureRandom().asKotlinRandom()

    fun getGlobalKey(): String {
        val enc = settingsRepository.current.globalMessageKeyEncrypted
        return localKeyCipher.decrypt(enc)
    }

    suspend fun setGlobalKey(plainKey: String) {
        val enc = localKeyCipher.encrypt(plainKey.trim())
        settingsRepository.update { it.copy(globalMessageKeyEncrypted = enc) }
    }

    fun getConversationKey(conversationKey: String): String? {
        val map = settingsRepository.current.conversationKeysEncrypted
        val key = map[conversationKey] ?: if (conversationKey.contains(':')) map[conversationKey.substringAfter(':')] else null
        val enc = key ?: return null
        val dec = localKeyCipher.decrypt(enc)
        return dec.takeIf { it.isNotBlank() }
    }

    suspend fun setConversationKey(conversationKey: String, plainKey: String?) {
        val trimmed = plainKey?.trim().orEmpty()
        val currentMap = settingsRepository.current.conversationKeysEncrypted.toMutableMap()
        if (trimmed.isBlank()) {
            currentMap.remove(conversationKey)
            if (conversationKey.contains(':')) {
                currentMap.remove(conversationKey.substringAfter(':'))
            }
        } else {
            currentMap[conversationKey] = localKeyCipher.encrypt(trimmed)
        }
        settingsRepository.update { it.copy(conversationKeysEncrypted = currentMap) }
    }

    fun getConversationEncryptionMode(conversationKey: String): EncryptionMode? {
        val map = settingsRepository.current.conversationEncryptionModes
        val str = map[conversationKey] ?: if (conversationKey.contains(':')) map[conversationKey.substringAfter(':')] else null
        return str?.let { runCatching { EncryptionMode.valueOf(it) }.getOrNull() }
    }

    suspend fun setConversationEncryptionMode(conversationKey: String, mode: EncryptionMode?) {
        val currentMap = settingsRepository.current.conversationEncryptionModes.toMutableMap()
        if (mode == null) {
            currentMap.remove(conversationKey)
            if (conversationKey.contains(':')) {
                currentMap.remove(conversationKey.substringAfter(':'))
            }
        } else {
            currentMap[conversationKey] = mode.name
        }
        settingsRepository.update { it.copy(conversationEncryptionModes = currentMap) }
    }

    fun getCandidateKeys(conversationKey: String?): List<String> {
        val privateKey = conversationKey?.let { getConversationKey(it) }
        val globalKey = getGlobalKey().takeIf { it.isNotBlank() }
        return listOfNotNull(privateKey, globalKey).distinct()
    }

    fun generateStrongKey(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$"
        return (1..16).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}
