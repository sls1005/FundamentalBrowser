package test.sls1005.projects.fundamentalbrowser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.VISIBLE
import android.view.View.GONE
import android.view.ViewGroup.LayoutParams
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.google.android.material.snackbar.Snackbar

class MainActivity : ConfiguratedActivity() {
    private var previousTitle = ""
    private var currentURL = ""
    private var textToDisplayInUrlField = ""
    private val logMsgs = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<WebView>(R.id.view1).apply {
            settings.apply {
                javaScriptEnabled = shouldUseJavaScript
                blockNetworkImage = !shouldLoadImages
                blockNetworkLoads = !shouldLoadResources
                builtInZoomControls = true
                displayZoomControls = false
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    v: WebView,
                    req: WebResourceRequest
                ): Boolean {
                    if (req.isRedirect) {
                        val url = req.url.toString()
                        val msg = buildString {
                            append(req.method)
                            append(getString(R.string.space_redirected_space))
                            append(url)
                        }
                        synchronized(logMsgs) {
                            logMsgs.apply {
                                if (size >= maxLogMsgs) {
                                    removeFirst()
                                }
                                add(msg)
                            }
                        }
                        if (foregroundLoggingEnabled) {
                            showMsg(msg, v)
                        }
                    }
                    return false
                }
                override fun shouldInterceptRequest(v: WebView, req: WebResourceRequest): WebResourceResponse? {
                    val url = req.url.toString()
                    val msg = "${req.method} $url"
                    synchronized(logMsgs) {
                        logMsgs.apply {
                            if (size >= maxLogMsgs) {
                                removeFirst()
                            }
                            add(msg)
                        }
                    }
                    if (synchronized(
                            foregroundLoggingEnabled
                        ) { foregroundLoggingEnabled }
                    ) {
                        synchronized(v) {
                            showMsg(msg, v)
                        }
                    }
                    return super.shouldInterceptRequest(v, req)
                }
                override fun onPageFinished(v: WebView, url: String) {
                    with (this@MainActivity) {
                        title = v.title
                        findViewById<EditText>(R.id.url_field).apply {
                            if (visibility == VISIBLE) {
                                val e = isEnabled().also {
                                    if (it) {
                                        setEnabled(false)
                                    }
                                }
                                text.apply {
                                    if (url != toString()) {
                                        clear()
                                        append(url)
                                    }
                                }
                                if (e) {
                                    setEnabled(true)
                                }
                            }
                        }
                        if (currentURL != url) {
                            currentURL = url
                            textToDisplayInUrlField = url
                        }
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    synchronized(logMsgs) {
                        logMsgs.add(consoleMessage.message())
                        updateLogIfShowing()
                    }
                    return true
                }
                override fun onReceivedTitle(v: WebView, newTitle: String) {
                    if (previousTitle.isNotEmpty()) {
                        previousTitle = newTitle
                    } else {
                        (this@MainActivity).title = newTitle
                    }
                }
            }
        }.also {
            CookieManager.getInstance().setAcceptThirdPartyCookies(it, shouldAccept3rdPartyCookies)
        }
        findViewById<Button>(R.id.button_go).setOnClickListener {
            val self = findViewById<Button>(R.id.button_go).apply {
                setClickable(false)
            }
            val url = findViewById<EditText>(R.id.url_field).text.toString().let {
                if (shouldRemoveLfAndSpacesFromUrl) {
                    buildString(it.length / 2) {
                        for (c in it) {
                            if (c != ' ' && c != '\n') {
                                append(c)
                            }
                        }
                    }
                } else {
                    it
                }
            }
            currentURL = url
            textToDisplayInUrlField = url
            val v1 = findViewById<WebView>(R.id.view1)
            val showingLog = isShowingLog()
            val noPage = hasNotLoadedAnyPage()
            if (url.startsWith("javascript:", ignoreCase=true) && (showingLog || noPage)) {
                // Console mode
                logMsgs.clear()
                if (noPage && (!showingLog)) {
                    showLog()
                }
                v1.loadUrl(url)
            } else if (url.isEmpty()) {
                showMsg(getString(R.string.error3), self)
            } else {
                hideUrlBar()
                hideLogIfShowing()
                v1.loadUrl(url)
            }
            self.setClickable(true)
        }
        findViewById<Button>(R.id.button_decode).setOnClickListener {
            findViewById<EditText>(R.id.url_field).apply {
                text.apply {
                    toString().also {
                        if (it.isNotEmpty()) {
                            clear()
                            append(Uri.decode(it))
                        }
                    }
                }
            }
        }
        findViewById<Button>(R.id.button_clear).setOnClickListener {
            findViewById<EditText>(R.id.url_field).text.clear()
        }
        findViewById<Button>(R.id.button_restore).setOnClickListener {
            if (currentURL.isNotEmpty()) {
                findViewById<EditText>(R.id.url_field).text.apply {
                    clear()
                    append(currentURL)
                }
            }
        }
        findViewById<Button>(R.id.button_search).setOnClickListener {
            val self = findViewById<Button>(R.id.button_search)
            val rawInput = findViewById<EditText>(R.id.url_field).text.toString()
            if (searchURL.isEmpty()) {
                showMsg(getString(R.string.error1), self)
            } else if (rawInput.isEmpty()) {
                showMsg(getString(R.string.error2), self)
            } else {
                self.setClickable(false)
                val url = searchURL + Uri.encode(
                    rawInput.replace('\n', ' ')
                )
                hideUrlBar()
                hideLogIfShowing()
                currentURL = url
                textToDisplayInUrlField = url
                findViewById<WebView>(R.id.view1).loadUrl(url)
                self.setClickable(true)
            }
        }
        findViewById<Button>(R.id.button_copy).setOnClickListener {
            findViewById<EditText>(R.id.url_field).run {
                if (hasSelection()) {
                    text.subSequence(selectionStart, selectionEnd)
                } else {
                    text
                }
            }.toString().also {
                if (it != "") {
                    getSystemService(CLIPBOARD_SERVICE).apply {
                        if (this is ClipboardManager) {
                            setPrimaryClip(
                                ClipData.newPlainText("", it)
                            )
                        }
                    }
                }
            }
        }
        findViewById<Button>(R.id.button_paste).setOnClickListener {
            val clip = getSystemService(CLIPBOARD_SERVICE).run {
                if (this is ClipboardManager) {
                    getPrimaryClip() // ?: null
                } else {
                    null
                }
            }
            if (clip != null) {
                findViewById<EditText>(R.id.url_field).apply {
                    val start = selectionStart
                    if (start != -1) {
                        if (hasSelection()) {
                            val end = selectionEnd
                            text.delete(start, end)
                        }
                        for (i in (clip.itemCount - 1) downTo 0) {
                            clip.getItemAt(i).coerceToText(this@MainActivity).also {
                                text.insert(start, it)
                            }
                        }
                    } else {
                        for (i in 0 ..< clip.itemCount) {
                            clip.getItemAt(i).coerceToText(this@MainActivity).also {
                                text.append(it)
                            }
                        }
                    }
                }
            }
        }
        findViewById<Button>(R.id.button_run).setOnClickListener {
            val self = findViewById<Button>(R.id.button_run).apply {
                setClickable(false)
            }
            val code = findViewById<EditText>(R.id.url_field).text.toString()
            logMsgs.clear()
            if (! isShowingLog()) {
                showLog()
            }
            findViewById<WebView>(R.id.view1).loadUrl("javascript:" + code)
            self.setClickable(true)
        }
        findViewById<HorizontalScrollView>(R.id.button_area).postDelayed({
            val self = findViewById<HorizontalScrollView>(R.id.button_area)
            self.fullScroll(View.FOCUS_RIGHT)
        }, 400)
        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (findViewById<TextView>(R.id.log).visibility == VISIBLE) {
                        hideLog()
                    } else {
                        findViewById<WebView>(R.id.view1).apply {
                            if (canGoBack()) {
                                goBack()
                            } else {
                                (this@MainActivity).finish()
                            }
                        }
                    }
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        findViewById<WebView>(R.id.view1).apply {
            settings.apply {
                javaScriptEnabled = shouldUseJavaScript
                blockNetworkImage = !shouldLoadImages
                blockNetworkLoads = !shouldLoadResources
            }
        }.also {
            CookieManager.getInstance().setAcceptThirdPartyCookies(it, shouldAccept3rdPartyCookies)
        }
        showRunButtonIfApplicable()
        //logMsgs.add("$shouldAccept3rdPartyCookies $shouldRemoveLfAndSpacesFromUrl $maxLogMsgs $searchURL")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu1, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (! super.onPrepareOptionsMenu(menu)) {
            return false
        }
        val v1 = findViewById<WebView>(R.id.view1)
        listOf(
            Pair(
                R.id.action_enable_disable_js,
                v1.settings.javaScriptEnabled
            ),
            Pair(
                R.id.action_enable_disable_images,
                !v1.settings.blockNetworkImage
            ),
            Pair(
                R.id.action_enable_disable_resources,
                !v1.settings.blockNetworkLoads
            ),
            Pair(
                R.id.action_enable_disable_foreground_logging,
                foregroundLoggingEnabled
            )
        ).forEach { it ->
            val (id, state) = it
            menu.findItem(id).setChecked(state)
        }
        listOf(
            Pair(
                R.id.group1,
                (!isShowingUrlBar()) && currentURL.isNotEmpty()
            ),
            Pair(
                R.id.group2,
                !hasNotLoadedAnyPage()
            ),
            Pair(
                R.id.group3,
                logMsgs.isNotEmpty()
            )
        ).forEach { it ->
            val (id, state) = it
            menu.setGroupVisible(id, state)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_display_or_hide_url_bar -> run {
                if (isShowingUrlBar()) {
                    hideUrlBar()
                } else {
                    showUrlBar()
                }
                (true)
            }
            R.id.action_edit_url -> run {
                showUrlBarIfHidden()
                (true)
            }
            R.id.action_refresh -> run {
                if (! hasNotLoadedAnyPage()) {
                    findViewById<WebView>(R.id.view1).loadUrl(currentURL)
                }
                updateLogIfShowing()
                (true)
            }
            R.id.action_enable_disable_js -> run {
                shouldUseJavaScript = !item.isChecked()
                val v1 = findViewById<WebView>(R.id.view1).apply {
                    settings.javaScriptEnabled = shouldUseJavaScript
                }
                item.setChecked(v1.settings.javaScriptEnabled)
                (true)
            }
            R.id.action_enable_disable_images -> run {
                shouldLoadImages = !item.isChecked()
                val v1 = findViewById<WebView>(R.id.view1).apply {
                    settings.blockNetworkImage = !shouldLoadImages
                }
                item.setChecked(!v1.settings.blockNetworkImage)
                (true)
            }
            R.id.action_enable_disable_resources -> run {
                shouldLoadResources = !item.isChecked()
                val v1 = findViewById<WebView>(R.id.view1).apply {
                    settings.blockNetworkLoads = !shouldLoadResources
                }
                item.setChecked(!v1.settings.blockNetworkLoads)
                (true)
            }
            R.id.action_enable_disable_foreground_logging -> run {
                foregroundLoggingEnabled = !item.isChecked()
                item.setChecked(foregroundLoggingEnabled)
                (true)
            }
            R.id.action_settings -> run {
                startActivity(
                    Intent(this@MainActivity, SettingsActivity::class.java)
                )
                (true)
            }
            R.id.action_view_log -> run {
                if (isShowingLog()) {
                    updateLogIfShowing()
                } else {
                    showLog()
                }
                (true)
            }
            else -> false
        }
    }

    private fun showUrlBar() {
        findViewById<EditText>(R.id.url_field).apply {
            visibility = VISIBLE
            setEnabled(false)
            text.clear()
            if (textToDisplayInUrlField.isEmpty() && currentURL.isNotEmpty()) {
                text.append(currentURL)
            } else {
                text.append(textToDisplayInUrlField)
            }
            setEnabled(true)
        }
        findViewById<HorizontalScrollView>(R.id.button_area).visibility = VISIBLE
        listOf(
            R.id.button_decode,
            R.id.button_clear,
            R.id.button_restore,
            R.id.button_search,
            R.id.button_copy,
            R.id.button_paste,
            R.id.button_go
        ).forEach { id ->
            findViewById<Button>(id).apply {
                visibility = VISIBLE
                setEnabled(true)
            }
        }
        showRunButtonIfApplicable()
        for (v in listOf(
            findViewById<WebView>(R.id.view1),
            findViewById<TextView>(R.id.log)
        )) {
            if (v.visibility == VISIBLE) {
                v.layoutParams!!.height = LayoutParams.MATCH_PARENT
                break
            }
        }
    }

    private fun hideUrlBar() {
        listOf(
            R.id.button_decode,
            R.id.button_clear,
            R.id.button_restore,
            R.id.button_search,
            R.id.button_copy,
            R.id.button_paste,
            R.id.button_go
        ).forEach { id ->
            findViewById<Button>(id).apply {
                setEnabled(false)
                visibility = GONE
            }
        }
        hideRunButtonIfShowing()
        findViewById<EditText>(R.id.url_field).apply {
            setEnabled(false)
            textToDisplayInUrlField = text.toString()
            text.clear()
            if (isFocused()) {
                clearFocus()
            }
            visibility = GONE
        }
        findViewById<HorizontalScrollView>(R.id.button_area).visibility = GONE
        for (v in listOf(
            findViewById<WebView>(R.id.view1),
            findViewById<TextView>(R.id.log)
        )) {
            if (v.visibility == VISIBLE) {
                v.layoutParams!!.height = LayoutParams.MATCH_PARENT
                break
            }
        }
    }

    private fun showLog() {
        findViewById<WebView>(R.id.view1).visibility = GONE
        findViewById<ScrollView>(R.id.scroll_view1).apply {
            visibility = VISIBLE
            layoutParams!!.height = LayoutParams.MATCH_PARENT
        }
        findViewById<TextView>(R.id.log).apply {
            visibility = VISIBLE
            layoutParams!!.height = LayoutParams.MATCH_PARENT
            text = synchronized(logMsgs) {
                logMsgs.joinToString("\n")
            }
        }
        (this@MainActivity).apply {
            previousTitle = title.toString()
            title = getString(R.string.log)
        }
    }

    private fun hideLog() {
        findViewById<ScrollView>(R.id.scroll_view1).apply {
            visibility = GONE
        }
        findViewById<TextView>(R.id.log).apply {
            text = ""
            visibility = GONE
        }
        findViewById<WebView>(R.id.view1).apply {
            visibility = VISIBLE
            layoutParams!!.height = LayoutParams.MATCH_PARENT
        }
        if (previousTitle.isNotEmpty()) {
            title = previousTitle
            previousTitle = ""
        }
    }

    private inline fun isShowingUrlBar(): Boolean {
        return (findViewById<EditText>(R.id.url_field).visibility == VISIBLE)
    }

    private inline fun showUrlBarIfHidden() {
        if (! isShowingUrlBar()) {
            showUrlBar()
        }
    }

    private inline fun isShowingLog(): Boolean {
        return (findViewById<TextView>(R.id.log).visibility == VISIBLE)
    }

    private fun updateLogIfShowing() {
        findViewById<TextView>(R.id.log).apply {
            if (visibility == VISIBLE) {
                text = logMsgs.joinToString("\n")
            }
        }
    }

    private inline fun hideLogIfShowing() {
        if (isShowingLog()) {
            hideLog()
        }
    }

    private fun showRunButtonIfApplicable() {
        findViewById<Button>(R.id.button_run).apply {
            if (isShowingUrlBar() && shouldDisplayRunButton) {
                visibility = VISIBLE
                setEnabled(true)
            } else {
                visibility = GONE
                setEnabled(false)
            }
        }
    }

    private fun hideRunButtonIfShowing() {
        findViewById<Button>(R.id.button_run).apply {
            if (visibility == VISIBLE) {
                visibility = GONE
                setEnabled(false)
            }
        }
    }

    private inline fun hasNotLoadedAnyPage(): Boolean {
        return (findViewById<WebView>(R.id.view1).originalUrl == null)
    }

    private inline fun showMsg(msg: String, v: View) {
        Snackbar.make(v, msg, 5000).apply {
            setBackgroundTint(getColor(R.color.snackbar_background))
            setTextColor(getColor(R.color.white))
            show()
        }
    }
}