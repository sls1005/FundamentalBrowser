package test.sls1005.projects.fundamentalbrowser

import android.app.UiModeManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.io.File

private const val THEME_SETTING_LENGTH = 2

open class ThemedActivity : AppCompatActivity() {
    protected data class ThemeSettings(val theme: Byte, val useDynamicColors: Boolean)
    //                                   Byte 1           Byte 2, Bit 1, true if set
    // 0: Default (system or dark)
    // 1: Light
    // 2: Dark
    override fun onCreate(savedInstanceState: Bundle?) {
        val stored = getStoredOrDefaultThemeSettings()
        val theme = stored.theme
        setTheme(
            if (stored.useDynamicColors) {
                when (theme) {
                    (1).toByte() -> R.style.Theme_FundamentalBrowserLightDynamic
                    (2).toByte() -> R.style.Theme_FundamentalBrowserDarkDynamic
                    else -> R.style.Theme_FundamentalBrowserDynamic
                }
            } else {
                when (theme) {
                    (1).toByte() -> R.style.Theme_FundamentalBrowserLight
                    (2).toByte() -> R.style.Theme_FundamentalBrowserDark
                    else -> R.style.Theme_FundamentalBrowser
                }
            }
        )
        super.onCreate(savedInstanceState)
    }

    protected fun saveThemeSettings(settings: ThemeSettings) {
        val file = File(filesDir, "theme.bin")
        if (file.exists()) {
            file.delete()
        }
        file.writeBytes(
            byteArrayOf(
                settings.theme,
                bitsSetAccordingTo(
                    booleanArrayOf(settings.useDynamicColors)
                )
            )
        )
    }

    private fun getStoredThemeSettings(): ThemeSettings? {
        val file = File(filesDir, "theme.bin")
        if (! file.exists()) {
            return null
        }
        val data1 = file.readBytes()
        if (data1.size < THEME_SETTING_LENGTH) {
            file.delete()
            return null
        }
        return ThemeSettings(
            theme = data1[0],
            useDynamicColors = bitsSetOrNot(data1[1])[0]
        )
    }

    protected fun getStoredOrDefaultThemeSettings(): ThemeSettings {
        return getStoredThemeSettings() ?: ThemeSettings(theme = 0, useDynamicColors = true)
    }
}