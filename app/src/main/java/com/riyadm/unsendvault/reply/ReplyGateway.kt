package com.riyadm.unsendvault.reply

/**
 * Sends a reply through the chat app's *own* notification reply action (the same RemoteInput an Android Auto / Wear OS
 * reply uses). UnsendVault never talks to any messaging server. A reply is possible while that app's notification
 * for the chat is showing, or via a cached reply action if the notification was dismissed (until the source app cancels
 * the underlying PendingIntent).
 */
interface ReplyGateway {
    suspend fun availability(packageName: String, conversationKey: String): ReplyAvailability

    /** Hands [text] to the app's reply action. [ReplyResult.SENT] means the app accepted the intent, not that it delivered. */
    suspend fun send(packageName: String, conversationKey: String, text: String): ReplyResult
}

enum class ReplyAvailability {
    /** An active or cached free-text reply action is available for this chat. */
    AVAILABLE,

    /** No active or cached notification action for this chat offers a reply (or the app has no reply action). */
    NO_NOTIFICATION,

    /** UnsendVault's listener isn't connected (capture paused or Notification Access off), so it can't see notifications. */
    LISTENER_OFF,
}

enum class ReplyResult { SENT, UNAVAILABLE, FAILED, EMPTY }
