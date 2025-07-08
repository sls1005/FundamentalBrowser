package test.sls1005.projects.fundamentalbrowser

import java.io.File

private const val START_INDEX_OF_MAX_LOGS = 1
private const val FINAL_INDEX_OF_MAX_LOGS = 4
private const val START_INDEX_OF_SEARCH_URL = 5

open class ConfiguratedActivity : ThemedActivity() {
    // Default configuration:                                           // Stored file format:
    protected var shouldUseJavaScript = true                            // Line 1, Byte 1, Bit 1 (LSB), true if set, false if not set
    protected var shouldLoadImages = true                               //         Bit 2
    protected var shouldLoadResources = true                            //         Bit 3
    protected var foregroundLoggingEnabled = true                       //         Bit 4
    protected var shouldAccept3rdPartyCookies = false                   //         Bit 5
    protected var shouldRemoveLfAndSpacesFromUrl = true                 //         Bit 6
    protected var desktopMode = false                                   //         Bit 7
    protected var shouldAcceptCookies = false                           //         Bit 8
    protected var maxLogMsgs = 20                                       // Byte 2-5, L.E.
    protected var searchURL = ""                                        // Starting from byte 6, and then LF
    protected var shouldDisplayRunButton = false                        // Line 2, Byte 1, Bit 1
    protected var shouldClearLogWhenRunningScript = true                //                 Bit 2
    protected var useCustomUserAgent = false                            //                 Bit 3
    protected var manuallySetLanguageTags = false                       //                 Bit 4
    protected var shouldAllowJSForUrlsFromOtherApps = false             //                 Bit 5
    protected var shouldAskBeforeLoadingUrlThatIsFromAnotherApp = false //                 Bit 6
    protected var autoscrollLogMsgs = false                             //                 Bit 7
    protected var shouldAllowHTTP = false                               //                 Bit 8
    protected var showAdvancedDeveloperTools = false                    // Line 2, Byte 2, Bit 1
    protected var shouldAskBeforeFollowingRedirection = false           //                 Bit 2
    protected var shouldLinkURLsInLog = true                            //                 Bit 3
    protected var shouldUseAlgorithmicDarkening = false                 //                 Bit 4
    protected var shouldUseAlternativeSourceViewer = false              //                 Bit 5

    override fun onResume() {
        super.onResume()
        val file = File(filesDir, "config.bin")
        if (file.exists()) {
            val data1 = file.readBytes()
            if (data1.size < FINAL_INDEX_OF_MAX_LOGS + 1) {
                file.delete()
            } else {
                run {
                    val a = bitsSetOrNot(data1[0])
                    shouldUseJavaScript = a[0]
                    shouldLoadImages = a[1]
                    shouldLoadResources = a[2]
                    foregroundLoggingEnabled = a[3]
                    shouldAccept3rdPartyCookies = a[4]
                    shouldRemoveLfAndSpacesFromUrl = a[5]
                    desktopMode = a[6]
                    shouldAcceptCookies = a[7]
                }
                maxLogMsgs = run {
                    var k = 0
                    for (i in FINAL_INDEX_OF_MAX_LOGS downTo START_INDEX_OF_MAX_LOGS) {
                        k = k shl 8
                        k += (data1[i]).toUByte().toInt()
                    }
                    if (k < 0) {
                        0
                    } else {
                        k
                    }
                }
                if (data1.size > START_INDEX_OF_SEARCH_URL) {
                    val n = data1.size
                    var lastIndex = START_INDEX_OF_SEARCH_URL
                    searchURL = (ArrayList<Byte>(n - 1 - FINAL_INDEX_OF_MAX_LOGS).apply {
                        for (i in START_INDEX_OF_SEARCH_URL ..< n) {
                            lastIndex = i // LF or end of URL
                            val byte = data1[i]
                            if (byte.toInt() == '\n'.code) {
                                break
                            } else {
                                add(byte)
                            }
                        }
                    }).toByteArray().decodeToString()
                    if (n > lastIndex + 1) {
                        run {
                            val a = bitsSetOrNot(data1[lastIndex+1])
                            shouldDisplayRunButton = a[0]
                            shouldClearLogWhenRunningScript = a[1]
                            useCustomUserAgent = a[2]
                            manuallySetLanguageTags = a[3]
                            shouldAllowJSForUrlsFromOtherApps = a[4]
                            shouldAskBeforeLoadingUrlThatIsFromAnotherApp = a[5]
                            autoscrollLogMsgs = a[6]
                            shouldAllowHTTP = a[7]
                        }
                        if (n > lastIndex + 2) {
                            val a = bitsSetOrNot(data1[lastIndex+2])
                            showAdvancedDeveloperTools = a[0]
                            shouldAskBeforeFollowingRedirection = a[1]
                            shouldLinkURLsInLog = a[2]
                            shouldUseAlgorithmicDarkening = a[3]
                            shouldUseAlternativeSourceViewer = a[4]
                        }
                    }
                } else {
                    searchURL = "" // if it has been assigned another string.
                }
            }
        }
    }

