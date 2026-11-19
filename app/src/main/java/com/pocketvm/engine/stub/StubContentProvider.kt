package com.pocketvm.engine.stub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * ContentProvider proxy that hosts guest ContentProviders inside PocketVM containers.
 * Each instance can serve different guest ContentProviders depending on the authority mapping.
 *
 * 10 numbered subclasses (StubProvider0–StubProvider9) are declared for the manifest pool,
 * each with a unique authority (com.pocketvm.vprovider0 through vprovider9).
 */
open class StubContentProvider : ContentProvider() {

    companion object {
        private const val TAG = "StubContentProvider"

        /** Maps virtual authority → real guest ContentProvider class name + vmId */
        private val authorityMap = mutableMapOf<String, ProviderMapping>()

        data class ProviderMapping(
            val vmId: String,
            val packageName: String,
            val providerClassName: String,
            val guestAuthority: String
        )

        fun registerProvider(
            stubAuthority: String,
            vmId: String,
            packageName: String,
            providerClassName: String,
            guestAuthority: String
        ) {
            authorityMap[stubAuthority] = ProviderMapping(
                vmId, packageName, providerClassName, guestAuthority
            )
        }

        fun unregisterProvider(stubAuthority: String) {
            authorityMap.remove(stubAuthority)
        }

        fun findAvailableAuthority(): String? {
            for (i in 0 until 10) {
                val authority = "com.pocketvm.vprovider$i"
                if (!authorityMap.containsKey(authority)) return authority
            }
            return null
        }
    }

    private var guestProvider: ContentProvider? = null

    override fun onCreate(): Boolean {
        Log.d(TAG, "StubContentProvider created")
        return true
    }

    private fun resolveGuestProvider(): ContentProvider? {
        if (guestProvider != null) return guestProvider

        val authority = callingAuthority() ?: return null
        val mapping = authorityMap[authority] ?: return null

        try {
            val vfs = com.pocketvm.engine.io.VirtualFileSystem.get()
            val apkDir = vfs.getApkDir(mapping.vmId, mapping.packageName)
            val apkFile = apkDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
                ?: return null

            val classLoader = StubActivity.getOrCreateClassLoader(
                context!!, mapping.vmId, mapping.packageName, apkFile.absolutePath
            )
            val providerClass = classLoader.loadClass(mapping.providerClassName)
            val provider = providerClass.newInstance() as ContentProvider
            guestProvider = provider
            return provider
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve guest provider: ${e.message}")
            return null
        }
    }

    private fun callingAuthority(): String? {
        // Derive authority from class name
        val className = javaClass.simpleName
        val index = className.removePrefix("StubProvider").toIntOrNull() ?: return null
        return "com.pocketvm.vprovider$index"
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        val guest = resolveGuestProvider()
        return try {
            guest?.query(uri, projection, selection, selectionArgs, sortOrder)
        } catch (e: Exception) {
            Log.e(TAG, "Guest query failed: ${e.message}")
            null
        }
    }

    override fun getType(uri: Uri): String? {
        val guest = resolveGuestProvider()
        return try {
            guest?.getType(uri)
        } catch (e: Exception) {
            null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val guest = resolveGuestProvider()
        return try {
            guest?.insert(uri, values)
        } catch (e: Exception) {
            Log.e(TAG, "Guest insert failed: ${e.message}")
            null
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val guest = resolveGuestProvider()
        return try {
            guest?.delete(uri, selection, selectionArgs) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Guest delete failed: ${e.message}")
            0
        }
    }

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int {
        val guest = resolveGuestProvider()
        return try {
            guest?.update(uri, values, selection, selectionArgs) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Guest update failed: ${e.message}")
            0
        }
    }

    // ── 10 Stub subclasses ───────────────────────────────────────────────
    class StubProvider0 : StubContentProvider()
    class StubProvider1 : StubContentProvider()
    class StubProvider2 : StubContentProvider()
    class StubProvider3 : StubContentProvider()
    class StubProvider4 : StubContentProvider()
    class StubProvider5 : StubContentProvider()
    class StubProvider6 : StubContentProvider()
    class StubProvider7 : StubContentProvider()
    class StubProvider8 : StubContentProvider()
    class StubProvider9 : StubContentProvider()
}
