package test.sls1005.projects.fundamentalbrowser

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.net.DnsResolver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.FileUriExposedException
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.text.style.URLSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.view.View.VISIBLE
import android.view.View.GONE
import android.view.ViewGroup.LayoutParams
import android.view.inputmethod.InputMethodManager
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
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.text.buildSpannedString
import androidx.core.text.method.LinkMovementMethodCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.URISyntaxException
import java.net.URL
import java.net.HttpURLConnection
import java.net.UnknownServiceException
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.EdECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.interfaces.XECPublicKey
import java.security.spec.ECFieldF2m
import java.security.spec.ECFieldFp
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.interfaces.DHPublicKey

open class MainActivity : ConfiguratedActivity() {
    private inner class BrowserHttpOrHttpsGetMethodSpan(url: String) : URLSpan(url) {
        override fun onClick(v: View) {
            if (v.context == this@MainActivity) {
                val url = (this@BrowserHttpOrHttpsGetMethodSpan).getURL()
                if (isHttpOrHttpsUri(
                    Uri.parse(url)
                )) {
                    hideLogIfShowing()
                    hideSearchBarIfShowing()
                    supportActionBar?.apply {
                        if (!isShowing()) {
                            show()
                        }
                    }
                    load(url) // The browser is supposed to use the cached files instead of making new requests.
                }
            } else {
                super.onClick(v)
            }
        }
    }

