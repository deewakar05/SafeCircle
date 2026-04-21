# 🛡️ SafeCircle — Real-time Group Travel Tracking

A full-stack application for real-time group travel safety and coordination.

## Tech Stack
| Layer | Technology |
|-------|-----------|
| Backend | Java 21 + Spring Boot 3.3 (Maven) |
| Database | MongoDB |
| Real-time | WebSocket (STOMP) |
| Frontend | React 18 + Vite |
| Auth | JWT (Spring Security) |
| Maps | Google Maps SDK |

## Prerequisites
- Java 21+
- Maven 3.8+
- MongoDB 6+ (local or Atlas)
- Node.js 18+
- Google Maps API Key (for map view)

## Quick Start

### 1. MongoDB
```bash
# Start MongoDB locally
mongod --dbpath /usr/local/var/mongodb
```

### 2. Backend
```bash
cd backend
mvn spring-boot:run
# API running on http://localhost:8080
```

### 3. Frontend
```bash
cd frontend
cp .env.example .env
# Edit .env → add your VITE_GOOGLE_MAPS_KEY
npm install
npm run dev
# App running on http://localhost:5173
```

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/signup` | Register |
| POST | `/api/auth/login` | Login → JWT |
| POST | `/api/groups/create` | Create group |
| POST | `/api/groups/join` | Join via code |
| GET | `/api/groups/{id}` | Group details |
| PUT | `/api/groups/{id}/threshold` | Set alert distance |
| POST | `/api/locations/update` | Push GPS location |
| GET | `/api/locations/group/{id}` | Get group locations |

## WebSocket Topics (STOMP)
- Send location: `/app/location.update`
- Receive updates: `/topic/group/{groupId}`
- Receive alerts: `/topic/alerts/{groupId}`

## Project Structure
```
SafeCircle/
├── backend/        # Spring Boot API
│   └── src/main/java/com/safecircle/
│       ├── model/          # User, Group, Location
│       ├── repository/     # MongoDB repos
│       ├── service/        # Business logic + alerts
│       ├── controller/     # REST endpoints
│       ├── websocket/      # STOMP handler
│       ├── security/       # JWT filter
│       └── config/         # Security + WebSocket config
└── frontend/       # React + Vite SPA
    └── src/
        ├── pages/      # Login, Dashboard, Group, Tracking
        ├── services/   # API + WebSocket clients
        └── context/    # Auth state
```

## Roadmap
- [x] Phase 0 – Project skeleton
- [ ] Phase 1 – Auth + Group management
- [ ] Phase 2 – Real-time tracking + WebSocket
- [ ] Phase 3 – Alerts + distance monitoring
- [ ] Phase 4 – Route planning + UI polish
