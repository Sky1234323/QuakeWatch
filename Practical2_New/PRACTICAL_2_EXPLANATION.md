# Practical No. 02: API Integration & AI QA Mobile Assistant
**College Academic Evaluation & Viva Guide**

---

## 1. Practical Aim & Objectives
According to the curriculum syllabus for **PRACTICAL NO. 02 (API Integration - 6 Hours)**:
1. **Build a Java-based Android app to fetch data from a REST API and display the JSON response.**
2. **Integrate Google Gemini API and create a simple AI QA mobile assistant.**

---

## 2. Technical Workflow & Architecture

### Part 1: REST API Integration & JSON Parsing
1. **Which REST APIs are used?**
   - **Endpoint A (Primary REST API):** `https://jsonplaceholder.typicode.com/posts`
     - Returns a clean JSON Array of objects containing `id`, `title`, and `body`.
   - **Endpoint B (Real-time GeoJSON API):** `https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson`
     - Returns real-time seismic event features.
2. **How is the network request executed?**
   - Implemented using pure standard Java `HttpURLConnection` inside `RestApiClient.java`.
   - Executed on a background thread pool via `AppExecutors.java` so that the Android UI thread remains smooth and non-blocking.
   - Measures the HTTP response code (e.g., `HTTP 200 OK`) and the round-trip network latency in milliseconds.
3. **How is the data extracted?**
   - Uses Android's native `org.json.JSONArray` and `org.json.JSONObject`.
   - Maps JSON attributes into an `ApiItem` data model (`id`, `title`, `body`, `category`).
4. **How is it displayed on screen?**
   - Rendered in a Material 3 `RecyclerView` using `ApiItemAdapter.java`.
   - Provides a dedicated **"View JSON"** button opening a Bottom Sheet Dialog with the formatted raw JSON string, allowing the professor to inspect the response payload.

---

### Part 2: Google Gemini AI QA Assistant
1. **Which AI API is used?**
   - Google Gemini 2.5 Flash API endpoint:
     `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={API_KEY}`
2. **How does it ensure fast responses?**
   - Connects directly using lightweight HTTP POST requests.
   - Generates answers in **under 1–2 seconds**, displaying the exact latency timestamp on each answer bubble (e.g., `Answered in 0.85s`).
3. **Strict English Language Guarantee:**
   - The request payload includes a strict system instruction:
     > *"You are an academic AI assistant for Android students. Always reply strictly in the English language."*
4. **Interactive QA Features:**
   - **One-tap Suggestion Chips:** Pre-built questions such as *"What is a REST API?"*, *"HTTP GET vs POST differences"*, and *"How JSON parsing works in Android"*.
   - **Custom Questions:** Input bar where students and evaluators can type any technical question.

---

## 3. Key Files in `Practical2_New`

| File | Purpose |
| :--- | :--- |
| `MainActivity.java` | Coordinates the TabLayout, REST API fetching, Bottom Sheet dialog, and AI QA flow. |
| `RestApiClient.java` | Background `HttpURLConnection` client for REST GET requests and JSON deserialization. |
| `GeminiAiClient.java` | HTTP POST client communicating with Google Gemini 2.5 Flash API with strict English instructions. |
| `ApiItem.java` | Model representing parsed REST records. |
| `ChatMessage.java` | Model representing chat turns between the student and the AI assistant. |
| `ApiItemAdapter.java` | RecyclerView adapter rendering REST API cards. |
| `ChatAdapter.java` | RecyclerView adapter rendering user bubbles and AI response cards. |
| `Practical2UnitTest.java` | Automated JUnit unit tests validating JSON parsing and data models. |

---

## 4. Viva Questions & Model Answers for Evaluator

**Q1: What is a REST API and what HTTP method was used?**
> **Answer:** A REST (Representational State Transfer) API is an architectural style for networked systems. In this assignment, we used the **HTTP GET** method to retrieve data from `https://jsonplaceholder.typicode.com/posts` in JSON format.

**Q2: Why did you not use third-party libraries like Retrofit or Volley?**
> **Answer:** We implemented the network layer using pure Java `HttpURLConnection` and background thread executors (`ExecutorService`). This demonstrates core Android fundamentals, memory management, and thread communication without external dependencies.

**Q3: How does JSON parsing work in this application?**
> **Answer:** The HTTP response stream is read into a String. We check if the response starts with `[` (a `JSONArray`) or `{` (a `JSONObject`). We iterate through the array, extract keys like `id`, `title`, and `body` using `optString()` and `optInt()`, and instantiate `ApiItem` models.

**Q4: How does the AI QA Assistant communicate with Gemini?**
> **Answer:** It sends a structured JSON payload over HTTP POST containing `contents` (the user's question) and `systemInstruction` (enforcing English). When the JSON response arrives, we extract `candidates[0].content.parts[0].text` and post it to the main UI thread.
