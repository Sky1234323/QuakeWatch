# QuakeWatch: Project Workflow & Architecture Guide
**Prepared for Academic Presentation & Viva Examination**

This document explains the complete, end-to-end workflow of **QuakeWatch** in clear, simple language without diving into complex code. Any team member can read this guide to understand exactly what APIs are used, how data flows, how the database works, and how each screen displays its information.

---

## 1. What is QuakeWatch? (Problem & Purpose)

When an earthquake happens, government seismology agencies provide raw technical numbers (e.g. *"M5.4, Depth 202.7 km, Lat 36.5, Lon 71.0"*). To an ordinary citizen, these numbers are confusing and terrifying:
- *Did people feel this?*
- *Is this close to me?*
- *Is seismic activity rising or falling?*
- *What should I actually do right now to stay safe?*

**QuakeWatch** solves this problem by pulling real seismic data from the US Geological Survey, analyzing patterns, rendering epicenters on an interactive map, and using Google Gemini AI to translate raw numbers into plain-language safety guides.

---

## 2. Core Feature Workflows (Professor's Viva Format)

---

### Workflow 1: The Live Earthquake Feed (USGS REST API)

1. **Which API is used?**
   - Official **USGS Earthquake Hazards Program API** (United States Geological Survey).
   - Endpoint: `https://earthquake.usgs.gov/fdsnws/event/1/query`
   - Request Type: `HTTP GET` with `HttpURLConnection`.
2. **What data is fetched from the API?**
   - The API returns **XML (QuakeML)** containing recent earthquake events over the last 30 days for the user's selected region (e.g., South Asia, Worldwide, or Near Me).
   - Each event contains:
     - Event ID (e.g., `us7000ttii`)
     - Place name (e.g., `43 km S of Jurm, Afghanistan`)
     - Magnitude (e.g., `4.9`) and Magnitude Type (e.g., `mb`)
     - Epicenter coordinates (Latitude, Longitude)
     - Focal depth (in kilometers)
     - UTC timestamp of occurrence
3. **How is the data extracted?**
   - The app uses an on-device streaming **`XmlPullParser`**.
   - As the XML stream arrives from the network, the parser inspects tags line-by-line using an element stack, extracting only valid earthquake events and skipping incomplete records.
4. **How is the data saved locally? (Offline Cache)**
   - The parsed events are immediately saved into a local **SQLite database** (`quakes` table).
   - This ensures that if the phone goes into **Airplane Mode** or loses internet connection, the user can still open the app and view all saved earthquakes. Old quakes older than 30 days are automatically pruned.
5. **How is it displayed on the page? (Home Tab)**
   - **Hero Card**: Displays the single latest detected earthquake with a large colored magnitude badge.
   - **2x2 Metrics Grid**: Shows Today's count, Strongest magnitude, Average depth, and Activity trend percentage.
   - **Time Filters**: Filter between *Today*, *7 days*, and *30 days*.
   - **Magnitude Chips**: Filter the list by *Recent*, *M4+*, *M5+*, and *Felt reports*.
   - **RecyclerView List**: Displays each earthquake with location, time-ago, depth, and distance.

---

### Workflow 2: User Sign-In & Authentication (Firebase)

1. **Which Service is used?**
   - **Firebase Authentication** combined with the modern **Android Credential Manager API**.
2. **How does it work?**
   - **Email/Password**: Users can register with an email and password.
   - **Google Sign-In**: Users can tap "Sign in with Google", which opens the native Google One-Tap account selector.
3. **What data is stored?**
   - Once signed in, a user profile document is automatically created in **Cloud Firestore** under the collection `users/{uid}` storing the user's display name, email, and registration timestamp.
4. **Session Persistence**:
   - The user remains logged in across app restarts. The Profile tab shows their avatar initials, name, and an option to Sign Out.

---

### Workflow 3: Crowdsourced "I Felt This" Reports (Cloud Firestore)

1. **Which Service is used?**
   - **Google Cloud Firestore** (NoSQL Real-Time Database).
   - Collection: `felt_reports`
   - Document ID pattern: `{quakeId}_{userId}` (enforcing a strict rule: **1 report per user per earthquake**).
2. **How does the user submit a report?**
   - When a user opens any earthquake detail screen, there is a community section with a button: **"I felt this"**.
   - Tapping it writes a document to Firestore with `{ quakeId, uid, userName, createdAt }`.
   - Tapping it again removes the document (toggle behavior).
3. **How does it update live on other phones?**
   - The app attaches a **Real-Time Snapshot Listener** (`addSnapshotListener`) to Firestore.
   - Whenever *any* user anywhere in the world taps "I felt this", all other phones listening to that earthquake receive the update instantly and increment the headcount (e.g. *"3 people reported feeling this earthquake"*) without needing to refresh!

---

### Workflow 4: Visual Charts & Analytics (MPAndroidChart)

