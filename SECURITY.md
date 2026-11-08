# Security Policy

## Supported Versions

Only the latest release and the current `main` branch receive security patches and updates.

| Version | Supported |
|---------|-----------|
| 1.0.x   | Yes       |
| < 1.0   | No        |

---

## Security Model & Threat Boundaries

Antigravity Mobile Companion is designed with a privacy-first, zero-knowledge security architecture:

### 1. Dual-Mode Connection Architecture

The application operates in two distinct, isolated connection modes:

- **Official Google Cloud Remote (WAN)**:
  - Connects to your workstation session via Google's official Remote Control relay infrastructure (`antigravity.google.com/r/<relay_id>`).
  - Authentication is handled exclusively through Android Chrome Custom Tabs on official Google endpoints (`accounts.google.com`).
  - The application **never** intercepts, stores, parses, or accesses your Google account password or primary session cookies.
  - All communication over the cloud relay is end-to-end TLS encrypted by Google Cloud.

- **Direct Local Edge Gateway (LAN)**:
  - Operates 100% locally within your private home or office network.
  - Requires a cryptographically generated Bearer token (HMAC-SHA256) produced by your local workstation daemon (`antigravity-mobile-daemon`).
  - No connection to any cloud broker or third-party server is established in this mode.
  - Unauthenticated requests are rejected immediately with HTTP 401 Unauthorized.

### 2. Device Permissions & Sensor Data

- **Camera (`android.permission.CAMERA`)**:
  - Used strictly for scanning desktop QR codes to extract connection parameters.
  - Processing is performed locally on the device using Google ML Kit Barcode Scanning.
  - No images, video frames, or metadata are ever recorded, saved to disk, or transmitted outside the device.

- **Storage & Sandboxing**:
  - All application configuration (saved tokens, relay preferences, connection histories) is stored within the private internal app sandbox (`SharedPreferences` with `Context.MODE_PRIVATE`).
  - No sensitive application data is written to external shared storage.

- **Telemetry & Third-Party Trackers**:
  - The application contains **zero** third-party analytics libraries, advertising SDKs, crash trackers, or telemetry beacons.
  - Network requests are sent exclusively to your specified local host IP or the official Google Antigravity relay domain.

---

## Reporting a Vulnerability

If you discover a security vulnerability or privacy concern:

1. **Do NOT open a public GitHub issue.**
2. Send an email to the maintainer: `vladimirfedorko225@gmail.com` with the subject `[SECURITY] Antigravity Mobile Companion`.
3. Include detailed steps to reproduce the vulnerability, including platform version, device details, and network topology.
4. Maintainers will review the report within 48 hours and coordinate a coordinated disclosure and patch release.
