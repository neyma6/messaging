# Neyma Messenger

A distributed, real-time messaging platform built with microservices architecture. This application demonstrates modern software engineering practices including event-driven architecture, WebSocket communication, and containerized deployment.

> **Note:** This project was created with AI assistance through **vibe coding** - a collaborative approach where artificial intelligence and human creativity work together to build software.

---

## 🏗️ System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                    NEYMA MESSENGER                                       │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                          │
│    ┌──────────────┐                                                                      │
│    │ Messenger UI │ ◄──────── React + Vite (Port 5173)                                   │
│    │   (Browser)  │                                                                      │
│    └──────┬───────┘                                                                      │
│           │                                                                              │
│           │ HTTP/REST + WebSocket                                                        │
│           ▼                                                                              │
│    ┌──────────────┐                                                                      │
│    │ API Gateway  │ ◄──────── Spring WebFlux + JWT Auth (Port 8080)                      │
│    │              │           Routes: /api/users, /api/history, /api/registry            │
│    └──────┬───────┘                                                                      │
│           │                                                                              │
│     ┌─────┴─────┬──────────────┬─────────────────┐                                       │
│     │           │              │                 │                                       │
│     ▼           ▼              ▼                 ▼                                       │
│ ┌────────┐ ┌──────────┐ ┌────────────────┐ ┌───────────────┐                             │
│ │ User   │ │   Chat   │ │    Service     │ │   Messaging   │                             │
│ │Service │ │ History  │ │   Registry     │ │   Service     │ ◄── WebSocket Handler       │
│ │        │ │ Service  │ │   Service      │ │               │                             │
│ └───┬────┘ └────┬─────┘ └───────┬────────┘ └───────┬───────┘                             │
│     │           │               │                  │                                     │
│     ▼           ▼               ▼                  │                                     │
│ ┌────────┐ ┌──────────┐    ┌─────────┐             │                                     │
│ │User DB │ │Chat DB   │    │  Redis  │◄────────────┤ Pub/Sub                             │
│ │Postgres│ │Postgres  │    │         │             │                                     │
│ └────────┘ └────┬─────┘    └─────────┘             │                                     │
│                 │                                  │                                     │
│                 │                                  ▼                                     │
│                 │          ┌───────────────────────────────────────┐                     │
│                 │          │         Message Service               │                     │
│                 └─────────►│   (Processes & Stores Messages)       │                     │
│                            └──────────────────┬────────────────────┘                     │
│                                               │                                          │
│                                               ▼                                          │
│                            ┌──────────────────────────────────────┐                      │
│                            │            Cassandra                 │                      │
│                            │       (Message Storage)              │                      │
│                            └──────────────────────────────────────┘                      │
│                                               │                                          │
│                                               │ Kafka                                    │
│                                               ▼                                          │
│                            ┌──────────────────────────────────────┐                      │
│                            │       Message Dispatcher             │                      │
│                            │   (Kafka Consumer → Redis Pub)       │                      │
│                            └──────────────────────────────────────┘                      │
│                                                                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 📨 Message Flow

The following diagram illustrates how a message travels through the system from sender to receiver:

```
┌─────────┐   WebSocket    ┌───────────────┐   HTTP POST   ┌─────────────────┐
│ Sender  │───────────────►│   Messaging   │──────────────►│    Message      │
│   UI    │                │    Service    │               │    Service      │
└─────────┘                └───────────────┘               └────────┬────────┘
                                                                    │
                                                                    │ 1. Save to Cassandra
                                                                    │ 2. Publish to Kafka
                                                                    ▼
                                                           ┌────────────────┐
                                                           │     Kafka      │
                                                           │  (Topic: msg)  │
                                                           └───────┬────────┘
                                                                   │
                                                                   ▼
                                                           ┌────────────────┐
                                                           │   Message      │
                                                           │  Dispatcher    │
                                                           └───────┬────────┘
                                                                   │
                                                                   │ Redis PUBLISH
                                                                   │ to inbox:user:{id}
                                                                   ▼
┌──────────┐   WebSocket   ┌───────────────┐   Redis Sub   ┌───────────────┐
│ Receiver │◄──────────────│   Messaging   │◄──────────────│     Redis     │
│    UI    │               │    Service    │               │   (Pub/Sub)   │
└──────────┘               └───────────────┘               └───────────────┘
```

