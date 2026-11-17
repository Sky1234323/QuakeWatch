# QuakeWatch: Earthquake Awareness & Seismic Intelligence

**QuakeWatch** is a native Android earthquake awareness application developed in Java for academic presentation. It transforms raw seismic data from the United States Geological Survey (USGS) into clear, actionable intelligence, complete with real-time community reports, analytical trend visualizations, an interactive epicenter map, and on-device generative AI explanations powered by Google Gemini.

---

## 1. Key Features

- **Live USGS XML (QuakeML) Feed**: Direct HTTP client communicating with USGS Earthquake Hazards Program, parsed on-device via `XmlPullParser`.
- **Local SQLite Offline Cache**: Transaction-safe persistence maintaining a 30-day seismic catalog and full functionality in offline/airplane mode.
- **Firebase Authentication & Cloud Firestore**:
  - Secure Email/Password registration and One-Tap Google Sign-In via Android Credential Manager.
  - Live crowdsourced "I felt this" tremor reporting synchronized in real-time across users.
- **Analytics & Trend Visualizations**:
  - Daily seismic frequency BarChart.
  - Magnitude band distribution DonutChart.
  - Dual-axis 7-day vs. 30-day line charts comparing count and average depth.
- **Interactive OpenStreetMap Surface**:
  - Map tiles powered by osmdroid with severity-colored epicenter badges.
  - GPS "Locate Me" integration, user distance calculations, and tap-to-inspect preview sheet with English region transliteration.
- **Google Gemini Seismic Intelligence**:
  - Plain-language 3-part event breakdowns (*What happened*, *How it may feel*, *What to do*) with persistent SQLite caching and instant regeneration.
  - Streaming conversational safety chat with real-time SSE token delivery and persistent multi-session conversation history ("Recents").
- **Background Alert Dispatcher**:
  - Android WorkManager `PeriodicWorkRequest` running every 15 minutes.
  - High-priority system notification channel (`quake_alerts`) targeting user-configured magnitude thresholds (e.g. M5.0+).
- **Seismic Safety Field Guide**:
  - Editorial 3-phase emergency protocol: *Before Shaking (Prepare)*, *During Shaking (Drop, Cover, Hold On)*, and *After Shaking (Inspect & Recover)*.

---

## 2. Architecture & Design Principles

- **Language**: 100% pure Java 8+ source (No Kotlin, conforming to syllabus).
- **User Interface**: Android XML layouts with ViewBinding enabled (No Jetpack Compose).
- **Local Database**: Native `SQLiteOpenHelper` with index-optimized time queries and cascade-deleting chat sessions (No Room ORM).
- **Networking**: Pure `HttpURLConnection` with UTF-8 byte streams and SSE chunk processors (No Retrofit).
- **Design Language**: Editorial field-guide visual style featuring a warm cream background (`#FFFDF9`), terracotta primary accent (`#B85D19`), rich serif typography, and zero emoji.

---

## 3. Project Configuration & Setup

### Prerequisites
1. Android Studio Ladybug or newer.
2. JDK 17.
3. Android SDK (minSdk 24, targetSdk 35).

### Step 1: Firebase Configuration
1. Open the Firebase Console and configure project `QuakeWatch`.
2. Enable **Authentication** providers: **Email/Password** and **Google Sign-In**.
3. Generate your local debug SHA-1 fingerprint:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
4. Register the SHA-1 in your Firebase Project Settings.
5. Download the updated `google-services.json` and place it in the `app/` folder.
6. Publish the Cloud Firestore rules:
   ```javascript
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{uid} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
       match /felt_reports/{reportId} {
         allow read: if request.auth != null;
         allow create: if request.auth != null && request.resource.data.uid == request.auth.uid;
         allow delete: if request.auth != null && resource.data.uid == request.auth.uid;
       }
     }
   }
   ```

### Step 2: Gemini API Key
Add your Gemini API key to `local.properties` in the project root:
```properties
GEMINI_API_KEY=your_gemini_api_key_here
```

---

## 4. Building & Running

### Run Local Unit Tests
```bash
./gradlew testDebugUnitTest
```
Runs unit tests for `QuakeMlParserTest`, `AnalyticsEngineTest`, `GeoUtilsTest`, and `TimeUtilsTest`.

### Build Debug APK
```bash
./gradlew assembleDebug
```
The resulting debug binary is output to:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 5. College Viva / Class Reference

| Package / File | Purpose for Presentation |
|---|---|
| `data.remote.QuakeMlParser` | Custom XML parser converting USGS QuakeML streams into domain objects using a tag stack. |
| `data.local.QuakeDbHelper` | Native SQLite helper managing the 30-day cache, AI explanation cache, and persistent chat threads. |
| `data.FeltRepository` | Live Firestore listener managing crowdsourced felt reports and real-time counter updates. |
| `analytics.AnalyticsEngine` | Mathematical analysis computing rolling averages, frequency distributions, and activity trends. |
| `ai.GeminiClient` | REST & SSE client streaming generative AI advice with automatic fallback across Gemini 3.5 models. |
| `service.QuakeCheckWorker` | Background WorkManager scheduled every 15 minutes to discover unnotified quakes above the threshold. |
| `util.NotificationHelper` | System notification channel setup and interactive PendingIntent dispatching to detail views. |

---

## 6. Continuous Integration (CI)

A GitHub Actions workflow is provided in `.github/workflows/android.yml`. It runs on every push and pull request to `main`/`master`, compiling the APK and executing all unit tests.

> **Note on CI APK vs. Local APK**: Google Sign-In requires the debug certificate fingerprint matching your Firebase project settings. Because CI runners sign binaries with an ephemeral runner key, test Google Sign-In using your local build.
