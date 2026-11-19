package com.pocketvm.engine.stub

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Base proxy Activity that hosts a guest app's Activity inside a PocketVM container.
 *
 * When launched, it reads the real guest intent from extras, loads the guest APK's
 * classloader, and delegates lifecycle to the guest Activity.
 *
 * The manifest declares 50 numbered subclasses (Stub0–Stub49) so that multiple
 * guest activities can be alive at once, each in its own task/back stack slot.
 */
open class StubActivity : Activity() {

    companion object {
        private const val TAG = "StubActivity"
        const val EXTRA_VM_ID = "pocketvm.extra.VM_ID"
        const val EXTRA_TARGET_PACKAGE = "pocketvm.extra.TARGET_PACKAGE"
        const val EXTRA_TARGET_ACTIVITY = "pocketvm.extra.TARGET_ACTIVITY"
        const val EXTRA_ORIGINAL_INTENT = "pocketvm.extra.ORIGINAL_INTENT"

        /** ClassLoader cache keyed by "vmId:packageName" */
        private val classLoaderCache = ConcurrentHashMap<String, ClassLoader>()

        /** Tracks which stub indices are in use; returns the first free one. */
        private val usedStubs = ConcurrentHashMap<Int, Boolean>()
        private const val STUB_COUNT = 50

        fun allocateStubIndex(): Int {
            for (i in 0 until STUB_COUNT) {
                if (usedStubs.putIfAbsent(i, true) == null) return i
            }
            // Fallback: reuse slot 0
            return 0
        }

        fun releaseStubIndex(index: Int) {
            usedStubs.remove(index)
        }

        /**
         * Create a launch Intent that wraps a guest Activity launch through a Stub.
         */
        fun createLaunchIntent(
            context: Context,
            vmId: String,
            targetPackage: String,
            targetActivity: String,
            originalIntent: Intent? = null
        ): Intent {
            val stubIndex = allocateStubIndex()
            val stubClassName = "com.pocketvm.engine.stub.StubActivity\$Stub$stubIndex"
            val intent = Intent()
            intent.component = ComponentName(context.packageName, stubClassName)
            intent.putExtra(EXTRA_VM_ID, vmId)
            intent.putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
            intent.putExtra(EXTRA_TARGET_ACTIVITY, targetActivity)
            originalIntent?.let { intent.putExtra(EXTRA_ORIGINAL_INTENT, it) }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }

        /**
         * Gets or creates a ClassLoader for a guest APK.
         */
        fun getOrCreateClassLoader(
            context: Context,
            vmId: String,
            packageName: String,
            apkPath: String
        ): ClassLoader {
            val key = "$vmId:$packageName"
            return classLoaderCache.getOrPut(key) {
                val optimizedDir = File(context.codeCacheDir, "dex/$vmId/$packageName")
                optimizedDir.mkdirs()
                DexClassLoader(
                    apkPath,
                    optimizedDir.absolutePath,
                    null,
                    context.classLoader
                )
            }
        }

        fun clearClassLoaderCache(vmId: String) {
            val keysToRemove = classLoaderCache.keys.filter { it.startsWith("$vmId:") }
            keysToRemove.forEach { classLoaderCache.remove(it) }
        }
    }

    private var guestActivity: Activity? = null
    private var stubIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vmId = intent.getStringExtra(EXTRA_VM_ID)
        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        val targetActivity = intent.getStringExtra(EXTRA_TARGET_ACTIVITY)

        if (vmId == null || targetPackage == null || targetActivity == null) {
            Log.e(TAG, "Missing required extras for stub launch")
            finish()
            return
        }

        // Determine stub index from class name
        val className = javaClass.simpleName
        stubIndex = className.removePrefix("Stub").toIntOrNull() ?: -1

        Log.d(TAG, "Stub$stubIndex launching guest: $targetPackage/$targetActivity in VM $vmId")

