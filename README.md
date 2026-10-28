> 🇫🇷 [Lire en français](README.fr.md)

# DivePilot

**Control your HotDive waterproof housing — camera and dive computer — from any Android phone (7+), fully offline, with no account and no login.**

DivePilot is an Android app (Kotlin + Jetpack Compose) that **drives the HotDive waterproof housing** over Bluetooth and reimplements its **dive computer** features. It lets you **keep using your HotDive housing**, even if the original app is no longer available.

It is a **reimplementation**: no code from the original app is copied. Only the **interoperability facts** (BLE UUIDs and command bytes) are reused so the app can talk to the housing.

---

## Download

**Latest release (v0.9.1):** [dive-pilot-0.9.1.apk](https://github.com/Soleiado/Divepilot/releases/download/v0.9.1/dive-pilot-0.9.1.apk)

See all versions on the [Releases page](https://github.com/Soleiado/Divepilot/releases).

---

## Highlights

| | |
|---|---|
| 📱 **Any Android 7.0+ phone** | minSdk 24 — works on old and new devices alike |
| 🔌 **Fully offline** | no internet connection, no server, no data sent |
| 🔐 **No account** | no login, no sign-up, no tracking |
| 🌍 **Bilingual** | French / English (Settings → Language) |
| 🆓 **Free** | no ads |

---

## Features

### 🔗 Housing connection (Bluetooth Low Energy)
- Scan, pair, GATT connection and notification subscription.
- Automatic reconnection; the phone only **receives** (no writes).

### 📷 Camera driven by the housing
- 4 modes: **Photo**, **Selfie**, **Video**, **Video selfie**.
- The housing buttons control: focus, zoom, photo/video toggle, shutter, menu.
- Camera settings: white balance, image size, video size, exposure.
- **Flash is disabled** (light reflects inside the housing).

### 🤿 Dive computer (TEST MODE)
Disabled by default — enable it in **Settings → "Experimental option"** (with a large disclaimer).

- **Bühlmann ZHL-16C** decompression with **Gradient Factors 30/70**.
- **NDL** (remaining no-decompression time), **deco stops** + **TTS**, ceiling.
- **Oxygen toxicity**: CNS % + OTU (nitrox, O₂ ≤ 40%).
- **Surface interval** and **time-to-fly** (no-fly), repetitive dives.
- **Altitude** (0 / 1000 / 2000 / 3000 m) and **fresh / salt water**.
- **Safety stop** (6 m).

> ⚠️ **Do not use as a real dive computer.** This feature is in **TEST MODE**: the calculations are unvalidated and do not replace training or a certified computer.

### 📖 Dive logbook
- Records every dive: **depth profile**, duration, max depth, temperatures, **GPS coordinates** (last position before immersion), altitude, fresh/salt water.
- GPS coordinates are **clickable** → opens Google Maps.

### 🧭 Other screens
- **Compass** (phone sensor).
- **Gallery** of photos/videos taken.
- Built-in **user guide**.
- **Color correction** for underwater photos.
- **Settings**: units (m/ft, °C/°F), language, alerts, dive computer.
- **Foreground service**: dive tracking keeps running in the background.

---

## How it works (summary)

1. **Turn on the housing**, then in DivePilot tap **"Scan"** (or the housing in the paired devices list).
2. The app connects over **BLE** and listens to the button commands and **telemetry** (battery, temperature, depth).
3. On the **Camera** screen, the housing buttons drive the camera; dive telemetry is overlaid on screen.
4. Below 1.2 m a **dive starts** automatically (timer, max depth, safety stop…) and ends when ascending above 0.5 m.
5. The dive is **saved to the logbook** with its profile, GPS position, etc.

---

## BLE protocol (interoperability facts)

- **Service**: `e6400001-b5a3-f393-e0a9-e50e24dcca9e`
- **Notify/read**: `e6400003-b5a3-f393-e0a9-e50e24dcca9e`
- **Write**: `e6400002-b5a3-f393-e0a9-e50e24dcca9e` (unused)
- **Commands** (2 bytes): `f101` focus · `f102` compass · `f103` photo/video · `f105`/`f106` zoom in · `f107`/`f108` menu · `f109`/`f10a` zoom out · `f10b` shutter · `f10d` flash
- **Telemetry** (13 bytes): battery, temperature (°C), depth (m)

---

## Dependencies

### Build tools

| Component | Version |
|---|---|
| Gradle | 9.7.1 |
| Android Gradle Plugin (AGP) | 9.4.1 |
| Kotlin | 2.4.21 |
| compileSdk / targetSdk | 37 |
| minSdk | 24 (Android 7.0) |
| JDK | 17+ (build tested with JDK 25) |

### Libraries

| Library | Version | Purpose |
|---|---|---|
| Jetpack Compose (BOM) | 2026.08.00 | UI (`ui`, `ui-graphics`, `material3`, `ui-tooling-preview`) |
| AndroidX Core KTX | 1.16.0 | Kotlin extensions |
| AndroidX Lifecycle | 2.9.1 | `runtime-ktx`, `runtime-compose`, `viewmodel-compose` |
| AndroidX Activity Compose | 1.10.1 | Compose integration |
| AndroidX Navigation Compose | 2.10.2 | screen navigation |
| CameraX | 1.6.1 | camera (`core`, `camera2`, `lifecycle`, `video`, `view`) |
| Gson | 2.11.0 | dive logbook serialization |
| JUnit | 4.13.2 | unit tests (decompression model) |

---

## Requirements & Build

- **JDK 17 or later**.
- **Android SDK** with `android-37` (and `android-34`) platforms.

```powershell
# Debug APK
.\gradlew.bat :app:assembleDebug

# Release (signed) APK
.\gradlew.bat :app:assembleRelease
```

> Release signing uses `keystore/divepilot-release.jks` (git-ignored — keep it safe for future updates).

---

## License & credits

- The **code** is under the **MIT** license — see [`LICENSE`](LICENSE).
- **Icon** "scuba_diving": Material Symbols (Google), **Apache 2.0** license.
- **Background image**: "Sea fans coral reef Pagan 2022", NOAA (public domain).
- See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for details.

---

## Disclaimer

The **dive computer** is an **experimental feature, disabled by default**. Never use it to plan or perform a real dive. DivePilot is not a certified dive device.
