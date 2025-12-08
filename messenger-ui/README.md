# Messenger UI

React + Vite application for Messenger.

## Features
- Login / Register
- Dashboard with Real-time Chat
- Search Users
- Chat History

## Running

### With Docker
Verified running in `docker-compose.yml`.
Access at http://localhost:5173

### Local Dev
```bash
npm install
npm run dev
```

Note: Local Dev requires Backend services running (API Gateway at localhost:8080).
Vite proxy is configured to forward /api requests to http://localhost:8080.
Using Docker Compose is recommended.
