# 🛡️ SafeCircle — Real-Time Group Tracking System

A production-ready, real-time group tracking platform for travel safety, coordination, and live geospatial intelligence.

SafeCircle enables users to create private groups, share invite codes, and track each other in real-time on an army-style interactive map. Built with a modern event-driven architecture, it delivers secure, low-latency, and highly responsive location synchronization across all users.

---

## 🚀 Project Status

**✅ Production-Ready — All Development Phases (0–8) + Security Hardening Complete**

- [x] Core MVP
- [x] Real-time engine
- [x] Alerts & monitoring
- [x] Route planning
- [x] SOS system
- [x] UI/UX optimization
- [x] **Security hardening** (IDOR/BOLA, WebSocket auth, GPS validation)
- [x] **Performance optimization** (N+1 fix, indexed queries, rate limiting)
- [x] **48 automated tests** (unit + integration, all passing)

👉 **The system is feature-complete, security-audited, and production-ready**

---

## 📁 Repository Structure

```
SafeCircle/
├── backend/      ← Spring Boot (Java 21) — REST API + WebSocket server
└── frontend/     ← React + Vite — Interactive tracking UI
```

### Branch Strategy

| Branch | Purpose |
|--------|---------|
| `main` | ✅ **Production** — fully hardened, all tests pass |
| `backend` | 🔧 Backend feature development |
| `frontend` | 🎨 Frontend feature development |

> **Always deploy from `main`.** Feature branches are merged into `main` after review and test passage.

---

## 🌟 Features

### 🔐 Authentication & Security
- JWT-based stateless authentication (RS256 signed)
- BCrypt password hashing
- **IDOR/BOLA protection** — every API and WebSocket endpoint validates group membership
- JWT validated on both REST and WebSocket CONNECT/SUBSCRIBE frames
- GPS coordinate bounds validation (`lat ∈ [-90,90]`, `lng ∈ [-180,180]`)
- Structured security logging for all access denials

### 👥 Group Management
- Create private tracking groups
- Join via 6-character invite codes (SecureRandom)
- Role-based system (Admin / Member)
- Admin controls (member management, thresholds, routes)

### 📍 Real-Time Location Tracking
- Continuous GPS tracking with accuracy metadata
- In-memory rate limiting (2s debounce per user+group) — prevents DB flooding
- Efficient MongoDB storage with indexed queries
- Optimized update intervals (active + background modes)

### ⚡ High-Performance Real-Time Engine
- WebSocket-based communication (STOMP + SockJS)
- Sub-second data propagation
- Topic-based group broadcasting
- Duplicate subscription guard on the frontend

### 🟢 Live Presence Detection
- **Multi-session aware** — user only marked `OFFLINE` when ALL tabs/devices close
- `UserSessionRegistry` tracks active sessions per user in-memory
- Background scheduler for fail-safe detection (uses direct indexed query — no `findAll()`)
- Status indicators: `ONLINE` / `OFFLINE` / `NO_GPS` / `SOS`

### 🗺️ Interactive Map System
*Powered by Leaflet and OpenStreetMap*
- Real-time animated user markers
- Auto zoom-to-fit all members
- Deterministic user colors (djb2 hash of userId — consistent across refreshes)
- Custom avatars and visual identifiers

### 🚨 Smart Alerts & Monitoring
- Distance threshold alerts
- Offline detection alerts
- Real-time group notifications
- Dedicated alert channels per group

### 🧭 Route Planning & Navigation
- Admin-defined checkpoints
- Route generation via OSRM
- Real-time route synchronization across members

### 🆘 Emergency SOS System
- One-tap emergency trigger
- Instant high-priority broadcast
- Auto-focus on distressed user
- Pulsing emergency markers + global alerts

### 📊 Advanced Tracking Analytics
- Breadcrumb trails (movement history)
- Real-time speed calculation (km/h)
- Relative timestamps (live updates)

### 🔄 Fault Tolerance & Optimization
- WebSocket → REST fallback mechanism
- Battery-efficient GPS tracking
- Memory-safe frontend architecture
- In-memory group cache with eviction on mutation

---

## 🛠 Tech Stack

| Component | Technology |
|---|---|
| **Backend** | Java 21 + Spring Boot 3.3 |
| **Frontend** | React 18 + Vite 5 |
| **Database** | MongoDB |
| **Security** | Spring Security + JWT (jjwt) |
| **Real-Time Engine** | Spring WebSockets (STOMP + SockJS) |
| **Maps** | Leaflet.js + React-Leaflet + OpenStreetMap |
| **Routing Engine** | OSRM (Open Source Routing Machine) |
| **Styling** | Vanilla CSS (Dark mode optimized) |
| **Testing** | JUnit 5 + Mockito + Spring Boot Test + Embedded MongoDB |

