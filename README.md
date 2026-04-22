# 🛡️ SafeCircle — Real-Time Group Tracking System

A production-ready, real-time group tracking platform for travel safety, coordination, and live geospatial intelligence.

SafeCircle enables users to create private groups, share invite codes, and track each other in real-time on an army-style interactive map. Built with a modern event-driven architecture, it delivers secure, low-latency, and highly responsive location synchronization across all users.

---

## 🚀 Project Status

**✅ All Development Phases (0–8) Completed**

- [x] Core MVP
- [x] Real-time engine
- [x] Alerts & monitoring
- [x] Route planning
- [x] SOS system
- [x] UI/UX optimization

👉 **The system is now feature-complete and production-ready**

---

## 🌟 Features

### 🔐 Authentication & Security
- JWT-based stateless authentication
- BCrypt password hashing
- Secured REST APIs and WebSocket connections
- Token validation during WebSocket handshake

### 👥 Group Management
- Create private tracking groups
- Join via 6-character invite codes
- Role-based system (Admin / Member)
- Admin controls (member management, thresholds, routes)

### 📍 Real-Time Location Tracking
- Continuous GPS tracking with accuracy metadata
- Optimized update intervals (active + background modes)
- Efficient MongoDB storage with indexed queries

### ⚡ High-Performance Real-Time Engine
- WebSocket-based communication (STOMP + SockJS)
- Sub-second data propagation
- Topic-based group broadcasting

### 🗺️ Interactive Map System
*Powered by Leaflet and OpenStreetMap*
- Real-time animated user markers
- Auto zoom-to-fit all members
- Locate-me functionality
- Custom avatars and visual identifiers

### 🟢 Live Presence Detection
- Instant offline detection via WebSocket disconnect
- Background scheduler for fail-safe detection
- Status indicators: `ONLINE` / `OFFLINE` / `NO_GPS`

### 🚨 Smart Alerts & Monitoring
- Distance threshold alerts
- Offline detection alerts
- Real-time group notifications
- Dedicated alert channels

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
- Optimized database indexing

---

## 🛠 Tech Stack

| Component | Technology |
|---|---|
| **Backend** | Java 21 + Spring Boot |
| **Frontend** | React + Vite |
| **Database** | MongoDB |
| **Security** | Spring Security + JWT |
| **Real-Time Engine** | Spring WebSockets (STOMP + SockJS) |
| **Maps** | Leaflet.js + React-Leaflet + OpenStreetMap |
| **Routing Engine** | OSRM (Open Source Routing Machine) |
| **Styling** | Vanilla CSS (Dark mode optimized) |

---

## 🧱 System Architecture

```text
Client (React)
     ↓
REST APIs (Auth, Groups, Location)
     ↓
Spring Boot Backend
     ↓
MongoDB Database
     ↓
WebSocket Broker (STOMP)
     ↓
Real-Time Broadcast → Clients
```

---

## 🚀 Quick Start (Local Setup)

### 1️⃣ Start MongoDB
```bash
mongod --dbpath /usr/local/var/mongodb
```

### 2️⃣ Run Backend
```bash
cd backend
./mvnw spring-boot:run
```
*Runs on: http://localhost:8080*

### 3️⃣ Run Frontend
```bash
cd frontend
npm install
npm run dev
```
*Runs on: http://localhost:5173*

---

## 🔌 API & WebSocket Flow

### Authentication
* `POST /api/auth/register`
* `POST /api/auth/login`

### Groups
* `POST /api/groups/create`
* `POST /api/groups/join`
* `GET /api/groups/{id}`

### Location (Fallback)
* `POST /api/locations/update`
* `GET /api/locations/group/{id}`

### WebSocket
* **Connect:** `ws://localhost:8080/ws`
* **Publish:** `/app/location.update`
* **Subscribe:** `/topic/group/{groupId}`
* **Alerts:** `/topic/alerts/{groupId}`

---

## 💡 Key Engineering Highlights
- Event-driven architecture (WebSocket-based)
- Secure real-time communication layer
- Optimized geospatial data processing
- Scalable modular backend design
- Battery-efficient mobile tracking logic
- Fault-tolerant system design

---

## 🌍 Use Cases
- Group travel coordination
- Trekking / hiking safety
- Event crowd tracking
- Family safety systems
- Emergency response coordination

---

## 🏁 Conclusion

SafeCircle is a full-scale real-time distributed application that combines:
- Secure authentication
- Live geolocation tracking
- Instant communication
- Intelligent monitoring

👉 **The project demonstrates strong expertise in:**
- Full-stack development
- Real-time systems
- System design
- Performance optimization

---

## ⭐ Future Scope (Optional Enhancements)
- AI-based route optimization
- Offline-first tracking system
- Voice navigation
- Wearable device integration