    protected var languageTags = ""
    protected var urlToLoad = ""
    protected var currentURL = ""
    protected var textToDisplayInUrlField = ""
    private val logMsgs = ArrayDeque<CharSequence>()
    private val maxLogMsgsAtomic = AtomicInteger(maxLogMsgs)
    private val shouldLinkURLsInLogAtomic = AtomicBoolean(shouldLinkURLsInLog)
    private val manuallySetLanguageTagsAtomic = AtomicBoolean(manuallySetLanguageTags)
    private val allowsForegroundLogging = AtomicBoolean(foregroundLoggingEnabled)
    private var previousTitle = ""
    private var previousSubTitle: CharSequence? = ""
    private var shouldLeaveOnBackGesture = false
    private var isCurrentlyAllowingSearchingInLog = true
    private var isResumed = false // flag for callback
    private var simulatingDisconnection = false
    private val loggingDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val clickListener = OnClickListener { view ->
        when(view.id) {
            R.id.toolbar -> run {
                if (isShowingUrlBar()) {
                    hideUrlBar()
                } else {
                    showUrlBar()
                }
            }
            R.id.button_load -> run {
                val button = findViewById<MaterialButton>(R.id.button_load).apply {
                    setClickable(false)
                }
                val rawInput = findViewById<EditText>(R.id.url_field).text.toString()
                val url = run {
                    val textLooksLikeDomainWithoutScheme = (rawInput.indexOf('.') != -1 && rawInput.indexOf(':') == -1)
                    val k = if (shouldRemoveLfAndSpacesFromUrl) { 2 } else { 1 }
                    val n = if (textLooksLikeDomainWithoutScheme) { 8 } else { 0 }
                    buildString(n + rawInput.length / k) {
                        if (textLooksLikeDomainWithoutScheme) {
                            append("https://")
                        }
                        if (shouldRemoveLfAndSpacesFromUrl) {
                            for (c in rawInput) {
                                if (c != ' ' && c != '\n') {
                                    append(c)
                                }
                            }
                        } else {
                            append(rawInput)
                        }
                    }
                }
                val parsed = Uri.parse(url)
                val scheme = parsed.scheme ?: ""
                val schemeIsKnown = isOfKnownScheme(parsed)
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
                    updateLogIfShowing()
                } else if (url.isEmpty()) {
                    showMsg(getString(R.string.error3), button)
                } else if (scheme.isEmpty()) {
                    (object: DialogFragment() {
                        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                            val ctx = requireActivity()
                            return (AlertDialog.Builder(ctx).apply {
                                setView(
                                    layoutInflater.inflate(R.layout.issue_dialog, null).apply {
                                        findViewById<TextView>(R.id.issue_dialog_title).text = ctx.getString(R.string.parsing_issue_title)
                                        findViewById<TextView>(R.id.issue_dialog_text1).text = buildString {
                                            append(ctx.getString(R.string.failed_to_parse_url, rawInput))
                                            append('\n')
                                            append(ctx.getString(R.string.confirm_search_instead))
                                        }
                                    }
                                )
                                setPositiveButton(R.string.ok) { _, _ ->
                                    search(rawInput).also { success ->
                                        if (!success && !isShowingLog() && maxLogMsgsAtomic.get() > 0) {
                                            showLog(scroll = true)
                                        }
                                    }
                                }
                                setNegativeButton(R.string.cancel) { _, _ -> }
                            }).create()
                        }
                    }).show(supportFragmentManager, null)
                } else if (!schemeIsKnown) {
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
                        if (!urlLoaded && hasNotLoadedAnyPage() && !isShowingLog() && maxLogMsgsAtomic.get() > 0) {
                            showLog(scroll = true)
                            showUrlBarIfNotShowing()
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
            R.id.button_edit -> run {
                showDialogForEditingURL()
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
                val button = findViewById<MaterialButton>(R.id.button_search).apply {
                    setClickable(false)
                }
                search(findViewById<EditText>(R.id.url_field).text.toString())
                button.setClickable(true)
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
                    val button = findViewById<MaterialButton>(R.id.button_run)
                    findViewById<EditText>(R.id.url_field).text.toString().also {
                        if (it.isNotEmpty()) {
                            val code = it
                            button.setClickable(false)
                            if (shouldClearLogWhenRunningScript) {
                                synchronized(logMsgs) {
                                    logMsgs.clear()
                                }
                            }
                            if (!isShowingLog()) {
                                showLog()
                            }
                            if (shouldUseJavaScript) { // i.e. JS is allowed, enabled. Otherwise it's impossible to execute.
                                findViewById<WebView>(R.id.window).evaluateJavascript(code) { result ->
                                    val maxMsgNum = maxLogMsgsAtomic.get()
                                    val logMsg = "[REPL] $result"
                                    lifecycleScope.launch(loggingDispatcher) {
                                        synchronized(logMsgs) {
                                            logMsgs.apply {
                                                if (size >= maxMsgNum) {
                                                    removeFirst()
                                                }
                                                add(logMsg)
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            updateLogIfShowing()
                                        }
                                    }
                                }
                            } else {
                                val errMsg = getString(R.string.js_not_enabled)
                                showMsg(getString(R.string.error, errMsg), button)
                                logMsgs.apply {
                                    if (size >= maxLogMsgsAtomic.get()) {
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
                hideSearchBarIfShowing()
            }
        }
    }

    private val longClickListener = View.OnLongClickListener { view ->
        when(view.id) {
            R.id.toolbar -> run {
                if (isShowingLog()) {
                    showOptionDialog(
                        arrayOf(
                            Pair(getString(R.string.menu1_action_refresh_log)) { updateLogIfShowing() }, // yes. code block after pair()
                            Pair(getString(R.string.menu1_action_search_in_log)) { showSearchBarForLog() },
                            Pair(getString(R.string.menu1_action_clear_log)) {
                                synchronized(logMsgs) {
                                    logMsgs.clear()
                                }
                                updateLogIfShowing()
                            },
                            Pair(getString(R.string.menu1_action_close_log)) {
                                hideLogIfShowing()
                            }
                        )
                    )
                    (true)
                } else if (currentURL.isNotEmpty()) {
                    showOptionDialog(
                        arrayOf(
                            Pair(title.toString()) {
                                copyTextToClipboard(title.toString())
                                showMsg(getString(R.string.title_is_copied)) // Do not check the api version. Always show, becuse the action and target of copying are not obvious here.
                            },
                            Pair(currentURL) {
                                copyTextToClipboard(currentURL)
                                showMsg(getString(R.string.url_is_copied))
                            }
                        )
                    )
                    (true)
                } else {
                    false
                }
            }
            R.id.window -> run {
                findViewById<WebView>(R.id.window).hitTestResult.let { result ->
                    when (result.type) {
                        WebView.HitTestResult.SRC_ANCHOR_TYPE -> run {
                            result.extra?.let { url ->
                                showOptionDialog(
                                    arrayOf(
                                        Pair(url) {
                                            copyTextToClipboard(url)
                                            showMsg(getString(R.string.url_is_copied))
                                        },
                                        Pair(getString(R.string.copy_url)) {
                                            copyTextToClipboard(url)
                                            showMsg(getString(R.string.url_is_copied))
                                        },
                                        Pair(getString(R.string.open_in_new_window)) {
                                            Uri.parse(url).also { uri ->
                                                if (isHttpOrHttpsUri(uri)) {
                                                    saveCurrentConfiguration()
                                                    startActivity(
                                                        Intent(Intent.ACTION_VIEW, uri, (this@MainActivity).applicationContext, NewWindowActivity::class.java).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    )
                                )
                                (true)
                            } ?: false
                        }
                        WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE, WebView.HitTestResult.IMAGE_TYPE -> run {
                            result.extra?.let { url ->
                                showOptionDialog(
                                    arrayOf(
                                        Pair(url) {
                                            copyTextToClipboard(url)
                                            showMsg(getString(R.string.url_is_copied))
                                        },
                                        Pair(getString(R.string.copy_url)) {
                                            copyTextToClipboard(url)
                                            showMsg(getString(R.string.url_is_copied))
                                        },
                                        Pair(getString(R.string.open_in_new_window)) {
                                            Uri.parse(url).also { uri ->
                                                if (isHttpOrHttpsUri(uri)) {
                                                    saveCurrentConfiguration()
                                                    startActivity(
                                                        Intent(Intent.ACTION_VIEW, uri, (this@MainActivity).applicationContext, NewWindowActivity::class.java).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    )
                                )
                                (true)
                            } ?: false
                        }
                        else -> false
                    }
                }
            }
            else -> false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<MaterialToolbar>(R.id.toolbar).also { toolBar ->
            toolBar.apply {
                setOnClickListener(clickListener)
                setOnLongClickListener(longClickListener)
            }
            setSupportActionBar(toolBar)
        }
        findViewById<WebView>(R.id.window).apply {
            settings.apply {
                setSupportMultipleWindows(false)
                setGeolocationEnabled(false)
                allowContentAccess = false
                allowFileAccess = false
                builtInZoomControls = true
                displayZoomControls = false
            }
            setOnLongClickListener(longClickListener)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
                    val schemeIsSupported = isOfSupportedScheme(req.url)
                    val schemeIsHttp = isHttpUri(req.url)
                    val schemeIsHttpOrHttps = schemeIsHttp || checkUriScheme(req.url, "https")
                    val schemeIsKnown = (schemeIsSupported || isOfKnownScheme(req.url) || schemeIsHttp)
                    val url = req.url.toString()
                    if (schemeIsSupported) {
                        if (url.endsWith(".pdf", ignoreCase = true) && isHttpOrHttpsUri(req.url)) {
                            val x = Intent(Intent.ACTION_VIEW, req.url.buildUpon().build())
                            if (!onlyICanHandle(x)) {
                                (object: DialogFragment() {
                                    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                                        val ctx = requireActivity()
                                        return (AlertDialog.Builder(ctx).apply {
                                            setView(
                                                layoutInflater.inflate(R.layout.uri_question_dialog, null).apply {
                                                    findViewById<TextView>(R.id.uri_question_dialog_text1).text = ctx.getString(R.string.confirm_opening_url_with_another_app1)
                                                    findViewById<TextView>(R.id.uri_question_dialog_uri).text = url
                                                    findViewById<TextView>(R.id.uri_question_dialog_text2).text = buildSpannedString {
                                                        append(
                                                            if (schemeIsHttp) {
                                                                ctx.getText(R.string.warnings)
                                                            } else {
                                                                ctx.getText(R.string.warning)
                                                            }
                                                        )
                                                        append(ctx.getString(R.string.cannot_display_pdf))
                                                        if (schemeIsHttp) {
                                                            append(ctx.getString(R.string.protocol_is_insecure))
                                                        }
                                                    }
                                                }
                                            )
                                            setPositiveButton(R.string.yes) { _, _ ->
                                                saveCurrentConfiguration()
                                                try {
                                                    ctx.startActivity(x)
                                                } catch(_: ActivityNotFoundException) {
                                                    showMsg(getString(R.string.error5))
                                                } catch (_: FileUriExposedException) { // always the case if file:/// uri is used, as min is 24
                                                    showMsg(getString(R.string.error8))
                                                }
                                            }
                                            setNegativeButton(R.string.open_here) { _, _ ->
                                                hideLogIfShowing()
                                                load(url).also { urlLoaded ->
                                                    if (!urlLoaded && !isShowingLog() && maxLogMsgsAtomic.get() > 0) {
                                                        showLog(scroll = true)
                                                    }
                                                }
                                            }
                                            setNeutralButton(R.string.dont_load) { _, _ -> }
                                        }).create()
                                    }
                                }).show(supportFragmentManager, null)
                                return true
                            }
                        }
                        if (schemeIsHttp) {
                            if (shouldAllowHTTP) {
                                (object: DialogFragment() {
                                    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                                        val ctx = requireActivity()
                                        return (AlertDialog.Builder(ctx).apply {
                                            setView(
                                                layoutInflater.inflate(R.layout.dialog_with_selectable_text, null).apply {
                                                    findViewById<TextView>(R.id.dialog_selectable_text).text = buildSpannedString {
                                                        append(ctx.getString(
                                                            if (req.isRedirect) {
                                                                R.string.confirm_following_redirection_to_load_page
                                                            } else {
                                                                R.string.confirm_loading_page
                                                            }, url)
                                                        )
                                                        append("\n\n")
                                                        append(ctx.getText(R.string.warning))
                                                        append(ctx.getString(R.string.protocol_is_insecure))
                                                    }
                                                }
                                            )
                                            setPositiveButton(R.string.yes) { _, _ ->
                                                hideLogIfShowing()
                                                load(url).also { urlLoaded ->
                                                    if (!urlLoaded && !isShowingLog() && maxLogMsgsAtomic.get() > 0) {
                                                        showLog(scroll = true)
                                                    }
                                                }
                                            }
                                            setNegativeButton(R.string.no) { _, _ -> }
                                            setNeutralButton(R.string.try_encryption) { _, _ -> 
                                                hideLogIfShowing()
                                                load(
                                                    (req.url.buildUpon().apply { scheme("https") }).build().toString()
                                                ).also { urlLoaded ->
                                                    if (!urlLoaded && !isShowingLog() && maxLogMsgsAtomic.get() > 0) {
                                                        showLog(scroll = true)
                                                    }
                                                }
                                            }
                                        }).create()
                                    }
                                }).show(supportFragmentManager, null)
                                return true
                            } else {
                                val msg = buildString {
                                    append(getString(R.string.warning_not_https, url))
                                    append(getString(R.string.method_was, req.method))
                                    append(getString(R.string.blocked_but_can_change_in_settings))
                                }
                                val maxMsgNum = maxLogMsgsAtomic.get()
                                synchronized(logMsgs) {
                                    logMsgs.apply {
                                        if (size >= maxMsgNum) {
                                            removeFirst()
                                        }
                                        add(msg)
                                    }
                                }
                                showMsg(msg)
                                return true
                            }
                        } else if (req.isRedirect && shouldAskBeforeFollowingRedirection) {
                            (object: DialogFragment() {
                                override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                                    val ctx = requireActivity()
                                    return (AlertDialog.Builder(ctx).apply {
                                        setView(
                                            layoutInflater.inflate(R.layout.dialog_with_selectable_text, null).apply {
                                                findViewById<TextView>(R.id.dialog_selectable_text).text = ctx.getString(R.string.confirm_following_redirection_to_load_page, url)
                                            }
                                        )
                                        setPositiveButton(R.string.yes) { _, _ ->
                                            hideLogIfShowing()
                                            load(url).also { urlLoaded ->
                                                if (!urlLoaded && !isShowingLog() && maxLogMsgsAtomic.get() > 0) {
                                                    showLog(scroll = true)
                                                }
                                            }
                                        }
                                        setNegativeButton(R.string.no) { _, _ -> }
                                    }).create()
                                }
                            }).show(supportFragmentManager, null)
                            return true
                        }
                    } else if (checkUriScheme(req.url, arrayOf("intent", "android-app"))) {
                        if (url.contains((this@MainActivity).packageName, ignoreCase=true)) {
                            return true
                            // See: https://www.mbsd.jp/Whitepaper/IntentScheme.pdf
                            // An intent URL is most dangerous when targeting the app itself.
                        }
                    } else if (schemeIsKnown || url.isEmpty() || (req.url.scheme?.isEmpty() ?: true)) { // known but not supported or should be blocked
                        val maxMsgNum = maxLogMsgsAtomic.get()
                        val logMsg = "[Log] (ignored) ${req.method} $url\n"
                        synchronized(logMsgs) {
                            logMsgs.apply {
                                if (size >= maxMsgNum) {
                                    removeFirst()
                                }
                                add(logMsg)
                            }
                        }
                        return true
                    }
                    if (req.isRedirect || (!schemeIsSupported)) {
                        val maxMsgNum = maxLogMsgsAtomic.get()
                        val shouldRecord = (maxMsgNum > 0)
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
                                    val logMsg = "[Error] $errMsg\n"
                                    synchronized(logMsgs) {
                                        logMsgs.apply {
                                            if (size >= maxMsgNum) {
                                                removeFirst()
                                            }
                                            add(logMsg)
                                        }
                                    }
                                }
                            }
                            if (code != 0) {
                                return true
                            } // else: log
                        }
                        if (shouldRecord) {
                            val logMsg: CharSequence = (
                                if (shouldLinkURLsInLogAtomic.get() && req.method == "GET" && schemeIsHttpOrHttps) {
                                    buildSpannedString {
                                        append("[Log] ")
                                        append(req.method)
                                        if (req.isRedirect) {
                                            append(getString(R.string.space_redirected_space))
                                        } else {
                                            // This block currently will not be executed.
                                            append(' ')
                                        }
                                        append(url, BrowserHttpOrHttpsGetMethodSpan(url), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                        append('\n')
                                    }
                                } else {
                                    "[Log] $msg\n"
                                }
                            )
                            val maxMsgNum = maxLogMsgsAtomic.get()
                            synchronized(logMsgs) {
                                logMsgs.apply {
                                    if (size >= maxMsgNum) {
                                        removeFirst()
                                    }
                                    add(logMsg)
                                }
                            }
                        }
                        if (schemeIsSupported && allowsForegroundLogging.get()) {
                            showMsg(msg, v)
                        }
                    }
                    return !schemeIsSupported
                }
                override fun shouldInterceptRequest(v: WebView, req: WebResourceRequest): WebResourceResponse? {
                    val schemeIsHttp = isHttpUri(req.url)
                    val schemeIsHttpOrHttps = schemeIsHttp || checkUriScheme(req.url, "https")
                    if (schemeIsHttp) {
                        if (!shouldAllowHTTP) {
                            return WebResourceResponse(null, null, 426, "Upgrade Required", null, null)
                        }
                    }
                    val url = req.url.toString()
                    val msg = "${req.method} $url"
                    if (manuallySetLanguageTagsAtomic.get()) {
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
                    val maxMsgNum = maxLogMsgsAtomic.get()
                    if (maxMsgNum > 0) {
                        val logMsg = (
                            if (shouldLinkURLsInLogAtomic.get() && req.method == "GET" && schemeIsHttpOrHttps) {
                                buildSpannedString {
                                    append("[Log] ")
                                    append(req.method)
                                    append(' ')
                                    append(url, BrowserHttpOrHttpsGetMethodSpan(url), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    append('\n')
                                }
                            } else {
                                "[Log] $msg\n"
                            }
                        )
                        lifecycleScope.launch(loggingDispatcher) {
                            synchronized(logMsgs) {
                                logMsgs.apply {
                                    if (size >= maxMsgNum) {
                                        removeFirst()
                                    }
                                    add(logMsg)
                                }
                            }
                        }
                    }
                    if (allowsForegroundLogging.get()) {
                        v.post { showMsg(msg, v) }
                    }
                    return super.shouldInterceptRequest(v, req)
                }
                override fun onPageFinished(v: WebView, url: String) {
                    with (this@MainActivity) {
                        if (previousTitle.isEmpty()) {
                            title = v.title
                            supportActionBar?.subtitle = url
                        } else {
                            previousTitle = v.title ?: ""
                            previousSubTitle = url
                        }
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
                    val maxMsgNum = maxLogMsgsAtomic.get()
                    lifecycleScope.launch(loggingDispatcher) {
                        synchronized(logMsgs) {
                            logMsgs.apply {
                                if (size >= maxMsgNum) {
                                    removeFirst()
                                }
                                add(logMsg)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            updateLogIfShowing()
                        }
                    }
                    return true
                }
                override fun onReceivedTitle(v: WebView, newTitle: String) {
                    with (this@MainActivity) {
                        if (previousTitle.isEmpty()) {
                            title = newTitle
                        } else {
                            previousTitle = newTitle
                            
                        }
                    }
                }
            }
        }
        intArrayOf(
            R.id.button_close,
            R.id.button_decode,
            R.id.button_edit,
            R.id.button_clear,
            R.id.button_restore,
            R.id.button_copy,
            R.id.button_paste,
            R.id.button_search,
            R.id.button_load,
            R.id.button_run
        ).forEach { id ->
            findViewById<MaterialButton>(id).setOnClickListener(clickListener)
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
                                it.text = buildSpannedString {
                                    var flag = false
                                    if (s.isEmpty()) {
                                        records
                                    } else {
                                        records.filter({ it -> it.indexOf(s, ignoreCase=true) != -1 })
                                    }.forEach { record ->
                                        if (flag) {
                                            append('\n')
                                        } else {
                                            flag = true
                                        }
                                        append(
                                            if (shouldLinkURLsInLogAtomic.get()) {
                                                record
                                            } else {
                                                record.toString()
                                            }
                                        )
                                    }
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
                        hideSearchBarIfShowing() // This handles cases like searching in the log or the page
                        updateLogIfShowing()
                    } else if (!(supportActionBar?.isShowing() ?: true)) { // The result of this expression: false if showing or (unlikely) null, true if not null and not showing
                        supportActionBar?.show()
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
        maxLogMsgsAtomic.set(maxLogMsgs)
        shouldLinkURLsInLogAtomic.set(shouldLinkURLsInLog)
        manuallySetLanguageTagsAtomic.set(manuallySetLanguageTags)
        allowsForegroundLogging.set(foregroundLoggingEnabled)
        val defaultEncodingForText = getStoredOrDefaultEncodingForText()
        findViewById<WebView>(R.id.window).apply {
            settings.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setAlgorithmicDarkeningAllowed(shouldUseAlgorithmicDarkening)
                    } else {
                        setForceDark(if (shouldUseAlgorithmicDarkening) { WebSettings.FORCE_DARK_ON } else { WebSettings.FORCE_DARK_OFF })
                    }
                }
                javaScriptEnabled = shouldUseJavaScript
                blockNetworkImage = !shouldLoadImages
                blockNetworkLoads = !shouldLoadResources
                defaultTextEncodingName = defaultEncodingForText
            }
        }.also { v ->
            CookieManager.getInstance().also { cm ->
                cm.setAcceptCookie(shouldAcceptCookies)
                cm.setAcceptThirdPartyCookies(v, shouldAccept3rdPartyCookies)
            }
        }
        updateWindowSizeAndSetCorrectUserAgent()
        findViewById<TextView>(R.id.log).movementMethod = (
            if (shouldLinkURLsInLogAtomic.get()) {
                LinkMovementMethodCompat.getInstance()
            } else {
                ScrollingMovementMethod.getInstance()
            }
        )
        if (manuallySetLanguageTags) {
            languageTags = getStoredLanguageTags()  ?: ""
        }
        showRunButtonIfApplicable()
        val maxMsgNum = maxLogMsgsAtomic.get()
        synchronized(logMsgs) {
            logMsgs.apply {
                if (maxMsgNum >= 0) {
                    while (size > maxMsgNum) {
                        removeFirst()
                    }
                }
            }
        }
        if (!isResumed) {
            isResumed = true
            afterInitialization()
        }
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
        val forwardPossible = w.canGoForward()
        val backwardPossible = w.canGoBack()
        val nightModeEnabled = ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        val runningOnLevel29OrLater = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
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
            ),
            Pair(
                R.id.action_enable_disable_algorithmic_darkening,
                runningOnLevel29OrLater && nightModeEnabled && (
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        w.settings.isAlgorithmicDarkeningAllowed()
                    } else {
                        w.settings.forceDark == WebSettings.FORCE_DARK_ON
                    }
                )
            )
        ).forEach { it ->
            val (id, state) = it
            menu.findItem(id).setChecked(state)
        }
        arrayOf(
            Pair(
                R.id.group_url,
                currentURL.isNotEmpty()
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
                R.id.group_only_enabled_under_dark_mode_on_level_29_or_later,
                runningOnLevel29OrLater && nightModeEnabled
            ),
            Pair(
                R.id.group_search_log,
                showingLog && logMsgs.isNotEmpty()
            ),
            Pair(
                R.id.group_navigation,
                forwardPossible || backwardPossible
            ),
            Pair(
                R.id.group_tools,
                showAdvancedDeveloperTools && runningOnLevel29OrLater
            )
        ).forEach { it ->
            val (id, state) = it
            menu.setGroupVisible(id, state)
        }
        menu.findItem(R.id.action_refresh).title = getString(
            if (hasLoadedPage && showingLog) {
                R.string.menu1_action_reload_page
            } else {
                R.string.menu1_action_refresh
            }
        )
        if (forwardPossible || backwardPossible) {
            menu.findItem(R.id.menu1_submenu_navigation)?.subMenu?.also { sub ->
                arrayOf(
                    Pair(
                        R.id.menu1_submenu_navigation_group_backward,
                        backwardPossible
                    ),
                    Pair(
                        R.id.menu1_submenu_navigation_group_forward,
                        forwardPossible
                    )
                ).forEach { it ->
                    val (id, state) = it
                    sub.setGroupVisible(id, state)
                }
            }
        }
        menu.findItem(R.id.menu1_submenu_more)?.subMenu?.also { sub ->
            arrayOf(
                Pair(
                    R.id.menu1_submenu_more_group_page,
                    hasLoadedPage
                ),
                Pair(
                    R.id.menu1_submenu_more_group_website,
                    hasLoadedPage && (!checkUriScheme(Uri.parse(currentURL), arrayOf("data", "javascript", "intent", "android-app", "blob", "file", "content")))
                ),
                Pair(
                    R.id.menu1_submenu_more_group_advanced_tools,
                    showAdvancedDeveloperTools
                ),
                Pair(
                    R.id.menu1_submenu_more_group_copy_url,
                    hasLoadedPage
                ),
                Pair(
                    R.id.menu1_submenu_more_group_advanced_tools_website_only,
                    showAdvancedDeveloperTools && hasLoadedPage && (!checkUriScheme(Uri.parse(currentURL), arrayOf("data", "javascript", "intent", "android-app", "blob", "file", "content")))
                ),
                Pair(
                    R.id.menu1_submenu_more_group_log,
                    logMsgs.isNotEmpty()
                ),
                Pair(
                    R.id.menu1_submenu_more_group_showing_log,
                    showingLog
                )
            ).forEach { it ->
                val (id, state) = it
                sub.setGroupVisible(id, state)
            }
            sub.findItem(R.id.action_view_log).title = getString(
                if (showingLog) {
                    R.string.menu1_action_refresh_log
                } else {
                    R.string.menu1_action_view_log
                }
            )
            if (showAdvancedDeveloperTools) {
                sub.findItem(R.id.action_simulate_disconnection).setTitle(
                    if (simulatingDisconnection) {
                        getString(R.string.menu1_stop_simulating_disconnection)
                    } else {
                        getString(R.string.menu1_action_simulate_disconnection)
                    }
                )
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
            R.id.action_hide_tool_bar -> run {
                supportActionBar?.apply {
                    if (isShowing()) {
                        hide()
                    }
                }
                hideUrlBarIfShowing()
                (true)
            }
            R.id.action_edit_url -> run {
                showDialogForEditingURL()
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
            R.id.action_enable_disable_algorithmic_darkening -> run {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    shouldUseAlgorithmicDarkening = !item.isChecked()
                    val w = findViewById<WebView>(R.id.window)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        w.settings.setAlgorithmicDarkeningAllowed(shouldUseAlgorithmicDarkening)
                        item.setChecked(w.settings.isAlgorithmicDarkeningAllowed())
                    } else {
                        w.settings.forceDark = if (shouldUseAlgorithmicDarkening) { WebSettings.FORCE_DARK_ON } else { WebSettings.FORCE_DARK_OFF }
                        item.setChecked(w.settings.forceDark == WebSettings.FORCE_DARK_ON)
                    }
                }
                (true)
            }
            R.id.action_enable_disable_foreground_logging -> run {
                foregroundLoggingEnabled = !item.isChecked()
                if (!isShowingSearchBar()) {
                    allowsForegroundLogging.set(foregroundLoggingEnabled)
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
                saveCurrentConfiguration()
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
                updateLogIfShowing()
                (true)
            }
            R.id.action_close_log -> run {
                hideLogIfShowing()
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
                                    } catch (_: FileUriExposedException) {
                                        showMsg(getString(R.string.error8))
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
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) { // This constant would be used in this file anyway. So let's use it here.
                        showMsg(getString(R.string.url_is_copied))
                    }
                } // else: do nothing, as no one would intend to copy an empty string
                (true)
            }
            R.id.action_copy_url_as_link -> run {
                if (currentURL.isNotEmpty()) {
                    copyTextToClipboard(
                        buildSpannedString {
                            append(currentURL, URLSpan(currentURL), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    )
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        showMsg(getString(R.string.link_is_copied))
                    }
                }
                (true)
            }
            R.id.action_view_source -> run {
                if (currentURL.isNotEmpty() && (!currentURL.startsWith("view-source:", ignoreCase=true))) {
                    if (shouldUseAlternativeSourceViewer) {
                        val uri = Uri.parse(currentURL)
                        if (checkUriScheme(uri, "https")) {
                            alternativeApproachForViewingSource(uri.toString())
                        } else if (isHttpUri(uri)) {
                            if (shouldAllowHTTP) {
                                alternativeApproachForViewingSource(uri.toString())
                            } else {
                                showMsg(getString(R.string.error8))
                            }
                        } else {
                            showMsg(getString(R.string.error, getString(R.string.unsupported_protocol)))
                        }
                    } else {
                        hideLogIfShowing()
                        load("view-source:$currentURL", updateCurrentUrl=false)
                    }
                }
                (true)
            }
            R.id.action_view_cert -> run {
                showCurrentSiteCert()
                (true)
            }
            R.id.action_find_robots_txt -> run {
                if (currentURL.isNotEmpty()) {
                    Uri.parse(currentURL).also { uri ->
                        val scheme = if (isHttpUri(uri)) { "http" } else { "https" }
                        val host = uri.host
                        if (host != null) {
                            load(Uri.Builder().scheme(scheme).authority(host).path("robots.txt").build().toString()) // should use default port? Don't know, but it would be strange for a bot to use another port.
                        }
                    }
                }
                (true)
            }
            R.id.action_simulate_disconnection -> run {
                simulatingDisconnection = !simulatingDisconnection
                findViewById<WebView>(R.id.window).apply {
                    settings.blockNetworkLoads = if (simulatingDisconnection) { true } else { !shouldLoadResources }
                    setNetworkAvailable(!simulatingDisconnection)
                }
                (true)
            }
            R.id.action_previous_page -> run {
                findViewById<WebView>(R.id.window).apply {
                    if (canGoBack()) {
                        goBack()
                    }
                }
                (true)
            }
            R.id.action_next_page -> run {
                findViewById<WebView>(R.id.window).apply {
                    if (canGoForward()) {
                        goForward()
                    }
                }
                (true)
            }
            R.id.action_load_html_code -> run {
                showDialogForLoadingHTML()
                (true)
            }
            R.id.menu1_tool_dns_resolver -> run {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showDNSResolver()
                }
                (true)
            }
            else -> false
        }
    }

    override fun onConfigurationChanged(config: Configuration) {
        super.onConfigurationChanged(config)
    }

    override fun onDestroy() {
        logMsgs.clear()
        findViewById<TextView>(R.id.log).text = ""
        super.onDestroy()
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
            R.id.button_edit,
            R.id.button_clear,
            R.id.button_restore,
            R.id.button_copy,
            R.id.button_paste,
            R.id.button_search,
            R.id.button_load
        ).forEach { id ->
            findViewById<MaterialButton>(id).apply {
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
            R.id.button_edit,
            R.id.button_clear,
            R.id.button_restore,
            R.id.button_copy,
            R.id.button_paste,
            R.id.button_search,
            R.id.button_load
        ).forEach { id ->
            findViewById<MaterialButton>(id).apply {
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

    private fun logMsgsToText(): CharSequence {
        val records = synchronized(logMsgs) { logMsgs.toList() }
        return buildSpannedString {
            var flag = false
            for (record in records) {
                if (flag) {
                    append('\n')
                } else {
                    flag = true
                }
                append(
                    if (shouldLinkURLsInLogAtomic.get()) {
                        record
                    } else {
                        record.toString()
                    }
                )
            }
        }
    }

    private fun showLog(scroll: Boolean = false) {
        findViewById<WebView>(R.id.window).visibility = GONE
        findViewById<ScrollView>(R.id.log_scroll).apply {
            visibility = VISIBLE
            layoutParams!!.height = LayoutParams.MATCH_PARENT
        }
        findViewById<TextView>(R.id.log).apply {
            visibility = VISIBLE
            layoutParams!!.height = LayoutParams.MATCH_PARENT
            text = logMsgsToText()
        }
        (this@MainActivity).apply {
            previousTitle = title.toString()
            title = getString(R.string.log)
            supportActionBar?.also {
                previousSubTitle = it.subtitle
                it.subtitle = null
            }
        }
        if (scroll) {
            scrollLog()
        }
    }

    private inline fun isShowingLog(): Boolean {
        return (findViewById<TextView>(R.id.log).visibility == VISIBLE)
    }

    private fun updateLogIfShowing() {
        findViewById<TextView>(R.id.log).apply {
            if (visibility == VISIBLE) {
                text = logMsgsToText()
                postDelayed({
                    (this@MainActivity).apply {
                        if (autoscrollLogMsgs) {
                            scrollLog()
                        }
                    }
                }, 10)
            }
        }
    }

    private fun scrollLog() {
        val y = findViewById<TextView>(R.id.log).bottom.let {
            if (it > 1) {
                it - 1
            } else {
                it
            }
        }
        findViewById<ScrollView>(R.id.log_scroll).scrollTo(0, y)
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
            supportActionBar?.also {
                it.subtitle = previousSubTitle
                previousSubTitle = null
            }
        }
    }

    private inline fun hideLogIfShowing() {
        if (isShowingLog()) {
            hideLog()
        }
    }

    private inline fun isShowingSearchBar(): Boolean {
        return findViewById<RelativeLayout>(R.id.search_area).visibility == VISIBLE
    }

    private inline fun showContentSearchBar() {
        allowsForegroundLogging.set(false)
        hideUrlBarIfShowing()
        supportActionBar?.hide()
        findViewById<RelativeLayout>(R.id.search_area).visibility = VISIBLE
        val searchField = findViewById<EditText>(R.id.content_search_field).apply {
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
        searchField.apply {
            requestFocus()
            postDelayed({
                getSystemService(INPUT_METHOD_SERVICE).also {
                    if (it is InputMethodManager) {
                        it.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }, 200)
        }

    }

    private inline fun showSearchBarForLog() {
        allowsForegroundLogging.set(false)
        hideUrlBarIfShowing()
        supportActionBar?.hide()
        findViewById<RelativeLayout>(R.id.search_area).visibility = VISIBLE
        val searchField = findViewById<EditText>(R.id.content_search_field).apply {
            visibility = VISIBLE
            hint = getString(R.string.main_search_field_hint2)
            setEnabled(true)
        }
        findViewById<ImageView>(R.id.end_search).apply {
            visibility = VISIBLE
            setEnabled(true)
        }
        searchField.apply {
            requestFocus()
            postDelayed({
                getSystemService(INPUT_METHOD_SERVICE).also {
                    if (it is InputMethodManager) {
                        it.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }, 200)
        }
    }

    private inline fun hideSearchBar() {
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
        supportActionBar?.show()
        allowsForegroundLogging.set(foregroundLoggingEnabled)
    }
    
    private inline fun hideSearchBarIfShowing() {
        if (isShowingSearchBar()) {
            val shouldClear = (findViewById<ImageView>(R.id.search_next).visibility == VISIBLE) // searching in content
            hideSearchBar()
            if (shouldClear) {
                findViewById<WebView>(R.id.window).clearMatches()
            }
        }
    }

    private inline fun isShowingUrlBar(): Boolean {
        return (findViewById<EditText>(R.id.url_field).visibility == VISIBLE)
    }

    private inline fun showUrlBarIfNotShowing() {
        if (!isShowingUrlBar()) {
            showUrlBar()
        }
    }

    private fun hideUrlBarIfShowing() {
        if (isShowingUrlBar()) {
            hideUrlBar()
        }
    }

    private fun showRunButtonIfApplicable() {
        findViewById<MaterialButton>(R.id.button_run).apply {
            if (isShowingUrlBar() && shouldDisplayRunButton) {
                visibility = VISIBLE
                setEnabled(true)
            } else {
                visibility = GONE
                setEnabled(false)
            }
        }
    }

    private inline fun hideRunButtonIfShowing() {
        findViewById<MaterialButton>(R.id.button_run).apply {
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

    private fun showOptionDialog(options: Array< Pair< String, () -> Unit > >) {
        val (a, b) = options.unzip()
        (object: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                return (AlertDialog.Builder(requireActivity()).apply {
                    setItems(a.toTypedArray()) { _, i ->
                        if (i >= 0 && i < b.size) {
                            b[i]()
                        }
                    }
                }).create()
            }
        }).show(supportFragmentManager, null)
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
        val originalSchemeIntentSchemeFlag = checkUriScheme(Uri.parse(url), arrayOf("intent", "android-app"))
        if (originalSchemeIntentSchemeFlag) {
            if (url.contains((this@MainActivity).packageName, ignoreCase=true)) {
                return -3
            }
        }
        val x = try {
            Intent.parseUri(url, 0)
        } catch (_: URISyntaxException) {
            return -2
        }
        val altUrl /* : String? */ = (
            if (originalSchemeIntentSchemeFlag) {
                x.getStringExtra("browser_fallback_url")?.let {
                    Uri.decode(it)
                }
            } else {
                null
            }
        )
        if (altUrl != null) {
            x.removeExtra("browser_fallback_url")
        }
        if (onlyICanHandle(x)) {
            return 1
        } else {
            (object: DialogFragment() {
                override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                    val ctx = requireActivity()
                    return (AlertDialog.Builder(ctx).apply {
                        setView(
                            layoutInflater.inflate(R.layout.uri_question_dialog, null).apply {
                                findViewById<TextView>(R.id.uri_question_dialog_text1).text = ctx.getString(R.string.confirm_opening_url_with_another_app1)
                                findViewById<TextView>(R.id.uri_question_dialog_uri).text = url
                                findViewById<TextView>(R.id.uri_question_dialog_text2).text = ctx.getText(R.string.confirm_opening_url_with_another_app_warning1)
                            }
                        )
                        setPositiveButton(R.string.yes) { _, _ ->
                            saveCurrentConfiguration()
                            try {
                                ctx.startActivity(x)
                            } catch(_: ActivityNotFoundException) {
                                showMsg(ctx.getString(R.string.error5))
                            } catch (_: FileUriExposedException) {
                                showMsg(getString(R.string.error8))
                            }
                        }
                        setNegativeButton(R.string.no,
                            if (altUrl == null) {
                                { _, _ -> }
                            } else {
                                { _, _ ->
                                    (object: DialogFragment() {
                                        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                                            val ctx = requireActivity()
                                            val altUri = Uri.parse(altUrl)
                                            val schemeIsHttp = isHttpUri(altUri)
                                            val schemeIsHttpOrHttps = schemeIsHttp || checkUriScheme(altUri, "https")
                                            return (AlertDialog.Builder(ctx).apply {
                                                setView(
                                                    layoutInflater.inflate(R.layout.uri_question_dialog, null).apply {
                                                        findViewById<TextView>(R.id.uri_question_dialog_text1).text = ctx.getString(R.string.alt_url_supplied)
                                                        findViewById<TextView>(R.id.uri_question_dialog_uri).text = altUrl
                                                        findViewById<TextView>(R.id.uri_question_dialog_text2).text = buildSpannedString {
                                                            if (schemeIsHttp) {
                                                                append(ctx.getText(R.string.warning))
                                                                append(ctx.getString(R.string.protocol_is_insecure))
                                                                append("\n\n")
                                                            }
                                                            append(ctx.getString(R.string.confirm_loading_alt_url))
                                                        }
                                                    }
                                                )
                                                setPositiveButton(R.string.yes) { _, _ ->
                                                    if (schemeIsHttpOrHttps) {
                                                        hideLogIfShowing()
                                                        load(altUrl).also { urlLoaded ->
                                                            if (!urlLoaded && !isShowingLog() && maxLogMsgsAtomic.get() > 0) {
                                                                showLog(scroll = true)
                                                            }
                                                        }
                                                    } else {
                                                        showMsg(ctx.getString(R.string.error8))
                                                    }
                                                }
                                                setNegativeButton(R.string.no) { _, _ -> }
                                                if (schemeIsHttp) {
                                                    setNeutralButton(R.string.try_encryption_and_load) { _, _ ->
                                                        hideLogIfShowing()
                                                        load(
                                                            (altUri.buildUpon().apply { scheme("https") }).build().toString()
                                                        ).also { urlLoaded ->
                                                            if (!urlLoaded && !isShowingLog() && maxLogMsgsAtomic.get() > 0) {
                                                                showLog(scroll = true)
                                                            }
                                                        }
                                                    }
                                                }
                                            }).create()
                                        }
                                    }).show(ctx.supportFragmentManager, null)
                                }
                            }
                        )
                    }).create()
                }
            }).show(supportFragmentManager, null)
        }
        return 0
    }

    private fun showDialogForEditingURL() {
        (object: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                val ctx = requireActivity()
                val url = (
                    if (isShowingUrlBar()) {
                        findViewById<EditText>(R.id.url_field).text.toString()
                    } else {
                        textToDisplayInUrlField
                    }
                )
                return (AlertDialog.Builder(ctx).apply {
                    val v = layoutInflater.inflate(R.layout.code_input_dialog, null).apply {
                        findViewById<EditText>(R.id.code_input).apply {
                            setHint("Enter URL")
                            text.apply {
                                clear()
                                append(url)
                            }
                        }
                    }
                    setView(v)
                    setPositiveButton(R.string.ok) { _, _ ->
                        v.findViewById<EditText>(R.id.code_input).text.toString().also {
                            textToDisplayInUrlField = it
                            showUrlBarIfNotShowing()
                            findViewById<EditText>(R.id.url_field).text.apply {
                                clear()
                                append(it)
                            }
                        }
                    }
                    setNegativeButton(R.string.cancel) { _, _ -> }
                }).create()
            }

            override fun onStart() {
                super.onStart()
                val ctx = requireActivity()
                this.view?.findViewById<EditText>(R.id.code_input)?.apply {
                    requestFocus()
                    postDelayed({
                        getSystemService(INPUT_METHOD_SERVICE).also {
                            if (it is InputMethodManager) {
                                it.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }
                    }, 200)
                }
            }
        }).show(supportFragmentManager, null)
    }

    private fun showDialogForLoadingHTML() {
        (object: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                val ctx = requireActivity()
                return (AlertDialog.Builder(ctx).apply {
                    val v = layoutInflater.inflate(R.layout.code_input_dialog, null).apply {
                        findViewById<EditText>(R.id.code_input).setHint(R.string.html_input_field_hint)
                    }
                    setView(v)
                    setPositiveButton(R.string.html_input_field_load) { _, _ ->
                        v.findViewById<EditText>(R.id.code_input).text.toString().also {
                            if (it.isNotEmpty()) {
                                val encoded = Uri.encode(it)
                                val dataUri = "data:text/html,$encoded"
                                currentURL = dataUri
                                textToDisplayInUrlField = dataUri
                                if (isShowingLog()) {
                                    previousTitle = ""
                                    previousSubTitle = dataUri
                                }
                                ctx.apply {
                                    findViewById<ScrollView>(R.id.log_scroll).visibility = GONE
                                    findViewById<TextView>(R.id.log).apply {
                                        text = ""
                                        visibility = GONE
                                    }
                                    intArrayOf(
                                        R.id.button_close,
                                        R.id.button_decode,
                                        R.id.button_edit,
                                        R.id.button_clear,
                                        R.id.button_restore,
                                        R.id.button_copy,
                                        R.id.button_paste,
                                        R.id.button_search,
                                        R.id.button_load
                                    ).forEach { id ->
                                        findViewById<MaterialButton>(id).apply {
                                            setEnabled(false)
                                            visibility = GONE
                                        }
                                    }
                                    findViewById<MaterialButton>(R.id.button_run).apply {
                                        if (visibility == VISIBLE) {
                                            visibility = GONE
                                            setEnabled(false)
                                        }
                                    }
                                    findViewById<EditText>(R.id.url_field).apply {
                                        setEnabled(false)
                                        text.clear()
                                        if (isFocused()) {
                                            clearFocus()
                                        }
                                        visibility = GONE
                                    }
                                    findViewById<HorizontalScrollView>(R.id.button_area).visibility = GONE
                                    findViewById<WebView>(R.id.window).apply {
                                        visibility = VISIBLE
                                        layoutParams!!.height = LayoutParams.MATCH_PARENT
                                    }.also {
                                        it.loadDataWithBaseURL(dataUri, encoded, "text/html", null, null)
                                    }
                                }
                            }
                        }
                    }
                    setNegativeButton(R.string.cancel) { _, _ -> }
                }).create()
            }
        }).show(supportFragmentManager, null)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun showDNSResolver(domainNameToResolve: String = "") {
        (object: DialogFragment() {
            @RequiresApi(Build.VERSION_CODES.Q)
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                val ctx = requireActivity()
                val textToDisplayInInputField = ( // Note that this variable name is similar to that of another.
                    if (domainNameToResolve.isNotEmpty()) {
                        domainNameToResolve
                    } else {
                        Uri.parse(currentURL)?.let { uri ->
                            if (checkUriScheme(uri, "android-app")) {
                                ""
                            } else {
                                uri.host // ?: null
                            }
                        } ?: ""
                    }
                )
                return (AlertDialog.Builder(ctx).apply {
                    val v = layoutInflater.inflate(R.layout.dns_resolver, null)
                    setView(v)
                    if (textToDisplayInInputField.isNotEmpty()) {
                        v.findViewById<EditText>(R.id.domain_name).text.apply {
                            clear()
                            append(textToDisplayInInputField)
                        }
                    }
                    setPositiveButton(R.string.dns_resolver_resolve) { _, _ ->
                        val input = v.findViewById<EditText>(R.id.domain_name).text.toString()
                        try {
                            DnsResolver.getInstance().query(null, input, DnsResolver.FLAG_EMPTY, Executors.newCachedThreadPool(), null,
                                object : DnsResolver.Callback<List<InetAddress>> {
                                    override fun onAnswer(result: List<InetAddress>, code: Int) {
                                        if (code == 0) {
                                            if (result.isEmpty()) {
                                                showDNSResolutionResult(input, ctx.getString(R.string.error, ctx.getString(R.string.dns_resolver_error1)))
                                            } else {
                                                showDNSResolutionResult(input,
                                                    buildString(result.size * 15 + input.length + 1) {
                                                        append(input)
                                                        append(": \n\n  ")
                                                        var flag = false
                                                        for (address in result) {
                                                            if (flag) {
                                                                append(",\n  ")
                                                            } else {
                                                                flag = true
                                                            }
                                                            append(address.hostAddress)
                                                        }
                                                    }
                                                )
                                            }
                                        } else {
                                            showDNSResolutionResult(input, ctx.getString(R.string.dns_resolver_error2, input) + '\n' + ctx.getString(R.string.error_code_colon, code))
                                        }
                                    }
                                    override fun onError(e: DnsResolver.DnsException) {
                                        showDNSResolutionResult(input, ctx.getString(R.string.error, e.cause) + '\n' + ctx.getString(R.string.error_code_colon, e.code))
                                    }
                                }
                            )
                        } catch (_: Exception) {
                            showDNSResolutionResult(input, ctx.getString(R.string.dns_resolver_error2, input) + '\n' + ctx.getString(R.string.dns_resolver_error3))
                        }
                    }
                    setNegativeButton(R.string.cancel) { _, _ -> }
                }).create()
            }
        }).show(supportFragmentManager, null)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun showDNSResolutionResult(domain: String, output: String) {
        (object: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                return (AlertDialog.Builder(requireActivity()).apply {
                    setView(
                        layoutInflater.inflate(R.layout.dialog_with_selectable_text, null).apply {
                            findViewById<TextView>(R.id.dialog_selectable_text).text = output
                        }
                    )
                    setPositiveButton(R.string.confirm) { _, _ -> }
                    setNeutralButton(R.string.back) { _, _ ->  showDNSResolver(domain) } // Simple analysis: lambda in `showDNSResolver` creates `input` as string, `input` on heap, reference passed to `showDNSResolutionResult`. Lambda in `showDNSResolutionResult` passes `input` to `showDNSResolver`. No cycle (of memory, not logic).
                }).create()
            }
        }).show(supportFragmentManager, null)
    }

    private fun alternativeApproachForViewingSource(url: String) {
        synchronized(logMsgs) {
            logMsgs.add(getString(R.string.source_viewer_sending_request) + '\n')
        }
        if (!isShowingLog()) {
            showLog(scroll = true)
        }
        val userAgent = findViewById<WebView>(R.id.window).settings.userAgentString
        lifecycleScope.launch(Dispatchers.IO) {
            val result = ArrayList<Byte>()
            try {
                URL(url).openConnection().also {
                    if (it is HttpURLConnection) {
                        it.setRequestProperty("User-Agent", userAgent)
                        if (manuallySetLanguageTagsAtomic.get() && languageTags.isNotEmpty()) {
                            it.setRequestProperty("Accept-Language", languageTags)
                        }
                        it.instanceFollowRedirects = !shouldAskBeforeFollowingRedirection
                        val stream = it.inputStream // The request is launched here. This line should be before the following block.
                        val statusCode = it.responseCode
                        val statusMsg = it.responseMessage
                        lifecycleScope.launch(Dispatchers.Main) {
                            synchronized(logMsgs) {
                                logMsgs.add(getString(R.string.source_viewer_status_code, statusCode, statusMsg) + '\n')
                                logMsgs.add(getString(R.string.source_viewer_receiving) + '\n')
                            }
                            updateLogIfShowing()
                        }
                        val buffer = ByteArray(1000)
                        while (true) {
                            val n = stream.read(buffer)
                            if (n == -1) {
                                break
                            } else if (n > 0) {
                                result.addAll(buffer.slice(0 ..< n))
                            }
                        }
                        it.disconnect()
                        withContext(Dispatchers.Main) {
                            showSource(result.toByteArray().toString(Charsets.UTF_8))
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showMsg(getString(R.string.error, getString(R.string.unsupported_protocol)))
                        }
                    }
                }
            } catch (_: IOException) {
                withContext(Dispatchers.Main) {
                    showMsg(getString(R.string.unknown_error_occurred))
                }
            } catch (_: UnknownServiceException) {
                withContext(Dispatchers.Main) {
                    showMsg(getString(R.string.unknown_error_occurred))
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showMsg(getString(R.string.unknown_error_occurred))
                }
            }
        }
    }

    private fun showSource(code: String) {
        (object: DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                return (AlertDialog.Builder(requireActivity()).apply {
                    setView(
                        layoutInflater.inflate(R.layout.dialog_with_selectable_monospaced_text, null).apply {
                            findViewById<TextView>(R.id.dialog_selectable_monospaced_text).text = code
                        }
                    )
                    setNeutralButton(R.string.back) { _, _ ->   }
                }).create()
            }
        }).show(supportFragmentManager, null)
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
            (object: DialogFragment() {
                override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                    return (AlertDialog.Builder(requireActivity()).apply {
                        setView(
                            layoutInflater.inflate(R.layout.cert, null).apply {
                                findViewById<TextView>(R.id.content).text = content.toString()
                            }
                        )
                    }).create()
                }
            }).show(supportFragmentManager, null)
        }
    }

    private inline fun search(keywords: String): Boolean {
        return if (searchURL.isEmpty()) {
            showMsg(getString(R.string.error1))
            (false)
        } else if (keywords.isEmpty()) {
            showMsg(getString(R.string.error2))
            (false)
        } else {
            hideUrlBar()
            hideLogIfShowing()
            load(Uri.encode(searchURL,"\"#$%&+,/:;<=>?@[\\]^`{|}") + Uri.encode(keywords.replace('\n', ' ')))
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
            if (schemeIsHttp || isPDF || shouldAskBeforeLoadingUrlThatIsFromAnotherApp) {
                (object: DialogFragment() {
                    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                        val ctx = requireActivity()
                        return (AlertDialog.Builder(ctx).apply {
                            setView(
                                layoutInflater.inflate(R.layout.dialog_with_selectable_text, null).apply {
                                    findViewById<TextView>(R.id.dialog_selectable_text).text = buildSpannedString {
                                        append(ctx.getString(R.string.confirm_loading_page, url))
                                        if (isPDF || schemeIsHttp) {
                                            append("\n\n")
                                            append(ctx.getText(R.string.warning))
                                        }
                                        if (isPDF) {
                                            append(ctx.getString(R.string.cannot_display_pdf))
                                        }
                                        if (schemeIsHttp) {
                                            append(ctx.getString(R.string.protocol_is_insecure))
                                        }
                                    }
                                }
                            )
                            setPositiveButton(R.string.ok) { _, _ ->
                                disableJavaScriptAsRequested()
                                load(url).also { urlLoaded ->
                                    if (!urlLoaded && !isShowingLog() && maxLogMsgsAtomic.get() > 0) {
                                        showLog(scroll = true)
                                    }
                                }
                            }
                            setNegativeButton(R.string.cancel) { _, _ -> }
                            if (schemeIsHttp) {
                                disableJavaScriptAsRequested()
                                setNeutralButton(R.string.try_encryption) { _, _ ->
                                    load(
                                        (Uri.parse(url).buildUpon().apply { scheme("https") }).build().toString()
                                    ).also { urlLoaded ->
                                        if (!urlLoaded && !isShowingLog() && maxLogMsgsAtomic.get() > 0) {
                                            showLog(scroll = true)
                                        }
                                    }
                                }
                            }
                        }).create()
                    }
                }).show(supportFragmentManager, null)
            } else {
                disableJavaScriptAsRequested()
                load(url).also { urlLoaded ->
                    if (!urlLoaded && !isShowingLog() && maxLogMsgsAtomic.get() > 0) {
                        showLog(scroll = true)
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
            val maxMsgNum = maxLogMsgsAtomic.get()
            synchronized(logMsgs) {
                logMsgs.apply {
                    if (size >= maxMsgNum) {
                        removeFirst()
                    }
                    add(msg)
                }
            }
            showMsg(msg, w) // even if allowsForegroundLogging.get() is false
        }
        if (schemeIsOrSeemsLikeHttp && (!shouldAllowHTTP)) {
            return false
        } else {
            if (updateCurrentUrl) {
                currentURL = url
                textToDisplayInUrlField = url
            }
            if (manuallySetLanguageTagsAtomic.get() && languageTags.isNotEmpty()) {
                w.loadUrl(url, mutableMapOf(Pair("Accept-Language", languageTags)))
            } else {
                w.loadUrl(url)
            }
            return true
        }
        return false
    }
}

private inline fun containedIgnoringCase(s: String, a: Array<String>): Boolean { // Array<String> seems to be the most efficient type here
    for (e in a) {
        if (s.equals(e, ignoreCase=true)) {
            return true
        }
    }
    return false
}

private inline fun checkUriScheme(uri: Uri, scheme: String): Boolean {
    return uri.scheme?.equals(scheme, ignoreCase=true) ?: false
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
    return checkUriScheme(uri, arrayOf("http", "https", "javascript", "data", "file", "blob", "view-source", "android.resource"))
    // Excluded: "content", "intent", "android-app"
}

private inline fun shouldNotBlockUserLoading(url: String): Boolean {
    // This only lists known schemes. Unknown schemes and `intent:` and `android-app:` will be handled differently (not directly loaded nor always blocked).
    return checkUriScheme(
        Uri.parse(url),
        arrayOf("http", "https", "javascript", "view-source", "data")
    )
    // Excluded: "file", "content", "blob", "intent", "android-app"
}
