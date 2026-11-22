package app.zsync.core

import okhttp3.Request
import java.io.File

data class AppRelease(val version: String, val apkUrl: String)

object Updates {
    private const val LATEST = "https://api.github.com/repos/ElDrito/Z-Sync/releases/latest"

    /** Latest published release with an APK attached, or null when GitHub can't be reached. */
    fun latest(): AppRelease? {
        val r = Http.request("GET", LATEST, headers = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "Z-Sync"))
        val body = r.body?.takeIf { r.status == 200 } ?: return null
        val tag = body.str("tag_name")?.removePrefix("v") ?: return null
        val apk = body.arr("assets").firstOrNull { it.str("name")?.endsWith(".apk") == true } ?: return null
        return AppRelease(tag, apk.str("browser_download_url") ?: return null)
    }

    /** "1.0.1" is newer than "1.0"; with equal numbers, a final release beats a pre-release like "1.1-rc". */
    fun isNewer(latest: String, current: String): Boolean {
        fun nums(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = nums(latest); val b = nums(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return '-' in current && '-' !in latest
    }

    fun download(url: String, target: File, onProgress: (Float) -> Unit): Boolean = runCatching {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        Http.client.newCall(Request.Builder().url(url).header("User-Agent", "Z-Sync").build()).execute().use { res ->
            if (!res.isSuccessful) return false
            val body = res.body ?: return false
            val total = body.contentLength()
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        target.delete()
        part.renameTo(target)
    }.getOrDefault(false)
}
