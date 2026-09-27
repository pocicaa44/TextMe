# TextMe 💬🔒

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-brightgreen.svg?logo=android)](https://www.android.com/)
[![Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Supabase](https://img.shields.io/badge/Backend-Supabase-3ECF8E.svg?logo=supabase)](https://supabase.com/)
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

**TextMe** is a lightweight, privacy-first, anonymous 1-to-1 ephemeral messaging application for Android.

Unlike traditional messaging apps, TextMe requires **no phone numbers, email addresses, usernames, passwords, or personal profiles**. Users communicate strictly via temporary, server-generated **8-character Public IDs** with **hardware-backed End-to-End Encryption (E2EE)** and a strict **ephemeral lifecycle**.

---

## 🌟 Key Features

- **🛡️ 100% Anonymous & Accountless**
  - No phone numbers, emails, passwords, usernames, or avatars.
  - Instant one-tap onboarding utilizing Supabase Anonymous Authentication.
  - Device-bound credentials stored securely in hardware-backed storage.

- **🔑 8-Character Authoritative Public ID**
  - Each identity receives a unique, server-generated 8-character alphanumeric code (e.g., `K7F2A91X`).
  - The Public ID serves solely as a shareable contact token—never an auth secret or encryption key.

- **⏳ Ephemeral Identities & Conversations**
  - Fixed 24-hour lifetime per identity with optional automated or manual identity rotation.
  - Conversations expire when **either** participant's identity expires (`MIN(Identity_A.expires_at, Identity_B.expires_at)`).
  - Server-authoritative timestamps prevent device clock tampering.
  - Full cascading purge removes conversations, messages, and requests upon expiration or manual deletion.

- **🔐 True End-to-End Encryption (E2EE)**
  - ECDH key agreement (**X25519**) + **HKDF-SHA256** key derivation + **AES-GCM-256** authenticated encryption.
  - Private cryptographic keys are safeguarded in the **Android KeyStore**.
  - Plaintext never reaches the server—only ciphertext and cryptographic nonces are transmitted.

- **📥 1-to-1 Chat Request Inbox**
  - Unsolicited messages are prevented by design.
  - Users enter a Public ID to send a request; conversations only activate after the recipient explicitly accepts.
  - Full block and abuse prevention support at the database RLS level.

- **⚡ Real-Time & Offline Resilient**
  - Powered by Supabase Realtime (WebSocket PostgreSQL change streams).
  - Local-first storage powered by Android Room database with reactive Kotlin `StateFlow`.
  - Automatic synchronization and expiration cleanup upon reconnection.

---

## 🏛️ Architecture & Codebase Structure

TextMe is built following **Clean Architecture**, **MVVM (Model-View-ViewModel)**, and **Unidirectional Data Flow (UDF)** principles.

```
                      ┌────────────────────────┐
                      │   Jetpack Compose UI   │
                      │  (Screens & Themes)    │
                      └───────────┬────────────┘
                                  │ Observes StateFlow / Dispatches Actions
                                  ▼
                      ┌────────────────────────┐
                      │       ViewModels       │
                      │ (Home, Chat, Settings) │
                      └───────────┬────────────┘
                                  │ Calls Domain Contracts
                                  ▼
                      ┌────────────────────────┐
                      │   Domain Repositories  │
                      │     & Pure Models      │
                      └───────────┬────────────┘
                                  │ Implemented By
                                  ▼
                      ┌────────────────────────┐
                      │  Repository Layer      │
                      │ (Offline-First Coord)  │
                      └─────┬────────────┬─────┘
                            │            │
            ┌───────────────▼┐          ┌▼──────────────┐
            │ Room Database  │          │   Supabase    │
            │  (Local Cache) │          │  (Postgrest,  │
            │                │          │  Auth, Real)  │
            └────────────────┘          └───────────────┘
```

### 📂 Directory Walkthrough

```text
TextMe/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   └── java/com/lactose/textme/
│   │   │       ├── core/                      # Core infrastructure
│   │   │       │   ├── common/                # Shared utilities (TimeUtils, Constants)
│   │   │       │   ├── crypto/                # E2EE CryptoEngine (AES-GCM, HKDF, X25519)
│   │   │       │   ├── database/              # Room AppDatabase definition & migrations
│   │   │       │   ├── network/               # SupabaseClient configuration & singleton
│   │   │       │   └── security/              # Android KeyStoreManager
│   │   │       ├── data/                      # Data layer implementation
│   │   │       │   ├── local/                 # Room DAOs and database entities
│   │   │       │   ├── remote/                # Supabase DTO models & serializable entities
│   │   │       │   └── repository/            # Offline-first repository implementations
│   │   │       ├── domain/                    # Clean Architecture domain layer
│   │   │       │   ├── model/                 # Pure domain models (Identity, Message, etc.)
│   │   │       │   └── repository/            # Domain repository interfaces
│   │   │       ├── presentation/              # Presentation layer (Jetpack Compose)
│   │   │       │   ├── conversation/          # 1-to-1 Chat screen & ViewModel
│   │   │       │   ├── home/                  # Home dashboard (conversations list, start chat)
│   │   │       │   ├── inbox/                 # Incoming chat requests screen & ViewModel
│   │   │       │   ├── navigation/            # Compose NavHost and destination routes
│   │   │       │   ├── settings/              # Settings, rotation, blocks & account deletion
│   │   │       │   ├── startup/               # Identity verification & auto-routing
│   │   │       │   └── welcome/               # Onboarding screen for new identities
│   │   │       ├── ui/theme/                  # Material 3 Design System (Color, Typography, Theme)
│   │   │       ├── MainActivity.kt            # Single activity entry point
│   │   │       └── TextMeApplication.kt       # Application class initializing app dependencies
│   │   └── test/                              # Unit tests (TimeUtils, ViewModels, Routing, etc.)
│   └── build.gradle.kts                       # App-level dependencies & build configuration
├── supabase/
│   └── schema.sql                             # Complete PostgreSQL schema, RLS policies & RPCs
├── DATABASE.md                                # Supabase setup and migration documentation
├── TextMe_PRD.md                              # Detailed Product Requirements Document
├── local.properties.example                   # Template for local credentials
└── build.gradle.kts                           # Root project build configuration
```

---

## 🛠️ Tech Stack & Libraries

- **Language:** [Kotlin 2.1.0](https://kotlinlang.org/)
- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/)
- **Asynchronous / Reactive:** Kotlin Coroutines & `StateFlow`
- **Local Database:** [Room 2.7.0](https://developer.android.com/training/data-storage/room) with SQLite & KSP
- **Backend / Realtime:** [Supabase Kotlin SDK (v3.1.1)](https://github.com/supabase-community/supabase-kt)
  - `auth-kt`: Anonymous authentication
  - `postgrest-kt`: RESTful database access & stored RPC execution
  - `realtime-kt`: Realtime WebSocket change notifications
- **Cryptography:** Android KeyStore, `javax.crypto` (AES-GCM-256), HKDF, and X25519
- **Serialization:** `kotlinx.serialization` (JSON)
- **Dependency Management:** Gradle Version Catalog (`libs.versions.toml`)

---

## 🔒 Security & Privacy Model

1. **No Personally Identifiable Information (PII):** No phone numbers, emails, contacts, or names are ever collected or stored.
2. **Server-Side Enforcement:** Row Level Security (RLS) ensures that authenticated users can only query records matching their active identity.
3. **Zero Plaintext on Server:** All messages are encrypted locally before being transmitted to Supabase; the server stores only ciphertext and initialization vectors (`nonce`).
4. **Hardware Key Isolation:** Symmetric and asymmetric keys generated for E2EE are secured by the Android KeyStore provider.
5. **Authoritative Expiration:** Identity expiration is computed server-side via PostgreSQL timestamps (`now()`), making it impervious to client-side clock tampering.

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio:** Ladybug (2024.2.1+) or newer
- **JDK:** Java 17 or Java 21
- **Android SDK:** Compile SDK 35, Min SDK 26 (Android 8.0 Oreo)
- **Supabase Account:** Free tier at [supabase.com](https://supabase.com)

---

### Step 1: Clone the Repository

```bash
git clone https://github.com/pocicaa44/TextMe.git
cd TextMe
```

---

### Step 2: Set Up Supabase Backend

1. Create a new project in your [Supabase Dashboard](https://supabase.com/dashboard).
2. Go to **SQL Editor** in your Supabase project.
3. Open [`supabase/schema.sql`](supabase/schema.sql), copy its entire contents, and execute them in the SQL Editor.
4. Verify that the tables (`identities`, `chat_requests`, `conversations`, `messages`, `blocks`) and RPC functions (`accept_chat_request`) are created.
5. For complete details and troubleshooting, see [DATABASE.md](DATABASE.md).

---

### Step 3: Configure Local Properties

1. Copy [`local.properties.example`](local.properties.example) to `local.properties`:
   ```bash
   cp local.properties.example local.properties
   ```
2. Open `local.properties` and provide your Android SDK path and Supabase credentials:
   ```properties
   sdk.dir=/path/to/Android/Sdk

   supabase.url=https://your-project-id.supabase.co
   supabase.anon.key=your-supabase-anon-public-key
   ```
   > ℹ️ **Note:** `local.properties` is strictly ignored by Git and will never be committed or exposed.

---

### Step 4: Build and Run

To compile and run unit tests:
```bash
./gradlew test
```

To build a debug APK:
```bash
./gradlew assembleDebug
```

To build a release APK:
```bash
./gradlew assembleRelease
```

---

## 🧪 Testing

The repository contains unit tests covering routing, identities, relative time formatting, and ViewModels:

```bash
./gradlew test
```

Test reports are generated at `app/build/reports/tests/testDebugUnitTest/index.html`.

---

## 🤝 Contributing

Contributions, bug reports, and feature requests are welcome!

1. Fork the repository.
2. Create your feature branch (`git checkout -b feature/amazing-feature`).
3. Commit your changes (`git commit -m "Add amazing feature"`).
4. Push to the branch (`git push origin feature/amazing-feature`).
5. Open a Pull Request.

Please make sure all tests pass (`./gradlew test`) before submitting a pull request.

---

## 📄 License

This project is open-source software licensed under the [MIT License](LICENSE).
