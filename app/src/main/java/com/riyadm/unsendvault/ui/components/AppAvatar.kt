package com.riyadm.unsendvault.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import com.riyadm.unsendvault.appContainer
import com.riyadm.unsendvault.apps.AppDirectory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Small in-memory cache of launcher icons. Misses are cached too, so absent packages are looked up once. */
internal object AppIconCache {
    private object Missing

    private val cache = LruCache<String, Any>(96)

    fun peek(packageName: String, sizePx: Int): ImageBitmap? = cache.get("$packageName@$sizePx") as? ImageBitmap

    fun load(context: Context, packageName: String, sizePx: Int): ImageBitmap? {
        val key = "$packageName@$sizePx"
        cache.get(key)?.let { return it as? ImageBitmap }
        // Current profile first, then the badged icon of the app in a work/other profile.
        val bitmap = try {
            AppDirectory(context).icon(packageName)?.toBitmap(sizePx, sizePx)?.asImageBitmap()
        } catch (_: RuntimeException) {
            null
        }
        cache.put(key, bitmap ?: Missing)
        return bitmap
    }
}

/**
 * The source app's launcher icon (loaded off the main thread), falling back to a lettered circle when the app is not
 * installed or not visible to UnsendVault. Decorative: the app name is always shown as text next to it.
 */
@Composable
fun AppAvatar(packageName: String, label: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState(AppIconCache.peek(packageName, sizePx), packageName, sizePx) {
        value = withContext(Dispatchers.IO) { AppIconCache.load(context.applicationContext, packageName, sizePx) }
    }
    val current = icon
    if (current != null) {
        Image(current, contentDescription = null, modifier = modifier.size(size).clip(CircleShape))
    } else {
        LetterAvatar(label, modifier, size)
    }
}

@Composable
fun LetterAvatar(label: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val avatarColors = remember {
        listOf(
            Color(0xFF7E57C2), // Purple (like WimLow)
            Color(0xFF1E88E5), // Blue
            Color(0xFF00897B), // Teal
            Color(0xFFE53935), // Red
            Color(0xFFFB8C00), // Orange
            Color(0xFF00ACC1), // Cyan
            Color(0xFF5E35B1), // Deep purple
            Color(0xFF3949AB), // Indigo
        )
    }
    val colorIndex = remember(label) {
        val hash = label.hashCode()
        (hash and 0x7FFFFFFF) % avatarColors.size
    }
    val bgColor = avatarColors[colorIndex]

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = bgColor,
        contentColor = Color.White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label.trim().firstOrNull()?.uppercase() ?: "?",
                style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/**
 * Avatar for conversation items: shows contact photo (from AvatarStorage) or letter avatar,
 * with a small badge of the source app icon in the corner.
 */
@Composable
fun ConversationAvatar(
    packageName: String,
    conversationKey: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val context = LocalContext.current
    val avatarStorage = remember(context) {
        try { context.appContainer.avatarStorage } catch (_: Exception) { null }
    }
    val avatarFile by produceState<File?>(null, packageName, conversationKey, avatarStorage) {
        value = if (avatarStorage != null) {
            withContext(Dispatchers.IO) {
                avatarStorage.avatarFile(packageName, conversationKey)
            }
        } else null
    }

    Box(modifier = modifier.size(size)) {
        val currentFile = avatarFile
        if (currentFile != null && currentFile.exists()) {
            val bitmap = remember(currentFile) {
                try {
                    BitmapFactory.decodeFile(currentFile.absolutePath)?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                LetterAvatar(label = label, modifier = Modifier.fillMaxSize(), size = size)
            }
        } else {
            LetterAvatar(label = label, modifier = Modifier.fillMaxSize(), size = size)
        }

        // Small badge of the source app
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp),
            shadowElevation = 2.dp,
        ) {
            AppAvatar(
                packageName = packageName,
                label = label,
                modifier = Modifier.fillMaxSize().padding(1.dp),
                size = 18.dp,
            )
        }
    }
}
