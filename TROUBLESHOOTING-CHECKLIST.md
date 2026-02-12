# Troubleshooting Checklist - Quick Reference

## 🔴 Application Won't Start Locally

### Symptom: Database connection refused
```
org.postgresql.util.PSQLException: Connection refused
```

**Checklist**:
- [ ] PostgreSQL container is running: `docker ps | grep postgres`
- [ ] Port mapping is correct: `5433:5432`
- [ ] Environment variables match:
  - `DB_HOST=postgres-fasttest` (in Docker network)
  - `DB_HOST=localhost` (from host machine)
- [ ] Network exists: `docker network ls | grep fasttest`
- [ ] Both containers on same network

**Fix**:
```bash
# Restart database
docker-compose -f docker-compose-db.yml down
docker-compose -f docker-compose-db.yml up -d

# Check logs
docker logs postgres-fasttest
```

---

## 🔴 Cloud Run Deployment Fails

### Symptom: Container failed to start within timeout

**Checklist**:
- [ ] Memory at least 1Gi: `--memory 1Gi`
- [ ] CPU at least 2: `--cpu 2`
- [ ] Timeout at least 600s: `--timeout 600`
- [ ] CPU boost enabled: `--cpu-boost`
- [ ] Profile set: `SPRING_PROFILES_ACTIVE=gcp`

**Check Logs**:
```bash
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=spring-boot-fastesttest" --project=fastesttest --limit=50 --format="value(timestamp,severity,textPayload)"
```

### Symptom: gRPC native library crash
```
SIGSEGV in libio_grpc_netty_shaded_netty_tcnative
```

**Fix**: Disable GCP services in `application-gcp.properties`:
```properties
spring.cloud.gcp.secretmanager.enabled=false
spring.cloud.gcp.logging.enabled=false
spring.cloud.gcp.trace.enabled=false
```

---

## 🔴 Database Connection Issues on Cloud Run

### Symptom: Connection attempt failed

**Checklist**:
- [ ] Instance connection name correct: `PROJECT:REGION:INSTANCE`
  ```bash
  gcloud sql instances describe INSTANCE_NAME --format="value(connectionName)"
  ```
- [ ] Database exists:
  ```bash
  gcloud sql databases list --instance=INSTANCE_NAME
  ```
- [ ] User credentials correct:
  ```bash
  gcloud sql users list --instance=INSTANCE_NAME
  ```
- [ ] Cloud SQL instance added to deployment:
  ```bash
  --add-cloudsql-instances PROJECT:REGION:INSTANCE
  ```
- [ ] Service account has Cloud SQL Client role:
  ```bash
  gcloud projects add-iam-policy-binding PROJECT_ID \
    --member="serviceAccount:SERVICE_ACCOUNT_EMAIL" \
    --role="roles/cloudsql.client"
  ```
- [ ] Socket factory dependency version matches PostgreSQL:
  - PostgreSQL 18 → `postgres-socket-factory:1.20.1`

---

## 🔴 GitHub Actions Failures

### Symptom: Empty environment variables
```
--set-env-vars "SPRING_CLOUD_GCP_SQL_INSTANCE_CONNECTION_NAME="
```

**Checklist**:
- [ ] Secret exists in GitHub repo settings
- [ ] Secret name matches exactly (case-sensitive)
- [ ] Secret has no trailing spaces or newlines
- [ ] Re-create secret if unsure

**Verify**:
1. Go to `https://github.com/USER/REPO/settings/secrets/actions`
2. Check all required secrets exist:
   - `GCP_WORKLOAD_IDENTITY_PROVIDER`
   - `GCP_SERVICE_ACCOUNT`
   - `GCP_DB_INSTANCE_CONNECTION_NAME`
   - `GCP_DB_PASSWORD`

### Symptom: Permission denied errors
```
ERROR: Permission 'run.services.get' denied
```

**Fix**: Grant required roles:
```bash
# Cloud Run Admin
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:SERVICE_ACCOUNT_EMAIL" \
  --role="roles/run.admin"

# Service Account User
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:SERVICE_ACCOUNT_EMAIL" \
  --role="roles/iam.serviceAccountUser"

# Cloud SQL Client
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:SERVICE_ACCOUNT_EMAIL" \
  --role="roles/cloudsql.client"

# Artifact Registry Writer
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:SERVICE_ACCOUNT_EMAIL" \
  --role="roles/artifactregistry.writer"
```

### Symptom: Image tag immutability error
```
manifest invalid: cannot update tag X
```

**Fix**: Already implemented in workflow - uses unique tags:
```yaml
IMAGE_TAG=registry/image:${GITHUB_SHA}-$(date +%Y%m%d-%H%M%S)
```

---

## 🔴 Maven Build Failures

### Symptom: Dependency not found
```
Dependency 'com.google.cloud.sql:postgres-socket-factory:X.X.X' not found
```

