#!/usr/bin/env python3
"""
Bundles the PocketVM virtualization engine.

Downloads the Termux-built QEMU 11 packages (aarch64, GPL — sources at
https://github.com/termux/termux-packages), prunes everything that
qemu-system-aarch64 does not actually link against (DT_NEEDED walk), adds
the UEFI firmware image, and packs the result into app assets.
"""
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile

BASE = "https://packages.termux.dev/apt/termux-main"
ARCH = "aarch64"
ROOTS = ["qemu-system-aarch64-headless"]
OUT_DIR = "out-engine"
OUT_ZIP = "app/src/main/assets/runtime.zip"
OUT_INFO = "app/src/main/assets/runtime_info.json"
CACHE = "ci/.engine-cache"

# Libraries Android itself always provides to apps.
SYSTEM_LIBS = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libz.so", "libandroid.so"}


def http_get(url: str) -> bytes:
    last = None
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "pocketvm-ci"})
            with urllib.request.urlopen(req, timeout=120) as r:
                return r.read()
        except Exception as e:  # flaky network tolerance
            last = e
            print(f"retry {attempt + 1} for {url}: {e}", flush=True)
    raise RuntimeError(f"download failed: {url}: {last}")


def parse_packages_index() -> dict:
    print("fetching package index…", flush=True)
    text = http_get(f"{BASE}/dists/stable/main/binary-{ARCH}/Packages").decode("utf-8", "replace")
    pkgs = {}
    cur = {}
    for line in text.splitlines():
        if not line.strip():
            if cur.get("Package"):
                cur_name = cur["Package"]
                if cur_name not in pkgs or cur.get("Version", "") > pkgs[cur_name].get("Version", ""):
                    pkgs[cur_name] = cur
            cur = {}
            continue
        m = re.match(r"^([\w-]+):\s*(.*)", line)
        if m:
            cur[m.group(1)] = m.group(2)
    if cur.get("Package"):
        pkgs.setdefault(cur["Package"], cur)
    print(f"index: {len(pkgs)} packages")
    return pkgs


def dep_names(depstr: str):
    out = []
    for alt in (depstr or "").split(","):
        name = alt.split("|")[0].strip().split(" ")[0].strip()
        if name:
            out.append(name)
    return out


def resolve_closure(pkgs: dict, roots):
    seen, order, missing = set(), [], []
    stack = list(roots)
    while stack:
        p = stack.pop()
        if p in seen:
            continue
        seen.add(p)
        if p not in pkgs:
            missing.append(p)
            continue
        s = pkgs[p]
        order.append(s)
        stack.extend(dep_names(s.get("Depends")))
    if missing:
        raise RuntimeError(f"packages not found in index: {missing}")
    return order


def extract_deb(deb: str, dest: str):
    subprocess.run(["dpkg-deb", "-x", deb, dest], check=True)


def elf_needed(path: str):
    r = subprocess.run(["readelf", "-d", path], capture_output=True, text=True)
    if r.returncode != 0:
        return []
    return re.findall(r"Shared library: \[([^\]]+)\]", r.stdout)


