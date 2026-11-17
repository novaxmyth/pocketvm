# PocketVM 2.0

> **High-Performance Native Android Virtual Instance Host**  
> Run isolated, independent Android virtual instances inside a normal, unprivileged Android application — no root required.

[![Build PocketVM APK](https://github.com/novaxmyth/pocketvm/actions/workflows/build.yml/badge.svg)](https://github.com/novaxmyth/pocketvm/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/novaxmyth/pocketvm?color=00E5FF)](https://github.com/novaxmyth/pocketvm/releases)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

---

## Overview

PocketVM is an open-source Android virtualization host inspired by **VPhoneGaGa**, **VMOS**, and **VirtualMaster**. It allows you to create, manage, and run complete isolated Android instances directly on your phone without rooting your device.

Unlike previous attempts that relied on slow, battery-draining QEMU TCG software emulation and heavy 800+ MB multi-gigabyte disk images, **PocketVM 2.0** utilizes a lightweight, zero-overhead native container architecture. It executes applications at **100% native hardware speed (60–120 FPS)** with sub-second boot times and minimal battery consumption.

```
┌─────────────────────────────────────────────────────────────┐
│                       Host Android                          │
│                                                             │
│   ┌─────────────────────────────────────────────────────┐   │
│   │               PocketVM Application                  │   │
│   │                                                     │   │
│   │   ┌─────────────────────────────────────────────┐   │   │
│   │   │            PocketVM OS Launcher             │   │   │
│   │   │   • Dynamic Status Bar (Clock, WiFi, Batt)  │   │   │
│   │   │   • Quick Settings Pull-Down Shade          │   │   │
│   │   │   • App Grid & Search Widget                │   │   │
│   │   │   • Nav Bar (Back, Home, Recents Switcher)  │   │   │
│   │   │   • Floating Assistant Ball (V-Ball)        │   │   │
│   │   └──────────────────────┬──────────────────────┘   │   │
│   │                          │                          │   │
│   │   ┌──────────────────────▼──────────────────────┐   │   │
│   │   │         Virtual Container Engine            │   │   │
│   │   │   • Virtual Package Manager (VPM)           │   │   │
│   │   │   • Sandboxed Storage (/data/data, /sdcard) │   │   │
│   │   │   • Virtual System Properties (getprop)     │   │   │
│   │   │   • Virtual Shell (Toybox/Busybox + SU)     │   │   │
│   │   │   • Mock GPS & Battery Sensor Provider      │   │   │
│   │   └──────────────────────┬──────────────────────┘   │   │
│   │                          │                          │   │
│   │   ┌──────────────────────▼──────────────────────┐   │   │
│   │   │          Isolated VM Instances              │   │   │
│   │   │   [Instance 1: Pixel 8] [Instance 2: S24]   │   │   │
│   │   └─────────────────────────────────────────────┘   │   │
│   └─────────────────────────────────────────────────────┘   │
│                              │                              │
│               Direct Hardware GPU Acceleration              │
│               Native ARM64 / x86_64 CPU Execution           │
└─────────────────────────────────────────────────────────────┘
```

---

## Key Features

### ⚡ 100% Native Performance (Zero Emulation Lag)
- Guest applications run on bare-metal ARM64/x86_64 using the Android ART runtime.
- Direct hardware-accelerated GPU rendering (Vulkan & EGL) up to 120Hz.
- Instant cold boot in under **200 milliseconds** — no waiting 20 minutes for a virtual kernel to boot.

### 📱 Complete "PocketVM OS" Virtual Phone UI
- **Interactive Android Desktop**: Full launcher with time/date widget, real-time app search, app drawer, and favorites dock.
- **Dynamic Status Bar**: Real-time clock, WiFi status, battery percentage, charging indicator, and `#` root icon.
- **Pull-Down Quick Settings**: Toggle WiFi, Virtual Root, Mock Location, Auto-Rotate, and Display Brightness.
- **3D Task Switcher (Recents)**: Multi-tasking card view to switch between active apps or dismiss tasks.
- **System Navigation Bar**: Back, Home, and Recents navigation.

### 🪟 Picture-in-Picture (PiP) Floating Window Mode
- Just like VPhoneGaGa, shrink the virtual phone into a movable, resizable floating overlay on top of any host application.
- Multitask between your host phone and your virtual phone simultaneously.

### 📦 APK Installer & Host App Cloner
- **1-Tap App Cloner**: Browse apps already installed on your host phone and clone them into the virtual machine with clean, isolated data.
- **APK Package Installer**: Install any `.apk` file from host storage or shared folders.
- **Dual Accounts**: Run two instances of WhatsApp, Telegram, Discord, or games side by side.

### 🛡️ Virtual Root (SU) & Magisk Simulator
- Toggle Root access on or off per VM instance with a single tap.
- Provides `/system/xbin/su` with privilege elevation (`uid=0`) inside the Virtual Shell.
- Completely safe: requires **zero root privileges on the host device**.

### 🛰️ Mock Sensors & GPS Lab
- Built-in Mock GPS location injection with one-tap presets (San Francisco, Tokyo, London, Paris, New York) or custom latitude/longitude.
- Mock Battery Simulator: customize virtual battery level (1–100%) and charging status.

### 💻 Built-in Virtual Terminal Emulator
- Colorized ANSI terminal console.
- Emulates Busybox/Toybox commands: `ls`, `cd`, `pwd`, `cat`, `touch`, `mkdir`, `rm`, `cp`, `mv`, `uname`, `whoami`, `id`, `df`, `free`, `ps`, `top`, `pm`, `am`, `getprop`, `setprop`, `su`.

### 📁 Shared Storage Bridge
- Seamless file exchange between Host and VM: files placed in `/sdcard/PocketVM/Shared` are immediately available inside all VMs at `/sdcard/Shared/`.

### 🔄 Multi-Instance Management
- Create multiple independent virtual machines:
  - **Google Pixel 8 Pro** (Android 14)
  - **Samsung Galaxy S24 Ultra** (Android 14)
  - **Gaming Sandbox** (120Hz, Rooted)
  - **Privacy Sandbox** (Clean AOSP, Isolated)
- Custom RAM allocation (1 GB, 2 GB, 3 GB, 4 GB).
- Custom resolutions (1080x2400 FHD+, 1080x1920 FHD, 720x1280 HD, custom DPI).
- One-click VM cloning, backup (`.pvm`), and restore.

---

## Built-in System Applications

Every newly created PocketVM instance includes 9 built-in virtual apps:

| App | Package | Description |
|---|---|---|
| ⚙️ **Settings** | `com.pocketvm.settings` | Configure VM hardware, resolution, DPI, Root access, and clean cache. |
| 📁 **Files** | `com.pocketvm.files` | Browse virtual storage (`/sdcard/`, `/shared/`), install APKs, import/export files. |
| 🌐 **Browser** | `com.pocketvm.browser` | Sandboxed web browser with tabbed navigation and search. |
| 💻 **Terminal** | `com.pocketvm.terminal` | Interactive Linux/Android shell with command shortcuts and root toggle. |
| 📦 **App Center** | `com.pocketvm.installer` | Clone apps from host phone or manage installed VM packages. |
| 🛰️ **Sensors & GPS** | `com.pocketvm.sensor` | Mock GPS coordinates provider and battery simulator. |
| 🛡️ **Superuser** | `com.pocketvm.su` | Root permission controller and Magisk mock verification. |
| 📷 **Camera Sim** | `com.pocketvm.camera` | Virtual camera test pattern feed with SMPTE color bars and timecode. |
| ℹ️ **Device Info** | `com.pocketvm.deviceinfo` | Hardware inspection, CPU cores, RAM, and GPU capabilities. |

---

## Floating Assistant Ball (V-Ball)

While inside the Virtual Phone, a floating assistant ball is docked to the edge of the screen:
- Drag to reposition anywhere.
- Tap to access the quick menu:
  - 🏠 **Return to Host Dashboard**
  - 🪟 **Minimize to Floating Window (PiP)**
  - 📸 **Take VM Screenshot**
  - 🔄 **Rotate Screen (Portrait / Landscape)**
  - ⚡ **Soft Reboot VM**
  - ⏹️ **Power Off VM**

---

## Building from Source

### Prerequisites
- JDK 17 (Temurin recommended)
- Android SDK 35 (compileSdk 35, minSdk 26, targetSdk 34)

### Local Build
```bash
# Clone the repository
git clone https://github.com/novaxmyth/pocketvm.git
cd pocketvm

# Build Signed Release APK
./gradlew :app:assembleRelease

# The output APK will be generated at:
# app/build/outputs/apk/release/app-release.apk
```

### Automated CI / CD
Every push to `main` automatically triggers GitHub Actions (`.github/workflows/build.yml`), which builds both Debug and signed Release APKs and publishes them directly to **GitHub Releases** and **Actions Artifacts**.

---

## License

```
Copyright 2026 PocketVM Authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
