package test.sls1005.projects.fundamentalbrowser

import android.content.Intent
import android.os.Bundle
import android.view.View.VISIBLE
import android.view.View.GONE
import android.view.View.OnClickListener
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import com.mikepenz.aboutlibraries.LibsBuilder
import java.util.Locale

class SettingsActivity : ConfiguratedActivity() {
    private class Language(
        val name: String,
        val code: String
    ) {
        override fun toString(): String {
            return name
        }
    }

    private class Weight(
        val defaultStr: String,
        val value: Float
    ) {
        override fun toString(): String {
            return value.let {
                if (it == 1.0f) {
                    defaultStr
                } else {
                    it.toString()
                }
            }
        }
    }

    public val clickListener = OnClickListener { view ->
        when(view.id) {
            R.id.button_clear_cache -> run {
                WebView(this@SettingsActivity).clearCache(true)
            }
            R.id.button_clear_usernames_and_passwords -> run {
                WebViewDatabase.getInstance(this@SettingsActivity).clearHttpAuthUsernamePassword()
            }
            R.id.button_clear_cookies -> run {
                CookieManager.getInstance().removeAllCookies(null)
            }
            R.id.button_add_language -> run {
                showDiaLogForAddingLanguage()
            }
            R.id.button_clear_languages -> run {
                saveLanguageTags("")
                findViewById<TextView>(R.id.language_tags).text = getString(R.string.tap_to_set)
            }
            R.id.button_doc -> run {
                startActivity(Intent(this@SettingsActivity, DocumentationActivity::class.java))
            }
            R.id.button_view_privacy_policy -> run {
                showPrivacyPolicy()
            }
            R.id.button_libraries -> run {
                LibsBuilder().start(this@SettingsActivity)
            }
            R.id.max_log_msgs -> run {
                showDialogForSettingMaxLogs()
            }
            R.id.search_url -> run {
                showDialogForSettingSearchUrl()
            }
            R.id.user_agent -> run {
                showDialogForSettingUserAgent()
            }
            R.id.language_tags -> run {
                confirmManuallySettingLanguage()
            }
        }
    }
    public val checkedChangeListener = OnCheckedChangeListener { button, checked ->
        when (button.id) {
            R.id.switch_accept_cookies -> run {
                CookieManager.getInstance().setAcceptCookie(checked)
                (this@SettingsActivity).findViewById<SwitchMaterial>(R.id.switch_accept_3rd_party_cookies).apply {
                    setEnabled(checked)
                    if (!checked) {
                        shouldAccept3rdPartyCookies = false
                        setChecked(false)
                    }
                }
            }
            R.id.switch_accept_3rd_party_cookies -> run {
                shouldAccept3rdPartyCookies = checked
            }
            R.id.switch_allow_js -> run {
                shouldUseJavaScript = checked
            }
            R.id.switch_load_images -> run {
                shouldLoadImages = checked
            }
            R.id.switch_load_resources -> run {
                shouldLoadResources = checked
            }
            R.id.switch_foreground_logging -> run {
                foregroundLoggingEnabled = checked
            }
            R.id.switch_remove_lf_and_space_from_url -> run {
                shouldRemoveLfAndSpacesFromUrl = checked
            }
            R.id.switch_desktop_mode -> run {
                desktopMode = checked
                findViewById<TextView>(R.id.desktop_mode_extra_text).apply {
                    visibility = if (desktopMode) { VISIBLE } else { GONE }
                }
            }
            R.id.switch_enable_custom_user_agent -> run {
                useCustomUserAgent = checked
                showUserAgentIfApplicable()
            }
            R.id.switch_enable_custom_language_setting -> run {
                manuallySetLanguageTags = checked
                showLanguageTagsIfApplicable()
            }
            R.id.switch_show_button_run -> run {
                shouldDisplayRunButton = checked
            }
            R.id.switch_clear_log_when_running_script -> run {
                shouldClearLogWhenRunningScript = checked
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        listOf(
            R.id.switch_accept_cookies,
            R.id.switch_accept_3rd_party_cookies,
            R.id.switch_allow_js,
            R.id.switch_load_images,
            R.id.switch_load_resources,
            R.id.switch_foreground_logging,
            R.id.switch_remove_lf_and_space_from_url,
            R.id.switch_desktop_mode,
            R.id.switch_enable_custom_user_agent,
            R.id.switch_enable_custom_language_setting,
            R.id.switch_show_button_run,
            R.id.switch_clear_log_when_running_script
        ).forEach { id ->
            findViewById<SwitchMaterial>(id).setOnCheckedChangeListener(checkedChangeListener)
        }
        listOf(
            R.id.button_clear_cache,
            R.id.button_clear_usernames_and_passwords,
            R.id.button_clear_cookies,
            R.id.button_add_language,
            R.id.button_clear_languages,
            R.id.button_doc,
            R.id.button_view_privacy_policy,
            R.id.button_libraries
        ).forEach { id ->
            findViewById<Button>(id).setOnClickListener(clickListener)
        }
        listOf(
            R.id.max_log_msgs,
            R.id.search_url,
            R.id.user_agent,
            R.id.language_tags
        ).forEach { id ->
            findViewById<TextView>(id).setOnClickListener(clickListener)
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<SwitchMaterial>(R.id.switch_accept_cookies).apply {
            CookieManager.getInstance().acceptCookie().also { checked ->
                setChecked(checked)
                (this@SettingsActivity).findViewById<SwitchMaterial>(R.id.switch_accept_3rd_party_cookies).apply {
                    setEnabled(checked)
                    if (!checked) {
                        shouldAccept3rdPartyCookies = false
                        setChecked(false)
                    } else {
                        setChecked(shouldAccept3rdPartyCookies)
                    }
                }
            }
        }
        listOf(
            Pair(R.id.switch_allow_js, shouldUseJavaScript),
            Pair(R.id.switch_load_images, shouldLoadImages),
            Pair(R.id.switch_load_resources, shouldLoadResources),
            Pair(R.id.switch_foreground_logging, foregroundLoggingEnabled),
            Pair(R.id.switch_remove_lf_and_space_from_url, shouldRemoveLfAndSpacesFromUrl),
            Pair(R.id.switch_desktop_mode, desktopMode),
            Pair(R.id.switch_enable_custom_user_agent, useCustomUserAgent),
            Pair(R.id.switch_enable_custom_language_setting, manuallySetLanguageTags),
            Pair(R.id.switch_show_button_run, shouldDisplayRunButton),
            Pair(R.id.switch_clear_log_when_running_script, shouldClearLogWhenRunningScript)
        ).forEach { it ->
            val (id, flag) = it
            findViewById<SwitchMaterial>(id).setChecked(flag)
        }
        findViewById<TextView>(R.id.desktop_mode_extra_text).apply {
            visibility = if (desktopMode) { VISIBLE } else { GONE }
        }
        findViewById<TextView>(R.id.max_log_msgs).text = maxLogMsgs.toString()
        findViewById<TextView>(R.id.search_url).text = searchURL.let {
            if (it.isEmpty()) {
                getString(R.string.none)
            } else {
                it
            }
        }
        showUserAgentIfApplicable()
        showLanguageTagsIfApplicable()
    }

    private fun showUserAgentIfApplicable() {
        if (useCustomUserAgent) {
            findViewById<TextView>(R.id.user_agent).apply {
                visibility = VISIBLE
            }.also {
                it.text = getStoredUserAgent().let {
                    if (it == null || it.isEmpty()) {
                        getString(R.string.tap_to_set)
                    } else {
                        it
                    }
                }
            }
            findViewById<TextView>(R.id.user_agent_extra_text).visibility = VISIBLE
        } else {
            listOf(
                R.id.user_agent,
                R.id.user_agent_extra_text
            ).forEach { id ->
                findViewById<TextView>(id).visibility = GONE
            }
        }
    }

    private fun showLanguageTagsIfApplicable() {
        if (manuallySetLanguageTags) {
            findViewById<TextView>(R.id.language_tags).apply {
                visibility = VISIBLE
            }.also {
                it.text = getStoredLanguageTags().let {
                    if (it == null || it.isEmpty()) {
                        getString(R.string.tap_to_set)
                    } else {
                        it
                    }
                }
            }
            listOf(
                R.id.button_add_language,
                R.id.button_clear_languages
            ).forEach { id ->
                findViewById<Button>(id).apply {
                    visibility = VISIBLE
                    setEnabled(true)
                }
            }
            findViewById<TextView>(R.id.language_tags_extra_text).visibility = VISIBLE
        } else {
            listOf(
                R.id.language_tags,
                R.id.language_tags_extra_text
            ).forEach { id ->
                findViewById<TextView>(id).visibility = GONE
            }
            listOf(
                R.id.button_add_language,
                R.id.button_clear_languages
            ).forEach { id ->
                findViewById<Button>(id).apply {
                    setEnabled(false)
                    visibility = GONE
                }
            }
        }
    }

    private fun showDialogForSettingMaxLogs() {
        (AlertDialog.Builder(this).apply {
            val v = layoutInflater.inflate(R.layout.number_field, null)
            setView(v)
            v.findViewById<EditText>(R.id.number_field).text.apply {
                clear()
                append(maxLogMsgs.toString())
            }
            setPositiveButton(getString(R.string.ok)) {_, _ ->
                val input = v.findViewById<EditText>(R.id.number_field).text.toString()
                if (input.isNotEmpty()) {
                    try {
                        maxLogMsgs = input.toInt()
                    } catch (_: NumberFormatException) {
                        return@setPositiveButton
                    }
                    findViewById<TextView>(R.id.max_log_msgs).text = maxLogMsgs.toString()
                }
            }
            setNegativeButton(getString(R.string.cancel), {_, _ -> })
        }).create().show()
    }

    private fun showDialogForSettingSearchUrl() {
        (AlertDialog.Builder(this).apply {
            val v = layoutInflater.inflate(R.layout.url_field, null)
            setView(v)
            v.findViewById<EditText>(R.id.url_field).text.apply {
                clear()
                append(searchURL)
            }
            setPositiveButton(getString(R.string.ok)) {_, _ ->
                searchURL = v.findViewById<EditText>(R.id.url_field).text.toString()
                findViewById<TextView>(R.id.search_url).text = searchURL.let {
                    if (it.isEmpty()) {
                        getString(R.string.none)
                    } else {
                        it
                    }
                }
            }
            setNegativeButton(getString(R.string.cancel), {_, _ -> })
        }).create().show()
    }

    private fun showDialogForSettingUserAgent() {
        (AlertDialog.Builder(this).apply {
            val v = layoutInflater.inflate(R.layout.string_field, null)
            setView(v)
            v.findViewById<EditText>(R.id.str_field).text.apply {
                clear()
                append(getStoredUserAgent() ?: "")
            }
            setPositiveButton(getString(R.string.ok)) {_, _ ->
                v.findViewById<EditText>(R.id.str_field).text.toString().also {
                    saveUserAgent(it)
                    findViewById<TextView>(R.id.user_agent).text = it.let {
                        if (it.isEmpty()) {
                            getString(R.string.tap_to_set)
                        } else {
                            it
                        }
                    }
                }
            }
            setNegativeButton(getString(R.string.cancel), {_, _ -> })
        }).create().show()
    }

    private fun showDialogForManuallySettingLanguageTags() {
        (AlertDialog.Builder(this).apply {
            val v = layoutInflater.inflate(R.layout.string_field, null)
            setView(v)
            v.findViewById<EditText>(R.id.str_field).text.apply {
                clear()
                append(getStoredLanguageTags() ?: "")
            }
            setPositiveButton(getString(R.string.ok)) {_, _ ->
                v.findViewById<EditText>(R.id.str_field).text.toString().also {
                    saveLanguageTags(it)
                    findViewById<TextView>(R.id.language_tags).text = it.let {
                        if (it.isEmpty()) {
                            getString(R.string.tap_to_set)
                        } else {
                            it
                        }
                    }
                }
            }
            setNegativeButton(getString(R.string.cancel), {_, _ -> })
        }).create().show()
    }

    private fun showDiaLogForAddingLanguage() {
        (AlertDialog.Builder(this).apply {
            val v = layoutInflater.inflate(R.layout.language_selection, null)
            setView(v.apply {
                findViewById<Spinner>(R.id.language).setAdapter(
                    ArrayAdapter<Language>(this@SettingsActivity, android.R.layout.simple_spinner_item).apply {
                        Locale.getAvailableLocales().forEach { loc ->
                            add(Language(loc.displayName, loc.toLanguageTag()))
                        }
                    }
                )
                findViewById<Spinner>(R.id.weight).setAdapter(
                    ArrayAdapter<Weight>(this@SettingsActivity, android.R.layout.simple_spinner_item).apply {
                        for (k in 10 downTo 1) {
                            add(Weight(getString(R.string.text_default), k.toFloat() / 10f))
                        }
                    }
                )
            })
            setPositiveButton(getString(R.string.ok)) {_, _ ->
                val code = v.findViewById<Spinner>(R.id.language).selectedItem.let {
                    if (it is Language) {
                        it.code
                    } else {
                        ""
                    }
                }
                if (code.isNotEmpty()) {
                    val q = v.findViewById<Spinner>(R.id.weight).selectedItem.let {
                        if (it is Weight) {
                            it.value.let { v ->
                                if (v == 1.0f) {
                                    ""
                                } else {
                                    v.toString()
                                }
                            }
                        } else {
                            ""
                        }
                    }
                    val languages = getStoredLanguageTags() ?: ""
                    findViewById<TextView>(R.id.language_tags).text = (buildString(languages.length + code.length + q.length) {
                        if (languages.isNotEmpty()) {
                            append(languages)
                            append(", ")
                        }
                        append(code)
                        if (q.isNotEmpty()) {
                            append(";q=")
                            append(q)
                        }
                    }).also {
                        saveLanguageTags(it)
                    }
                }
            }
            setNegativeButton(getString(R.string.cancel), {_, _ -> })
        }).create().show()
    }

    private fun confirmManuallySettingLanguage() {
        (AlertDialog.Builder(this).apply {
            setView(
                layoutInflater.inflate(R.layout.confirm_manually_setting_language, null)
            )
            setPositiveButton(getString(R.string.yes)) {_, _ ->
                showDialogForManuallySettingLanguageTags()
            }
            setNeutralButton(R.string.add_language) {_, _ ->
                showDiaLogForAddingLanguage()
            }
            setNegativeButton(R.string.no) {_, _ -> }
        }).create().show()
    }

    private fun showPrivacyPolicy() {
        (AlertDialog.Builder(this@SettingsActivity).apply {
            setView(
                layoutInflater.inflate(R.layout.privacy_policy, null)
            )
        }).create().show()
    }
}
// Reference: https://datatracker.ietf.org/doc/html/rfc7231