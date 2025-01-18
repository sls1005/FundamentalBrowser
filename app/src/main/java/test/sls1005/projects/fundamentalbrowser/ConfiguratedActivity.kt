package test.sls1005.projects.fundamentalbrowser

import androidx.appcompat.app.AppCompatActivity
import java.io.File

private const val START_INDEX_OF_MAX_LOGS = 1
private const val FINAL_INDEX_OF_MAX_LOGS = 4
private const val START_INDEX_OF_SEARCH_URL = 5

open class ConfiguratedActivity : AppCompatActivity() {
    // Default:                                          // Stored file format:
    protected var shouldUseJavaScript = true             // Line 1, Byte 1, Bit 1 (L.S.B.), true if set, false if not set
    protected var shouldLoadImages = true                //         Bit 2
    protected var shouldLoadResources = true             //         Bit 3
    protected var foregroundLoggingEnabled = true        //         Bit 4
    protected var shouldAccept3rdPartyCookies = false    //         Bit 5
    protected var shouldRemoveLfAndSpacesFromUrl = true  //         Bit 6
    protected var maxLogMsgs = 20                        // Byte 2-5, L.E.
    protected var searchURL = ""                         // Starting from byte 6, ends with LF
    protected var shouldDisplayRunButton = false         // Line 2, Byte 1, Bit 1; experiemental

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
                        shouldDisplayRunButton = bitsSetOrNot(data1[lastIndex+1])[0]
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
                shouldRemoveLfAndSpacesFromUrl
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
                    arrayOf(shouldDisplayRunButton).toBooleanArray()
                )
            ).toByteArray()
        )
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