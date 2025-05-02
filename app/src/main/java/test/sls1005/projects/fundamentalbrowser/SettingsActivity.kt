package test.sls1005.projects.fundamentalbrowser

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View.VISIBLE
import android.view.View.GONE
import android.view.View.OnClickListener
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.widget.ArrayAdapter
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
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

    private val clickListener = OnClickListener { view ->
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
            R.id.theme -> {
                showDialogForSettingTheme()
            }
        }
    }

    private val checkedChangeListener = OnCheckedChangeListener { button, checked ->
        when (button.id) {
            R.id.switch_accept_cookies -> run {
                shouldAcceptCookies = checked
                CookieManager.getInstance().setAcceptCookie(checked)
                (this@SettingsActivity).findViewById<MaterialSwitch>(R.id.switch_accept_3rd_party_cookies).apply {
                    visibility = if (checked) { VISIBLE } else { GONE }
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
                (this@SettingsActivity).findViewById<MaterialSwitch>(R.id.switch_allow_js_for_urls_from_other_apps).apply {
                    visibility = if (checked) { VISIBLE } else { GONE }
                    if (!checked) {
                        shouldAllowJSForUrlsFromOtherApps = false
                        setChecked(false)
                    }
                }
            }
            R.id.switch_allow_js_for_urls_from_other_apps -> run {
                shouldAllowJSForUrlsFromOtherApps = checked
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
            R.id.switch_ask_before_loading_url_from_another_app -> run {
                shouldAskBeforeLoadingUrlThatIsFromAnotherApp = checked
            }
            R.id.switch_remove_lf_and_space_from_url -> run {
                shouldRemoveLfAndSpacesFromUrl = checked
            }
            R.id.switch_desktop_mode -> run {
                desktopMode = checked
                findViewById<TextView>(R.id.desktop_mode_extra_text).visibility = if (checked) { VISIBLE } else { GONE }
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
                findViewById<TextView>(R.id.switch_show_button_run_extra_text).visibility = if (checked) { VISIBLE } else { GONE }
            }
            R.id.switch_clear_log_before_running_script -> run {
                shouldClearLogWhenRunningScript = checked
            }
            R.id.switch_autoscroll_log_msgs -> run {
                autoscrollLogMsgs = checked
            }
            R.id.switch_allow_http -> run {
                shouldAllowHTTP = checked
            }
            R.id.switch_show_advanced_developer_tools -> run {
                showAdvancedDeveloperTools = checked
                findViewById<TextView>(R.id.switch_show_advanced_developer_tools_extra_text).text = (
                    if (checked) {
                        val part1 = getString(R.string.switch_show_advanced_developer_tools_extra_text_part1)
                        val part2 = getString(R.string.switch_show_advanced_developer_tools_extra_text_part2)
                        buildString(part1.length + part2.length + 2) {
                            append(part1)
                            append("\n\n")
                            append(part2)
                        }
                    } else {
                        getString(R.string.switch_show_advanced_developer_tools_extra_text_part1)
                    }
                )
            }
            R.id.switch_ask_before_following_redirection -> run {
                shouldAskBeforeFollowingRedirection = checked
            }
            R.id.switch_disable_dynamic_colors -> run {
                getStoredOrDefaultThemeSettings().also { oldSettings ->
                    saveThemeSettings(
                        ThemeSettings(theme = oldSettings.theme, useDynamicColors = !checked)
                    )
                }
            }
            R.id.switch_auto_link_urls_in_log -> run {
                shouldLinkURLsInLog = checked
                findViewById<TextView>(R.id.switch_auto_link_urls_in_log_extra_text).visibility = if (checked) { VISIBLE } else { GONE }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        intArrayOf(
            R.id.switch_accept_cookies,
            R.id.switch_accept_3rd_party_cookies,
            R.id.switch_allow_js,
            R.id.switch_allow_js_for_urls_from_other_apps,
            R.id.switch_load_images,
            R.id.switch_load_resources,
            R.id.switch_foreground_logging,
            R.id.switch_ask_before_loading_url_from_another_app,
            R.id.switch_remove_lf_and_space_from_url,
            R.id.switch_desktop_mode,
            R.id.switch_enable_custom_user_agent,
            R.id.switch_enable_custom_language_setting,
            R.id.switch_show_button_run,
            R.id.switch_clear_log_before_running_script,
            R.id.switch_autoscroll_log_msgs,
            R.id.switch_allow_http,
            R.id.switch_show_advanced_developer_tools,
            R.id.switch_ask_before_following_redirection,
            R.id.switch_disable_dynamic_colors,
            R.id.switch_auto_link_urls_in_log
        ).forEach { id ->
            findViewById<MaterialSwitch>(id).setOnCheckedChangeListener(checkedChangeListener)
        }
        intArrayOf(
            R.id.button_clear_cache,
            R.id.button_clear_usernames_and_passwords,
            R.id.button_clear_cookies,
            R.id.button_add_language,
            R.id.button_clear_languages,
            R.id.button_doc,
            R.id.button_view_privacy_policy,
            R.id.button_libraries
        ).forEach { id ->
            findViewById<MaterialButton>(id).setOnClickListener(clickListener)
        }
        intArrayOf(
            R.id.max_log_msgs,
            R.id.search_url,
            R.id.user_agent,
            R.id.language_tags,
            R.id.theme
        ).forEach { id ->
            findViewById<TextView>(id).setOnClickListener(clickListener)
        }
    }

    override fun onResume() {
        super.onResume()
        arrayOf(
            Pair(R.id.switch_allow_js, shouldUseJavaScript),
            Pair(R.id.switch_load_images, shouldLoadImages),
            Pair(R.id.switch_load_resources, shouldLoadResources),
            Pair(R.id.switch_accept_cookies, shouldAcceptCookies),
            Pair(R.id.switch_ask_before_loading_url_from_another_app, shouldAskBeforeLoadingUrlThatIsFromAnotherApp),
            Pair(R.id.switch_foreground_logging, foregroundLoggingEnabled),
            Pair(R.id.switch_remove_lf_and_space_from_url, shouldRemoveLfAndSpacesFromUrl),
            Pair(R.id.switch_desktop_mode, desktopMode),
            Pair(R.id.switch_enable_custom_user_agent, useCustomUserAgent),
            Pair(R.id.switch_enable_custom_language_setting, manuallySetLanguageTags),
            Pair(R.id.switch_show_button_run, shouldDisplayRunButton),
            Pair(R.id.switch_clear_log_before_running_script, shouldClearLogWhenRunningScript),
            Pair(R.id.switch_autoscroll_log_msgs, autoscrollLogMsgs),
            Pair(R.id.switch_allow_http, shouldAllowHTTP),
            Pair(R.id.switch_show_advanced_developer_tools, showAdvancedDeveloperTools),
            Pair(R.id.switch_ask_before_following_redirection, shouldAskBeforeFollowingRedirection),
            Pair(R.id.switch_auto_link_urls_in_log, shouldLinkURLsInLog)
        ).forEach { it ->
            val (id, flag) = it
            findViewById<MaterialSwitch>(id).setChecked(flag)
        }
        findViewById<MaterialSwitch>(R.id.switch_allow_js_for_urls_from_other_apps).apply {
            visibility = if (shouldUseJavaScript) { VISIBLE } else { GONE }
            if (shouldUseJavaScript) {
                setChecked(shouldAllowJSForUrlsFromOtherApps)
            } else {
                shouldAllowJSForUrlsFromOtherApps = false
            }
        }
        findViewById<MaterialSwitch>(R.id.switch_accept_3rd_party_cookies).apply {
            visibility = if (shouldAcceptCookies) { VISIBLE } else { GONE }
            if (shouldAcceptCookies) {
                setChecked(shouldAccept3rdPartyCookies)
            } else {
                shouldAccept3rdPartyCookies = false
            }
        }
        getStoredOrDefaultThemeSettings().also { settings ->
            findViewById<MaterialSwitch>(R.id.switch_disable_dynamic_colors).setChecked(!settings.useDynamicColors)
            findViewById<TextView>(R.id.theme).text = when (settings.theme) {
                (1).toByte() -> getString(R.string.theme_light)
                (2).toByte() -> getString(R.string.theme_dark)
                else -> getString(R.string.theme_default)
            }
        }
        arrayOf(
            Pair(R.id.desktop_mode_extra_text, desktopMode),
            Pair(R.id.switch_show_button_run_extra_text, shouldDisplayRunButton),
        ).forEach { it ->
            val (id, flag) = it
            findViewById<TextView>(id).visibility = if (flag) { VISIBLE } else { GONE }
        }
        findViewById<TextView>(R.id.switch_show_advanced_developer_tools_extra_text).text = (
            if (showAdvancedDeveloperTools) {
                val part1 = getString(R.string.switch_show_advanced_developer_tools_extra_text_part1)
                val part2 = getString(R.string.switch_show_advanced_developer_tools_extra_text_part2)
                buildString(part1.length + part2.length + 2) {
                    append(part1)
                    append("\n\n")
                    append(part2)
                }
            } else {
                getString(R.string.switch_show_advanced_developer_tools_extra_text_part1)
            }
        )
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
            intArrayOf(
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
            intArrayOf(
                R.id.button_add_language,
                R.id.button_clear_languages
            ).forEach { id ->
                findViewById<MaterialButton>(id).apply {
                    visibility = VISIBLE
                    setEnabled(true)
                }
            }
            findViewById<TextView>(R.id.language_tags_extra_text).visibility = VISIBLE
        } else {
            intArrayOf(
                R.id.language_tags,
                R.id.language_tags_extra_text
            ).forEach { id ->
                findViewById<TextView>(id).visibility = GONE
            }
            intArrayOf(
                R.id.button_add_language,
                R.id.button_clear_languages
            ).forEach { id ->
                findViewById<MaterialButton>(id).apply {
                    setEnabled(false)
                    visibility = GONE
                }
            }
        }
    }

    private fun showDialogForSettingMaxLogs() {
        (object: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                val ctx = requireActivity()
                return (AlertDialog.Builder(ctx).apply {
                    val v = layoutInflater.inflate(R.layout.number_field, null)
                    setView(v)
                    v.findViewById<EditText>(R.id.number_field).text.apply {
                        clear()
                        append(maxLogMsgs.toString())
                    }
                    setPositiveButton(R.string.ok) {_, _ ->
                        val input = v.findViewById<EditText>(R.id.number_field).text.toString()
                        if (input.isNotEmpty()) {
                            try {
                                maxLogMsgs = input.toInt()
                            } catch (_: NumberFormatException) {
                                return@setPositiveButton
                            }
                            ctx.findViewById<TextView>(R.id.max_log_msgs).text = maxLogMsgs.toString()
                        }
                    }
                    setNegativeButton(R.string.cancel) {_, _ -> }
                }).create()
            }
        }).show(supportFragmentManager, null)
    }

    private fun showDialogForSettingTheme() {
        (object: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                val ctx = requireActivity()
                return (AlertDialog.Builder(ctx).apply {
                    val v = layoutInflater.inflate(R.layout.select_theme, null)
                    setView(v)
                    v.findViewById<RadioGroup>(R.id.theme_selection_radio_group).check(
                        when (getStoredOrDefaultThemeSettings().theme) {
                            (1).toByte() -> R.id.theme_option_light
                            (2).toByte() -> R.id.theme_option_dark
                            else -> R.id.theme_option_default
                        }
                    )
                    setPositiveButton(R.string.ok) {_, _ ->
                       val theme = when(v.findViewById<RadioGroup>(R.id.theme_selection_radio_group).checkedRadioButtonId) {
                           R.id.theme_option_light -> (1).toByte()
                           R.id.theme_option_dark -> (2).toByte()
                           else -> (0).toByte()
                       }
                       getStoredOrDefaultThemeSettings().also { oldSettings ->
                            saveThemeSettings(
                                ThemeSettings(theme = theme, useDynamicColors = oldSettings.useDynamicColors)
                            )
                        }
                        ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
                        ctx.finish()
                    }
                    setNegativeButton(R.string.cancel) {_, _ -> }
                }).create()
            }
        }).show(supportFragmentManager, null)
    }

    private fun showDialogForSettingSearchUrl() {
        (object: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                val ctx = requireActivity()
                return (AlertDialog.Builder(ctx).apply {
                    val v = layoutInflater.inflate(R.layout.url_field, null)
                    setView(v)
                    v.findViewById<EditText>(R.id.url_field).text.apply {
                        clear()
                        append(searchURL)
                    }
                    setPositiveButton(R.string.ok) {_, _ ->
                        searchURL = v.findViewById<EditText>(R.id.url_field).text.toString()
                        ctx.findViewById<TextView>(R.id.search_url).text = searchURL.let {
                            if (it.isEmpty()) {
                                ctx.getString(R.string.none)
                            } else {
                                it
                            }
                        }
                    }
                    setNegativeButton(R.string.cancel) {_, _ -> }
                }).create()
            }
        }).show(supportFragmentManager, null)
    }

    private fun showDialogForSettingUserAgent() {
        (object: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                val ctx = requireActivity()
                return (AlertDialog.Builder(ctx).apply {
                    val v = layoutInflater.inflate(R.layout.string_field, null)
                    setView(v)
                    v.findViewById<EditText>(R.id.str_field).text.apply {
                        clear()
                        append(getStoredUserAgent() ?: "")
                    }
                    setPositiveButton(R.string.ok) {_, _ ->
                        v.findViewById<EditText>(R.id.str_field).text.toString().also {
                            saveUserAgent(it)
                            ctx.findViewById<TextView>(R.id.user_agent).text = it.let {
                                if (it.isEmpty()) {
                                    getString(R.string.tap_to_set)
                                } else {
                                    it
                                }
                            }
                        }
                    }
                    setNegativeButton(R.string.cancel) {_, _ -> }
                }).create()
            }
        }).show(supportFragmentManager, null)
    }

    private fun showDialogForManuallySettingLanguageTags() {
        (object: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                val ctx = requireActivity()
                return (AlertDialog.Builder(ctx).apply {
                    val v = layoutInflater.inflate(R.layout.string_field, null)
                    setView(v)
                    v.findViewById<EditText>(R.id.str_field).text.apply {
                        clear()
                        append(getStoredLanguageTags() ?: "")
                    }
                    setPositiveButton(R.string.ok) {_, _ ->
                        v.findViewById<EditText>(R.id.str_field).text.toString().also {
                            saveLanguageTags(it)
                            ctx.findViewById<TextView>(R.id.language_tags).text = it.let {
                                if (it.isEmpty()) {
                                    ctx.getString(R.string.tap_to_set)
                                } else {
                                    it
                                }
                            }
                        }
                    }
                    setNegativeButton(R.string.cancel) {_, _ -> }
                }).create()
            }
        }).show(supportFragmentManager, null)
    }

    private fun showDiaLogForAddingLanguage() {
        (object: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                val ctx = requireActivity()
                return (AlertDialog.Builder(ctx).apply {
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
                    setPositiveButton(R.string.ok) {_, _ ->
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
                            ctx.findViewById<TextView>(R.id.language_tags).text = (buildString(languages.length + code.length + q.length) {
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
                    setNegativeButton(R.string.cancel) {_, _ -> }
                }).create()
            }
        }).show(supportFragmentManager, null)
    }

    private fun confirmManuallySettingLanguage() {
        (object: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                return (AlertDialog.Builder(requireActivity()).apply {
                    setView(
                        layoutInflater.inflate(R.layout.confirm_manually_setting_language, null)
                    )
                    setPositiveButton(R.string.yes) {_, _ ->
                        showDialogForManuallySettingLanguageTags()
                    }
                    setNeutralButton(R.string.add_language) {_, _ ->
                        showDiaLogForAddingLanguage()
                    }
                    setNegativeButton(R.string.no) {_, _ -> }
                }).create()
            }
        }).show(supportFragmentManager, null)
    }

    private fun showPrivacyPolicy() {
        (object: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                return (AlertDialog.Builder(requireActivity()).apply {
                    setView(
                        layoutInflater.inflate(R.layout.privacy_policy, null)
                    )
                }).create()
            }
        }).show(supportFragmentManager, null)
    }
}
// Reference: https://datatracker.ietf.org/doc/html/rfc7231