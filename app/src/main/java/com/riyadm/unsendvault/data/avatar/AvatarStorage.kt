package com.riyadm.unsendvault.data.avatar

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.StatusBarNotification
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Persists contact avatars (extracted from incoming notification [Notification.getLargeIcon] or extras)
 * to local app storage, allowing the chat UI to display contact profile photos like native messaging apps.
 */
class AvatarStorage(private val context: Context) {
    private val dir: File = File(context.filesDir, "avatars").apply { mkdirs() }

    /** Returns the local image file for [packageName] and [conversationKey], or null if not yet cached. */
    fun avatarFile(packageName: String, conversationKey: String): File? {
        val file = File(dir, "${fileName(packageName, conversationKey)}.png")
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Extracts the contact/group large icon from [sbn] and saves it locally under [conversationKey].
     */
    fun saveFromNotification(sbn: StatusBarNotification, conversationKey: String) {
        val pkg = sbn.packageName ?: return
        val bitmap = extractBitmap(sbn) ?: return
        val target = File(dir, "${fileName(pkg, conversationKey)}.png")
        try {
            val temp = File(dir, "${target.name}.tmp")
            FileOutputStream(temp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            if (temp.exists()) {
                temp.renameTo(target)
            }
        } catch (_: Exception) {
        }
    }

    private fun extractBitmap(sbn: StatusBarNotification): Bitmap? {
        val notification = sbn.notification ?: return null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notification.getLargeIcon()?.let { icon ->
                    val drawable = icon.loadDrawable(context)
                    val bmp = drawableToBitmap(drawable)
                    if (bmp != null) return bmp
                }
            }
            @Suppress("DEPRECATION")
            notification.extras?.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON)?.let { return it }
            @Suppress("DEPRECATION")
            notification.extras?.getParcelable<Bitmap>("android.largeIcon.big")?.let { return it }
        } catch (_: Exception) {
        }
        return null
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bmp
    }

    private fun fileName(packageName: String, conversationKey: String): String {
        val input = "$packageName:$conversationKey"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "${packageName.hashCode()}_${conversationKey.hashCode()}"
        }
    }
}