        try {
            // Find the APK path for this package in the VM
            val vfs = com.pocketvm.engine.io.VirtualFileSystem.get()
            val apkDir = vfs.getApkDir(vmId, targetPackage)
            val apkFile = apkDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }

            if (apkFile == null || !apkFile.exists()) {
                Log.e(TAG, "APK not found for $targetPackage in VM $vmId")
                finish()
                return
            }

            val classLoader = getOrCreateClassLoader(this, vmId, targetPackage, apkFile.absolutePath)
            val activityClass = classLoader.loadClass(targetActivity)
            val guest = activityClass.newInstance() as Activity

            // Attach guest activity to this stub's window and context
            guestActivity = guest
            Log.d(TAG, "Guest activity $targetActivity loaded successfully")

            // Try to call guest onCreate
            try {
                val onCreateMethod = Activity::class.java.getDeclaredMethod(
                    "onCreate", Bundle::class.java
                )
                onCreateMethod.isAccessible = true
                onCreateMethod.invoke(guest, savedInstanceState)
            } catch (e: Exception) {
                Log.w(TAG, "Could not invoke guest onCreate: ${e.message}")
            }

        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Guest activity class not found: $targetActivity", e)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch guest activity", e)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            guestActivity?.let {
                val method = Activity::class.java.getDeclaredMethod("onResume")
                method.isAccessible = true
                method.invoke(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Guest onResume failed: ${e.message}")
        }
    }

    override fun onPause() {
        try {
            guestActivity?.let {
                val method = Activity::class.java.getDeclaredMethod("onPause")
                method.isAccessible = true
                method.invoke(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Guest onPause failed: ${e.message}")
        }
        super.onPause()
    }

    override fun onDestroy() {
        try {
            guestActivity?.let {
                val method = Activity::class.java.getDeclaredMethod("onDestroy")
                method.isAccessible = true
                method.invoke(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Guest onDestroy failed: ${e.message}")
        }
        guestActivity = null
        if (stubIndex >= 0) releaseStubIndex(stubIndex)
        super.onDestroy()
    }

    // ── 50 Stub subclasses for manifest declaration ──────────────────────
    class Stub0 : StubActivity()
    class Stub1 : StubActivity()
    class Stub2 : StubActivity()
    class Stub3 : StubActivity()
    class Stub4 : StubActivity()
    class Stub5 : StubActivity()
    class Stub6 : StubActivity()
    class Stub7 : StubActivity()
    class Stub8 : StubActivity()
    class Stub9 : StubActivity()
    class Stub10 : StubActivity()
    class Stub11 : StubActivity()
    class Stub12 : StubActivity()
    class Stub13 : StubActivity()
    class Stub14 : StubActivity()
    class Stub15 : StubActivity()
    class Stub16 : StubActivity()
    class Stub17 : StubActivity()
    class Stub18 : StubActivity()
    class Stub19 : StubActivity()
    class Stub20 : StubActivity()
    class Stub21 : StubActivity()
    class Stub22 : StubActivity()
    class Stub23 : StubActivity()
    class Stub24 : StubActivity()
    class Stub25 : StubActivity()
    class Stub26 : StubActivity()
    class Stub27 : StubActivity()
    class Stub28 : StubActivity()
    class Stub29 : StubActivity()
    class Stub30 : StubActivity()
    class Stub31 : StubActivity()
    class Stub32 : StubActivity()
    class Stub33 : StubActivity()
    class Stub34 : StubActivity()
    class Stub35 : StubActivity()
    class Stub36 : StubActivity()
    class Stub37 : StubActivity()
    class Stub38 : StubActivity()
    class Stub39 : StubActivity()
    class Stub40 : StubActivity()
    class Stub41 : StubActivity()
    class Stub42 : StubActivity()
    class Stub43 : StubActivity()
    class Stub44 : StubActivity()
    class Stub45 : StubActivity()
    class Stub46 : StubActivity()
    class Stub47 : StubActivity()
    class Stub48 : StubActivity()
    class Stub49 : StubActivity()
}
