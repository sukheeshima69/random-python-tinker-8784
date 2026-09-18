# 2FA — TOTP Authenticator for Nokia E71

A J2ME (MIDP 2.0 / CLDC 1.1) TOTP authenticator app for the Nokia E71
and other S60 3rd Edition feature phones.

## Features

- **TOTP code generation** (RFC 6238) — standard 6-digit codes, 30-second rotation
- **QR code scanning** — capture the service's setup QR with the camera;
  account name + secret are auto-filled like other authenticator apps
- **Load QR from an image** — decode a saved QR photo (PNG/JPEG) from the
  memory card or phone memory, e.g. one received by Bluetooth
- **Multiple accounts** — store as many 2FA accounts as you need
- **Manual clock offset** — compensate for phone clock drift (the #1 failure mode)
- **Zero network dependency** — works fully offline, no data connection needed
- **Zero external dependencies** — SHA-1 and HMAC-SHA1 hand-rolled in pure Java
- **Countdown ring** — visual arc showing seconds remaining on the current code
- **Persistent storage** — accounts saved to RMS (survives reboot)

## Screenshots

```
┌─────────────────────┐     ┌─────────────────────┐
│     2FA             │     │  Google:alice        │
│                     │     │     ╭──╮             │
│ Google:alice        │     │    ╭╯23╰╮            │
│ GitHub:alice        │     │    │ 12s │            │
│ AWS:alice@co        │     │    ╰─────╯            │
│                     │     │                      │
│                     │     │     482 913           │
│                     │     │   ▓▓▓▓▓▓▓▓░░░░       │
│ [Open][Add]         │     │                      │
│        [Exit]       │     │         [Back]       │
└─────────────────────┘     └─────────────────────┘
```

## Project Structure

```
2FA-J2ME/
├── src/
│   ├── MANIFEST.MF              # MIDlet manifest
│   └── com/twofa/
│       ├── app/
│       │   └── TwoFAMidlet.java # Main MIDlet entry point
│       ├── crypto/
│       │   ├── SHA1.java        # Pure-Java SHA-1 (RFC 3174)
│       │   ├── HMACSHA1.java    # HMAC-SHA1 (RFC 2104)
│       │   ├── TOTP.java        # TOTP generator (RFC 6238)
│       │   └── Base32.java      # Base32 codec (RFC 4648)
│       ├── qr/
│       │   ├── OtpauthParser.java # otpauth:// URI parser + validator
│       │   └── QrImageDecoder.java # QR from image bytes (+ downsampling)
│       ├── store/
│       │   ├── Account.java     # Account data model
│       │   └── AccountStore.java # RMS persistence
│       └── ui/
│           ├── AccountListScreen.java   # Main list
│           ├── AccountDetailScreen.java # Code + countdown
│           ├── AddAccountScreen.java    # Add form (manual + prefill)
│           ├── ScanScreen.java          # Camera QR scanner (MMAPI)
│           ├── ImagePickerScreen.java   # Saved-image picker (JSR-75)
│           └── SettingsScreen.java      # Clock offset
├── dk/onlinecity/qrr/          # Vendored QR decoder, MIT (see below)
├── com/google/zxing/common/reedsolomon/ # Vendored RS codec, Apache 2.0
├── lib/                         # Compile-time-only API stubs (never packaged)
├── res/                         # Icon resources (generated)
├── dist/                        # Build output (JAR + JAD)
├── build.bat                    # Windows build script
├── generate_icons.py            # Icon generator (Python + Pillow)
├── generate_icons.bat           # Icon generator launcher
└── README.md
```

## Prerequisites

### 1. JDK 8 (32-bit)

J2ME/WTK 2.5.2 requires a 32-bit JDK. Download from:
- Oracle: https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html
- Or Adoptium Temurin 8 (32-bit)

Install to a path without spaces, e.g. `C:\Java\jdk1.8.0_xxx`

### 2. Wireless Toolkit 2.5.2

Download WTK 2.5.2 from Oracle/Sun archives. Install to e.g. `C:\WTK252`

### 3. Python 3 + Pillow (for icon generation only)

```powershell
winget install Python.Python.3.12
pip install Pillow
```

## Setup

1. **Edit `build.bat`** — set `JAVA_HOME` and `WTK_HOME` to your actual paths:
   ```bat
   set "JAVA_HOME=C:\Java\jdk1.8.0_202"
   set "WTK_HOME=C:\WTK252"
   ```

2. **Generate icons** (one-time):
   ```bat
   generate_icons.bat
   ```

3. **Build**:
   ```bat
   build.bat
   ```

4. **Run in emulator**:
   ```bat
   build.bat run
   ```

## Deploying to Nokia E71

1. Build the project: `build.bat`
2. Copy `dist\2FA.jar` to your phone via:
   - **Bluetooth**: Send the .jar file to the E71
   - **USB**: Copy to mass storage, then install from File Manager
   - **Memory card**: Copy to SD card, install from phone
3. The phone will prompt to install — accept and launch "2FA"

> **Note**: Only the `.jar` file is needed for installation. The `.jad` file is
> optional and mainly used for OTA (over-the-air) installation or emulator testing.

## Adding Accounts

When you enable 2FA on a service (Google, GitHub, AWS, etc.):

**Option A — scan the QR code (recommended):**

1. In the 2FA app, press **Scan** (or **Add → Scan QR**)
2. Allow camera access when the phone asks
3. Aim at the QR code shown by the service and press **Capture**
4. The account name and secret are filled in automatically —
   review them and press **Save**

**Option B — load a saved QR image:**

1. Save the QR code as a PNG/JPEG photo on the memory card or phone
   memory (e.g. send it to the E71 by Bluetooth and save it)
2. In the 2FA app, press **Add → Load image**
3. Allow file access when the phone asks
4. Browse to the photo (memory card / phone memory) and open it
5. The account name and secret are filled in automatically —
   review them and press **Save**

**Option B — manual entry:**

1. Choose "manual entry" or "can't scan QR code"
2. The service will show you a **secret key** (usually 16-32 characters, Base32 encoded)
3. In the 2FA app, press **Add**
4. Enter a name (e.g. `Google:alice@gmail.com`)
5. Paste/type the secret key
6. Press **Save**

> **Scanning tips:** fill the frame with the QR code, hold the phone
> steady, and use good light. If a scan fails, just press **Capture**
> again — the app retries with different photo sizes automatically.

## Troubleshooting Codes Not Matching

If your codes don't match the service:

1. Go to **Settings** in the app
2. Compare your E71's clock with a reliable time source (e.g., time.google.com)
3. If your phone is **30 seconds fast**, set offset to `30`
4. If your phone is **30 seconds slow**, set offset to `-30`
5. Check the code again — it should now match

## Technical Notes

- **SHA-1 and HMAC-SHA1** are implemented from scratch (no `java.security.*` or
  `javax.crypto.*` available on CLDC 1.1). The implementation is based on
  RFC 3174 (SHA-1) and RFC 2104 (HMAC).
- **TOTP** follows RFC 6238 with HMAC-SHA1, 30-second time step, 6-digit output,
  and dynamic truncation per RFC 4226.
- **Base32** decoding handles uppercase, lowercase, whitespace, and padding characters.
- **RMS storage** uses two RecordStores: `2FA_Accounts` for account data and
  `2FA_Settings` for the global clock offset.
- **QR decoding** covers QR versions 1–10, all error-correction levels
  (L/M/Q/H) and numeric/alphanumeric/byte modes — every realistic TOTP
  setup code. Camera snapshots are requested at 640×480 JPEG first,
  falling back to smaller sizes if the heap is tight. Saved photos of any
  size are subsampled row-by-row (never a full-size pixel buffer), and
  files over 2MB are refused. Only 6-digit / 30-second / SHA1
  `otpauth://totp/` codes are accepted; anything else is refused with a
  clear message rather than generating wrong codes.
- **Target JAR size**: ~90KB with the QR decoder (E71 installs MIDlets
  this size without issue; the heap, not the JAR, is the limit).
- **Color palette**: Dark navy (#1A1A2E), warm amber (#FFD166), teal (#06D6A0),
  cream (#FAF0D7), red warning (#EF476F).

## Third-Party Code

- `src/dk/onlinecity/qrr/` — on-device QR detector/decoder, vendored from
  [oc-qrreader](https://github.com/onlinecity/oc-qrreader) (MIT license,
  © 2011 OnlineCity). Used as-is; only the `client/` MIDlet shell was left
  out (this app provides its own camera screen).
- `src/com/google/zxing/common/reedsolomon/` — Reed-Solomon codec from
  [ZXing](https://github.com/zxing/zxing) (Apache License 2.0,
  © 2007 ZXing authors), as vendored by oc-qrreader.
- `lib/microemu-*-2.0.4.jar` — MicroEmulator API stubs (LGPL) for MIDP 2.0,
  JSR-135 (camera) and JSR-75 (file access), used **at compile time only**;
  they are never packaged into the app — the E71 provides the real
  implementations.

## License

Free to use, modify, and distribute.
