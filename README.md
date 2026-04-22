# 🛡️ SafeCircle — Core MVP

> **A real-time group travel tracking application designed for seamless coordination and safety.**

SafeCircle allows users to create private groups, share invite codes, and track each other in real-time on an "army-style" interactive map. Built with a focus on high-performance geospatial data handling and sub-second WebSocket broadcasting.

**Status:** 🚀 **Core Phase 1-4 Complete**

---

## 🌟 Features

* **Secure Authentication:** BCrypt hashed passwords and JWT-protected REST APIs and WebSocket endpoints.
* **Group Coordination:** Create and join private instances with generated 6-character alphanumeric invite codes.
* **Army-Style Live Map:** 
  * OpenStreetMap mapping via Leaflet (No Google Maps API costs!)
  * Custom smooth-gliding SVG markers with unique member colors.
  * Pulsing visual rings for active movements (<5s).
  * Fading breadcrumb trails representing the last 8 positions.
  * Real-time velocity indicator (km/h badge).
* **High-Precision GPS:** Custom throttled polling hook to minimize battery drain while maintaining accuracy.
* **True Real-Time Sync:** Native Spring Boot WebSockets over STOMP/SockJS allow users to see friend movements instantly without browser refreshes.
* **Instant Disconnect Detection:** Backend instantly broadcasts `OFFLINE` status the moment a user closes their browser or loses cell service.

---

## 🛠 Tech Stack

| Component | Technology |
| :--- | :--- |
| **Backend Framework** | Java 21 + Spring Boot 3.3.5 |
| **Database** | MongoDB (+ MongoDB Compass for visualization) |
| **Security** | Spring Security + JWT |
| **Real-Time Engine**| Spring WebSockets (STOMP via SockJS) |
| **Frontend Framework**| React 18 + Vite |
| **Maps & Geospatial** | Leaflet.js + React-Leaflet + OpenStreetMap |
| **Styling** | Vanilla CSS (Dark mode optimized) |

---

## 🚀 Quick Start (Local Development)

### 1. Database Setup
```bash
# Ensure MongoDB is running locally on port 27017
mongod --dbpath /usr/local/var/mongodb
```

### 2. Run Backend (Spring Boot)
```bash
cd backend

# Compile and start the server
./mvnw spring-boot:run
# OR
mvn spring-boot:run

# API & WebSocket broker will start on http://localhost:8080
```

### 3. Run Frontend (React/Vite)
```bash
cd frontend

# Install dependencies (only required once)
npm install

# Start the dev server
npm run dev

# App will start on http://localhost:5173 
# (Vite proxies /api and /ws requests directly to port 8080)
```

---

## 🔌 API & System Architecture

### Authentication
* `POST /api/auth/register` — Register a new account
* `POST /api/auth/login` — Exchange credentials for a Bearer JWT

### Group Management
* `POST /api/groups/create` — Create a new group (returns ID and 6-char code)
* `POST /api/groups/join` — Join an existing group via code
* `GET /api/groups/{id}` — Fetch group details and configurations

### Location & Real-Time Sync
1. **Fallback/Initial Polling:** 
   * `POST /api/locations/update`
   * `GET /api/locations/group/{id}`
2. **WebSocket Flow:**
   * **Connect:** `ws://localhost:8080/ws` (Secured via `WebSocketChannelInterceptor` reading STOMP Connect Headers).
   * **Publish:** `/app/location.update`
   * **Subscribe:** `/topic/group/{groupId}` (Streams real-time Location DTOs).
   * **Alerts:** `/topic/alerts/{groupId}` (For group notifications and disconnect events).

---

## 📌 Development Roadmap

- [x] **Phase 1:** Setup User System & JWT Authentication
- [x] **Phase 2:** Implement Group Management APIs & Models
- [x] **Phase 3:** GPS Tracking, MongoDB Geospatial Data, & Front-End Leaflet Maps
- [x] **Phase 4:** The Real-Time Engine (WebSockets, Instant OFFLINE detection, Army-style UI)
- [ ] **Phase 5 (Next):** Geofencing alerts, Battery optimizations, and Historical Route Playback.
