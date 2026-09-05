package com.antidaze.pocketvm.guest

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/** Finds and downloads the prebuilt Android 12 guest image from GitHub releases. */
object GuestImages {

    const val REPO = "novaxmyth/pocketvm"
    const val ASSET_PREFIX = "pocketvm-android12"

    /** Returns (download URL, byte size) of the latest Android guest image asset. */
    fun latestAndroidAsset(): Pair<String, Long>? {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "pocketvm")
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name", "")
                if (name.startsWith(ASSET_PREFIX) && name.endsWith(".zip")) {
                    return a.getString("browser_download_url") to a.optLong("size", 0L)
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** Streams a URL to a file with progress callbacks. */
    fun download(url: String, dest: File, onProgress: (read: Long, total: Long) -> Unit) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 19)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    /** Extracts vmlinuz + rootfs.img.gz from the guest bundle zip. */
    fun unzip(zip: File, into: File): List<String> {
        val names = mutableListOf<String>()
        into.mkdirs()
        zip.inputStream().use { raw ->
            ZipInputStream(raw.buffered(1 shl 19)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (!e.isDirectory) {
                        File(into, e.name).outputStream().use { z.copyTo(it, 1 shl 19) }
                        names.add(e.name)
                    }
                    z.closeEntry()
                }
            }
        }
        return names
    }

    /** Decompresses rootfs.img.gz -> rootfs.img (raw ext4 for QEMU). */
    fun gunzip(src: File, dest: File) {
        GZIPInputStream(src.inputStream().buffered(1 shl 19), 1 shl 17).use { input ->
            dest.outputStream().buffered(1 shl 19).use { output ->
                input.copyTo(output, 1 shl 19)
            }
        }
    }
}
