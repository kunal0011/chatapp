# ChatApp: Production-Ready Secure Messaging Platform

ChatApp is a high-performance, "Local-First" messaging application designed for maximum privacy and modern hardware compatibility. It features military-grade encryption and a robust, resilient architecture.

## 🛡️ Security Architecture (E2EE & Privacy)

ChatApp implements a "Zero-Knowledge" architecture, ensuring that message content is never visible to the server or any third party.

### 1. End-to-End Encryption (Signal Protocol)
*   **Protocol:** Implements the **Signal Protocol** (Double Ratchet + X3DH handshake).
*   **Zero-Native Implementation:** Uses a pure-Java cryptographic engine to ensure 100% compatibility across all Android devices without the risks of native library alignment issues.
*   **Forward Secrecy:** Every message uses a unique, rotating key. Even if a session is compromised, past messages remain secure.
*   **Blind Relay:** The backend acts purely as a relay for encrypted "blobs." It has no access to the cryptographic keys required to read your messages.

### 2. Encryption at Rest (SQLCipher)
*   **Hardware-Backed Security:** The local Room database (`chatapp.db`) is fully encrypted using **SQLCipher 4.6.1**.
*   **Dynamic Passphrase:** A unique encryption key is generated on the first run and stored in the **Android Keystore**, ensuring it cannot be extracted even from rooted devices.
*   **Full-Disk Privacy:** Messages, contact lists, and session tokens are never stored in plaintext on the device storage.

### 3. Secure Push Notifications
*   **Encrypted Payloads:** Message content is sent to Google FCM as ciphertext. 
*   **Background Decryption:** The app performs "on-the-fly" decryption in the background only when the notification reaches the target device, ensuring your private data never touches Google's logs in readable form.

---

## 🚀 Key Features
*   **Verified Onboarding:** Multi-step registration with 6-digit OTP verification.
*   **Offline Persistence:** View, search, and draft messages without an internet connection.
*   **Engagement:** Real-time typing indicators, read receipts (blue ticks), and emoji reactions.
*   **User Controls:** Soft-delete (unsend), message editing, and user blocking.
*   **Organization:** Global message search and device contact discovery.

---

## 🛠️ Critical Issues Resolved

### 1. Android 15 (16 KB Page Size) Compatibility
*   **Problem:** Newer Android devices use a 16 KB memory page size, causing legacy native libraries (like SQLCipher and libcurve) to crash.
*   **Fix:** 
    *   Upgraded to **SQLCipher 4.6.1**, which is natively 16 KB aligned.
    *   Transitioned to a **Pure Java Signal engine** to eliminate native alignment conflicts entirely.
    *   Configured `android:extractNativeLibs="true"` and AGP packaging rules for optimal hardware performance.

### 2. Hardened Token Rotation
*   **Problem:** Stolen refresh tokens could be reused to hijack user sessions.
*   **Fix:** Implemented **Token Families** with **Reuse Detection**. If an old refresh token is reused, the entire session chain is instantly revoked, locking out both the attacker and the user (forcing a secure re-login).

### 3. Automated Database Recovery
*   **Problem:** Switching from a plaintext build to an encrypted build caused "file is not a database" crashes.
*   **Fix:** Implemented a proactive **Connection Check** during Hilt initialization. The app now detects legacy/corrupted database headers and automatically performs a safe recovery (deletion + fresh encrypted recreation) without user intervention.

---

## ⚙️ Setup & Development

### Backend
1.  Set up PostgreSQL and update `.env`.
2.  `npm install`
3.  `npx prisma migrate dev`
4.  `npm run dev` (OTP codes are logged to this console for development).

### Android
1.  Open in Android Studio (Koala or newer).
2.  Ensure `minSdk` 26 and `compileSdk` 35 are set.
3.  Build and Run on an Emulator or Android 15 device.

---

## ⚖️ License & Credits
Built with ❤️ for privacy and performance. Using Signal Protocol, SQLCipher, and Jetpack Compose.
