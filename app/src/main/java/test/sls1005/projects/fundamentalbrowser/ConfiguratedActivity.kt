package test.sls1005.projects.fundamentalbrowser

import androidx.appcompat.app.AppCompatActivity
import java.io.File

private const val START_INDEX_OF_MAX_LOGS = 1
private const val FINAL_INDEX_OF_MAX_LOGS = 4
private const val START_INDEX_OF_SEARCH_URL = 5

open class ConfiguratedActivity : AppCompatActivity() {
    // Default:                                          // Stored file format:
    protected var shouldUseJavaScript = true             // Line 1, Byte 1, Bit 1 (LSB), true if set, false if not set
    protected var shouldLoadImages = true                //         Bit 2
    protected var shouldLoadResources = true             //         Bit 3
    protected var foregroundLoggingEnabled = true        //         Bit 4
    protected var shouldAccept3rdPartyCookies = false    //         Bit 5
    protected var shouldRemoveLfAndSpacesFromUrl = true  //         Bit 6
    protected var desktopMode = false                    //         Bit 7
    protected var maxLogMsgs = 20                        // Byte 2-5, L.E.
    protected var searchURL = ""                         // Starting from byte 6, and then LF
    protected var shouldDisplayRunButton = false         // Line 2, Byte 1, Bit 1; experimental
    protected var shouldClearLogWhenRunningScript = true //                 Bit 2
    protected var useCustomUserAgent = false             //                 Bit 3
    protected var manuallySetLanguageTags = false        //                 Bit 4

    override fun onResume() {
        super.onResume()
        val file = File(filesDir, "config.bin")
        if (file.exists()) {
            val data1 = file.readBytes()
            if (data1.size < FINAL_INDEX_OF_MAX_LOGS + 1) {
                file.delete()
            } else {
                val a1 = bitsSetOrNot(data1[0])
                shouldUseJavaScript = a1[0]
                shouldLoadImages = a1[1]
                shouldLoadResources = a1[2]
                foregroundLoggingEnabled = a1[3]
                shouldAccept3rdPartyCookies = a1[4]
                shouldRemoveLfAndSpacesFromUrl = a1[5]
                desktopMode = a1[6]

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
                        val a2 = bitsSetOrNot(data1[lastIndex+1])
                        shouldDisplayRunButton = a2[0]
                        shouldClearLogWhenRunningScript = a2[1]
                        useCustomUserAgent = a2[2]
                        manuallySetLanguageTags = a2[3]
                    }
                } else {
                    searchURL = "" // if it has been assigned another string.
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val file = File(filesDir, "config.bin")
        if (file.exists()) {
            file.delete()
        }
        val byte1 = bitsSetAccordingTo(
            arrayOf(
                shouldUseJavaScript,
                shouldLoadImages,
                shouldLoadResources,
                foregroundLoggingEnabled,
                shouldAccept3rdPartyCookies,
                shouldRemoveLfAndSpacesFromUrl,
                desktopMode
            ).toBooleanArray()
        )
        val a = ByteArray(FINAL_INDEX_OF_MAX_LOGS + 1)
        a[0] = byte1
        var k = maxLogMsgs
        for (i in START_INDEX_OF_MAX_LOGS .. FINAL_INDEX_OF_MAX_LOGS) {
            a[i] = k.mod(256).toUByte().toByte()
            k = k shr 8
        }
        file.writeBytes(a)
        file.appendText(searchURL + "\n")
        file.appendBytes(
            arrayOf(
                bitsSetAccordingTo(
                    arrayOf(
                        shouldDisplayRunButton,
                        shouldClearLogWhenRunningScript,
                        useCustomUserAgent,
                        manuallySetLanguageTags
                    ).toBooleanArray()
                )
            ).toByteArray()
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
}

private fun bitsSetOrNot(byte: Byte): BooleanArray {
    val res = BooleanArray(8)
    val u = byte.toUByte()
    for (i in 0 .. 7) {
        val n = (1 shl i).toUInt().toUByte()
        res[i] = (u and n) == n
    }
    return res
}

private fun bitsSetAccordingTo(a: BooleanArray): Byte {
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