1. **How is the data analyzed?**
   - The app has an on-device calculation engine (`AnalyticsEngine`).
   - It takes the cached SQLite list of earthquakes and computes:
     - Daily frequency counts grouped by day.
     - Severity distribution grouped into 4 bands: Low (<M4), Moderate (M4–4.9), Strong (M5–5.9), Major (M6+).
     - Focal depth breakdown: Shallow (<70 km), Intermediate (70–300 km), Deep (>300 km).
     - Activity trend: Compares the current time window's count against the preceding equivalent period to calculate percentage increase or decrease.
2. **How are the charts displayed? (Insights Tab)**
   - **Summary Sentence**: A natural English explanation at the top (e.g. *"Over the last 7 days, 14 earthquakes were recorded. Strongest was M5.2 near Tonga. Activity is falling by 18%."*).
   - **Bar Chart**: Daily frequency distribution over time.
   - **Donut Chart**: Proportional slice breakdown across magnitude severity bands.
   - **Depth Progress Bars**: Visual percentages showing shallow vs. deep events.
   - **Dual-Axis Line Chart**: Compares daily earthquake frequency (primary axis) against average focal depth (secondary axis).

---

### Workflow 5: Interactive Epicenter Map (osmdroid & OpenStreetMap)

1. **Which Map library & tiles are used?**
   - **osmdroid** (native OpenStreetMap library for Android).
   - Official Mapnik tile server with custom Application User-Agent configuration.
2. **How are earthquakes plotted on the map?**
   - For every earthquake in the database, a custom circular canvas badge is created at its exact Latitude and Longitude coordinates.
   - The badge is color-coded by severity:
     - 🟢 **Green**: Low (< M4.0)
     - 🟡 **Yellow**: Moderate (M4.0 – M4.9)
     - 🟠 **Orange**: Strong (M5.0 – M5.9)
     - 🔴 **Red**: Major (M6.0+)
   - The number inside the circle is the magnitude (e.g. `5.4`).
3. **User Location & Distance**:
   - The map includes a floating **"Locate Me"** button.
   - Tapping it requests GPS permission, animates the camera to the user's current position, and drops a blue "You are here" marker.
   - Distances from the user to each epicenter are calculated using the spherical **Haversine formula**.
4. **Bottom Preview Card & English Region Clarification**:
   - Tapping any epicenter marker on the map opens a slide-up preview card at the bottom.
   - To make sure users always understand the location (even if the underlying map tiles show local foreign script like Chinese or Cyrillic), the preview card extracts and displays an explicit **`English Region: <Country>`** banner (e.g. `English Region: China`, `English Region: Afghanistan`).

---

### Workflow 6: Generative AI Intelligence (Google Gemini REST API)

1. **Which API is used?**
   - **Google Gemini Generative AI REST API** (`v1beta`).
   - Endpoint: `https://generativelanguage.googleapis.com/v1beta/models/<model>:generateContent` and `:streamGenerateContent?alt=sse`.
   - Models used: `gemini-3.5-flash-lite`, `gemini-3.8-flash`, and `gemini-3.6-flash` (with automatic fallback).
2. **Feature A: "Explain This Earthquake" (Detail Screen)**
   - When a user views an earthquake and taps **"Explain"**, the app sends the event facts (magnitude, depth, location, distance from user) to Gemini.
   - Gemini responds with a structured, plain-language 3-part breakdown:
     - **What happened**: A simple explanation of the fault movement and depth.
     - **How it may feel**: What surface shaking an ordinary person would experience.
     - **What to do**: 3 immediate practical safety action lines.
   - The explanation is cached in SQLite (`ai_cache` table) so reopening it is instantaneous offline.
   - Tapping **"Explain again"** re-queries Gemini to provide a freshly phrased breakdown retaining the exact same meaning.
3. **Feature B: Streaming Conversational Assistant (Ask AI Tab)**
   - Users can ask any question about earthquake preparedness, emergency kits, or seismology.
   - The app streams the response token-by-token using **Server-Sent Events (SSE)**, showing a live "Thinking..." and streaming text animation with the response latency metric (e.g. *Response time: 1.2 s*).
   - **Recents History (ChatGPT style)**: Every conversation is stored in SQLite under `chat_sessions` and `chat_messages`. Tapping the **"Recents"** button in the toolbar opens a bottom sheet listing past chat topics, so users never lose their chat history!

---

### Workflow 7: Background Alerts (WorkManager & Notification Channels)

1. **How do background checks work?**
   - The app schedules a background job using Android's **`WorkManager`** (`PeriodicWorkRequest` running every 15 minutes).
   - Even if the app is closed, Android wakes up the worker in the background.
2. **What does the worker do?**
   - Queries the USGS API for quakes from the last 2 days.
   - Compares each quake's magnitude against the user's alert threshold slider set in Profile (e.g. **M5.0+**).
   - If an earthquake meets or exceeds the threshold and has not been notified yet, it fires an alert.
