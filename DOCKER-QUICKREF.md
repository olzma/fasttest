# Docker Quick Reference

## Quick Commands

### Start Everything (Combined File)
```powershell
docker-compose up --build -d
```

### Start Separately (Recommended for Development)
```powershell
# Start database first
docker-compose -f docker-compose-db.yml up -d

# Start backend
docker-compose -f docker-compose-backend.yml up --build -d
```

### Stop Everything
```powershell
# Stop all with combined file
docker-compose down

# Stop separately
docker-compose -f docker-compose-backend.yml down
docker-compose -f docker-compose-db.yml down
```

### Rebuild Backend Only (Keep DB Running)
```powershell
docker-compose -f docker-compose-backend.yml down
docker-compose -f docker-compose-backend.yml up --build -d
```

### View Logs
```powershell
docker logs spring-boot-fasttest-app -f
docker logs postgres-fasttest -f
```

### Database Access
```powershell
# Connect to database
docker exec -it postgres-fasttest psql -U fasttest -d fasttest_db

# Check tables
docker exec -it postgres-fasttest psql -U fasttest -d fasttest_db -c "\dt"

# Query data
docker exec -it postgres-fasttest psql -U fasttest -d fasttest_db -c "SELECT * FROM software_engineer;"
```

### Reset Database (Delete All Data)
```powershell
docker-compose -f docker-compose-db.yml down -v
docker-compose -f docker-compose-db.yml up -d
```

### Clean Up
```powershell
# Stop and remove everything
docker-compose down -v

# Or separately
docker-compose -f docker-compose-backend.yml down
docker-compose -f docker-compose-db.yml down -v

# Remove unused images
docker image prune
```

## File Overview

| File | Purpose |
|------|---------|
| `docker-compose.yml` | All services together |
| `docker-compose-db.yml` | Database only |
| `docker-compose-backend.yml` | Backend only |
| `Dockerfile` | Backend build instructions |
| `application-docker.properties` | Docker environment config |

## Ports

- Backend: http://localhost:8079
- Database: localhost:5332 (external), db:5432 (internal)

## Typical Development Day

```powershell
# Morning: Start everything
docker-compose -f docker-compose-db.yml up -d
docker-compose -f docker-compose-backend.yml up -d

# During development: Rebuild after code changes
docker-compose -f docker-compose-backend.yml up --build -d

# Check logs
docker logs spring-boot-fasttest-app -f

# Evening: Stop everything
docker-compose -f docker-compose-backend.yml down
docker-compose -f docker-compose-db.yml down
```