### Detailed Flow:

1. **User sends message** via WebSocket to the Messaging Service
2. **Messaging Service** forwards the message to Message Service via HTTP
3. **Message Service**:
   - Fetches chat participants (cached in Redis)
   - Saves message to Cassandra
   - Publishes a Kafka message for each recipient
4. **Message Dispatcher** consumes from Kafka and publishes to Redis channels (`inbox:user:{userId}`)
5. **Messaging Service** (subscribed to user's Redis channel) receives and pushes via WebSocket
6. **Recipient's UI** receives the message in real-time

---

## 🧩 Services Overview

### 1. Messenger UI (`messenger-ui`)
**Technology:** React, Vite, Axios, Lucide Icons  
**Port:** 5173

The frontend single-page application that provides:
- User registration and login
- Real-time chat interface
- User search functionality
- Chat list management

### 2. API Gateway (`apiGateway`)
**Technology:** Spring Boot WebFlux, Spring Security, JWT  
**Port:** 8080

Central entry point for all API requests:
- JWT-based authentication and authorization
- Request routing to downstream services
- WebSocket connection proxying
- CORS configuration

**Routes:**
| Path | Target Service |
|------|----------------|
| `/api/users/**` | User Service |
| `/api/history/**` | Chat History Service |
| `/api/registry/**` | Service Registry |
| `/api/messages/**` | Message Service |

### 3. User Service (`userService`)
**Technology:** Spring Boot, Spring Data JPA, PostgreSQL  
**Port:** 8083

Manages user accounts and authentication:
- User registration with password hashing (BCrypt)
- User login with JWT token generation
- User search by name
- User profile retrieval

### 4. Service Registry Service (`serviceRegistryService`)
**Technology:** Spring Boot, Redis  
**Port:** 8081

Manages messaging service instances for load balancing:
- Service instance registration with heartbeat
- User-to-service assignment (sticky sessions)
- Health monitoring via Redis PUBSUB

### 5. Messaging Service (`messagingService`)
**Technology:** Spring Boot WebFlux, Reactive Redis, WebSocket  
**Port:** 8085

Handles real-time WebSocket connections:
- Accepts WebSocket connections from clients
- Forwards outgoing messages to Message Service
- Subscribes to Redis channels for incoming messages
- Registers itself with Service Registry on startup

### 6. Message Service (`messageService`)
**Technology:** Spring Boot WebFlux, Cassandra, Kafka, Redis  
**Port:** 8084

Core message processing service:
- Persists messages to Cassandra
- Caches chat participants in Redis (30-minute TTL)
- Publishes messages to Kafka for delivery
- Retrieves historical messages

### 7. Message Dispatcher (`messageDispatcher`)
**Technology:** Spring Boot, Kafka Consumer, Redis  
**Port:** 8086

Bridges Kafka and Redis for message delivery:
- Consumes messages from Kafka topic
- Publishes to recipient's Redis inbox channel
- Handles offline user scenarios

### 8. Chat History Service (`chatHistoryService`)
**Technology:** Spring Boot, Cassandra, PostgreSQL  
**Port:** 8082

Manages chat metadata and history:
- Creates and retrieves chat IDs for user pairs
- Stores chat participants in PostgreSQL
- Retrieves message history from Cassandra
- Manages user-to-chat mappings

---

## 🛠️ Technology Stack

### Backend
| Technology | Purpose |
|------------|---------|
| **Java 21** | Primary programming language |
| **Spring Boot 3.x** | Microservices framework |
| **Spring WebFlux** | Reactive programming (API Gateway, Messaging) |
| **Spring Security** | Authentication & Authorization |
| **Spring Data JPA** | PostgreSQL ORM |
| **Spring Data Cassandra** | Cassandra ORM |
| **Spring Kafka** | Message queue integration |

### Frontend
| Technology | Purpose |
|------------|---------|
| **React 18** | UI framework |
| **Vite** | Build tool & dev server |
| **React Router** | Client-side routing |
| **Axios** | HTTP client |
| **Lucide React** | Icon library |

### Infrastructure
| Technology | Purpose |
|------------|---------|
| **PostgreSQL 16** | Relational database (Users, Chats) |
| **Apache Cassandra** | Distributed message storage |
| **Apache Kafka** | Event streaming platform |
| **Redis** | Caching, Pub/Sub, Service registry |
| **Docker & Docker Compose** | Containerization & orchestration |

---

## 🚀 Getting Started

### Prerequisites

- Docker Desktop (with Docker Compose v2)
- Node.js 18+ (for local frontend development)
- Java 21 (for local backend development)

### Quick Start

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd messaging
   ```

2. **Start all services:**
   ```bash
   docker compose up -d
   ```

3. **Wait for services to be healthy** (especially Cassandra, which may take 1-2 minutes):
   ```bash
   docker compose ps
   ```

4. **Access the application:**
   - **UI:** http://localhost:5173
   - **API Gateway:** http://localhost:8080

### Development Mode

To run the frontend in development mode with hot reload:

```bash
cd messenger-ui
npm install
npm run dev
```

### Service Ports

| Service | Port |
|---------|------|
| Messenger UI | 5173 |
| API Gateway | 8080 |
| Service Registry | 8081 |
| Chat History Service | 8082 |
| User Service | 8083 |
| Message Service | 8084 |
| Messaging Service (WebSocket) | 8085 |
| Message Dispatcher | 8086 |
| PostgreSQL (User DB) | 5432 |
| PostgreSQL (Chat DB) | 5433 |
| Redis | 6379 |
| Cassandra | 9042 |
| Kafka | 29092 |

---

## 📚 API Documentation

Each service exposes OpenAPI documentation at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

### Key Endpoints

#### User Service
```
POST   /users/register     - Register new user
POST   /users/login        - Authenticate user
GET    /users/{id}         - Get user by ID
GET    /users/search       - Search users by name
```

#### Chat History Service
```
GET    /history/chat                     - Get/create chat between users
GET    /history/chat/{id}/participants   - Get chat participants
GET    /history/messages                 - Get messages in time range
GET    /history/user/{id}/chats          - Get user's chat list
```

#### Service Registry
```
POST   /registry/service/{id}   - Register messaging instance
GET    /registry/user/{id}      - Get assigned WebSocket endpoint
```

#### Message Service
```
POST   /messages   - Send a new message
```

---

## 🔐 Security

- **JWT Authentication:** All API requests require valid JWT tokens
- **Password Hashing:** User passwords are hashed using BCrypt
- **WebSocket Authentication:** Tokens are passed as query parameters
- **API Gateway Protection:** Centralized security enforcement

---

## 📁 Project Structure

```
messaging/
├── apiGateway/           # API Gateway service
├── chatHistoryService/   # Chat history & metadata service
├── messageDispatcher/    # Kafka → Redis bridge service
├── messageService/       # Message processing service
├── messagingService/     # WebSocket service
├── messenger-ui/         # React frontend
├── serviceRegistryService/ # Service discovery
├── userService/          # User management service
├── docker-compose.yml    # Container orchestration
├── cassandra-init.cql    # Cassandra schema
└── README.md             # This file
```

---

## 🧪 Testing

Run the end-to-end test script:

```bash
./test_flow.sh
```

This script tests:
- User registration
- User login
- Chat creation
- Message sending
- Message retrieval

---

## 🤖 About This Project

This project was developed using **vibe coding** - an AI-assisted software development approach where:

- **AI Partner:** Claude (Anthropic) assisted with architecture design, code implementation, and debugging
- **Human Creativity:** Design decisions, feature requirements, and overall vision were guided by human developers
- **Collaborative Process:** The code evolved through iterative conversations between human intent and AI capability

Vibe coding represents a new paradigm in software development where AI serves as an intelligent pair programmer, helping to translate ideas into working code while maintaining best practices and modern architectural patterns.

---

## 📄 License

This project is provided as-is for educational and demonstration purposes.

---

## 🙏 Acknowledgments

- Built with Spring Boot and the Spring ecosystem
- React and Vite for the modern frontend experience
- Apache Kafka and Cassandra for scalable messaging infrastructure
- Redis for real-time pub/sub and caching
- Docker for containerized deployment

---

*Made with ❤️ and AI*
