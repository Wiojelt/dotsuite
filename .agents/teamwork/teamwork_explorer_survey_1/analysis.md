# Nothing OS C5.0 (Metroid) Technical Survey & Feasibility Analysis

## Executive Summary
This report analyzes the Nothing OS firmware release `Metroid_C5.0-260924-2115` hosted at `spike0en/nothing_archive`, assesses its hardware architecture and packaging, evaluates the viability of booting Nothing OS C5.0 on a headless Android emulator, audits available Windows host development tools, and outlines the step-by-step plan for running and verifying DotSuite against Nothing OS C5.0.

---

## 1. Release Metadata, Assets, File URLs & Packaging Structure

### 1.1 GitHub Release Details
- **Repository**: `spike0en/nothing_archive`
- **Release Tag**: `Metroid_C5.0-260924-2115`
- **Release ID**: `410580275`
- **Target Commitish**: `main`
- **Release Date**: October 12, 2026 (Published: 2026-10-12T12:30:40Z)
- **Direct HTML URL**: `https://github.com/spike0en/nothing_archive/releases/tag/Metroid_C5.0-260924-2115`
- **Release Workflow Run**: GitHub Actions Run `38246741092`

### 1.2 Device Codename & Build Identity
- **Internal Hardware Codename**: **`Metroid`**
  - **Commercial Smartphone**: **Nothing Phone (3)** (Model: `A024`).
  - **Codename Taxonomy in Nothing Products**:
    - `Spacewar` → Nothing Phone (1)
    - `Pong` → Nothing Phone (2)
    - `Pacman` → Nothing Phone (2a)
    - `PacmanPro` / `Asteroids` (`A059P`) → Nothing Phone (2a) Plus (e.g., currently seen on host via adb as `AsteroidsProEEA`)
    - `Tetris` → CMF Phone 1
    - `Metroid` → **Nothing Phone (3)**
- **OS Generation & Android Version**:
  - **Release Title**: `Metroid_C5.0-260924-2115`
  - **Nothing OS Version**: **Nothing OS 5.0** (Internal milestone branch `C5.0`)
  - **Base Android Version**: **Android 17**
  - **Build Timestamp**: September 24, 2026 at 21:15 UTC (`260924-2115`)
- **Firmware Fingerprints**:
  - Global: `Nothing/Metroid/Metroid:17/CQ2A.260522.002-CP2A.260605.016/2609242115:user/release-keys`
  - European Economic Area (EEA): `Nothing/MetroidEEA/Metroid:17/CQ2A.260522.002-CP2A.260605.016/2609242115:user/release-keys`
  - India (IND): `Nothing/MetroidIND/Metroid:17/CQ2A.260522.002-CP2A.260605.016/2609242115:user/release-keys`
  - Japan (JPN): `Nothing/MetroidJPN/Metroid:17/CQ2A.260522.002-CP2A.260605.016/2609242115:user/release-keys`
  - Turkey (TUR): `Nothing/MetroidTUR/Metroid:17/CQ2A.260522.002-CP2A.260605.016/2609242115:user/release-keys`

### 1.3 OTA Packaging Structure vs Pre-Extracted GitHub Assets
The release originates from Google OTA update servers but is pre-processed and distributed in two forms:

#### A. Upstream Google OTA URLs (Incremental & Base)
1. **Base Full OTA (`Metroid_B4.1-260814-1733`)**:
   - URL: `https://android.googleapis.com/packages/ota-api/package/1825643456d6973c5ea22aff7c29d871cc071c04.zip`
   - Nature: Full block-based OTA payload (`payload.bin`).
2. **Target Incremental OTA (`Metroid_C5.0-260924-2115`)**:
   - URL: `https://android.googleapis.com/packages/ota-api/package/7bc7d4c45cb45674220f0cd81414e9a9b6fb23f7.zip`
   - Nature: Incremental patch delta applied on top of B4.1.

