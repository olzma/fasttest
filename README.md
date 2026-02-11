<<<<<<< HEAD
# fasttest
fast test of an spring boot app
=======
# Spring Boot FastTest Application

A Spring Boot REST API application for managing software engineers with PostgreSQL database, fully containerized with Docker and deployable to Google Cloud Run via GitHub Actions.

---

## 🚀 Quick Start

### Local Development
```bash
# Start database
docker-compose -f docker-compose-db.yml up -d

# Start backend
docker-compose -f docker-compose-backend.yml up -d

# Test
curl http://localhost:8079/api/v1/engineers/dummy
```

### Deploy to GCP
```bash
# Go to GitHub Actions tab → Run workflow
# Or follow: DEPLOYMENT-CHECKLIST.md
```

---

## 📚 Documentation

- **[DEPLOYMENT-CHECKLIST.md](DEPLOYMENT-CHECKLIST.md)** - Step-by-step setup guide (START HERE!)
- **[GITHUB-ACTIONS-DEPLOYMENT.md](GITHUB-ACTIONS-DEPLOYMENT.md)** - Complete GitHub Actions setup
- **[QUICK-REFERENCE.md](QUICK-REFERENCE.md)** - Common commands
- **[DEPLOYMENT-SUMMARY.md](DEPLOYMENT-SUMMARY.md)** - Overview of deployment options
- **[SPRING-PROFILES-GUIDE.md](SPRING-PROFILES-GUIDE.md)** - Environment configuration
- **[gcp-deploy.ps1](gcp-deploy.ps1)** - Local deployment script

---