3. **How is the notification displayed?**
   - Uses Android's high-priority notification channel: `quake_alerts`.
   - Notification Title: `M5.4 earthquake`
   - Notification Body: `43 km S of Jurm, Afghanistan | 15m ago | depth 202 km`
   - Tapping the notification opens `QuakeDetailActivity` directly to show all details.
   - In Profile, the **"Send test alert"** button allows instant demonstration for examiners.

---

## 3. High-Level Architecture Diagram

```
+-------------------------------------------------------------------------+
|                                 USER UI                                 |
|   [Home Tab]       [Map Tab]     [Insights Tab]   [Ask AI]   [Profile]  |
+--------+---------------+---------------+--------------+--------+--------+
         |               |               |              |        |
         v               v               v              v        v
+-------------------------------------------------------------------------+
|                              VIEWMODELS                                 |
|             QuakeViewModel   •   LiveData Observers                     |
+--------------------+----------------------------------+-----------------+
                     |                                  |
                     v                                  v
+-----------------------------------------+   +---------------------------+
|              REPOSITORIES               |   |        AI CLIENT          |
|  • QuakeRepository  • FeltRepository    |   |     GeminiClient (SSE)    |
|  • AuthRepository   • SettingsStore     |   |  (gemini-3.5-flash-lite)  |
+--------+-----------------------+--------+   +-------------+-------------+
         |                       |                          |
         v                       v                          v
+------------------+   +-------------------+      +-----------------------+
|  LOCAL STORAGE   |   |   REMOTE (USGS)   |      |   CLOUD (FIREBASE)    |
|  SQLite Database |   |   QuakeML (XML)   |      | • Firebase Auth       |
| • quakes         |   |   HttpURLConnection|     | • Firestore Database  |
| • ai_cache       |   |   XmlPullParser   |      |   (Live Felt Reports) |
| • chat_sessions  |   +-------------------+      +-----------------------+
| • chat_messages  |
+------------------+
```

---

## 4. The 2-Minute Viva Presentation Script (For Your Friends)

If the professor asks any team member to explain the project in 2 minutes, recite this:

> *"Our project is **QuakeWatch**, an earthquake monitoring and awareness Android application built entirely in Java without external third-party architectural frameworks.
> 
> Here is how our data flows:
> 1. **Data Ingestion**: We connect to the official USGS REST service via `HttpURLConnection` and fetch live earthquake records in XML format. On the phone, our custom XML Pull Parser extracts the event coordinates, magnitude, depth, and location.
> 2. **Local Storage**: All data is cached in a local SQLite database so the application works seamlessly offline without internet.
> 3. **Live Crowdsourcing**: We use Firebase Authentication for Google and Email sign-in, and Cloud Firestore with real-time listeners for 'I felt this' community reports, which update live across all phones.
> 4. **Visual Analytics**: Our analytics engine calculates frequency distributions, severity bands, and rising/falling trends displayed via Bar, Donut, and Line charts using MPAndroidChart.
> 5. **Interactive Map**: Epicenters are rendered on an OpenStreetMap surface using osmdroid, featuring severity color coding, GPS distance calculations, and English region transliteration.
> 6. **Generative AI**: We integrate Google Gemini 3.5 Flash using REST and Server-Sent Events (SSE). It generates 3-part plain-language safety guides for any earthquake and powers a streaming conversational safety assistant with persistent multi-session chat history.
> 7. **Background Alerts**: Using Android WorkManager, the app periodically polls in the background every 15 minutes and dispatches high-priority system notifications when tremors exceed the user's magnitude threshold."*

---

## 5. Technology Stack Summary Table

| Layer | Technology Used | Why It Was Chosen |
|---|---|---|
| **Language** | Java 8+ (No Kotlin) | Conforms to academic syllabus; object-oriented, readable. |
| **UI Framework** | Android XML + ViewBinding | Modern view binding; no deprecated `findViewById`. |
| **Seismic Data API** | USGS Earthquake Hazards Program | Official, free, global earthquake authority returning real XML. |
| **XML Parser** | `org.xmlpull.v1.XmlPullParser` | Fast, low-memory streaming parser built directly into Android. |
| **Local Database** | Native `SQLiteOpenHelper` | Offline cache for earthquakes, AI explanations, and chat sessions. |
| **Cloud Database** | Firebase Cloud Firestore | NoSQL database with WebSocket snapshot listeners for live headcount. |
| **Authentication** | Firebase Auth + Credential Manager | Modern one-tap Google and Email credential handling. |
| **Mapping Engine** | osmdroid (OpenStreetMap) | Open-source mapping without proprietary Google Maps billing or keys. |
| **Charts Engine** | MPAndroidChart | Renders BarChart, PieChart/Donut, and LineChart. |
| **Generative AI** | Google Gemini 3.5 Flash REST API | Plain-language earthquake translation & streaming safety chatbot. |
| **Background Tasks** | Android WorkManager | Battery-conscious 15-minute background polling for earthquake alerts. |
| **Design System** | Custom Warm Cream & Terracotta | Editorial field-guide theme with serif typography and clean cards. |