**Fix**: Use correct version:
```xml
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>postgres-socket-factory</artifactId>
    <version>1.20.1</version>
</dependency>
```

### Symptom: Version missing error
```
'dependencies.dependency.version' for X is missing
```

**Fix**: Check `pom.xml` structure:
- Dependencies with `scope=import` → Must be in `<dependencyManagement>`
- Actual dependencies → Must be in `<dependencies>`

---

## 🔴 Docker Build Issues

### Symptom: Permission denied on mvnw
```
./mvnw: Permission denied
```

**Fix**: Add to Dockerfile:
```dockerfile
RUN chmod +x mvnw
```

### Symptom: Cached dependencies cause issues

**Fix**: Rebuild without cache:
```bash
docker-compose -f docker-compose-backend.yml build --no-cache
docker-compose -f docker-compose-backend.yml up -d
```

---

## 🟢 Verification Commands

### Check if everything is working

**Local**:
```bash
# Database
docker exec postgres-fasttest psql -U fasttest_user -d fasttest_db -c '\l'

# Backend
curl http://localhost:8079/v1/engineers

# Logs
docker logs spring-boot-fasttest --tail 50
```

**GCP**:
```bash
# Service is running
gcloud run services describe spring-boot-fasttest \
  --region=europe-central2 \
  --format="value(status.url,status.conditions)"

# Test endpoint
SERVICE_URL=$(gcloud run services describe spring-boot-fasttest --region=europe-central2 --format="value(status.url)")
curl $SERVICE_URL/v1/engineers

# Database connectivity
gcloud sql connect INSTANCE_NAME --user=USERNAME --database=DATABASE_NAME
```

---

## 🔧 Common Fixes

### Reset Everything Locally
```bash
# Stop and remove containers
docker-compose -f docker-compose-backend.yml down
docker-compose -f docker-compose-db.yml down

# Remove images
docker rmi spring-boot-fasttest postgres:18-alpine

# Remove volumes (WARNING: Deletes data)
docker volume rm fasttest-main_postgres-data-fasttest

# Remove network
docker network rm fasttest-network

# Start fresh
docker network create fasttest-network
docker-compose -f docker-compose-db.yml up -d
docker-compose -f docker-compose-backend.yml up -d --build
```

### Reset Cloud Run Deployment
```bash
# Delete service
gcloud run services delete spring-boot-fastesttest \
  --region=europe-central2 \
  --project=fastesttest

# Redeploy via GitHub Actions
# Go to: https://github.com/USER/REPO/actions
# Trigger: Deploy to Google Cloud Run
```

### Update Single Environment Variable
```bash
# Use --update-env-vars (preserves others)
gcloud run services update spring-boot-fastesttest \
  --region=europe-central2 \
  --update-env-vars "KEY=VALUE"

# NOT --set-env-vars (replaces all!)
```

---

## 📋 Pre-Deployment Checklist

Before deploying to Cloud Run:

- [ ] Code builds locally: `mvn clean package`
- [ ] Docker image builds: `docker build -t test .`
- [ ] Tests pass: `mvn test`
- [ ] Cloud SQL instance exists and running
- [ ] Database created in instance
- [ ] User created with correct password
- [ ] All GitHub secrets configured
- [ ] Service account has required roles
- [ ] Workload Identity Federation configured
- [ ] `application-gcp.properties` has correct settings
- [ ] GCP Secret Manager disabled in properties
- [ ] Socket factory version matches PostgreSQL version

---

## 🆘 Emergency Debugging

### Get detailed error from Cloud Run
```bash
# Get latest revision name
REVISION=$(gcloud run revisions list --service=spring-boot-fastesttest --region=europe-central2 --limit=1 --format="value(metadata.name)")

# Get logs from that revision
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.revision_name=$REVISION" \
  --project=fastesttest \
  --limit=100 \
  --format="value(timestamp,severity,textPayload,jsonPayload)" \
  --order=asc
```

### Test database connection from Cloud Shell
```bash
# Install PostgreSQL client in Cloud Shell
sudo apt-get install postgresql-client

# Connect via Cloud SQL Proxy
gcloud sql connect INSTANCE_NAME --user=USERNAME --database=DATABASE_NAME

# Test query
SELECT version();
```

### Check all environment variables in Cloud Run
```bash
gcloud run services describe spring-boot-fastesttest \
  --region=europe-central2 \
  --format="value(spec.template.spec.containers[0].env)"
```

---

## 💡 Pro Tips

1. **Always check logs first** - Most issues show clear errors in logs
2. **Verify instance vs database name** - Common confusion point
3. **Use --update-env-vars not --set-env-vars** - Preserves existing vars
4. **GitHub secrets are case-sensitive** - Double-check names
5. **Memory matters** - Spring Boot needs at least 1Gi on Cloud Run
6. **Disable unused GCP services** - Prevents gRPC crashes
7. **Use unique image tags** - Timestamp + git SHA prevents conflicts

---

**Quick Access**: Keep this file open when deploying!