## Table of Contents
- [Features](#features)
- [Architecture](#architecture)
- [Docker Setup](#docker-setup)
- [API Endpoints](#api-endpoints)
- [Deployment](#deployment)
- [Exception Handling Flow](#exception-handling-flow)

---

## Features

- ✅ RESTful API for managing software engineers
- ✅ PostgreSQL database with JPA/Hibernate
- ✅ Docker containerization
- ✅ GitHub Actions CI/CD
- ✅ Google Cloud Run deployment
- ✅ Cloud SQL integration
- ✅ Exception handling with custom responses
- ✅ CORS configuration
- ✅ Multiple environment profiles (local/GCP)

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                Docker Network                       │
│               (app-network)                         │
│                                                     │
│  ┌──────────────────────┐  ┌──────────────────┐   │
│  │  Spring Boot App     │  │   PostgreSQL     │   │
│  │  Container           │  │   Container      │   │
│  │                      │  │                  │   │
│  │  - Port: 8079       │  │  - Port: 5332    │   │
│  │  - Image: Custom    │  │  - Image: postgres│   │
│  │  - Build: Dockerfile│  │  - Volume: db    │   │
│  └──────────┬───────────┘  └────────┬─────────┘   │
│             │                       │              │
│             └───────connects to─────┘              │
│                   (db:5432)                        │
└─────────────────────────────────────────────────────┘
         │                              │
    Port 8079                      Port 5332
         │                              │
    ┌────▼──────────────────────────────▼────┐
    │          Host Machine                  │
    └────────────────────────────────────────┘
```

### Prerequisites

- Docker Desktop installed and running
- Java 17 (optional, only needed for local development)
- Maven (optional, Docker build handles this)

### Quick Start

This project uses **separate Docker Compose files** for database and backend:
- `docker-compose-db.yml` - PostgreSQL database
- `docker-compose-backend.yml` - Spring Boot application
- `docker-compose.yml` - Combined (both services)

#### Option 1: Start Everything Together (Recommended for beginners)

```powershell
# Build and start both containers (app + database)
docker-compose up --build

# Or run in detached mode (background)
docker-compose up --build -d
```

This command will:
1. Build the Spring Boot application Docker image
2. Pull the PostgreSQL image (if not already downloaded)
3. Create a shared network (`app-network`)
4. Start the database container first
5. Wait for database health check to pass
6. Start the application container
7. Application connects to database

#### Option 2: Start Database and Backend Separately (Recommended for development)

**Step 1: Start Database First**
```powershell
# Start only the database
docker-compose -f docker-compose-db.yml up -d

# Wait for database to be healthy (check logs)
docker logs postgres-fasttest -f
```

**Step 2: Start Backend Application**
```powershell
# Build and start the backend
docker-compose -f docker-compose-backend.yml up --build -d

# Check logs
docker logs spring-boot-fasttest-app -f
```

**Why separate files?**
- ✅ Start database once, restart backend multiple times during development
- ✅ Keep database running while rebuilding app
- ✅ Better separation of concerns
- ✅ Easier to manage in CI/CD pipelines

#### Check Container Status

```powershell
# View running containers
docker ps

# Expected output:
# CONTAINER ID   IMAGE                        STATUS                    PORTS
# abc123         spring-boot-fasttest-app     Up 30 seconds (healthy)   0.0.0.0:8079->8079/tcp
# def456         postgres:latest              Up 45 seconds (healthy)   0.0.0.0:5332->5432/tcp
```

#### View Application Logs

```powershell
# View logs for Spring Boot app
docker logs spring-boot-fasttest-app -f

# View logs for PostgreSQL
docker logs postgres-fasttest -f

# View logs for all services (when using docker-compose.yml)
docker-compose logs -f

# View logs for specific service file
docker-compose -f docker-compose-backend.yml logs -f
docker-compose -f docker-compose-db.yml logs -f
```

#### Test the Application

```powershell
# Test health (Windows PowerShell)
Invoke-WebRequest http://localhost:8079/api/v1/engineers

# Or use curl (if available)
curl http://localhost:8079/api/v1/engineers
```

#### Stop Containers

**Stop All Services (using combined file):**
```powershell
# Stop and remove containers (keeps volumes/data)
docker-compose down

# Stop, remove containers, and delete volumes (wipes database)
docker-compose down -v
```

**Stop Services Separately:**
```powershell
# Stop only the backend (keeps database running)
docker-compose -f docker-compose-backend.yml down

# Stop only the database
docker-compose -f docker-compose-db.yml down

# Stop database and delete data
docker-compose -f docker-compose-db.yml down -v

# Stop both separately
docker-compose -f docker-compose-backend.yml down
docker-compose -f docker-compose-db.yml down -v
```

### Docker Files Explained

#### Docker Compose File Structure

This project provides **three Docker Compose files** for different use cases:

```
📦 spring-boot-fasttest/
├── docker-compose.yml           ← All-in-one (database + backend)
├── docker-compose-db.yml        ← Database only
├── docker-compose-backend.yml   ← Backend only
└── Dockerfile                   ← Backend image build instructions
```

**When to use which file:**

| File | Use Case | Services |
|------|----------|----------|
| `docker-compose.yml` | Quick setup, testing, production deployment | Database + Backend |
| `docker-compose-db.yml` | Keep database running during development | Database only |
| `docker-compose-backend.yml` | Restart backend frequently while developing | Backend only |

**Network Configuration:**
- `docker-compose.yml` creates the `app-network`
- `docker-compose-db.yml` creates the `app-network`
- `docker-compose-backend.yml` uses **external** `app-network` (created by db file)

**Important:** When using separate files, start database first to create the network!

#### 📄 `Dockerfile` (Spring Boot App)

```dockerfile
# Two-stage build:
# Stage 1: Build the JAR file using Maven
# Stage 2: Create lightweight runtime image with just JRE
```

**Key Features:**
- Multi-stage build (reduces final image size)
- Uses Maven to compile and package
- Final image only contains JRE + JAR (no build tools)
- Exposes port 8079
- Uses `docker` Spring profile

#### 📄 `docker-compose.yml` (All-in-One Orchestration)

Defines two services in one file for convenience:

**1. `db` (PostgreSQL Database)**
- Container name: `postgres-fasttest`
- Image: `postgres:latest`
- Port mapping: `5332:5432` (host:container)
- Persistent volume: `db` (data survives container restarts)
- Health check: Ensures database is ready before app starts

**2. `app` (Spring Boot Application)**
- Container name: `spring-boot-fasttest-app`
- Build: Uses `Dockerfile` in project root
- Port mapping: `8079:8079`
- Depends on: `db` service (waits for healthy status)
- Profile: `docker` (uses `application-docker.properties`)

#### 📄 `docker-compose-db.yml` (Database Only)

Contains only the PostgreSQL service. Use this when:
- You want to keep the database running while restarting the app
- Testing database migrations or schema changes
- Running the Spring Boot app locally (not in Docker)

**Key differences from all-in-one:**
- Only defines the `db` service
- Creates the `app-network` (not external)
- Includes volume definition

#### 📄 `docker-compose-backend.yml` (Backend Only)

Contains only the Spring Boot application service. Use this when:
- Database is already running via `docker-compose-db.yml`
- You're making frequent code changes and need to rebuild
- Testing different application configurations

**Key differences from all-in-one:**
- Only defines the `app` service
- Uses **external network** (`app-network` must exist)
- No `depends_on` clause (assumes DB is already running)

#### 📄 `application-docker.properties`

Docker-specific configuration:
- Database URL: `jdbc:postgresql://db:5432/fasttest_db`
  - `db` = service name from docker-compose (Docker DNS resolution)
  - `5432` = internal container port
- All other settings same as local development

#### 📄 `.dockerignore`

Prevents copying unnecessary files to Docker image:
- `target/` (build artifacts)
- `.idea/` (IDE files)
- `.git/` (version control)

### Database Connection Details

| Environment | Host      | Port | URL                                         |
|-------------|-----------|------|---------------------------------------------|
| **Docker**  | `db`      | 5432 | `jdbc:postgresql://db:5432/fasttest_db`     |
| **Local**   | localhost | 5332 | `jdbc:postgresql://localhost:5332/fasttest_db` |

**Why Different?**
- Inside Docker network: Containers use service names (`db`)
- From host machine: Use `localhost:5332` (port mapping)

### Common Docker Commands

```powershell
# Build without cache (fresh build)
docker-compose build --no-cache

# Rebuild just the app container
docker-compose up --build app

# Execute commands inside containers
docker exec -it spring-boot-fasttest-app sh      # App container shell
docker exec -it postgres-fasttest bash            # Database container shell

# Connect to PostgreSQL database
docker exec -it postgres-fasttest psql -U fasttest -d fasttest_db

# View database tables
docker exec -it postgres-fasttest psql -U fasttest -d fasttest_db -c "\dt"

# Check resource usage
docker stats

# Remove all stopped containers
docker container prune

# Remove unused images
docker image prune
```

### Development Workflow

**Option 1: Full Docker Development**
```powershell
# Make code changes → Rebuild → Restart
docker-compose up --build
```

**Option 2: Hybrid (Local App + Docker DB)**
```powershell
# Start only database
docker-compose up db

# Run app locally (use application.properties with localhost:5332)
mvn spring-boot:run
```

**Option 3: Separate Files Workflow (RECOMMENDED)** 🌟
```powershell
# Day 1: Initial setup
docker-compose -f docker-compose-db.yml up -d
docker-compose -f docker-compose-backend.yml up --build -d

# During development: Rebuild only backend when code changes
docker-compose -f docker-compose-backend.yml up --build -d

# Database keeps running and retains data!

# End of day: Stop everything
docker-compose -f docker-compose-backend.yml down
docker-compose -f docker-compose-db.yml down

# Next day: Restart (no rebuild needed unless code changed)
docker-compose -f docker-compose-db.yml up -d
docker-compose -f docker-compose-backend.yml up -d
```

**Common Development Tasks:**

```powershell
# Rebuild backend after code changes (DB keeps running)
docker-compose -f docker-compose-backend.yml down
docker-compose -f docker-compose-backend.yml up --build -d

# View real-time logs while developing
docker logs spring-boot-fasttest-app -f

# Reset database (wipes all data)
docker-compose -f docker-compose-db.yml down -v
docker-compose -f docker-compose-db.yml up -d

# Access database shell
docker exec -it postgres-fasttest psql -U fasttest -d fasttest_db

# Check what's in the database
docker exec -it postgres-fasttest psql -U fasttest -d fasttest_db -c "SELECT * FROM software_engineer;"
```

### Troubleshooting

#### Container Won't Start

```powershell
# Check logs for errors
docker logs spring-boot-fasttest-app
docker logs postgres-fasttest

# Ensure no port conflicts
netstat -ano | findstr "8079"
netstat -ano | findstr "5332"
```

#### Database Connection Issues

```powershell
# Verify database is healthy
docker exec -it postgres-fasttest pg_isready -U fasttest

# Check if app can reach database
docker exec -it spring-boot-fasttest-app ping db
```

#### Rebuild Everything

```powershell
# Nuclear option: Remove everything and start fresh
docker-compose down -v
docker-compose build --no-cache
docker-compose up
```

### Development Workflow

**Option 1: Full Docker Development**
```powershell
# Make code changes → Rebuild → Restart
docker-compose up --build
```

**Option 2: Hybrid (Local App + Docker DB)**
```powershell
# Start only database
docker-compose up db

# Run app locally (use application.properties with localhost:5332)
mvn spring-boot:run
```

### Production Considerations

For production deployment, consider:
- Use specific image versions (not `latest`)
- Add proper health checks
- Use secrets management (not plain text passwords)
- Configure resource limits (CPU/memory)
- Use production-grade database configuration
- Add monitoring and logging aggregation
- Use multi-replica setup for high availability

---

# Exception Handling Flow

This project uses Spring Boot's global exception handling mechanism via `@RestControllerAdvice` to manage errors consistently across all controllers.

## Complete Flow Diagram

### Success Case ✅

```
Client Request↓
┌─────────────────┐
│   Controller    │
└────────┬────────┘
↓
┌─────────────────┐
│    Service      │
└────────┬────────┘
↓
┌─────────────────┐
│   Repository    │
└────────┬────────┘
↓
┌─────────────────┐
│    Database     │
└────────┬────────┘
↓
Returns Entity
↓
┌─────────────────┐
│ Service creates │
│      DTO        │
└────────┬────────┘
↓
┌─────────────────┐
│ Controller      │
│ returns DTO     │
└────────┬────────┘
↓
Client (200 OK + JSON)
```

### Exception Case ❌

```
Client Request
↓
┌─────────────────┐
│   Controller    │
└────────┬────────┘
↓
┌─────────────────┐
│    Service      │
└────────┬────────┘
↓
┌─────────────────┐
│   Repository    │
└────────┬────────┘
↓
┌─────────────────┐
│    Database     │
└────────┬────────┘
↓
Returns Optional.empty()
↓
┌─────────────────────────┐
│ Service calls           │
│ .orElseThrow()          │
│ → throws Exception      │
└────────┬────────────────┘
↓
┌─────────────────────────┐
│ GlobalExceptionHandler  │
│ catches Exception       │
└────────┬────────────────┘
↓
┌─────────────────────────┐
│ Returns                 │
│ ResponseEntity          │
│ <ErrorResponse>         │
└────────┬────────────────┘
↓
Client (404 + ErrorResponse JSON)
```

## Detailed Flow with Code

### 1. Controller Layer (Entry Point)

```java
@RestController
@RequestMapping("/api/v1/engineers")
public class SoftwareEngineerController {

    @GetMapping("/{id}")
    public SoftwareEngineerDTO getById(@PathVariable Integer id) {
        return service.getSoftwareEngineerById(id);
        // ↑ No try-catch needed - exceptions bubble up automatically
    }
}
```

### 2. Service Layer (Business Logic + Exception Trigger)

```java
@Service
public class SoftwareEngineerService {

    public SoftwareEngineerDTO getSoftwareEngineerById(Integer id) {
        // Repository returns Optional<SoftwareEngineer>
        SoftwareEngineer engineer = softwareEngineerRepository.findById(id)
            .orElseThrow(() -> new EngineerNotFoundException(id));
            // ↑ EXCEPTION THROWN HERE if entity not found

        // If entity exists, convert to DTO
        return new SoftwareEngineerDTO(engineer);
    }
}
```

### 3. Repository Layer (Database Access)

```java
public interface SoftwareEngineerRepository extends JpaRepository<SoftwareEngineer, Integer> {
    // JPA provides findById() method that returns Optional<SoftwareEngineer>
    // Returns Optional.empty() when entity not found in database
}
```

### 4. Global Exception Handler (Error Handling)

```java
@RestControllerAdvice  // ← Makes this a global exception handler
public class GlobalExceptionHandler {

    @ExceptionHandler(EngineerNotFoundException.class)  // ← Catches this specific exception
    public ResponseEntity<ErrorResponse> handleEngineerNotFound(EngineerNotFoundException ex) {
        // Spring calls this method automatically - you never call it yourself
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            System.currentTimeMillis()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }
}
```

### 5. Custom Exception

```java
public class EngineerNotFoundException extends RuntimeException {
    public EngineerNotFoundException(Integer id) {
        super("Engineer not found with id: " + id);
    }
}
```

### 6. Error Response DTO

```java
public class ErrorResponse {
    private int status;
    private String message;
    private long timestamp;

    // Constructor, getters, setters
}
```

## How Spring Wires It Together

```
┌─────────────────────────────────────────────────────┐
│ @SpringBootApplication                              │
│ (Component Scanning Enabled)                        │
└─────────────────┬───────────────────────────────────┘
                  ↓
    ┌─────────────────────────────────────┐
    │ Spring scans for annotations:       │
    │  • @RestControllerAdvice            │
    │  • @ExceptionHandler                │
    └─────────────────┬───────────────────┘
                      ↓┌─────────────────────────────────────┐
    │ Registers GlobalExceptionHandler    │
    │ and all its @ExceptionHandler       │
    │ methods internally                  │
    └─────────────────┬───────────────────┘
                      ↓
    ┌─────────────────────────────────────┐
    │ When exception occurs:              │
    │  1. Spring intercepts it            │
    │  2. Matches to @ExceptionHandler    │
    │  3. Calls handler method            │
    │  4. Returns ResponseEntity          │
    └─────────────────────────────────────┘
```

## Why This Works Without Try-Catch

The `@RestControllerAdvice` pattern means:

1. **You don't call the handler** - Spring does it automatically
2. **Exceptions bubble up** from Service → Spring Framework
3. **Spring routes exceptions** to matching `@ExceptionHandler`
4. **Handler returns response** - Spring sends it to client

**Analogy:** It's like JavaScript event listeners:

```javascript
// You register the handler:
button.addEventListener('click', handleClick);

// Browser calls handleClick() automatically on click
// You never call handleClick() yourself
```

Same with Spring - you register handlers with `@ExceptionHandler`, Spring calls them automatically when exceptions occur.

## Error Response Format

When an exception occurs, clients receive:

**HTTP Status:** `404 Not Found`

**Response Body:**
```json
{
  "status": 404,
  "message": "Engineer not found with id: 5",
  "timestamp": 1704067200000
}
```

## Testing Exception Handling

```bash
# Test with non-existent ID
GET http://localhost:8079/api/v1/engineers/999

# Expected Response:
# HTTP/1.1 404 Not Found
# Content-Type: application/json
#
# {
#   "status": 404,
#   "message": "Engineer not found with id: 999",
#   "timestamp": 1704067200000
# }
```

## Benefits of This Approach

1. ✅ **Centralized Error Handling** - All exceptions in one place
2. ✅ **Clean Controllers** - No repetitive try-catch blocks
3. ✅ **Consistent Error Format** - All errors follow same JSON structure
4. ✅ **Easy Maintenance** - Add handlers without touching controllers
5. ✅ **Separation of Concerns** - Controllers route, handlers handle errors
6. ✅ **Framework-Managed** - Spring handles exception routing automatically

## Common Misconceptions

❌ **Wrong:** "I need to catch exceptions in controllers"
✅ **Correct:** Let exceptions bubble up to `GlobalExceptionHandler`

❌ **Wrong:** "I need to call `handleEngineerNotFound()` myself"
✅ **Correct:** Spring calls it automatically via `@ExceptionHandler`

❌ **Wrong:** "Repository throws the exception"
✅ **Correct:** Repository returns `Optional.empty()`, Service throws exception via `.orElseThrow()`

```


## The Flow for INSERT engineer ✅

```
1. Client sends POST request with JSON
   ↓
2. Spring converts JSON → SoftwareEngineerDTO (using Jackson)
   ↓
3. Controller receives the DTO
   ↓
4. Service receives the DTO
   ↓
5. Service creates new SoftwareEngineer entity (defensive copy)
   ↓
6. Service copies fields from DTO → Entity
   engineer.setName(engineerDTO.getName())
   engineer.setTechStack(engineerDTO.getTechStack())
   ↓
7. Repository saves Entity to database
   softwareEngineerRepository.save(engineer)
   ↓
8. Database generates ID and returns saved Entity
   ↓
9. Service converts Entity back to DTO
   ↓
10. Controller returns DTO to client (201 CREATED)
```

## Why This Pattern? (DTO → Entity → DTO)

**DTO (Data Transfer Object)** acts as a "defensive copy" because:

1. **Security** - You control what data enters/exits your system
2. **Decoupling** - Your API contract (DTO) is separate from database structure (Entity)
3. **Validation** - You can validate DTOs before converting to Entities
4. **Flexibility** - You can change database schema without breaking API

## Visual Representation

```
┌─────────────────┐
│  Client JSON    │
│  {             │
│   "name": "X",  │
│   "tech": "Y"   │
│  }              │
└────────┬────────┘
↓ Spring Jackson converts
┌─────────────────┐
│ SoftwareEngin-  │ ← Defensive Copy #1
│ eerDTO          │   (Request DTO)
│ name = "X"      │
│ tech = "Y"      │
└────────┬────────┘
↓ Service creates Entity
┌─────────────────┐
│ SoftwareEngin-  │ ← Defensive Copy #2
│ eer (Entity)    │   (Database Entity)
│ name = "X"      │
│ tech = "Y"      │
│ id = null       │
└────────┬────────┘
↓ save() to database
┌─────────────────┐
│ Database Row    │
│ id = 3          │ ← Database generates ID
│ name = "X"      │
│ tech = "Y"      │
└────────┬────────┘
↓ Returns saved Entity
┌─────────────────┐
│ SoftwareEngin-  │
│ eer (saved)     │
│ id = 3          │ ← Now has ID!
│ name = "X"      │
│ tech = "Y"      │
└────────┬────────┘
↓ Convert back to DTO
┌─────────────────┐
│ SoftwareEngin-  │ ← Defensive Copy #3
│ eerDTO          │   (Response DTO)
│ id = 3          │
│ name = "X"      │
│ tech = "Y"      │
└────────┬────────┘
↓Client receives JSON
```

## Key Point About `save()`

```java
SoftwareEngineer saved = softwareEngineerRepository.save(engineer);
```

- **Input:** `engineer` (Entity with `id = null`)
- **Database:** Inserts row and generates ID
- **Output:** `saved` (Entity with `id = 3`)

The **defensive copy pattern** protects your application because:
- Client can't directly modify your database entities
- You control which fields are exposed
- You can transform/validate data before persistence

You understood it perfectly! 🎯

---

## API Endpoints

Base URL (local): `http://localhost:8079/api/v1/engineers`
Base URL (GCP): `https://your-service-url.run.app/api/v1/engineers`

### 1. Get All Engineers
```http
GET /api/v1/engineers
```

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "John Doe",
    "techStack": "Java, Spring Boot"
  },
  {
    "id": 2,
    "name": "Jane Smith",
    "techStack": "Python, Django"
  }
]
```

**Example:**
```bash
curl http://localhost:8079/api/v1/engineers
```

---

### 2. Get Engineer by ID
```http
GET /api/v1/engineers/{id}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "name": "John Doe",
  "techStack": "Java, Spring Boot"
}
```

**Response (404 Not Found):**
```json
{
  "message": "Engineer with id 999 not found",
  "status": 404
}
```

**Example:**
```bash
curl http://localhost:8079/api/v1/engineers/1
```

---

### 3. Get Dummy Engineers (Test Data)
```http
GET /api/v1/engineers/dummy
```

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "James",
    "techStack": "Java, Spring"
  },
  {
    "id": 2,
    "name": "Jamila",
    "techStack": "Python, Java"
  },
  {
    "id": 3,
    "name": "Charlie",
    "techStack": "JavaScript"
  }
]
```

**Example:**
```bash
curl http://localhost:8079/api/v1/engineers/dummy
```

---

### 4. Create Engineer
```http
POST /api/v1/engineers
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "John Doe",
  "techStack": "Java, Spring Boot, Docker"
}
```

**Response (201 Created):**
```json
{
  "id": 4,
  "name": "John Doe",
  "techStack": "Java, Spring Boot, Docker"
}
```

**Example:**
```bash
curl -X POST http://localhost:8079/api/v1/engineers \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","techStack":"Java, Spring Boot"}'
```

---

### 5. Delete Engineer by ID ✨ NEW
```http
DELETE /api/v1/engineers/{id}
```

**Response (204 No Content):**
```
(Empty body)
```

**Response (404 Not Found):**
```json
{
  "message": "Engineer with id 999 not found",
  "status": 404
}
```

**Example:**
```bash
curl -X DELETE http://localhost:8079/api/v1/engineers/1
```

---

### 6. Delete All Engineers ✨ NEW
```http
DELETE /api/v1/engineers
```

**Response (204 No Content):**
```
(Empty body)
```

**Example:**
```bash
curl -X DELETE http://localhost:8079/api/v1/engineers
```

---

### Testing API with PowerShell

```powershell
# Get all engineers
Invoke-WebRequest -Uri http://localhost:8079/api/v1/engineers -UseBasicParsing

# Create engineer
$body = @{name="John Doe"; techStack="Java"} | ConvertTo-Json
Invoke-WebRequest -Uri http://localhost:8079/api/v1/engineers -Method POST -Body $body -ContentType "application/json" -UseBasicParsing

# Delete engineer
Invoke-WebRequest -Uri http://localhost:8079/api/v1/engineers/1 -Method DELETE -UseBasicParsing

# Delete all
Invoke-WebRequest -Uri http://localhost:8079/api/v1/engineers -Method DELETE -UseBasicParsing
```

---

## Deployment

### Local Deployment (Docker)
See [Docker Setup](#docker-setup) section above.

### Google Cloud Platform Deployment

#### Prerequisites
1. GCP project with billing enabled
2. Cloud SQL PostgreSQL instance
3. Artifact Registry repository
4. Service account with permissions
5. GitHub secrets configured

#### Quick Deploy
1. Go to GitHub repository
2. Click **Actions** tab
3. Select **Deploy to Google Cloud Run**
4. Click **Run workflow**
5. Wait ~5-7 minutes
6. Get service URL from output

#### First-Time Setup
Follow **[DEPLOYMENT-CHECKLIST.md](DEPLOYMENT-CHECKLIST.md)** for complete step-by-step instructions.

#### Deployment Options

| Method | Trigger | Use Case |
|--------|---------|----------|
| **GitHub Actions (Manual)** | Click "Run workflow" | Current setup, controlled deploys |
| **GitHub Actions (Auto)** | Push to main | Uncomment trigger in workflow |
| **Local Script** | Run `gcp-deploy.ps1` | Quick test from local machine |

#### Environment Profiles

| Profile | File | Environment | Database |
|---------|------|-------------|----------|
| `docker` | `application-docker.properties` | Local development | Local PostgreSQL container |
| `gcp` | `application-gcp.properties` | Production (GCP) | Cloud SQL with proxy |

---

>>>>>>> 43b92cf (first code pushed)
