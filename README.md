# PocketVM

A full-system Android virtual machine host that runs entirely inside a normal,
unprivileged Android app — the open-source take on what VPhoneOS / VPhoneGaGa /
Virtual Master / VMOS do: a complete second operating system (kernel + userland)
booting inside an app, no root required.

## How it works

VPhoneOS-class apps ship a **complete guest Android OS image** and boot it under a
virtualization engine running in the app's own sandbox. PocketVM uses the
open-source stack for this:

```
┌─────────────────────────────────────────────┐
│  PocketVM app (Kotlin)                      │
│  ┌─────────────┐  ┌──────────────────────┐  │
│  │ VM manager  │  │ VNC console (RFB 3.8)│  │
│  └──────┬──────┘  └──────────▲───────────┘  │
│         │ exec()             │ 127.0.0.1    │
│  ┌──────▼───────────────────┴───────────┐  │
│  │ qemu-system-aarch64 (TCG, user-mode  │  │
│  │ networking via libslirp, VNC out)    │  │
│  └──────────────────────────────────────┘  │
│         guest: full OS image (disk/ISO)    │
└─────────────────────────────────────────────┘
```

- **Engine**: QEMU 11 (`qemu-system-aarch64`, Termux GPL build, pruned to its
  linked libraries in CI) is executed as a child process. Because the app
  targets SDK 28 (the Termux model), Android allows executing the engine from
  app storage — no root, no KVM needed (pure TCG software virtualization with
  multi-threaded MTTCG).
- **Display**: QEMU exports the guest screen over VNC on localhost; the app has
  a built-in RFB 3.8 client (Raw + CopyRect + DesktopSize) rendering to a
  SurfaceView, with touch → pointer injection and an on-screen keyboard.
- **Network**: user-mode slirp NAT — the guest gets internet through the phone.
- **Storage**: each VM is a directory with its disk images (raw sparse data
  disk is allocated lazily, so an "8 GB" disk costs nothing until written).
- **Background**: a foreground service + wake/wifi locks keep the VM running
  with the screen off, like VPhoneOS.

## Status — v0.1.0

What works today:

- VM manager (create, name, RAM/CPU selection, delete)
- Import your own disk image (.iso/.img/.qcow2) or download the bundled
  Alpine Linux aarch64 test ISO and install it to the VM's data disk
- Boot under UEFI, console display over VNC, touch + Esc/Tab/Ctrl/Alt input,
  soft keyboard, background operation with notification

Roadmap (in order):

1. **Android 12 guest image pipeline** — CI job assembling an AOSP
   (`aosp_cf_arm64_phone`, Android 12/13) guest image bootable via direct
   kernel, with microG (open-source Google Play services) preinstalled.
   GApps-flavored images stay a "bring your own image" option for personal use.
2. Hextile/zrle encodings + host-GPU-accelerated rendering pipeline.
3. Shared folder (guest ⇄ phone) over slirp host forwarding.
4. Snapshots, multiple parallel VMs, guest audio.

## Honest performance note

Unlike VPhoneGaGa's proprietary user-space kernel, TCG is JIT emulation — the
guest CPU runs at a fraction of native speed. Expect usable but definitely not
native performance: fine for a second Android desktop, apps, automation;
rough for 3D games. RAM matters more than CPU speed — 2 GB free is the
practical minimum for the guest.

## Legal notes

- The bundled engine is GPL/LGPL software built by Termux (sources:
  https://github.com/termux/termux-packages). UEFI firmware: TianoCore edk2.
- PocketVM distributes **no** Google-licensed Android images or GApps. You can
  point it at any system image you have the right to use.

## Build

CI (GitHub Actions) does everything: it downloads and prunes the engine, packs
it into the APK, builds, and uploads the APK to gofile.io. Build locally the
same way: `python3 ci/fetch_engine.py && ./gradlew assembleDebug`.