def main():
    os.makedirs(CACHE, exist_ok=True)
    shutil.rmtree(OUT_DIR, ignore_errors=True)
    os.makedirs(OUT_DIR, exist_ok=True)

    pkgs = parse_packages_index()
    closure = resolve_closure(pkgs, ROOTS)
    total = sum(int(s.get("Installed-Size", 0)) for s in closure) / 1024.0
    print(f"closure: {len(closure)} packages, ~{total:.0f} MB installed")

    staging = os.path.join(CACHE, "staging")
    shutil.rmtree(staging, ignore_errors=True)
    os.makedirs(staging)

    for s in closure:
        fn = s["Filename"].split("/")[-1]
        deb = os.path.join(CACHE, fn)
        if not os.path.exists(deb):
            url = f"{BASE}/{s['Filename']}"
            print(f"downloading {fn}", flush=True)
            with open(deb, "wb") as f:
                f.write(http_get(url))
        extract_deb(deb, staging)

    # Termux debs unpack under data/data/com.termux/files/usr; locate that tree
    # wherever it is so we don't depend on the packaging convention.
    usr_candidates = [
        d for d in glob.glob(os.path.join(staging, "**", "usr"), recursive=True)
        if os.path.isdir(os.path.join(d, "bin")) or os.path.isdir(os.path.join(d, "lib"))
    ]
    if not usr_candidates:
        raise RuntimeError("no usr/bin|usr/lib tree found after extraction")
    pkg_usr = usr_candidates[0]
    print(f"package usr tree: {pkg_usr}")

    bin_src = os.path.join(pkg_usr, "bin/qemu-system-aarch64")
    if not os.path.exists(bin_src):
        raise RuntimeError("qemu-system-aarch64 not found after extraction")

    out_bin = os.path.join(OUT_DIR, "bin")
    out_lib = os.path.join(OUT_DIR, "lib")
    out_share = os.path.join(OUT_DIR, "share")
    for d in (out_bin, out_lib, out_share):
        os.makedirs(d)

    # DT_NEEDED walk: copy exactly the shared libs the engine loads.
    todo = elf_needed(bin_src)
    seen = set()
    while todo:
        name = todo.pop()
        if name in seen:
            continue
        seen.add(name)
        cand = os.path.join(pkg_usr, "lib", name)
        if os.path.exists(cand):
            shutil.copy2(cand, os.path.join(out_lib, name))
            todo.extend(elf_needed(cand))
        elif name not in SYSTEM_LIBS:
            raise RuntimeError(f"missing shared library not provided by Android: {name}")

    shutil.copy2(bin_src, os.path.join(out_bin, "qemu-system-aarch64"))
    os.chmod(os.path.join(out_bin, "qemu-system-aarch64"), 0o755)

    # VNC needs its keyboard keymaps at startup (default layout en-us).
    km_src = os.path.join(pkg_usr, "share/qemu/keymaps")
    if os.path.isdir(km_src):
        shutil.copytree(km_src, os.path.join(out_share, "qemu/keymaps"))
        print(f"bundled {len(os.listdir(os.path.join(out_share, 'qemu/keymaps')))} keymaps")
    else:
        print("WARNING: no keymaps dir in qemu packages")

    # UEFI firmware (needed to boot ISOs/disk images without a bundled kernel).
    efi = os.path.join(CACHE, "efi")
    shutil.rmtree(efi, ignore_errors=True)
    os.makedirs(efi)
    apt_ok = False
    try:
        subprocess.run(["apt-get", "download", "qemu-efi-aarch64"], cwd=efi, check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        deb = [f for f in os.listdir(efi) if f.endswith(".deb")][0]
        extract_deb(os.path.join(efi, deb), efi)
        shutil.copy2(os.path.join(efi, "usr/share/qemu-efi-aarch64/QEMU_EFI.fd"),
                     os.path.join(out_share, "QEMU_EFI.fd"))
        apt_ok = True
    except Exception as e:
        print(f"apt download of qemu-efi-aarch64 failed: {e}", flush=True)
    if not apt_ok:
        raise RuntimeError("could not obtain QEMU_EFI.fd")

    qemu_version = pkgs[ROOTS[0]].get("Version", "?").split(":")[-1]

    zip_path = OUT_ZIP
    os.makedirs(os.path.dirname(zip_path), exist_ok=True)
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for root, _dirs, files in os.walk(OUT_DIR):
            for f in files:
                full = os.path.join(root, f)
                rel = os.path.relpath(full, OUT_DIR)
                z.write(full, rel)

    size_mb = os.path.getsize(zip_path) / (1024 * 1024)
    if size_mb > 80:
        raise RuntimeError(f"runtime.zip too large: {size_mb:.1f} MB")

    with open(OUT_INFO, "w") as f:
        rev = f"{os.environ.get('GITHUB_RUN_NUMBER', '0')}-{os.environ.get('GITHUB_SHA', 'local')[:8]}"
        json.dump({
            "qemuVersion": f"QEMU {qemu_version} (Termux build)",
            "rev": rev,
            "builtAt": os.environ.get("GITHUB_SHA", ""),
        }, f)

    n_libs = len(os.listdir(out_lib))
    print(f"engine bundled: bin/qemu-system-aarch64 + {n_libs} libs + QEMU_EFI.fd "
          f"-> {zip_path} ({size_mb:.1f} MB)")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)
