# Spring Profiles Configuration Guide

This application uses different Spring profiles for different environments:

## Profiles

### 1. **docker** (Local Development)
- **File:** `application-docker.properties`
- **Use case:** Running locally with Docker Compose
- **GCP features:** ALL DISABLED (Secret Manager, Pub/Sub, Storage, etc.)
- **Database:** Local PostgreSQL container (fasttest-db)
- **Activation:** Set `SPRING_PROFILES_ACTIVE=docker` (already configured in docker-compose-backend.yml)

### 2. **gcp** (Google Cloud Platform)
- **File:** `application-gcp.properties`
- **Use case:** Deployed to Google Cloud Platform (Cloud Run, GKE, App Engine, etc.)
- **GCP features:** ENABLED (Cloud SQL, Secret Manager, Logging, Trace)
- **Database:** Cloud SQL PostgreSQL instance
- **Activation:** Set `SPRING_PROFILES_ACTIVE=gcp` in your GCP deployment

## How to Deploy to GCP

### Option 1: Using Cloud Run

```bash
# Build and push image to Artifact Registry
docker build -t <REGION>-docker.pkg.dev/<PROJECT_ID>/fasttest/backend:latest .
docker push <REGION>-docker.pkg.dev/<PROJECT_ID>/fasttest/backend:latest

# Deploy to Cloud Run with 'gcp' profile
gcloud run deploy fasttest-backend \
  --image=<REGION>-docker.pkg.dev/<PROJECT_ID>/fasttest/backend:latest \
  --platform=managed \
  --region=<REGION> \
  --set-env-vars="SPRING_PROFILES_ACTIVE=gcp" \
  --set-env-vars="CLOUD_SQL_CONNECTION_NAME=<PROJECT_ID>:<REGION>:<INSTANCE_NAME>" \
  --set-env-vars="DB_NAME=fasttest_db" \
  --set-env-vars="DB_USER=fasttest" \
  --set-secrets="DB_PASS=db-password:latest" \
  --add-cloudsql-instances=<PROJECT_ID>:<REGION>:<INSTANCE_NAME>
```

### Option 2: Using GKE (Kubernetes)

Create a deployment YAML with environment variables:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fasttest-backend
spec:
  replicas: 1
  selector:
    matchLabels:
      app: fasttest-backend
  template:
    metadata:
      labels:
        app: fasttest-backend
    spec:
      containers:
      - name: backend
        image: <REGION>-docker.pkg.dev/<PROJECT_ID>/fasttest/backend:latest
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "gcp"
        - name: CLOUD_SQL_CONNECTION_NAME
          value: "<PROJECT_ID>:<REGION>:<INSTANCE_NAME>"
        - name: DB_NAME
          value: "fasttest_db"
        - name: DB_USER
          value: "fasttest"
        - name: DB_PASS
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password
        ports:
        - containerPort: 8080
```

## Environment Variables for GCP

| Variable | Description | Example |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile to activate | `gcp` |
| `CLOUD_SQL_CONNECTION_NAME` | Full Cloud SQL connection name | `my-project:us-central1:my-db` |
| `DB_NAME` | Database name | `fasttest_db` |
| `DB_USER` | Database username | `fasttest` |
| `DB_PASS` | Database password (use Secret Manager!) | (secret) |

## Database Configuration Differences

### Local (docker profile):
```properties
spring.datasource.url=jdbc:postgresql://fasttest-db:5432/fasttest_db
spring.cloud.gcp.sql.enabled=false
```

### GCP (gcp profile):
```properties
spring.datasource.url=jdbc:postgresql:///fasttest_db
spring.cloud.gcp.sql.enabled=true
spring.cloud.gcp.sql.instance-connection-name=${CLOUD_SQL_CONNECTION_NAME}
```

**Note:** GCP automatically handles the Cloud SQL connection through the Cloud SQL Proxy when `spring.cloud.gcp.sql.enabled=true`. You don't specify the host/port in the JDBC URL.

## Testing Locally

```bash
# Local development with Docker
docker-compose -f docker-compose-db.yml up -d
docker-compose -f docker-compose-backend.yml up -d

# Check it's using 'docker' profile
docker logs fasttest-backend | grep "profile"
# Should show: "The following 1 profile is active: 'docker'"
```

## Testing the GCP Profile Locally

If you want to test the GCP profile locally (requires GCP credentials):

```bash
# Set up Application Default Credentials
gcloud auth application-default login

# Run with gcp profile
export SPRING_PROFILES_ACTIVE=gcp
export CLOUD_SQL_CONNECTION_NAME=your-project:region:instance
export DB_NAME=fasttest_db
export DB_USER=fasttest
export DB_PASS=your-password

./mvnw spring-boot:run
```

## Important Notes

1. **Never commit secrets** to Git (passwords, API keys, etc.)
2. **Use Secret Manager** in GCP to store sensitive data
3. **The docker profile is for local development only** - it disables all GCP services
4. **The gcp profile is for production** - it enables Cloud SQL, logging, and tracing
5. **DDL auto mode differs**: `create-drop` for local, `update` for GCP (preserves data)
