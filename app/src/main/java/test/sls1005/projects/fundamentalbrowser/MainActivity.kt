package test.sls1005.projects.fundamentalbrowser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.URLSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.view.View.VISIBLE
import android.view.View.GONE
import android.view.ViewGroup.LayoutParams
import android.webkit.ConsoleMessage
import android.webkit.ConsoleMessage.MessageLevel.DEBUG
import android.webkit.ConsoleMessage.MessageLevel.ERROR
import android.webkit.ConsoleMessage.MessageLevel.LOG
import android.webkit.ConsoleMessage.MessageLevel.TIP
import android.webkit.ConsoleMessage.MessageLevel.WARNING
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import java.net.URISyntaxException
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.EdECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.interfaces.XECPublicKey
import java.security.spec.ECFieldF2m
import java.security.spec.ECFieldFp
import javax.crypto.interfaces.DHPublicKey

open class MainActivity : ConfiguratedActivity() {
    private val logMsgs = ArrayDeque<String>()
    private var previousTitle = ""
    private var allowsForegroundLogging = false
    private var shouldLeaveOnBackGesture = false
    private var isCurrentlyAllowingSearchingInLog = true
    protected var languageTags = ""
    protected var urlToLoad = ""
    protected var currentURL = ""
    protected var textToDisplayInUrlField = ""
    public val clickListener = OnClickListener { view ->
        when(view.id) {
            R.id.button_go -> run {
                val button = findViewById<Button>(R.id.button_go).apply {
                    setClickable(false)
                }
                val url = findViewById<EditText>(R.id.url_field).text.toString().let {
                    val textLooksLikeDomainWithoutScheme = (it.indexOf('.') != -1 && it.indexOf(':') == -1)
                    val k = if (shouldRemoveLfAndSpacesFromUrl) { 2 } else { 1 }
                    val n = if (textLooksLikeDomainWithoutScheme) { 8 } else { 0 }
                    buildString(n + (it.length) / k) {
                        if (textLooksLikeDomainWithoutScheme) {
                            append("https://")
                        }
                        if (shouldRemoveLfAndSpacesFromUrl) {
                            for (c in it) {
                                if (c != ' ' && c != '\n') {
                                    append(c)
                                }
                            }
                        } else {
                            append(it)
                        }
                    }
                }
                val scheme = Uri.parse(url).scheme ?: ""
                if (scheme.equals("javascript", ignoreCase=true)) {
                    if (shouldClearLogWhenRunningScript) {
                        synchronized(logMsgs) {
                            logMsgs.clear()
                        }
                    }
                    val notShowingLog = !isShowingLog()
                    if (hasNotLoadedAnyPage()) {
                        if (notShowingLog) {
                            showLog()
                        }
                    } else if (notShowingLog) {
                        hideUrlBar()
                    }
                    textToDisplayInUrlField = url
                    load(url, updateCurrentUrl = false)
                } else if (url.isEmpty()) {
                    showMsg(getString(R.string.error3), button)
                } else if (scheme.isEmpty()) {
                    val msg = getString(R.string.error, getString(R.string.failed_to_parse, url))
                    showMsg(msg, button)
                } else if (scheme.equals("intent", ignoreCase=true)) {
                    val code = showDialogForOpeningURLWithAnotherApp(url)
                    if (code == 1) {
                        showMsg(getString(R.string.error6), button)
                    } else if (code == -2) {
                        val msg = getString(R.string.error, getString(R.string.failed_to_parse, url))
                        showMsg(msg, button) 
                    }
                } else if (shouldNotBlockUserLoading(url)) {
                    hideUrlBar()
                    hideLogIfShowing()
                    load(url).also { urlLoaded ->
                        if (!urlLoaded && hasNotLoadedAnyPage() && !isShowingLog() && maxLogMsgs > 0) {
                            showLog()
                            showUrlBarIfHidden()
                        }
                    }
                } else {
                    showMsg(getString(R.string.error7, scheme), button)
                }
                button.setClickable(true)
            }
            R.id.button_close -> run {
                hideUrlBarIfShowing()
            }
            R.id.button_decode -> run {
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
            R.id.button_clear -> run {
                findViewById<EditText>(R.id.url_field).text.clear()
            }
            R.id.button_restore -> run {
                if (currentURL.isNotEmpty()) {
                    findViewById<EditText>(R.id.url_field).text.apply {
                        clear()
                        append(currentURL)
                    }
                }
            }
            R.id.button_search -> run {
                val button = findViewById<Button>(R.id.button_search)
                val rawInput = findViewById<EditText>(R.id.url_field).text.toString()
                if (searchURL.isEmpty()) {
                    showMsg(getString(R.string.error1), button)
                } else if (rawInput.isEmpty()) {
                    showMsg(getString(R.string.error2), button)
                } else {
                    button.setClickable(false)
                    hideUrlBar()
                    hideLogIfShowing()
                    load(searchURL + Uri.encode(rawInput.replace('\n', ' ')))
                    button.setClickable(true)
                }
            }
            R.id.button_copy -> run {
                findViewById<EditText>(R.id.url_field).run {
                    if (hasSelection()) {
                        text.subSequence(selectionStart, selectionEnd)
                    } else {
                        text
                    }
                }.toString().also {
                    if (it.isNotEmpty()) {
                        copyTextToClipboard(it)
                    }
                }
            }
            R.id.button_paste -> {
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
            R.id.button_run -> run {
                if (shouldDisplayRunButton) { // check if the button is really displayed
                    val button = findViewById<Button>(R.id.button_run)
                    findViewById<EditText>(R.id.url_field).text.toString().also {
                        if (it.isNotEmpty()) {
                            val code = it
                            button.setClickable(false)
                            if (shouldClearLogWhenRunningScript) {
                                synchronized(logMsgs) {
                                    logMsgs.clear()
                                }
                            }
                            if (! isShowingLog()) {
                                showLog()
                            }
                            if (shouldUseJavaScript) { // i.e. JS is allowed, enabled. Otherwise it's impossible to execute.
                                findViewById<WebView>(R.id.window).evaluateJavascript(code) { result ->
                                    synchronized(logMsgs) {
                                        logMsgs.apply {
                                            if (size >= maxLogMsgs) {
                                                removeFirst()
                                            }
                                            add("[REPL] $result")
                                        }
                                        updateLogIfShowing()
                                    }
                                }
                            } else {
                                val errMsg = getString(R.string.js_not_enabled)
                                showMsg(getString(R.string.error, errMsg), button)
                                logMsgs.apply {
                                    if (size >= maxLogMsgs) {
                                        removeFirst()
                                    }
                                    add("[Error] $errMsg\n")
                                }
                                updateLogIfShowing()
                            }
                            button.setClickable(true)
                        }
                    }
                }
            }
            R.id.search_previous -> run {
                findViewById<WebView>(R.id.window).findNext(false)
            }
            R.id.search_next -> run {
                findViewById<WebView>(R.id.window).findNext(true)
            }
            R.id.end_search -> run {
                val shouldClear = isShowingContentSearchBar()
                if (isShowingSearchBar()) {
                    hideSearchBar()
                }
                if (shouldClear) {
                    findViewById<WebView>(R.id.window).clearMatches()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<WebView>(R.id.window).apply {
            settings.apply {
                setSupportMultipleWindows(false)
                builtInZoomControls = true
                displayZoomControls = false
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
                    val schemeIsSupported = isOfSupportedScheme(req.url)
                    val schemeIsHttp = isHttpUri(req.url)
                    val schemeIsKnown = (schemeIsSupported || isOfKnownScheme(req.url) || schemeIsHttp)
                    val url = req.url.toString()
                    if (schemeIsSupported) {
                        if (url.endsWith(".pdf", ignoreCase = true) && isHttpOrHttpsUri(req.url)) {
                            val x = Intent(Intent.ACTION_VIEW, req.url.buildUpon().build())
                            if (!onlyICanHandle(x)) {
                                (AlertDialog.Builder(this@MainActivity).apply {
                                    setView(
                                        layoutInflater.inflate(R.layout.confirm_opening_url_with_another_app, null).apply {
                                            findViewById<TextView>(R.id.url).text = url
                                            findViewById<TextView>(R.id.warning).text = SpannableStringBuilder(
                                                if (schemeIsHttp) {
                                                    getText(R.string.warnings)
                                                } else {
                                                    getText(R.string.warning)
                                                }
                                            ).apply {
                                                append(getString(R.string.cannot_display_pdf))
                                                if (schemeIsHttp) {
                                                    append(getString(R.string.protocol_is_insecure))
                                                }
                                            }
                                        }
                                    )
                                    setPositiveButton(getString(R.string.yes)) {_, _ ->
                                        saveCurrentConfiguration()
                                        try {
                                            startActivity(x)
                                        } catch(_: ActivityNotFoundException) {
                                            showMsg(getString(R.string.error5))
                                        }
                                    }
                                    setNegativeButton(getString(R.string.open_here)) {_, _ ->
                                        load(url).also { urlLoaded ->
                                            if (!urlLoaded && !isShowingLog() && maxLogMsgs > 0) {
                                                showLog()
                                            }
                                        }
                                    }
                                    setNeutralButton(getString(R.string.dont_load)) {_, _ -> }
                                }).create().show()
                                return true
                            }
                        }
                        if (schemeIsHttp) {
                            if (shouldAllowHTTP) {
                                (AlertDialog.Builder(this@MainActivity).apply {
                                    setView(
                                        layoutInflater.inflate(R.layout.confirm_loading_url, null).apply {
                                            findViewById<TextView>(R.id.text1).text = SpannableStringBuilder().apply {
                                                append(getString(R.string.confirm_loading_page, url))
                                                append("\n\n")
                                                append(getText(R.string.warning))
                                                append(getString(R.string.protocol_is_insecure))
                                            }
                                        }
                                    )
                                    setPositiveButton(getString(R.string.yes)) {_, _ ->
                                        load(url).also { urlLoaded ->
                                            if (!urlLoaded && !isShowingLog() && maxLogMsgs > 0) {
                                                showLog()
                                            }
                                        }
                                    }
                                    setNegativeButton(R.string.no) {_, _ -> }
                                    setNeutralButton(R.string.try_encryption) {_, _ -> 
                                        load(
                                            (Uri.parse(url).buildUpon().apply { scheme("https") }).build().toString()
                                        ).also { urlLoaded ->
                                            if (!urlLoaded && !isShowingLog() && maxLogMsgs > 0) {
                                                showLog()
                                            }
                                        }
                                    }
                                }).create().show()
                                return true
                            } else {
                                val msg = buildString {
                                    append(getString(R.string.warning_not_https, url))
                                    append(getString(R.string.method_was, req.method))
                                    append(getString(R.string.blocked_but_can_change_in_settings))
                                }
                                synchronized(logMsgs) {
                                    logMsgs.apply {
                                        if (size >= maxLogMsgs) {
                                            removeFirst()
                                        }
                                        add(msg)
                                    }
                                }
                                showMsg(msg)
                                return true
                            }
                        }
                    } else if (checkUriScheme(req.url, "intent")) {
                        if (url.contains((this@MainActivity).packageName, ignoreCase=true)) {
                            return true
                            // See: https://www.mbsd.jp/Whitepaper/IntentScheme.pdf
                            // An intent URL is most dangerous when targeting the app itself.
                        }
                    } else if (schemeIsKnown || url.isEmpty() || (req.url.scheme?.isEmpty() ?: true)) { // known but not supported or should be blocked
                        synchronized(logMsgs) {
                            logMsgs.apply {
                                if (size >= maxLogMsgs) {
                                    removeFirst()
                                }
                                add("[Log] (ignored) ${req.method} $url\n")
                            }
                        }
                        return true
                    }
                    if (req.isRedirect || (!schemeIsSupported)) {
                        var shouldRecord = (maxLogMsgs > 0)
                        var msg = buildString(url.length) { // could be needed even if shouldRecord is false
                            if (req.isRedirect) {
                                append(req.method)
                                append(getString(R.string.space_redirected_space))
                            }
                            append(url)
                        }
                        if (!schemeIsSupported) {
                            val code = showDialogForOpeningURLWithAnotherApp(url)
                            if (code == 1) {
                                showMsg(getString(R.string.error6), v)
                            } else if (code == -2) {
                                if (shouldRecord) {
                                    val errMsg = getString(R.string.failed_to_parse, url)
                                    synchronized(logMsgs) {
                                        logMsgs.apply {
                                            if (size >= maxLogMsgs) {
                                                removeFirst()
                                            }
                                            add("[Error] $errMsg\n")
                                        }
                                    }
                                }
                            }
                            if (code != 0) {
                                return true
                            } // else: log
                        }
                        if (shouldRecord) {
                            synchronized(logMsgs) {
                                logMsgs.apply {
                                    if (size >= maxLogMsgs) {
                                        removeFirst()
                                    }
                                    add("[Log] $msg\n")
                                }
                            }
                        }
                        if (schemeIsSupported && allowsForegroundLogging) {
                            showMsg(msg, v)
                        }
                    }
                    return !schemeIsSupported
                }
                override fun shouldInterceptRequest(v: WebView, req: WebResourceRequest): WebResourceResponse? {
                    if (req.url.scheme?.equals("http", ignoreCase=true) ?: false) {
                        if (synchronized(shouldAllowHTTP) { (!shouldAllowHTTP) }) {
                            return WebResourceResponse(null, null, 426, "Upgrade Required", null, null)
                        }
                    }
                    val url = req.url.toString()
                    val msg = "${req.method} $url"
                    if (synchronized(manuallySetLanguageTags){
                            manuallySetLanguageTags
                        }) {
                        req.requestHeaders.also {
                            val languageHeader = "Accept-Language"
                            val languages = synchronized(languageTags) { languageTags }
                            if (it.containsKey(languageHeader)) {
                                if (languages.isEmpty()) {
                                    it.remove(languageHeader)
                                } else {
                                    it[languageHeader] = languages
                                }
                            }
                        }
                    }
                    val maxMsgNum = synchronized(maxLogMsgs) { maxLogMsgs }
                    if (maxMsgNum > 0) {
                        synchronized(logMsgs) {
                            logMsgs.apply {
                                if (size >= maxMsgNum) {
                                    removeFirst()
                                }
                                add("[Log] $msg\n")
                            }
                        }
                    }
                    if (synchronized(allowsForegroundLogging) {
                            allowsForegroundLogging
                        }) {
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
                    val kind = when (consoleMessage.messageLevel()) {
                        DEBUG -> "Debug"
                        ERROR -> "Error"
                        LOG -> "Log"
                        TIP -> "Tip"
                        WARNING -> "Warning"
                    }
                    val logMsg = buildString {
                        append("[$kind (from console)")
                        consoleMessage.sourceId().also { src ->
                            if (src.isNotEmpty()) {
                                append(" | Source: $src")
                            }
                        }
                        append(" | Line ${consoleMessage.lineNumber()}] ")
                        append(consoleMessage.message()) // No '\n' here.
                    }
                    synchronized(logMsgs) {
                        logMsgs.apply {
                            if (size >= maxLogMsgs) {
                                removeFirst()
                            }
                            add(logMsg)
                        }
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
        }
        intArrayOf(
            R.id.button_close,
            R.id.button_decode,
            R.id.button_clear,
            R.id.button_restore,
            R.id.button_copy,
            R.id.button_paste,
            R.id.button_search,
            R.id.button_go,
            R.id.button_run
        ).forEach { id ->
            findViewById<Button>(id).setOnClickListener(clickListener)
        }
        intArrayOf(
            R.id.search_previous,
            R.id.search_next,
            R.id.end_search
        ).forEach { id ->
            findViewById<ImageView>(id).setOnClickListener(clickListener)
        }
        findViewById<EditText>(R.id.content_search_field).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, i: Int, n: Int, n1: Int) { }
            override fun onTextChanged(s: CharSequence, i: Int, n0: Int, n: Int) { }
            override fun afterTextChanged(e: Editable) {
                val s = e.toString()
                findViewById<WebView>(R.id.window).also {
                    if (it.visibility == VISIBLE) {
                        it.findAllAsync(s)
                    } else if (isCurrentlyAllowingSearchingInLog) {
                        isCurrentlyAllowingSearchingInLog = false
                        val records = synchronized(logMsgs) { logMsgs.toList() }
                        findViewById<TextView>(R.id.log).also {
                            if (it.visibility == VISIBLE) {
                                it.text = if (s.isEmpty()) {
                                    records.joinToString("\n")
                                } else {
                                    records.filter({ it -> it.indexOf(s, ignoreCase=true) != -1 }).joinToString("\n")
                                }
                            }
                            it.postDelayed(
                                { isCurrentlyAllowingSearchingInLog = true },
                                100
                            )
                        }
                    }
                }
            }
        })
        findViewById<HorizontalScrollView>(R.id.button_area).postDelayed({
            val self = findViewById<HorizontalScrollView>(R.id.button_area)
            self.fullScroll(View.FOCUS_RIGHT)
        }, 400)
        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isShowingSearchBar()) {
                        hideSearchBar()
                        updateLogIfShowing()
                    } else if (isShowingLog()) {
                        hideLog()
                    } else {
                        findViewById<WebView>(R.id.window).apply {
                            if (canGoBack()) {
                                goBack()
                            } else {
                                with (this@MainActivity) {
                                    if (shouldLeaveOnBackGesture || (hasNotLoadedAnyPage() && findViewById<EditText>(R.id.url_field).text.isEmpty() && logMsgs.isEmpty())) {
                                        finish()
                                    } else {
                                        shouldLeaveOnBackGesture = true
                                        findViewById<LinearLayout>(R.id.linearlayout1).apply {
                                            showMsg(getString(R.string.repeat_to_close), this)
                                            postDelayed(
                                                { shouldLeaveOnBackGesture = false },
                                                5000
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
        if (intent.action == Intent.ACTION_VIEW) {
            urlToLoad = intent.data?.let { uri ->
                if (isHttpOrHttpsUri(uri)) {
                    uri.toString()
                } else {
                    ""
                }
            } ?: ""
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<WebView>(R.id.window).apply {
            settings.apply {
                javaScriptEnabled = shouldUseJavaScript
                blockNetworkImage = !shouldLoadImages
                blockNetworkLoads = !shouldLoadResources
            }
        }.also { v ->
            CookieManager.getInstance().also { cm ->
                cm.setAcceptCookie(shouldAcceptCookies)
                cm.setAcceptThirdPartyCookies(v, shouldAccept3rdPartyCookies)
            }
        }
        updateWindowSizeAndSetCorrectUserAgent()
        if (manuallySetLanguageTags) {
            languageTags = getStoredLanguageTags()  ?: ""
        }
        allowsForegroundLogging = foregroundLoggingEnabled
        showRunButtonIfApplicable()
        synchronized(logMsgs) {
            logMsgs.apply {
                if (maxLogMsgs >= 0) {
                    while (size > maxLogMsgs) {
                        removeFirst()
                    }
                }
            }
        }
        afterInitialization()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu1, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (! super.onPrepareOptionsMenu(menu)) {
            return false
        }
        val w = findViewById<WebView>(R.id.window)
        val hasLoadedPage = !hasNotLoadedAnyPage()
        val showingLog = isShowingLog()
        arrayOf(
            Pair(
                R.id.action_enable_disable_js,
                w.settings.javaScriptEnabled
            ),
            Pair(
                R.id.action_enable_disable_images,
                !w.settings.blockNetworkImage
            ),
            Pair(
                R.id.action_enable_disable_resources,
                !w.settings.blockNetworkLoads
            ),
            Pair(
                R.id.action_enable_disable_foreground_logging,
                foregroundLoggingEnabled
            ),
            Pair(
                R.id.action_enable_disable_desktop_mode,
                desktopMode
            )
        ).forEach { it ->
            val (id, state) = it
            menu.findItem(id).setChecked(state)
        }
        arrayOf(
            Pair(
                R.id.group_url,
                (!isShowingUrlBar()) && currentURL.isNotEmpty()
            ),
            Pair(
                R.id.group_page,
                hasLoadedPage
            ),
            Pair(
                R.id.group_content_search,
                hasLoadedPage && (!showingLog)
            ),
            Pair(
                R.id.group_search_log,
                showingLog && logMsgs.isNotEmpty()
            )
        ).forEach { it ->
            val (id, state) = it
            menu.setGroupVisible(id, state)
        }
        menu.findItem(R.id.submenu1).subMenu.also { sub ->
            if (sub != null) {
                arrayOf(
                    Pair(
                        R.id.menu1_sub1_group_page,
                        hasLoadedPage
                    ),
                    Pair(
                        R.id.menu1_sub1_group_log,
                        logMsgs.isNotEmpty()
                    )
                ).forEach { it ->
                    val (id, state) = it
                    sub.setGroupVisible(id, state)
                }
            }
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
                findViewById<WebView>(R.id.window).apply {
                    if (originalUrl != null) {
                        reload()
                    }
                }
                updateLogIfShowing()
                (true)
            }
            R.id.action_enable_disable_js -> run {
                shouldUseJavaScript = !item.isChecked()
                val w = findViewById<WebView>(R.id.window).apply {
                    settings.javaScriptEnabled = shouldUseJavaScript
                }
                item.setChecked(w.settings.javaScriptEnabled)
                (true)
            }
            R.id.action_enable_disable_images -> run {
                shouldLoadImages = !item.isChecked()
                val w = findViewById<WebView>(R.id.window).apply {
                    settings.blockNetworkImage = !shouldLoadImages
                }
                item.setChecked(!w.settings.blockNetworkImage)
                (true)
            }
            R.id.action_enable_disable_resources -> run {
                shouldLoadResources = !item.isChecked()
                val w = findViewById<WebView>(R.id.window).apply {
                    settings.blockNetworkLoads = !shouldLoadResources
                }
                item.setChecked(!w.settings.blockNetworkLoads)
                (true)
            }
            R.id.action_enable_disable_foreground_logging -> run {
                foregroundLoggingEnabled = !item.isChecked()
                if (!isShowingSearchBar()) {
                    allowsForegroundLogging = foregroundLoggingEnabled
                }
                item.setChecked(foregroundLoggingEnabled)
                (true)
            }
            R.id.action_enable_disable_desktop_mode -> run {
                desktopMode = !item.isChecked()
                item.setChecked(desktopMode)
                updateWindowSizeAndSetCorrectUserAgent()
                (true)
            }
            R.id.action_content_search -> run {
                if (!isShowingSearchBar()) {
                    showContentSearchBar()
                }
                (true)
            }
            R.id.action_search_in_log -> run {
                if (!isShowingSearchBar()) {
                    showSearchBarForLog()
                }
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
            R.id.action_clear_log -> run {
                synchronized(logMsgs) {
                    logMsgs.clear()
                }
                if (isShowingLog()) {
                    updateLogIfShowing()
                }
                (true)
            }
            R.id.action_new_window -> run {
                saveCurrentConfiguration()
                startActivity(
                    Intent(this@MainActivity, NewWindowActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    }
                )
                (true)
            }
            R.id.action_open_in_new_window -> run {
                Uri.parse(currentURL).also { uri ->
                    if (isHttpOrHttpsUri(uri)) {
                        saveCurrentConfiguration()
                        startActivity(
                            Intent(Intent.ACTION_VIEW, uri, (this@MainActivity).applicationContext, NewWindowActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                            }
                        )
                    }
                }
                (true)
            }
            R.id.action_open_in_another_app -> run {
                if (currentURL.isEmpty()) {
                    showMsg(getString(R.string.error4))
                } else {
                    Uri.parse(currentURL).also { uri ->
                        if (isHttpOrHttpsUri(uri)) {
                            saveCurrentConfiguration()
                            Intent(Intent.ACTION_VIEW, uri).also {
                                if (onlyICanHandle(it)) {
                                    showMsg(getString(R.string.error6))
                                } else {
                                    try {
                                        startActivity(it)
                                    } catch(_: ActivityNotFoundException) {
                                        showMsg(getString(R.string.error5))
                                    }
                                }
                            }
                        }
                    }
                }
                (true)
            }
            R.id.action_copy_url -> run {
                if (currentURL.isNotEmpty()) {
                    copyTextToClipboard(currentURL)
                }
                (true)
            }
            R.id.action_copy_url_as_link -> run {
                if (currentURL.isNotEmpty()) {
                    copyTextToClipboard(
                        SpannableString(currentURL).apply {
                            setSpan(URLSpan(currentURL), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    )
                }
                (true)
            }
            R.id.action_view_source -> run {
                if (currentURL.isNotEmpty() && (!currentURL.startsWith("view-source:", ignoreCase=true))) {
                    hideLogIfShowing()
                    load("view-source:$currentURL", updateCurrentUrl=false)
                }
                (true)
            }
            R.id.action_view_cert -> run {
                showCurrentSiteCert()
                (true)
            }
            else -> false
        }
    }

    override fun onConfigurationChanged(config: Configuration) {
        super.onConfigurationChanged(config)
    }

    private fun updateWindowSizeAndSetCorrectUserAgent() {
        findViewById<WebView>(R.id.window).settings.also {
            it.useWideViewPort = desktopMode
            it.loadWithOverviewMode = desktopMode
            it.userAgentString = if (useCustomUserAgent) {
                getStoredUserAgent().let { stored ->
                    if (stored == "") {
                        null
                    } else {
                        stored // ?: null
                    }
                }
            } else if (desktopMode) {
                val defaultAgent = WebSettings.getDefaultUserAgent(this)
                if ("Linux" in defaultAgent) {
                    buildString(defaultAgent.length) {
                        val i1 = defaultAgent.indexOf("Linux")
                        var i2 = i1 + 5
                        if (i1 > 0) {
                            append(defaultAgent.substring(0, i1))
                        }
                        if ("X11" !in defaultAgent) {
                            append("X11; ")
                        }
                        append(defaultAgent.substring(i1, i2))
                        if ("x86_64" !in defaultAgent) {
                            append(" x86_64")
                        }
                        if ("Android" in defaultAgent) {
                            val i3 = defaultAgent.indexOf("Android")
                            val i4 = defaultAgent.indexOf('(')
                            val i5 = defaultAgent.indexOf(')')
                            if (i4 < i1 && i2 < i3 && i3 < i5) {
                                i2 = i5
                            }
                        }
                        if (i2 < defaultAgent.length) {
                            append(defaultAgent.substring(i2, defaultAgent.length))
                        }
                    }
                    // This will result in a user agent without irrelevant information, rather than a user agent that is identical to that of desktop browsers. The latter is not intended and will not happen.
                } else {
                    null
                }
            } else {
                null
            }
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
        intArrayOf(
            R.id.button_close,
            R.id.button_decode,
            R.id.button_clear,
            R.id.button_restore,
            R.id.button_copy,
            R.id.button_paste,
            R.id.button_search,
            R.id.button_go
        ).forEach { id ->
            findViewById<Button>(id).apply {
                visibility = VISIBLE
                setEnabled(true)
            }
        }
        showRunButtonIfApplicable()
        for (v in arrayOf(
            findViewById<WebView>(R.id.window),
            findViewById<TextView>(R.id.log)
        )) {
            if (v.visibility == VISIBLE) {
                v.layoutParams!!.height = LayoutParams.MATCH_PARENT
                break
            }
        }
    }

    private fun hideUrlBar() {
        intArrayOf(
            R.id.button_close,
            R.id.button_decode,
            R.id.button_clear,
            R.id.button_restore,
            R.id.button_copy,
            R.id.button_paste,
            R.id.button_search,
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
        for (v in arrayOf(
            findViewById<WebView>(R.id.window),
            findViewById<TextView>(R.id.log)
        )) {
            if (v.visibility == VISIBLE) {
                v.layoutParams!!.height = LayoutParams.MATCH_PARENT
                break
            }
        }
    }

    private fun showLog() {
        findViewById<WebView>(R.id.window).visibility = GONE
        findViewById<ScrollView>(R.id.log_scroll).apply {
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
        findViewById<ScrollView>(R.id.log_scroll).visibility = GONE
        findViewById<TextView>(R.id.log).apply {
            text = ""
            visibility = GONE
        }
        findViewById<WebView>(R.id.window).apply {
            visibility = VISIBLE
            layoutParams!!.height = LayoutParams.MATCH_PARENT
        }
        if (previousTitle.isNotEmpty()) {
            title = previousTitle
            previousTitle = ""
        }
    }

    private inline fun isShowingSearchBar(): Boolean {
        return findViewById<RelativeLayout>(R.id.search_area).visibility == VISIBLE
    }

    private inline fun isShowingContentSearchBar(): Boolean {
        return (
            findViewById<RelativeLayout>(R.id.search_area).visibility == VISIBLE
        ) && (
            findViewById<ImageView>(R.id.search_next).visibility == VISIBLE
        )
    }

    private fun showContentSearchBar() {
        allowsForegroundLogging = false
        hideUrlBarIfShowing()
        this.supportActionBar?.hide()
        findViewById<RelativeLayout>(R.id.search_area).visibility = VISIBLE
        findViewById<EditText>(R.id.content_search_field).apply {
            visibility = VISIBLE
            hint = getString(R.string.main_search_field_hint1)
            setEnabled(true)
        }
        intArrayOf(
            R.id.search_previous,
            R.id.search_next,
            R.id.end_search
        ).forEach { id ->
            findViewById<ImageView>(id).apply {
                visibility = VISIBLE
                setEnabled(true)
            }
        }
    }

    private fun showSearchBarForLog() {
        allowsForegroundLogging = false
        hideUrlBarIfShowing()
        this.supportActionBar?.hide()
        findViewById<RelativeLayout>(R.id.search_area).visibility = VISIBLE
        findViewById<EditText>(R.id.content_search_field).apply {
            visibility = VISIBLE
            hint = getString(R.string.main_search_field_hint2)
            setEnabled(true)
        }
        findViewById<ImageView>(R.id.end_search).apply {
            visibility = VISIBLE
            setEnabled(true)
        }
    }

    private fun hideSearchBar() {
        findViewById<EditText>(R.id.content_search_field).apply {
            setEnabled(false)
            text.clear()
            visibility = GONE
        }
        intArrayOf(
            R.id.search_previous,
            R.id.search_next,
            R.id.end_search
        ).forEach { id ->
            findViewById<ImageView>(id).apply {
                setEnabled(false)
                visibility = GONE
            }
        }
        findViewById<RelativeLayout>(R.id.search_area).visibility = GONE
        this.supportActionBar?.show()
        allowsForegroundLogging = foregroundLoggingEnabled
    }

    private inline fun isShowingUrlBar(): Boolean {
        return (findViewById<EditText>(R.id.url_field).visibility == VISIBLE)
    }

    private inline fun showUrlBarIfHidden() {
        if (! isShowingUrlBar()) {
            showUrlBar()
        }
    }

    private fun hideUrlBarIfShowing() {
        if (isShowingUrlBar()) {
            hideUrlBar()
        }
    }

    private inline fun isShowingLog(): Boolean {
        return (findViewById<TextView>(R.id.log).visibility == VISIBLE)
    }

    private fun updateLogIfShowing() {
        findViewById<TextView>(R.id.log).apply {
            if (visibility == VISIBLE) {
                text = synchronized(logMsgs) { logMsgs.joinToString("\n") }
                postDelayed({
                    (this@MainActivity).apply {
                        if (autoscrollLogMsgs) {
                            val y = findViewById<TextView>(R.id.log).bottom.let {
                                if (it > 1) {
                                    it - 1
                                } else {
                                    it
                                }
                            }
                            findViewById<ScrollView>(R.id.log_scroll).scrollTo(0, y)
                        }
                    }
                }, 10)
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
        return (findViewById<WebView>(R.id.window).originalUrl == null)
    }

    @SuppressLint("QueryPermissionsNeeded") // Don't need. Because the purpose is never to see other apps.
    private inline fun onlyICanHandle(x: Intent): Boolean { // true if only this app will respond; false if any other or none
        return (x.resolveActivity(packageManager)?.packageName == this.packageName)
    }

    private fun copyTextToClipboard(text: CharSequence) {
        getSystemService(CLIPBOARD_SERVICE).apply {
            if (this is ClipboardManager) {
                setPrimaryClip(
                    ClipData.newPlainText("", text)
                )
            }
        }
    }

    private inline fun showMsg(msg: String, v: View? = null) {
        Snackbar.make(
            v ?: findViewById<LinearLayout>(R.id.linearlayout1),
            msg,
            5000
        ).apply {
            setBackgroundTint(
                (this@MainActivity).getColor(R.color.snackbar_background)
            )
            setTextColor(
                (this@MainActivity).getColor(R.color.white)
            )
            show()
        }
    }

    private fun showDialogForOpeningURLWithAnotherApp(url: String): Int { // This function does extra checks to prevent security issues.
        /* Error codes:
             0: the dialog is shown
             1: the intent would be sent back to this app, so it is blocked; the dialog is not shown
             -1: invalid argument
             -2: failed to parse
             -3: potential security issue; blocked; the dialog is not shown
        */
        if (url.isEmpty() || Uri.parse(url).scheme == null) {
            return -1
        }
        if (checkUriScheme(Uri.parse(url), "intent")) {
            if (url.contains((this@MainActivity).packageName, ignoreCase=true)) {
                return -3
            }
        }
        val x = try {
            Intent.parseUri(url, 0)
        } catch (_: URISyntaxException) {
            return -2
        }
        if (onlyICanHandle(x)) {
            return 1
        } else {
            (AlertDialog.Builder(this).apply {
                setView(
                    layoutInflater.inflate(R.layout.confirm_opening_url_with_another_app, null).apply {
                        findViewById<TextView>(R.id.url).text = url
                        findViewById<TextView>(R.id.warning).text = getText(R.string.confirm_opening_url_with_another_app_warning1)
                    }
                )
                setPositiveButton(getString(R.string.yes)) { _, _ ->
                    saveCurrentConfiguration()
                    try {
                        startActivity(x)
                    } catch(_: ActivityNotFoundException) {
                        showMsg(getString(R.string.error5))
                    }
                }
                setNegativeButton(R.string.no) {_, _ -> }
            }).create().show()
        }
        return 0
    }

    @OptIn(ExperimentalStdlibApi::class)
    private inline fun showCurrentSiteCert() { // Reference: IUT-T (10/2019), "X.509"
        val window = findViewById<WebView>(R.id.window)
        val sslCert = window.certificate
        if (sslCert == null) {
            showMsg(getString(R.string.error_no_cert), window)
        } else {
            val content = StringBuilder()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val x509Cert = sslCert.x509Certificate
                if (x509Cert != null) {
                    val version = x509Cert.version
                    val serialNumber = x509Cert.serialNumber.toString(16)
                    val signatureAlgorithm = x509Cert.sigAlgName
                    val issuer = x509Cert.issuerX500Principal.name
                    val notBefore = x509Cert.notBefore.toString()
                    val notAfter = x509Cert.notAfter.toString()
                    val subject = x509Cert.subjectX500Principal.name
                    val publicKey = x509Cert.publicKey
                    val keyAlgorithm = publicKey.algorithm
                    val signature = x509Cert.signature.toHexString()
                    content.apply {
                        append(
                            getString(
                                R.string.cert_part1,
                                version,
                                serialNumber,
                                signatureAlgorithm
                            )
                        )
                        append(getString(R.string.cert_part2, issuer, notBefore, notAfter, subject))
                        append(getString(R.string.cert_part3, keyAlgorithm))
                    }
                    if (publicKey is DHPublicKey) {
                        val parameters = publicKey.params
                        val p = parameters.p.toString(16)
                        val g = parameters.g.toString(16)
                        val y = publicKey.y.toString(16)
                        content.apply {
                            append(getString(R.string.cert_parameters_lf))
                            append(getString(R.string.cert_parameter_p_s, p))
                            append(getString(R.string.cert_parameter_g_s, g))
                            append(getString(R.string.cert_value_y_s, y))
                        }
                    } else if (publicKey is DSAPublicKey) {
                        val parameters = publicKey.params
                        val p = parameters.p.toString(16)
                        val q = parameters.q.toString(16)
                        val g = parameters.g.toString(16)
                        val y = publicKey.y.toString(16)
                        content.apply {
                            append(getString(R.string.cert_parameters_lf))
                            append(getString(R.string.cert_parameter_p_s, p))
                            append(getString(R.string.cert_parameter_q_s, q))
                            append(getString(R.string.cert_parameter_g_s, g))
                            append(getString(R.string.cert_value_y_s, y))
                        }
                    } else if (publicKey is RSAPublicKey) {
                        val modulus = publicKey.modulus.toString(16)
                        val exponent = publicKey.publicExponent
                        content.append(getString(R.string.cert_modulus_exponent, modulus, exponent))
                    } else if (publicKey is ECPublicKey) {
                        val parameters = publicKey.params
                        val a = parameters.curve.a.toString(16)
                        val b = parameters.curve.b.toString(16)
                        content.apply {
                            append(getString(R.string.cert_parameters_lf))
                            append(getString(R.string.cert_parameter_a_s, a))
                            append(getString(R.string.cert_parameter_b_s, b))
                        }
                        val field = parameters.curve.field
                        if (field is ECFieldFp) {
                            val p = field.p.toString(16)
                            content.append(getString(R.string.cert_parameter_p_s, p))
                        } else if (field is ECFieldF2m) {
                            val m = field.m
                            content.append(getString(R.string.cert_parameter_m_d, m))
                        }
                        val g = parameters.generator
                        val xg = g.affineX.toString(16)
                        val yg = g.affineY.toString(16)
                        val n = parameters.order.toString(16)
                        val h = parameters.cofactor
                        val w = publicKey.w
                        val xw = w.affineX.toString(16)
                        val yw = w.affineY.toString(16)
                        content.apply {
                            append(getString(R.string.cert_parameter_point_g, xg, yg))
                            append(getString(R.string.cert_parameter_n_s, n))
                            append(getString(R.string.cert_parameter_h_d, h))
                            append(getString(R.string.cert_point_w, xw, yw))
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (publicKey is EdECPublicKey) {
                            val parameters = publicKey.params.name
                            val point = publicKey.point
                            val y = point.y.toString(16)
                            content.apply {
                                append(getString(R.string.cert_parameters_s, parameters))
                                append(getString(R.string.cert_x_is_odd,
                                    if (point.isXOdd()) {
                                        getString(R.string.true_lowercase)
                                    } else {
                                        getString(R.string.false_lowercase)
                                    }
                                ))
                                append(getString(R.string.cert_value_y_s, y))
                            }
                        } else if (publicKey is XECPublicKey) {
                            val u = publicKey.u.toString(16)
                            content.append(getString(R.string.cert_value_u_s, u))
                        }
                    }
                    content.append(getString(R.string.cert_final_part, signatureAlgorithm, signature))
                }
            }
            if (content.isEmpty()) {
                val issuer = sslCert.issuedBy.dName
                val notBefore = sslCert.validNotBeforeDate.toString()
                val notAfter = sslCert.validNotAfterDate.toString()
                val subject = sslCert.issuedTo.dName
                content.append(getString(R.string.cert_part2, issuer, notBefore, notAfter, subject))
            }
            (AlertDialog.Builder(this@MainActivity).apply {
                setView(
                    layoutInflater.inflate(R.layout.cert, null).apply {
                        findViewById<TextView>(R.id.content).text = content
                    }
                )
            }).create().show()
        }
    }

    private inline fun disableJavaScriptAsRequested() {
        if (!shouldAllowJSForUrlsFromOtherApps) {
            shouldUseJavaScript = false
            findViewById<WebView>(R.id.window).apply {
                settings.javaScriptEnabled = false
            }
        }
    }

    protected open fun afterInitialization() {
        if (urlToLoad.isNotEmpty()) {
            val url = urlToLoad
            urlToLoad = ""
            val schemeIsHttp = isHttpUri(Uri.parse(url))
            val isPDF = url.endsWith(".pdf", ignoreCase = true)
            if (shouldAskBeforeLoadingUrlThatIsFromAnotherApp) {
                (AlertDialog.Builder(this).apply {
                    setView(
                        layoutInflater.inflate(R.layout.confirm_loading_url, null).apply {
                            findViewById<TextView>(R.id.text1).text = SpannableStringBuilder().apply {
                                append(getString(R.string.confirm_loading_page, url))
                                if (isPDF || schemeIsHttp) {
                                    append("\n\n")
                                    append(getText(R.string.warning))
                                }
                                if (isPDF) {
                                    append(getString(R.string.cannot_display_pdf))
                                }
                                if (schemeIsHttp) {
                                    append(getString(R.string.protocol_is_insecure))
                                }
                            }
                        }
                    )
                    setPositiveButton(getString(R.string.ok)) {_, _ ->
                        disableJavaScriptAsRequested()
                        load(url).also { urlLoaded ->
                            if (!urlLoaded && !isShowingLog() && maxLogMsgs > 0) {
                                showLog()
                            }
                        }
                    }
                    setNegativeButton(R.string.cancel) {_, _ -> }
                }).create().show()
            } else {
                disableJavaScriptAsRequested()
                load(url).also { urlLoaded ->
                    if (!urlLoaded && !isShowingLog() && maxLogMsgs > 0) {
                        showLog()
                    }
                }
            }
        }
    }

    protected final fun load(url: String, updateCurrentUrl: Boolean = true): Boolean {
        val schemeIsOrSeemsLikeHttp = isOrSeemsLikeHttpUrl(url)
        val w = findViewById<WebView>(R.id.window)
        if (schemeIsOrSeemsLikeHttp) {
            val msg = buildString {
                append(getString(R.string.warning_not_https, url))
                if (!shouldAllowHTTP) {
                    append(getString(R.string.blocked_but_can_change_in_settings))
                }
            }
            synchronized(logMsgs) {
                logMsgs.apply {
                    if (size >= maxLogMsgs) {
                        removeFirst()
                    }
                    add(msg)
                }
            }
            showMsg(msg, w) // even if allowsForegroundLogging is false
        }
        if (schemeIsOrSeemsLikeHttp && (!shouldAllowHTTP)) {
            return false
        } else {
            if (updateCurrentUrl) {
                currentURL = url
                textToDisplayInUrlField = url
            }
            if (manuallySetLanguageTags && languageTags.isNotEmpty()) {
                w.loadUrl(url, mutableMapOf(Pair("Accept-Language", languageTags)))
            } else {
                w.loadUrl(url)
            }
            return true
        }
        return false
    }
}

private inline fun containedIgnoringCase(s: String, container: Array<String>): Boolean { // Array<String> seems to be the most efficient type here
    for (e in container) {
        if (s.equals(e, ignoreCase=true)) {
            return true
        }
    }
    return false
}

private inline fun checkUriScheme(uri: Uri, scheme: String): Boolean {
    return uri.scheme?.let { uriScheme ->
        uriScheme.equals(scheme, ignoreCase=true)
    } ?: false
}

private inline fun checkUriScheme(uri: Uri, schemes: Array<String>): Boolean {
    return uri.scheme?.let { uriScheme ->
        containedIgnoringCase(uriScheme, schemes)
    } ?: false
}

private inline fun isHttpOrHttpsUri(uri: Uri): Boolean {
    return checkUriScheme(uri, arrayOf("http", "https"))
}

private inline fun isHttpUri(uri: Uri): Boolean { // Not HTTPS
    return checkUriScheme(uri, "http")
}

private fun isOrSeemsLikeHttpUrl(url: String): Boolean {
    val scheme1 = Uri.parse(url).scheme
    if (scheme1 == null) {
        return false
    } else if (scheme1.equals("http", ignoreCase=true) || (scheme1.equals("view-source", ignoreCase=true) && url.contains("http://", ignoreCase=true))) {
        return true
    }
    val url2 = Uri.decode(url).let { decoded ->
        buildString(decoded.length) {
            for (c in decoded) {
                if (c !in charArrayOf('\n', '\r', '\t', ' ', '\u0000', '\u000b')) {
                    append(c)
                }
            }
        }
    }
    val scheme2 = Uri.parse(url2).scheme
    if (scheme2 == null) {
        return false
    } else if (scheme2.equals("http", ignoreCase=true) || (scheme2.equals("view-source", ignoreCase=true) && url.contains("http://", ignoreCase=true))) {
        return true
    }
    return false
}

private inline fun isOfSupportedScheme(uri: Uri): Boolean {
    return checkUriScheme(uri, arrayOf("http", "https", "blob"))
    // Excluded: "javascript", "data", but manual loading is still possible and is not forbidden. Neither is there a reason to forbid explicit manual loading
}

private inline fun isOfKnownScheme(uri: Uri): Boolean { // schemes that should be handled by the browser, if at all
    return checkUriScheme(uri, arrayOf("http", "https", "javascript", "data", "blob", "view-source", "android.resource"))
    // Excluded: "file", "content", "intent"
}

private inline fun shouldNotBlockUserLoading(url: String): Boolean {
    return checkUriScheme(
        Uri.parse(url),
        arrayOf("http", "https", "javascript", "view-source", "data", "intent")
    )
    // Excluded: "file", "content", "blob"
}
