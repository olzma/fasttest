# Spring Boot + PostgreSQL + Cloud Run Deployment Reference

**Date**: February 12, 2026  
**Project**: fasttest - Spring Boot application with PostgreSQL on Google Cloud Run

---

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Local Development Setup](#local-development-setup)
3. [GCP Cloud SQL Configuration](#gcp-cloud-sql-configuration)
4. [Docker Configuration](#docker-configuration)
5. [GitHub Actions CI/CD](#github-actions-cicd)
6. [Cloud Run Deployment](#cloud-run-deployment)
7. [Troubleshooting Guide](#troubleshooting-guide)
8. [Key Learnings](#key-learnings)

---

## Architecture Overview

### Components
- **Backend**: Spring Boot 3.2.2 (Java 17)
- **Database**: PostgreSQL 18 on Cloud SQL
- **Container Registry**: Google Artifact Registry
- **Hosting**: Google Cloud Run (serverless)
- **CI/CD**: GitHub Actions

### Environment Strategy
- **Local**: Docker containers with `localhost` database connection
- **GCP**: Cloud Run with Cloud SQL Proxy socket connection

---

## Local Development Setup

### Database Configuration

**Local Environment** (`application.properties`):
```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:fasttest_db}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}
```

**Docker Compose for PostgreSQL** (`docker-compose-db.yml`):
```yaml
version: '3.8'
services:
  postgres-fasttest:
    image: postgres:18-alpine
    container_name: postgres-fasttest
    environment:
      POSTGRES_DB: fasttest_db
      POSTGRES_USER: fasttest_user
      POSTGRES_PASSWORD: SecurePassword123
    ports:
      - "5433:5432"  # External:Internal
    volumes:
      - postgres-data-fasttest:/var/lib/postgresql/data
    networks:
      - fasttest-network

volumes:
  postgres-data-fasttest:

networks:
  fasttest-network:
    driver: bridge
```

**Docker Compose for Backend** (`docker-compose-backend.yml`):
```yaml
version: '3.8'
services:
  spring-boot-fasttest:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: spring-boot-fasttest
    environment:
      DB_HOST: postgres-fasttest
      DB_PORT: 5432
      DB_NAME: fasttest_db
      DB_USER: fasttest_user
      DB_PASSWORD: SecurePassword123
    ports:
      - "8079:8080"  # Local:Container
    depends_on:
      - postgres-fasttest
    networks:
      - fasttest-network

networks:
  fasttest-network:
    external: true
```

### Running Locally

```bash
# 1. Start PostgreSQL
docker-compose -f docker-compose-db.yml up -d

# 2. Build and start backend
docker-compose -f docker-compose-backend.yml up -d --build

# 3. Check logs
docker logs -f spring-boot-fasttest

# 4. Test API
curl http://localhost:8079/v1/engineers
```

---

## GCP Cloud SQL Configuration

### Key Concepts

**Instance Connection Name Format**:
```
PROJECT_ID:REGION:INSTANCE_NAME
Example: fastesttest:europe-central2:fastesttest-postgres-inst
```

**Important Distinction**:
- **Cloud SQL Instance**: The PostgreSQL server (e.g., `fastesttest-postgres-inst`)
- **Database Name**: The database inside the instance (e.g., `fasttest-db`)

### Creating Cloud SQL Instance

```bash
# Create PostgreSQL instance
gcloud sql instances create fasttest-db-instance \
  --database-version=POSTGRES_18 \
  --tier=db-custom-2-8192 \
  --region=europe-central2 \
  --project=fastesttest

# Create database
gcloud sql databases create fasttest-db \
  --instance=fasttest-db-instance \
  --project=fastesttest

# Create user
gcloud sql users create fasttest \
  --instance=fasttest-db-instance \
  --password=YOUR_SECURE_PASSWORD \
  --project=fastesttest

# Get connection name
gcloud sql instances describe fasttest-db-instance \
  --project=fastesttest \
  --format="value(connectionName)"
```

### GCP Application Configuration

**Spring Boot Profile for GCP** (`application-gcp.properties`):
```properties
# Server configuration
spring.application.name=spring-boot-fasttest
server.port=${PORT:8080}

# Cloud SQL connection using Socket Factory
spring.datasource.url=jdbc:postgresql:///${SPRING_CLOUD_GCP_SQL_DATABASE_NAME:fasttest-db}?cloudSqlInstance=${SPRING_CLOUD_GCP_SQL_INSTANCE_CONNECTION_NAME}&socketFactory=com.google.cloud.sql.postgres.SocketFactory&enableIamAuth=false
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:fasttest}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

# Cloud SQL configuration
spring.cloud.gcp.sql.enabled=true
spring.cloud.gcp.sql.database-name=${SPRING_CLOUD_GCP_SQL_DATABASE_NAME:fasttest-db}
spring.cloud.gcp.sql.instance-connection-name=${SPRING_CLOUD_GCP_SQL_INSTANCE_CONNECTION_NAME}

# CRITICAL: Disable GCP services that cause gRPC crashes
spring.cloud.gcp.secretmanager.enabled=false
spring.cloud.gcp.pubsub.enabled=false
spring.cloud.gcp.storage.enabled=false
spring.cloud.gcp.logging.enabled=false
spring.cloud.gcp.trace.enabled=false
spring.cloud.gcp.config.enabled=false

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
```

### Required Maven Dependencies

```xml
<dependencies>
    <!-- Spring Cloud GCP SQL Starter -->
    <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>spring-cloud-gcp-starter-sql-postgresql</artifactId>
    </dependency>
    
    <!-- Cloud SQL Socket Factory for PostgreSQL 18 -->
    <dependency>
        <groupId>com.google.cloud.sql</groupId>
        <artifactId>postgres-socket-factory</artifactId>
        <version>1.20.1</version>
    </dependency>
    
    <!-- PostgreSQL JDBC Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.google.cloud</groupId>
            <artifactId>spring-cloud-gcp-dependencies</artifactId>
            <version>${spring-cloud-gcp.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## Docker Configuration

### Dockerfile (Multi-stage Build)

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Image Management

```bash
# Build image locally
docker build -t spring-boot-fasttest:local .

# Remove old images/containers
docker rm -f spring-boot-fasttest
docker rmi spring-boot-fasttest:old-tag

# Rebuild after code changes
docker-compose -f docker-compose-backend.yml up -d --build

# View logs
docker logs -f spring-boot-fasttest
```

---

## GitHub Actions CI/CD

### Required GitHub Secrets

Navigate to: `Settings → Secrets and variables → Actions`

| Secret Name | Description | Example Value |
|------------|-------------|---------------|
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | Workload Identity Federation provider | `projects/123456/locations/global/workloadIdentityPools/github-pool/providers/github-provider` |
| `GCP_SERVICE_ACCOUNT` | Service account email | `github-actions-ci@fastesttest.iam.gserviceaccount.com` |
| `GCP_DB_INSTANCE_CONNECTION_NAME` | Cloud SQL instance connection string | `fastesttest:europe-central2:fastesttest-postgres-inst` |
| `GCP_DB_PASSWORD` | Database password | `YourSecurePassword123` |

### Workload Identity Federation Setup

**Why**: Secure authentication without storing service account keys

```bash
# 1. Enable IAM Credentials API
gcloud services enable iamcredentials.googleapis.com --project=fastesttest

# 2. Create Workload Identity Pool
gcloud iam workload-identity-pools create "github-pool" \
  --project="fastesttest" \
  --location="global" \
  --display-name="GitHub Actions Pool"

# 3. Create Provider for GitHub
gcloud iam workload-identity-pools providers create-oidc "github-provider" \
  --project="fastesttest" \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --display-name="GitHub Provider" \
  --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository" \
  --issuer-uri="https://token.actions.githubusercontent.com"

# 4. Bind to Service Account
gcloud iam service-accounts add-iam-policy-binding github-actions-ci@fastesttest.iam.gserviceaccount.com \
  --project="fastesttest" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/attribute.repository/olzma/fasttest"

# 5. Get Provider ID (for GitHub secret)
gcloud iam workload-identity-pools providers describe "github-provider" \
  --project="fastesttest" \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --format="value(name)"
```

### Required IAM Permissions

```bash
# Service account needs these roles:
gcloud projects add-iam-policy-binding fastesttest \
  --member="serviceAccount:github-actions-ci@fastesttest.iam.gserviceaccount.com" \
  --role="roles/run.admin"

gcloud projects add-iam-policy-binding fastesttest \
  --member="serviceAccount:github-actions-ci@fastesttest.iam.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser"

gcloud projects add-iam-policy-binding fastesttest \
  --member="serviceAccount:github-actions-ci@fastesttest.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"

gcloud projects add-iam-policy-binding fastesttest \
  --member="serviceAccount:github-actions-ci@fastesttest.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"
```

### GitHub Actions Workflow

See `.github/workflows/deploy-to-gcp.yml` for complete workflow.

**Key Configuration**:
- Manual trigger via `workflow_dispatch`
- Builds Docker image with Maven
- Pushes to Artifact Registry with unique tags (git SHA + timestamp)
- Deploys to Cloud Run with Cloud SQL connection

---

## Cloud Run Deployment

### Optimal Configuration for Spring Boot

```bash
gcloud run deploy spring-boot-fasttest \
  --image europe-central2-docker.pkg.dev/fastesttest/fasttest-repo/spring-boot-fasttest:latest \
  --platform managed \
  --region europe-central2 \
  --allow-unauthenticated \
  --port 8080 \
  --min-instances 0 \
  --max-instances 10 \
  --memory 1Gi \               # Minimum for Spring Boot + GCP libs
  --cpu 2 \                    # Faster startup
  --timeout 600 \              # 10 minutes for startup
  --cpu-boost \                # Extra CPU during startup
  --set-env-vars "SPRING_PROFILES_ACTIVE=gcp" \
  --set-env-vars "SPRING_CLOUD_GCP_SQL_ENABLED=true" \
  --set-env-vars "SPRING_CLOUD_GCP_SQL_INSTANCE_CONNECTION_NAME=fastesttest:europe-central2:fastesttest-postgres-inst" \
  --set-env-vars "SPRING_CLOUD_GCP_SQL_DATABASE_NAME=fasttest-db" \
  --set-env-vars "SPRING_DATASOURCE_USERNAME=fasttest" \
  --set-env-vars "SPRING_DATASOURCE_PASSWORD=YourPassword" \
  --set-env-vars "SPRING_JPA_HIBERNATE_DDL_AUTO=update" \
  --set-env-vars "JAVA_TOOL_OPTIONS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
  --add-cloudsql-instances fastesttest:europe-central2:fastesttest-postgres-inst
```

### Manual Cloud Run Commands

```bash
# Update environment variables only
gcloud run services update spring-boot-fasttest \
  --region=europe-central2 \
  --project=fastesttest \
  --update-env-vars "KEY=VALUE"

# Restart service (trigger new revision)
gcloud run services update spring-boot-fasttest \
  --region=europe-central2 \
  --project=fastesttest \
  --max-instances=10

# View service details
gcloud run services describe spring-boot-fasttest \
  --region=europe-central2 \
  --project=fastesttest

# View logs
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=spring-boot-fasttest" \
  --project=fastesttest \
  --limit=50 \
  --format="value(timestamp,severity,textPayload)"
```

---

## Troubleshooting Guide

### Problem: Container Failed to Start

**Error**: "The user-provided container failed to start and listen on the port defined provided by the PORT=8080"

**Cause**: Application not starting within timeout or crashing

**Solutions**:
1. Check logs: `gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=spring-boot-fasttest" --project=fastesttest --limit=50`
2. Increase timeout: `--timeout 600`
3. Increase memory: `--memory 1Gi`
4. Add CPU boost: `--cpu-boost`

### Problem: gRPC Native Library Crash

**Error**: `SIGSEGV in libio_grpc_netty_shaded_netty_tcnative`

**Symptoms**:
```
# A fatal error has been detected by the Java Runtime Environment:
# SIGSEGV (0xb) at pc=0x00000000000204b6
# C  [libio_grpc_netty_shaded_netty_tcnative_linux_x86_64...so+0x2a154]
```

**Root Cause**: Google Cloud Secret Manager autoconfiguration initializing gRPC connections that crash

**Solution**: Disable Secret Manager in `application-gcp.properties`:
```properties
spring.cloud.gcp.secretmanager.enabled=false
spring.cloud.gcp.logging.enabled=false
spring.cloud.gcp.trace.enabled=false
```

### Problem: Database Connection Failed

**Error**: `The connection attempt failed`

**Checklist**:
1. ✅ Verify instance connection name format: `PROJECT:REGION:INSTANCE`
2. ✅ Check database exists: `gcloud sql databases list --instance=INSTANCE_NAME`
3. ✅ Verify user credentials match
4. ✅ Ensure `--add-cloudsql-instances` flag is set in Cloud Run deployment
5. ✅ Confirm service account has `roles/cloudsql.client` role
6. ✅ Check socket factory dependency version matches PostgreSQL version

**Debug Commands**:
```bash
# Test database connection
gcloud sql connect INSTANCE_NAME --user=USERNAME --database=DATABASE_NAME

# Verify connection name
gcloud sql instances describe INSTANCE_NAME --format="value(connectionName)"

# Check service account permissions
gcloud projects get-iam-policy fastesttest \
  --flatten="bindings[].members" \
  --filter="bindings.members:SERVICE_ACCOUNT_EMAIL"
```

### Problem: Maven Dependency Errors

**Error**: `Dependency 'com.google.cloud.sql:postgres-socket-factory:X.X.X' not found`

**Solution**: Use correct version from Maven Central:
- For PostgreSQL 18: Use version `1.20.1`
- For PostgreSQL 15-17: Use version `1.15.0` or higher

**Verify in pom.xml**:
```xml
<dependency>
    <groupId>com.google.cloud.sql</groupId>
    <artifactId>postgres-socket-factory</artifactId>
    <version>1.20.1</version>
</dependency>
```

### Problem: Docker Image Tag Immutability

**Error**: `manifest invalid: cannot update tag X. The repository has enabled tag immutability`

**Solution**: Add timestamp to make tags unique:
```yaml
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
IMAGE_TAG=registry/image:${{ github.sha }}-${TIMESTAMP}
```

### Problem: Empty Environment Variables in Cloud Run

**Symptom**: Deployment command shows `--set-env-vars "KEY="` (empty value)

**Cause**: GitHub secret not set or named incorrectly

**Solution**:
1. Verify secret exists in GitHub repo settings
2. Check exact secret name matches workflow reference
3. Re-create secret if needed (delete + create new)
4. Secret names are case-sensitive

---

## Key Learnings

### 1. Database Configuration Strategy

**Local vs Cloud**:
- **Local**: Use `DB_HOST` environment variable with `localhost`
- **Cloud**: Use Cloud SQL Socket Factory with instance connection name

**Pattern**:
```properties
# Local (application.properties)
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME}

# GCP (application-gcp.properties)
spring.datasource.url=jdbc:postgresql:///${DB_NAME}?cloudSqlInstance=${INSTANCE_CONNECTION_NAME}&socketFactory=com.google.cloud.sql.postgres.SocketFactory
```

### 2. Port Mapping Clarity

**Local Docker**:
```yaml
ports:
  - "8079:8080"  # Host:Container
```
- Access from host: `http://localhost:8079`
- Container listens on: `8080`

**Cloud Run**:
```yaml
--port 8080  # Container port only
```
- Cloud Run provides external URL
- Internal port is always what container listens on

### 3. Cloud SQL Connection Name Components

**Format**: `PROJECT_ID:REGION:INSTANCE_NAME`

**What it is**:
- **Instance Name**: The PostgreSQL server
- **NOT** the database name inside the instance

**Usage**:
- `--add-cloudsql-instances`: Uses instance connection name
- `spring.datasource.url`: Uses database name in connection string

### 4. GitHub Secrets Verification

**Problem**: Cannot view secret values after saving

**Solution**: Add debug step temporarily:
```yaml
- name: Verify Secret
  run: |
    if [ -z "${{ secrets.SECRET_NAME }}" ]; then
      echo "ERROR: Secret is empty!"
      exit 1
    fi
    echo "Secret length: ${#SECRET_VALUE}"
  env:
    SECRET_VALUE: ${{ secrets.SECRET_NAME }}
```

### 5. Spring Boot Cloud Run Requirements

**Minimum Resources**:
- Memory: `1Gi` (not 512Mi - causes OOM)
- CPU: `2` (for reasonable startup time)
- Timeout: `600` seconds (10 minutes)
- CPU Boost: Enabled for faster startup

**Critical Environment Variable**:
```bash
JAVA_TOOL_OPTIONS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
```

### 6. GCP Service Auto-configuration Issues

**Problem**: Spring Cloud GCP autoconfigures many services by default

**Impact**: Secret Manager, Logging, Trace all try to initialize gRPC connections

**Solution**: Explicitly disable unused services:
```properties
spring.cloud.gcp.secretmanager.enabled=false
spring.cloud.gcp.pubsub.enabled=false
spring.cloud.gcp.storage.enabled=false
spring.cloud.gcp.logging.enabled=false
spring.cloud.gcp.trace.enabled=false
spring.cloud.gcp.config.enabled=false
```

### 7. Workload Identity Federation vs Service Account Keys

**Workload Identity Federation** (Recommended):
- ✅ No stored credentials
- ✅ Automatic token exchange
- ✅ Better security
- ❌ More complex setup

**Service Account Key** (Simple but less secure):
- ✅ Easy to set up
- ❌ Long-lived credentials
- ❌ Security risk if leaked

### 8. Maven Dependency Management

**Structure**:
```xml
<!-- Define versions (doesn't include dependencies) -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.google.cloud</groupId>
            <artifactId>spring-cloud-gcp-dependencies</artifactId>
            <version>${spring-cloud-gcp.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Actually include dependencies -->
<dependencies>
    <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>spring-cloud-gcp-starter-sql-postgresql</artifactId>
        <!-- Version inherited from dependencyManagement -->
    </dependency>
</dependencies>
```

---

## Quick Reference Commands

### Local Development
```bash
# Start database
docker-compose -f docker-compose-db.yml up -d

# Build and run backend
docker-compose -f docker-compose-backend.yml up -d --build

# View logs
docker logs -f spring-boot-fasttest

# Stop services
docker-compose -f docker-compose-backend.yml down
docker-compose -f docker-compose-db.yml down

# Clean rebuild
docker-compose -f docker-compose-backend.yml down
docker rmi spring-boot-fasttest
docker-compose -f docker-compose-backend.yml up -d --build
```

### GCP Deployment
```bash
# Deploy from GitHub Actions
# Go to: https://github.com/USERNAME/REPO/actions
# Click: Run workflow → Select production → Run

# Manual deployment
gcloud run deploy APP_NAME \
  --image IMAGE_URL \
  --region REGION \
  --allow-unauthenticated

# View service URL
gcloud run services describe APP_NAME \
  --region REGION \
  --format="value(status.url)"

# Update environment variables
gcloud run services update APP_NAME \
  --region REGION \
  --update-env-vars "KEY=VALUE"
```

### Database Management
```bash
# Create database
gcloud sql databases create DB_NAME --instance=INSTANCE_NAME

# Create user
gcloud sql users create USERNAME \
  --instance=INSTANCE_NAME \
  --password=PASSWORD

# Connect to database
gcloud sql connect INSTANCE_NAME \
  --user=USERNAME \
  --database=DB_NAME

# List databases
gcloud sql databases list --instance=INSTANCE_NAME

# List users
gcloud sql users list --instance=INSTANCE_NAME
```

### Debugging
```bash
# View Cloud Run logs
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=SERVICE_NAME" \
  --project=PROJECT_ID \
  --limit=50 \
  --format="value(timestamp,severity,textPayload)"

# Stream logs in real-time
gcloud beta logging tail "resource.type=cloud_run_revision AND resource.labels.service_name=SERVICE_NAME" \
  --project=PROJECT_ID

# Describe service
gcloud run services describe SERVICE_NAME \
  --region=REGION \
  --format=yaml

# List revisions
gcloud run revisions list \
  --service=SERVICE_NAME \
  --region=REGION
```

---

## Additional Resources

- [Spring Cloud GCP Documentation](https://spring.io/projects/spring-cloud-gcp)
- [Cloud SQL Connector for Java](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory)
- [Cloud Run Documentation](https://cloud.google.com/run/docs)
- [Workload Identity Federation](https://cloud.google.com/iam/docs/workload-identity-federation)

---

**Last Updated**: February 12, 2026  
**Project Repository**: https://github.com/olzma/fasttest