---

## 🧱 System Architecture

```text
Client (React)
     ↓  HTTPS / WSS
REST APIs (Auth, Groups, Location) ← JWT validated on every request
     ↓
Spring Boot Backend
     ├── WebSocketChannelInterceptor  ← validates CONNECT + SUBSCRIBE
     ├── LocationService              ← IDOR guard + rate limiter + group cache
     ├── GroupService                 ← IDOR guard + cache eviction
     └── UserSessionRegistry          ← multi-session presence tracking
     ↓
MongoDB (indexed on userId+groupId, timestamp)
     ↓
WebSocket Broker (STOMP in-memory)
     ↓
Real-Time Broadcast → /topic/group/{id} → All group members
```

---

## 🚀 Quick Start (Local Setup)

### Prerequisites
- Java 21+, Maven 3.9+
- Node.js 18+
- MongoDB 7.0+ running locally

### 1️⃣ Start MongoDB
```bash
mongod --dbpath /usr/local/var/mongodb
```

### 2️⃣ Run Backend
```bash
cd backend
./mvnw spring-boot:run -Dspring.profiles.active=dev
```
*Runs on: http://localhost:8080*

### 3️⃣ Run Frontend
```bash
cd frontend
npm install
npm run dev
```
*Runs on: http://localhost:5173*

### 4️⃣ Run Tests
```bash
cd backend
mvn test
# Expected: Tests run: 48, Failures: 0, Errors: 0
```

---

## 🔌 API & WebSocket Reference

### Authentication
| Method | Endpoint | Auth |
|--------|----------|------|
| `POST` | `/api/auth/signup` | — |
| `POST` | `/api/auth/login` | — |

### Groups
| Method | Endpoint | Auth | Notes |
|--------|----------|------|-------|
| `POST` | `/api/groups/create` | ✅ JWT | Creates group, requester becomes admin |
| `POST` | `/api/groups/join` | ✅ JWT | Join by invite code |
| `GET` | `/api/groups/{id}` | ✅ JWT + member | 403 if not a member |
| `GET` | `/api/groups/my` | ✅ JWT | Only user's own groups |
| `PUT` | `/api/groups/{id}/threshold` | ✅ JWT + admin | 403 if not admin |
| `DELETE` | `/api/groups/{id}/members/{uid}` | ✅ JWT + admin | |
| `PUT` | `/api/groups/{id}/route` | ✅ JWT + admin | |

### Location
| Method | Endpoint | Auth | Notes |
|--------|----------|------|-------|
| `POST` | `/api/locations/update` | ✅ JWT + member | REST fallback for WS |
| `GET` | `/api/locations/group/{id}` | ✅ JWT + member | 403 if not a member |

### WebSocket
```
Connect:    ws://localhost:8080/ws  (Authorization: Bearer <token> header required)
Publish:    /app/location.update
Subscribe:  /topic/group/{groupId}   ← member-only (validated on SUBSCRIBE)
Alerts:     /topic/alerts/{groupId}  ← member-only (validated on SUBSCRIBE)
```

---

## 🧪 Test Coverage

| Test Class | Tests | What's Covered |
|---|---|---|
| `AuthServiceTest` | 5 | Signup, duplicate email, login, bad creds |
| `GroupServiceTest` | 12 | Create, join (valid/invalid/idempotent), IDOR, admin-only ops, cache eviction |
| `LocationServiceTest` | 8 | IDOR, GPS validation, SOS broadcast, offline marking, scheduler no-findAll |
| `AuthControllerIntegrationTest` | 10 | Full HTTP: signup/login/JWT/tampered token |
| `GroupControllerIntegrationTest` | 12 | Full HTTP: E2E flow, IDOR, admin-only, GPS validation |
| `SafeCircleApplicationTests` | 1 | Context load smoke test |
| **Total** | **48** | **All passing ✅** |

---

## 💡 Key Engineering Highlights
- Event-driven WebSocket architecture with STOMP protocol
- IDOR/BOLA protection on every API endpoint and WebSocket subscription
- Multi-session presence management (tab-close safe)
- O(1) offline detection via indexed MongoDB query (replaces findAll bottleneck)
- In-memory group config cache with eviction for zero N+1 queries
- Battery-efficient GPS with server-side rate limiting
- Axios 401 interceptor for automatic token refresh/logout on the frontend

---

## 🌍 Use Cases
- Group travel coordination
- Trekking / hiking safety
- Event crowd tracking
- Family safety systems
- Emergency response coordination

---

## ⭐ Future Scope
- Redis-based session registry for horizontal scaling
- AI-based route optimization
- Offline-first tracking system
- Voice navigation
- Wearable device integration