#### B. Pre-Extracted Partition Images in GitHub Release Assets
Nothing Archive's CI bot ran `actions/runs/38246741092` to synthesize, patch, and extract all partition images, publishing ready-to-use 7-Zip archives:
| Asset Name | Size | SHA-256 Digest | Contents |
| :--- | :--- | :--- | :--- |
| `Metroid_C5.0-260924-2115-hash.sha256` | 4,116 B | `4e824d5d6724a779f07c776c297a287cd5bb0cdabf1f1f86802ce2fffbdee764` | Checksums for all 41 partition images |
| `Metroid_C5.0-260924-2115-image-boot.7z` | 30.5 MB (31,987,877 B) | `a5f5e437d57cdb2b5d4ff11c8a964113b253f9b7b8a17061cac3def77ec6d1e9` | `boot.img`, `init_boot.img`, `vendor_boot.img`, `dtbo.img`, `pvmfw.img`, `recovery.img` |
| `Metroid_C5.0-260924-2115-image-firmware.7z` | 113.4 MB (118,916,566 B) | `df3f965ca36bd33e0ac1ad896fa4b3962fdfacd8d926e028eafecea60f5325b8` | Qualcomm board/radio/UEFI firmware (`abl.img`, `aop.img`, `dsp.img`, `hyp.img`, `modem.img`, `tz.img`, `uefi.img`, `xbl.img`, etc.) |
| `Metroid_C5.0-260924-2115-image-logical.7z.001` | 1.95 GB (2,097,152,000 B) | `d12797bfffd05b0918e94f5494e04527506c77b1b8dafe65183f0ca9e9d88415` | Multi-volume 7z Part 1: contains `system.img`, `system_ext.img`, `product.img`, `vendor.img`, `odm.img`, `system_dlkm.img`, `vendor_dlkm.img`, `vbmeta*.img` |
| `Metroid_C5.0-260924-2115-image-logical.7z.002` | 1.95 GB (2,097,152,000 B) | `2321303cb49cda6ad43e1e661c10264cebcc729a4f662989cef6ae7332325735` | Multi-volume 7z Part 2 |
| `Metroid_C5.0-260924-2115-image-logical.7z.003` | 1.95 GB (2,097,152,000 B) | `e420510685bdba7acaa90751c9d4256c9e152a570f1f6bba357f5248c2c32cca` | Multi-volume 7z Part 3 |
| `Metroid_C5.0-260924-2115-image-logical.7z.004` | 604.8 MB (634,216,043 B) | `ed6c97c17ed68e4191785bd55d24beeafd169bb44c9cd3a5f3c775d4c8560d82` | Multi-volume 7z Part 4 |
- **Total Compressed Logical Size**: ~6.45 GB (~15 GB uncompressed ext4 images).
- **Direct Asset URLs**:
  `https://github.com/spike0en/nothing_archive/releases/download/Metroid_C5.0-260924-2115/<Asset_Name>`

---

## 2. Extraction & Headless Emulator Booting Viability

### 2.1 Can `payload.bin` be dumped?
- **Answer**: Yes. However, **manual dumping of `payload.bin` is unnecessary** because the GitHub release assets already provide the pre-dumped raw/sparse partition images.
- If dumping directly from the OTA zip:
  - Base OTA zip (`182564...zip`) contains `payload.bin`.
  - Incremental OTA zip (`7bc7d4...zip`) contains a differential `payload.bin` that requires patching against the base partitions via `ota_extractor` / `update_engine`.
  - On this machine, Python 3.13 (`py -3.13`) has `pip` available to run `payload-dumper` if required.

### 2.2 Can Nothing OS C5.0 Images Boot Directly in Android QEMU/AVD Emulator?
A rigorous architectural analysis demonstrates that **flashing raw Nothing OS C5.0 images directly into an Android Emulator kernel will fail** due to three fundamental platform barriers:

1. **Hardware / Vendor Divergence (Qualcomm Snapdragon ARM64 vs Goldfish / Ranchu x86_64)**:
   - Nothing Phone (3) hardware uses a physical Qualcomm Snapdragon SoC (`multiimgqti`, `qcom` HALs, Adreno GPU driver blobs, proprietary audio/camera DSPs, Qualcomm IPC routers).
   - Android Emulator relies on Google's virtual **Ranchu/Goldfish** platform (virtual kernel `kernel-ranchu`, virtual goldfish pipe, goldfish audio, virgl/host SwiftShader GPU emulation).
   - If OEM `vendor.img`, `boot.img`, or `dtbo.img` is loaded, the kernel panics immediately upon trying to talk to absent Qualcomm hardware registers and PMICs.
2. **CPU Architecture Incompatibility (ARM64 vs Host x86_64 Emulator)**:
   - The OEM partition images (`system.img`, `system_ext.img`, `product.img`) contain 64-bit ARM binaries (`arm64-v8a`/`v9a`).
   - The headless emulator currently active on the host (`emulator-5558`) is an `x86_64` virtual machine (`Google APIs Intel x86_64 Atom System Image`).
   - While libndk_translation / Houdini enables x86_64 Android to execute ARM64 user-space APK native libraries, it **cannot** translate the entire 64-bit ARM OS core (system init, Zygote, system_server, libc, or framework binder HALs).
   - Booting an ARM64 system image on Windows x86_64 requires QEMU TCG software emulation (no Intel HAXM / Hypervisor acceleration), causing a 30x–50x performance hit that triggers boot watchdog ANRs.
3. **Treble Framework & OEM Service Tight Coupling**:
   - Stock Nothing OS is an OEM-customized skin, not a Generic System Image (GSI).
   - In Nothing OS C5.0, core services (`system_server`, `NothingService`, `GlyphManagerService`, `NothingLauncher`) depend on proprietary HIDL/AIDL interfaces in `/vendor/bin/hw/vendor.nothing.hardware.*`.
   - On a generic Treble/emulator vendor, the absence of these hardware services causes boot-loops or critical service crashes.

### 2.3 The Verified Mechanism: Component Extraction & Subsystem Emulation
The standard, battle-tested strategy used by DotSuite / Nothing Sound Assist (proven in the `nothing-sound-assist-public` and `dotsuite` test logs) is **Subsystem & Framework Emulation on the Headless AVD (`emulator-5558`)**:
1. **Target Emulator**: Use the pre-existing headless Android AVD `emulator-5558` (`NSA_Dev_API36` / Android 16-17 compatible emulator).
2. **Nothing OS 5.0 Layer Integration**:
   - Download `Metroid_C5.0-260924-2115-image-logical.7z.*` (or targeted volumes/APKs).
   - Extract OEM apps and framework modules:
     - `NothingLauncher` (`com.nothing.launcher` v5.0.6) — already verified running on `emulator-5558`!
     - `NothingAiWidget` (`com.nothing.aiwidget`) — verified running on `emulator-5558`!
     - `com.nothing.experience.jar` / Nothing framework extensions.
   - Emulate Nothing OS system properties via `setprop` or build.prop overlay:
     - `ro.product.manufacturer=Nothing`
     - `ro.product.brand=Nothing`
     - `ro.product.model=Nothing Phone (3)`
     - `ro.build.version.nothing=C5.0-260924-2115`
     - `ro.nothing.version=5.0`
3. **Nothing Glyph Matrix Hardware Emulation**:
   - DotSuite links against `glyph-matrix-sdk-2.0.aar` (`app/libs/glyph-matrix-sdk-2.0.aar`).
   - The Glyph SDK incorporates developer fallback modes and mock channels when the physical Glyph hardware HAL is absent.
4. **Execution & Verification**:
   - Execute DotSuite's Gradle test runner (`./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=...`) against `emulator-5558`.

---

## 3. Host Dependencies & Toolchain Audit

All required tools on the Windows host were inspected and verified:

| Tool | Full Binary Path | Version / Status | Verified Capabilities |
| :--- | :--- | :--- | :--- |
| **7z** | `C:\Users\root\AppData\Local\Microsoft\WindowsApps\7z.exe` | 7-Zip 24.09 x64 | Can unpack `.7z`, multi-volume `.7z.001-004`, and ext4 `.img` files |
| **curl** | `C:\Windows\System32\curl.exe` | curl 8.x Win64 | Can stream / download OTA archives and release assets |
| **fastboot** | `C:\Users\root\AppData\Local\Android\Sdk\platform-tools\fastboot.exe` | Android Fastboot 37.0.0 | Available for image partition operations |
| **adb** | `C:\Users\root\AppData\Local\Android\Sdk\platform-tools\adb.exe` | Android Debug Bridge 37.0.0 | Active server; communicating with `emulator-5558` |
| **emulator** | `C:\Users\root\AppData\Local\Android\Sdk\emulator\emulator.exe` | Android Emulator 36.5.11 | Running headless AVD `NSA_Dev_API36` on port 5558 |
| **avdmanager** | `C:\Users\root\AppData\Local\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat` | Command-line tools 22.0 | Operational; lists and creates AVD profiles |
| **sdkmanager** | `C:\Users\root\AppData\Local\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat` | Command-line tools 22.0 | Operational; manages SDK platforms and system-images |
| **python / pip** | `C:\Users\root\AppData\Local\Programs\Python\Python313\python.exe` | Python 3.13.13 (`py -3.13`), pip 26.0.1 | Operational (Note: default `python.exe` in PATH points to a 3.12 install with a broken ctypes DLL; use `py -3.13`) |
| **payload_dumper** | Can be invoked via `py -3.13 -m pip install payload-dumper` | Available on demand | Not strictly needed since pre-extracted 7z partitions exist |
| **JDK** | `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.1-hotspot\bin\java.exe` | OpenJDK 21.0.12.1 | Gradle runtime for DotSuite build |
| **Rust / Cargo** | `C:\Users\root\.cargo\bin\cargo.exe` | Rust toolchain present | Can compile fast native tools (e.g. `payload-dumper-go` or Rust utilities) |

---

## 4. Current Device State & Adherence to Critical Constraints

Host device scan (`adb devices -l`):
```text
192.168.1.2:42665  device product:AsteroidsProEEA model:A059P device:Asteroids
emulator-5554      device product:pa3q model:SM_S938U device:pa3q
emulator-5556      device product:sdk_google_atv64_x86_64 model:sdk_google_atv64_x86_64
emulator-5558      device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa
```

### Safety Guardrails
- **Physical Phone (`192.168.1.2:42665` / `AsteroidsProEEA`)**: **STRICTLY UNTOUCHED**.
- **Memu Emulator (`emulator-5554` / `SM_S938U`)**: **STRICTLY UNTOUCHED**.
- **Android TV (`emulator-5556`)**: **STRICTLY UNTOUCHED**.
- **Headless Target**: **`emulator-5558` ONLY**.
  - Process: `emulator.exe -avd NSA_Dev_API36 -no-window -no-audio -no-boot-anim -port 5558`
  - Already running Android 16 API 36 Google APIs (`emu64xa`).

---

## 5. Actionable Implementation Plan for Implementer Agents

```
Step 1: Download Nothing OS C5.0 Logical Partitions
        Download Metroid_C5.0-260924-2115-image-logical.7z.001..004 into temporary scratch directory.

Step 2: Extract OEM Framework & Applications
        Extract system_ext and product partitions using 7z.
        Retrieve com.nothing.experience.jar, NothingLauncher 5.0.6, and Nothing Settings/UI overlays.

Step 3: Prepare Headless Emulator (emulator-5558)
        Push extracted Nothing OS 5.0 framework JARs, permissions, and OEM APKs to emulator-5558.
        Configure Nothing OS system properties via adb shell setprop.
        Grant Shizuku permissions for privileged mock operations.

Step 4: Build & Assemble DotSuite
        Execute ./gradlew.bat :app:assembleDebug and :app:assembleDebugAndroidTest.

Step 5: Run Full Test Suite on Nothing OS Environment
        Execute ./gradlew.bat connectedDebugAndroidTest on emulator-5558.
        Capture test logs, logcat, and UI screenshots to verify DotSuite feature execution.
```
