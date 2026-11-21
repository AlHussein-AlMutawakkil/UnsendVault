package com.riyadm.unsendvault.ui.common

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.os.ConfigurationCompat
import java.util.Locale

object LocaleHelper {
    /**
     * Resolves the target [Locale] for [languageCode].
     * An empty string means following the system default language.
     */
    fun resolveLocale(languageCode: String): Locale {
        return if (languageCode.isNotBlank()) {
            Locale.forLanguageTag(languageCode)
        } else {
            val sysLocales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
            sysLocales.get(0) ?: Locale.getDefault()
        }
    }

    /**
     * Applies the desired app language.
     * On Android 13+ (API 33+), delegates directly to the framework [LocaleManager].
     * On older releases, updates the [Resources] configuration and layout direction.
     */
    fun applyLocale(context: Context, languageCode: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
            val currentLocales = localeManager.applicationLocales
            val targetLocales = if (languageCode.isBlank()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(languageCode)
            }
            if (currentLocales.toLanguageTags() != targetLocales.toLanguageTags()) {
                localeManager.applicationLocales = targetLocales
            }
        } else {
            val target = resolveLocale(languageCode)
            Locale.setDefault(target)
            val res = context.resources
            val config = Configuration(res.configuration)
            config.setLocale(target)
            config.setLayoutDirection(target)
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
        }
    }

    /**
     * Wraps [context] with the target locale configuration (for pre-API 33 attachBaseContext).
     */
    fun wrapContext(context: Context, languageCode: String): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || languageCode.isBlank()) {
            return context
        }
        val target = resolveLocale(languageCode)
        Locale.setDefault(target)
        val config = Configuration(context.resources.configuration)
        config.setLocale(target)
        config.setLayoutDirection(target)
        return context.createConfigurationContext(config)
    }
}
