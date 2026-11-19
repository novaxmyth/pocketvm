package com.pocketvm.ui

import android.app.Application
import com.pocketvm.apps.ApkInstaller
import com.pocketvm.apps.AppLauncher
import com.pocketvm.apps.HostAppScanner
import com.pocketvm.engine.VirtualCore
import com.pocketvm.engine.io.VirtualFileSystem
import com.pocketvm.identity.DeviceIdentityManager
import com.pocketvm.identity.MockLocationProvider
import com.pocketvm.instance.InstanceManager

/**
 * Application entry point. Initializes all PocketVM engine singletons.
 */
class PocketVmApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize engine singletons in dependency order
        VirtualFileSystem.init(this)
        VirtualCore.init(this)
        InstanceManager.init(this)
        DeviceIdentityManager.init(this)
        MockLocationProvider.init(this)
        ApkInstaller.init(this)
        // HostAppScanner is an `object` singleton — no init needed
        AppLauncher.init(this)
    }
}