    override fun onPause() {
        saveCurrentConfiguration()
        super.onPause()
    }

    override fun onStop() {
        saveCurrentConfiguration()
        super.onStop()
    }

    protected fun saveCurrentConfiguration() {
        val file = File(filesDir, "config.bin")
        if (file.exists()) {
            file.delete()
        }
        val a = ByteArray(FINAL_INDEX_OF_MAX_LOGS + 1)
        a[0] = bitsSetAccordingTo(
            booleanArrayOf(
                shouldUseJavaScript,
                shouldLoadImages,
                shouldLoadResources,
                foregroundLoggingEnabled,
                shouldAccept3rdPartyCookies,
                shouldRemoveLfAndSpacesFromUrl,
                desktopMode,
                shouldAcceptCookies
            )
        )
        var k = maxLogMsgs
        for (i in START_INDEX_OF_MAX_LOGS .. FINAL_INDEX_OF_MAX_LOGS) {
            a[i] = k.mod(256).toUByte().toByte()
            k = k shr 8
        }
        file.writeBytes(a)
        file.appendText(searchURL.replace("\n", "") + "\n")
        file.appendBytes(
            byteArrayOf(
                bitsSetAccordingTo(
                    booleanArrayOf(
                        shouldDisplayRunButton,
                        shouldClearLogWhenRunningScript,
                        useCustomUserAgent,
                        manuallySetLanguageTags,
                        shouldAllowJSForUrlsFromOtherApps,
                        shouldAskBeforeLoadingUrlThatIsFromAnotherApp,
                        autoscrollLogMsgs,
                        shouldAllowHTTP
                    )
                ),
                bitsSetAccordingTo(
                    booleanArrayOf(
                        showAdvancedDeveloperTools,
                        shouldAskBeforeFollowingRedirection,
                        shouldLinkURLsInLog,
                        shouldUseAlgorithmicDarkening,
                        shouldUseAlternativeSourceViewer
                    )
                )
            )
        )
    }

    protected fun saveUserAgent(userAgent: String) {
        File(filesDir, "userAgent.txt").also {
            if (it.exists()) {
                it.delete()
            }
            it.writeText(userAgent)
        }
    }

    protected fun getStoredUserAgent(): String? {
        return with(File(filesDir, "userAgent.txt")) {
            if (exists()) {
                readText()
            } else {
                null
            }
        }
    }

    protected fun saveLanguageTags(languageTags: String) {
        File(filesDir, "languages.txt").also {
            if (it.exists()) {
                it.delete()
            }
            it.writeText(languageTags)
        }
    }

    protected fun getStoredLanguageTags(): String? {
        return with(File(filesDir, "languages.txt")) {
            if (exists()) {
                readText()
            } else {
                null
            }
        }
    }

    protected fun saveEncodingForText(encoding: String) {
        File(filesDir, "text_encoding.txt").also {
            if (it.exists()) {
                it.delete()
            }
            it.writeText(encoding)
        }
    }

    protected fun getStoredEncodingForText(): String? {
        return with(File(filesDir, "text_encoding.txt")) {
            if (exists()) {
                readText()
            } else {
                null
            }
        }
    }

    protected fun getStoredOrDefaultEncodingForText(): String {
        return getStoredEncodingForText().orEmpty().ifEmpty { "UTF-8" }
    }
}

internal fun bitsSetOrNot(byte: Byte): BooleanArray {
    val res = BooleanArray(8)
    val u = byte.toUByte()
    for (i in 0 .. 7) {
        val n = (1 shl i).toUInt().toUByte()
        res[i] = (u and n) == n
    }
    return res
}

internal fun bitsSetAccordingTo(a: BooleanArray): Byte {
    var b = 0.toUByte()
    for (i in 0 ..< a.size) {
        if (i > 7) {
            break
        } else if (a[i]) {
            b = b or (1 shl i).toUInt().toUByte()
        }
    }
    return b.toByte()
}
