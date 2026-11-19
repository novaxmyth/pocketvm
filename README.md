# PocketVM 3.0

> **Real App Virtualization Engine for Android**
> Run isolated, independent Android app containers inside a normal Android app — no root required.

[![Build PocketVM APK](https://github.com/novaxmyth/pocketvm/actions/workflows/build.yml/badge.svg)](https://github.com/novaxmyth/pocketvm/actions/workflows/build.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

---

## What is PocketVM?

PocketVM is an open-source Android virtualization engine that creates isolated containers for running apps inside your app. Unlike the old version which was a UI-only simulation, **PocketVM 3.0 uses real app virtualization** — the same container-based approach used by VPhoneGaGa, VMOS, and VirtualApp.

Apps are loaded via `DexClassLoader`, framework services are intercepted with Java Dynamic Proxies, and each guest app runs in a sandboxed process with its own spoofed device identity.

```
┌──────────────────────────────────────────────────────────┐
│                    Host Android Device                    │
│                                                          │
│  ┌────────────────────────────────────────────────────┐  │
│  │                PocketVM Host App                    │  │
│  │                                                    │  │
│  │  ┌─────────────────────────────────────────────┐   │  │
│  │  │          Dashboard (Jetpack Compose)         │   │  │
│  │  │  [VM 1: Pixel 8] [VM 2: S24] [VM 3: AOSP]  │   │  │
│  │  └──────────────────┬──────────────────────────┘   │  │
│  │                     │                              │  │
│  │  ┌──────────────────▼──────────────────────────┐   │  │
│  │  │            Virtual Engine Core               │   │  │
│  │  │  • ServiceHookManager (AMS/PMS proxy)        │   │  │
│  │  │  • 50 Stub Activities (manifest pool)        │   │  │
│  │  │  • 20 Stub Services + 10 Stub Providers      │   │  │
│  │  │  • DexClassLoader (guest APK loading)        │   │  │
│  │  │  • VProcessManager (process tracking)        │   │  │
│  │  └──────────────────┬──────────────────────────┘   │  │
│  │                     │                              │  │
│  │  ┌──────────────────▼──────────────────────────┐   │  │
│  │  │          Sandboxed VM Instances               │   │  │
│  │  │  /vms/{id}/data/data/{pkg}/  (app data)      │   │  │
│  │  │  /vms/{id}/data/app/{pkg}/   (APKs)          │   │  │
│  │  │  /vms/{id}/sdcard/           (virtual SD)    │   │  │
│  │  │  /vms/{id}/system/build.prop (identity)      │   │  │
│  │  └─────────────────────────────────────────────┘   │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

---

## Features

### 🔧 Real App Virtualization
- **Dynamic APK Loading** via `DexClassLoader` — guest apps are loaded without installing on the host
- **Framework Service Hooking** — `IActivityManager` and `IPackageManager` intercepted via Java Dynamic Proxy
- **80 Stub Components** — 50 Activities + 20 Services + 10 ContentProviders pre-declared in manifest
- **Process Isolation** — guest apps run in separate `:vm` process

### 🆔 Per-Instance Device Identity Spoofing
- Unique IMEI, Android ID, serial number, MAC address per VM
- Full `build.prop` generation (model, manufacturer, fingerprint)
- Pre-built profiles: Pixel 8 Pro, Galaxy S24 Ultra, OnePlus 12, AOSP Generic

### 📦 App Management
- **1-Tap Host App Cloning** — clone any installed app into a VM with isolated data
- **APK File Installation** — install any `.apk` from storage
- **Dual Accounts** — run two WhatsApp, Telegram, etc. side by side

### 🛡️ Virtual Root (SU)
- Toggle root on/off per VM — completely safe, host stays unrooted
- Simulated `/system/xbin/su` with uid=0 elevation inside container

### 🛰️ Mock GPS & Sensors
- Inject mock GPS coordinates per VM
- Preset locations (San Francisco, Tokyo, London, Paris, New York, Sydney)
- Custom latitude/longitude input

### 🎨 Modern UI
- **Jetpack Compose** + **Material 3** — dark cyberpunk theme
- Simulated phone experience with status bar and navigation bar
- Floating V-Ball overlay for quick VM controls

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM (ViewModel + StateFlow) |
| Database | Room |
| Navigation | Navigation Compose |
| Hooking | Java Reflection + Dynamic Proxy |
| APK Loading | DexClassLoader |
| Build | Gradle KTS, compileSdk 35 |

---

## Building from Source

### Prerequisites
- JDK 17 (Temurin recommended)
- Android SDK 35

### Build
```bash
git clone https://github.com/novaxmyth/pocketvm.git
cd pocketvm
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

---

## Limitations (Honest)

- **No Google Play Services** — apps requiring GMS will not work unless microG is integrated
- **Banking/Payment Apps** — apps with strong integrity checks (SafetyNet, Play Integrity) will detect the container
- **Native-Heavy Games** — games with custom engines or anti-cheat may crash
- **This is a container, not a full VM** — apps share the host kernel, there is no hardware virtualization
- **Hidden API restrictions** — Android 9+ restricts reflection on hidden APIs; some hooks may fail on newer OS versions

---

## Project Structure

```
app/src/main/java/com/pocketvm/
├── data/           # Room database, models, DAOs
│   ├── model/      # VmInstance, InstalledApp, DeviceProfile, VmState
│   └── db/         # PocketVmDatabase, DAOs, Converters
├── engine/         # Virtualization engine core
│   ├── hook/       # ServiceHookManager, AMS/PMS proxies
│   ├── process/    # VProcessManager, StubActivityRecord
│   ├── stub/       # StubActivity (×50), StubService (×20), StubContentProvider (×10)
│   ├── component/  # ComponentResolver
│   └── io/         # VirtualFileSystem
├── instance/       # InstanceManager, InstanceStorageManager
├── identity/       # DeviceIdentityManager, BuildPropGenerator, MockLocationProvider
├── apps/           # ApkParser, ApkInstaller, HostAppScanner, AppLauncher
├── ui/             # Jetpack Compose UI layer
│   ├── theme/      # Color, Typography, Theme
│   ├── screen/     # Dashboard, VirtualPhone, Settings, AppDrawer, ApkInstaller
│   ├── component/  # VmInstanceCard, StatusBar, NavigationBar
│   └── viewmodel/  # Dashboard, VirtualPhone, Settings ViewModels
└── service/        # VmForegroundService, FloatingWindowService
```

---

## License

```
Copyright 2026 PocketVM Authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
