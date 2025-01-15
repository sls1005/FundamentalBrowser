package test.sls1005.projects.fundamentalbrowser

import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.experimental.and
import kotlin.experimental.or

open class ConfiguratedActivity : AppCompatActivity() {
    // Default:                                       // Stored file format:
    protected var shouldUseJavaScript = true          // Byte 1, Bit 1, true if set, false if not set
    protected var shouldLoadImages = true             //         Bit 2
    protected var shouldLoadResources = true          //         Bit 3
    protected var foregroundLoggingEnabled = true     //         Bit 4
    protected var shouldAccept3rdPartyCookies = false //         Bit 5
    protected var maxLogMsgs = 20                     // Byte 2-5, L.E.
    protected var searchURL = ""                      // Starting from byte 6

    override fun onResume() {
        super.onResume()
        val file = File(filesDir, "config.bin")
        if (file.exists()) {
            val data1 = file.readBytes()
            if (data1.size < 5) {
                file.delete()
            } else {
                val byte1 = data1[0]
                shouldUseJavaScript = ((byte1 and 1) == 1.toByte())
                shouldLoadImages = ((byte1 and 2) == 2.toByte())
                shouldLoadResources = ((byte1 and 4) == 4.toByte())
                foregroundLoggingEnabled = ((byte1 and 8) == 8.toByte())
                shouldAccept3rdPartyCookies = ((byte1 and 16) == 16.toByte())
                maxLogMsgs = run {
                    var k = 0
                    for (i in 4 downTo 1) {
                        k = k shl 8
                        k += (data1[i]).toUByte().toInt()
                    }
                    if (k < 0) {
                        0
                    } else {
                        k
                    }
                }
                if (data1.size > 5) {
                    val n = data1.size
                    val data2 = ByteArray(n - 5).apply {
                        for (i in 5 ..< n) {
                            val j = i - 5
                            this.set(j, data1[i])
                        }
                    }
                    searchURL = data2.decodeToString()
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
        var byte1: Byte = 0
        listOf<Pair<Boolean, Byte>>(
            Pair(shouldUseJavaScript, 1),
            Pair(shouldLoadImages, 2),
            Pair(shouldLoadResources, 4),
            Pair(foregroundLoggingEnabled, 8),
            Pair(shouldAccept3rdPartyCookies, 16)
        ).forEach { it ->
            val (flag, n) = it
            if (flag) {
                byte1 = (byte1 or n)
            }
        }
        val a = ByteArray(5)
        var k = maxLogMsgs
        a[0] = byte1.toByte()
        for (i in 1 .. 4) {
            a[i] = k.mod(256).toUByte().toByte()
            k = k shr 8
        }
        file.writeBytes(a)
        if (searchURL != "") {
            file.appendText(searchURL)
        }
    }
}