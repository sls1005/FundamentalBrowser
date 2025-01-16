package test.sls1005.projects.fundamentalbrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import com.google.android.material.switchmaterial.SwitchMaterial
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity

class SettingsActivity : ConfiguratedActivity() {
    @SuppressLint("CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<SwitchMaterial>(R.id.switch_accept_cookies).setOnCheckedChangeListener { _, checked ->
            CookieManager.getInstance().setAcceptCookie(checked)
            (this@SettingsActivity).findViewById<SwitchMaterial>(R.id.switch_accept_3rd_party_cookies).apply {
                setEnabled(checked)
                if (!checked) {
                    shouldAccept3rdPartyCookies = false
                    setChecked(false)
                }
            }
        }
        findViewById<SwitchMaterial>(R.id.switch_allow_js).setOnCheckedChangeListener { _, checked ->
            shouldUseJavaScript = checked
        }
        findViewById<SwitchMaterial>(R.id.switch_load_images).setOnCheckedChangeListener { _, checked ->
            shouldLoadImages = checked
        }
        findViewById<SwitchMaterial>(R.id.switch_load_resources).setOnCheckedChangeListener { _, checked ->
            shouldLoadResources = checked
        }
        findViewById<SwitchMaterial>(R.id.switch_foreground_logging).setOnCheckedChangeListener { _, checked ->
            foregroundLoggingEnabled = checked
        }
        findViewById<SwitchMaterial>(R.id.switch_accept_3rd_party_cookies).setOnCheckedChangeListener { _, checked ->
            shouldAccept3rdPartyCookies = checked
        }
        findViewById<Button>(R.id.button_clear_cache).setOnClickListener {
            WebView(this@SettingsActivity).clearCache(true)
        }
        findViewById<Button>(R.id.button_clear_usernames_and_passwords).setOnClickListener {
            WebViewDatabase.getInstance(this@SettingsActivity).clearHttpAuthUsernamePassword()
        }
        findViewById<Button>(R.id.button_clear_cookies).setOnClickListener {
            CookieManager.getInstance().removeAllCookies(null)
        }
        findViewById<TextView>(R.id.max_log_msgs).setOnClickListener {
            (AlertDialog.Builder(this@SettingsActivity).apply {
                val v = layoutInflater.inflate(R.layout.number_field, null)
                setView(v)
                v.findViewById<TextView>(R.id.number_field).text = maxLogMsgs.toString()
                setPositiveButton(getString(R.string.ok)) {_, _ ->
                    val input = v.findViewById<TextView>(R.id.number_field).text.toString()
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
                create()
            }).show()
        }
        findViewById<TextView>(R.id.search_url).setOnClickListener {
            (AlertDialog.Builder(this@SettingsActivity).apply {
                val v = layoutInflater.inflate(R.layout.url_field, null)
                setView(v)
                v.findViewById<TextView>(R.id.url_field).text = searchURL
                setPositiveButton(getString(R.string.ok)) {_, _ ->
                    searchURL = v.findViewById<TextView>(R.id.url_field).text.toString()
                    findViewById<TextView>(R.id.search_url).text = searchURL.let {
                        if (it.isEmpty()) {
                            getString(R.string.none)
                        } else {
                            it
                        }
                    }
                }
                setNegativeButton(getString(R.string.cancel), {_, _ -> })
                create()
            }).show()
        }
        findViewById<Button>(R.id.button_doc).setOnClickListener {
            startActivity(Intent(this, DocumentationActivity::class.java))
        }
        findViewById<Button>(R.id.button_libraries).setOnClickListener {
            startActivity(
                Intent(this, OssLicensesMenuActivity::class.java).apply {
                    putExtra("title", getString(R.string.libs_title))
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<SwitchMaterial>(R.id.switch_allow_js).setChecked(shouldUseJavaScript)
        findViewById<SwitchMaterial>(R.id.switch_load_images).setChecked(shouldLoadImages)
        findViewById<SwitchMaterial>(R.id.switch_load_resources).setChecked(shouldLoadResources)
        findViewById<SwitchMaterial>(R.id.switch_foreground_logging).setChecked(foregroundLoggingEnabled)
        findViewById<SwitchMaterial>(R.id.switch_accept_cookies).apply {
            CookieManager.getInstance().acceptCookie().also {
                setChecked(it)
                (this@SettingsActivity).findViewById<SwitchMaterial>(R.id.switch_accept_3rd_party_cookies).apply {
                    setEnabled(it)
                    if (!it) {
                        shouldAccept3rdPartyCookies = false
                        setChecked(false)
                    }
                }
            }
        }
        findViewById<TextView>(R.id.max_log_msgs).text = maxLogMsgs.toString()
        findViewById<TextView>(R.id.search_url).text = searchURL.let {
            if (it.isEmpty()) {
                getString(R.string.none)
            } else {
                it
            }
        }
    }
}