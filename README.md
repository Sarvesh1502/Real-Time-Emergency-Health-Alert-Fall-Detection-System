# Real-Time Emergency Health Alert & Fall Detection (Prototype)

Minimal, student-built 24-hour hackathon prototype.

- Frontend: vanilla HTML/CSS/JS single page. Simulates sensors and can toggle a fall.
- Backend: Spring Boot (Java 17), H2 in-memory DB. Simple REST API + CORS enabled.
- ML: Prebuilt model placeholder `model/fall_model.tflite` loaded as bytes; inference is stubbed.
- Detection: Hybrid (threshold rules + ML score) and logs mock SMS.
- Data: `data/sample_sensor.csv` included.

## Run Instructions

### Backend
1. Requirements: Java 17+, Maven.
2. Open a terminal in `backend` folder.
3. Build & run:
   - Windows PowerShell:
     ```powershell
     mvn spring-boot:run
     ```
4. API will start at `http://localhost:8080`.
   - Endpoints:
     - `POST /api/events` — send sensor event JSON
     - `GET /api/alerts` — list recent alerts
     - `GET /api/events/recent` — list recent events
5. Check console logs for `[ALERT]` and `[SMS]` messages.

### Frontend
1. Open `frontend/index.html` directly in a modern browser.
2. Click "Start Streaming" and optionally enable "Simulate Fall".
3. Live alerts and recent events will refresh every ~1.5s.

Note: when opening `index.html` from file://, some browsers block requests to localhost due to CORS/mixed content policies. If needed, serve a simple static server, e.g.:
```powershell
# Example with Python
python -m http.server 5500 -d frontend
# then open http://localhost:5500/
```

## JSON Example
```json
{
  "timestamp": 1732780001234,
  "accel": { "x": 0.1, "y": -0.2, "z": 9.7 },
  "gyro": { "x": 1.5, "y": 0.2, "z": -0.3 },
  "lat": 12.9721,
  "lng": 77.5933
}
```

## What to Implement Next
- Replace stub ML with actual tiny model inference (e.g., TensorFlow Lite Java with a real `.tflite`).
- Add authentication and user profiles (caregiver, patient).
- Push notifications (Web Push / Firebase) and real SMS (Twilio or local telecom API).
- Better map UI using Leaflet or MapLibre with reverse geocoding.
- Smoothing window + posture checks to reduce false positives.
- Battery/network optimizations for mobile.
- Offline buffering and retry for sensor uploads.
- Unit tests and simple e2e script replaying `data/sample_sensor.csv`.

## Notes
- This is intentionally simple and clearly student-made. No heavy frameworks in the frontend.
- The model file here is a placeholder; not trained as part of this repo.
