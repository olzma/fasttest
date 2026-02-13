# 🚀 Quick Start - GCP Setup Simplificat (Public IP)

> **Pentru Development/Testing - Setup în 30 minute**

---

## Prezentare

Acest ghid descrie setup-ul **simplificat** pe care îl ai tu acum:
- ✅ Cloud SQL cu **Public IP** (fără VPC)
- ✅ Cloud Run **fără VPC Connector**
- ✅ Perfect pentru **development/testing**
- ✅ Cost: **€186-314/lună** (economisești €8/lună vs Private IP)

**⚠️ Pentru production cu date sensibile:** Migrează la Private IP (vezi [GCP-INFRASTRUCTURE-SETUP.md](GCP-INFRASTRUCTURE-SETUP.md))

---

## Setup Complet în 5 Pași

### 1. Creează Cloud SQL Instance (cu Public IP)

```bash
# Autentificare GCP
gcloud auth login
gcloud config set project YOUR_PROJECT_ID

# Creează instanță PostgreSQL cu PUBLIC IP
gcloud sql instances create app-postgres \
    --database-version=POSTGRES_15 \
    --tier=db-custom-2-7680 \
    --region=europe-west1 \
    --assign-ip \
    --backup-start-time=03:00 \
    --enable-point-in-time-recovery \
    --availability-type=ZONAL

# ⏰ Timp: ~5-10 minute
```

### 2. Configurează Cloud SQL

```bash
# Setează password postgres
gcloud sql users set-password postgres \
    --instance=app-postgres \
    --password=$(openssl rand -base64 32)
# ⚠️ Salvează parola generată!

# Obține Public IP
SQL_IP=$(gcloud sql instances describe app-postgres \
    --format="value(ipAddresses[0].ipAddress)")
echo "Cloud SQL Public IP: $SQL_IP"

# Permite conexiuni de oriunde (doar pentru testing!)
gcloud sql instances patch app-postgres \
    --authorized-networks=0.0.0.0/0

# ⚠️ ATENȚIE: 0.0.0.0/0 = orice IP poate accesa!
# Pentru production: whitelist doar IP-urile Cloud Run
```

### 3. Creează Databases

```bash
# Database MASTER pentru tenant registry
gcloud sql databases create tenant_registry \
    --instance=app-postgres

# Database pentru primul tenant (test)
gcloud sql databases create tenant_test123_salon_demo \
    --instance=app-postgres
```

### 4. Deploy Spring Boot pe Cloud Run

```bash
# Build Docker image
docker build -t gcr.io/YOUR_PROJECT_ID/app-backend:latest .

# Push la Container Registry
docker push gcr.io/YOUR_PROJECT_ID/app-backend:latest

# Deploy pe Cloud Run (FĂRĂ VPC Connector!)
gcloud run deploy app-backend \
    --image=gcr.io/YOUR_PROJECT_ID/app-backend:latest \
    --region=europe-west1 \
    --platform=managed \
    --allow-unauthenticated \
    --memory=4Gi \
    --cpu=2 \
    --min-instances=0 \
    --max-instances=10 \
    --timeout=300 \
    --set-env-vars="SPRING_PROFILES_ACTIVE=gcp,DB_HOST=$SQL_IP,DB_PORT=5432,DB_NAME=tenant_registry,DB_USER=postgres"

# ⏰ Timp: ~2-3 minute

# Obține URL-ul serviciului
BACKEND_URL=$(gcloud run services describe app-backend \
    --region=europe-west1 \
    --format="value(status.url)")
echo "Backend URL: $BACKEND_URL"
```

### 5. Testare Conexiune

```bash
# Test health endpoint
curl $BACKEND_URL/api/health
# Expected: {"status":"UP"}

# Test database connection
curl $BACKEND_URL/actuator/health/db
# Expected: {"status":"UP","details":{"database":"PostgreSQL"}}
```

---

## Configurare Spring Boot

### application-gcp.properties

```properties
# Database connection (Public IP)
spring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=require
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}

# HikariCP connection pool
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000

# SSL required pentru Public IP
spring.datasource.hikari.data-source-properties.ssl=true
spring.datasource.hikari.data-source-properties.sslmode=require

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

### Dockerfile

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

EXPOSE 8080
ENTRYPOINT ["java", "-Xmx3g", "-jar", "app.jar"]
```

---

## Troubleshooting

### ❌ Eroare: "Connection refused"

**Cauză:** Cloud SQL nu permite conexiuni de la IP-ul tău

**Fix:**
```bash
# Whitelist toate IP-urile (doar pentru testing!)
gcloud sql instances patch app-postgres \
    --authorized-networks=0.0.0.0/0

# SAU whitelist doar IP-ul tău
MY_IP=$(curl -s ifconfig.me)
gcloud sql instances patch app-postgres \
    --authorized-networks=$MY_IP/32
```

### ❌ Eroare: "SSL connection required"

**Cauză:** PostgreSQL cere SSL, dar aplicația nu e configurată

**Fix în application-gcp.properties:**
```properties
spring.datasource.url=jdbc:postgresql://IP:5432/DB?sslmode=require
```

### ❌ Eroare: "Cloud Run timeout"

**Cauză:** Cloud Run nu poate ajunge la Cloud SQL (network issue)

**Verificare:**
```bash
# Test conexiune direct la Cloud SQL
gcloud sql connect app-postgres --user=postgres --database=tenant_registry

# Dacă funcționează local → problema e la Cloud Run permissions
```

### ❌ Eroare: "Too many connections"

**Cauză:** HikariCP pool exhausted

**Fix:**
```properties
# Reduce pool size per instanță
spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.minimum-idle=1
```

---

## Securitate

### ✅ Ce Este Securizat:

- ✅ Conexiuni SSL/TLS obligatorii
- ✅ Parole PostgreSQL puternice (32 caractere)
- ✅ Backups automate (retention 7 zile)
- ✅ Point-in-time recovery enabled

### ⚠️ Ce NU Este Securizat (Public IP):

- ⚠️ Cloud SQL expus pe internet
- ⚠️ Authorized networks = 0.0.0.0/0 (orice IP poate încerca să se conecteze)
- ⚠️ NU recomandat pentru production cu date sensibile

### 🔒 Recomandări:

1. **Pentru dev/test:** Setup actual OK
2. **Pentru production:** Migrează la Private IP + VPC
3. **Whitelist specific:** Înlocuiește `0.0.0.0/0` cu IP-uri Cloud Run specifice
4. **Rotate passwords:** La fiecare 90 zile

---

## Monitoring

### Cloud SQL Metrics

```bash
# CPU usage
gcloud sql operations list --instance=app-postgres --limit=10

# Connection count
gcloud sql instances describe app-postgres \
    --format="value(settings.ipConfiguration.authorizedNetworks)"
```

### Cloud Run Metrics

```bash
# Logs în timp real
gcloud run services logs read app-backend \
    --region=europe-west1 \
    --limit=50 \
    --format="table(timestamp,textPayload)"

# Request count
gcloud monitoring time-series list \
    --filter='metric.type="run.googleapis.com/request_count"'
```

### Alerting (Opțional)

```bash
# Alert dacă Cloud Run are error rate >5%
gcloud alpha monitoring policies create \
    --notification-channels=CHANNEL_ID \
    --display-name="Cloud Run High Error Rate" \
    --condition-display-name="Error rate > 5%" \
    --condition-threshold-value=0.05 \
    --condition-threshold-duration=300s
```

---

## Costuri Estimate

| Serviciu | Configurație | Cost/Lună (€) |
|----------|-------------|---------------|
| Cloud SQL | db-custom-2-7680 (2 vCPU, 7.5GB RAM) | €80-120 |
| Cloud Run | 4GB RAM, 2 vCPU, avg 2 instances | €30-80 |
| Cloud Storage | Backups (50GB) | €2-5 |
| **TOTAL** | **Development setup** | **€112-205/lună** |

**Economii vs Private IP:** €8/lună (fără VPC Connector)

---

## Next Steps

### Imediat:
1. ✅ Testează aplicația: `curl $BACKEND_URL/api/health`
2. ✅ Verifică logs: `gcloud run services logs read app-backend`
3. ✅ Test database queries din aplicație

### În 1-2 săptămâni:
1. 🔧 Setup CI/CD (GitHub Actions pentru auto-deploy)
2. 🔧 Configure Cloud Logging & Monitoring
3. 🔧 Setup alerting pentru errors

### Când ajungi în production (3-6 luni):
1. 🔒 **Migrează la Private IP + VPC** (vezi [GCP-INFRASTRUCTURE-SETUP.md](GCP-INFRASTRUCTURE-SETUP.md) secțiunea 11)
2. 🔒 Enable Cloud Armor (DDoS protection)
3. 🔒 Audit logging
4. 🔒 Secrets în Secret Manager (nu env vars)

---

## Comenzi Utile

```bash
# Restart Cloud Run service
gcloud run services update app-backend --region=europe-west1

# Scale Cloud Run manual
gcloud run services update app-backend \
    --min-instances=2 \
    --max-instances=20 \
    --region=europe-west1

# Connect la Cloud SQL din local (cu gcloud)
gcloud sql connect app-postgres --user=postgres --database=tenant_registry

# Export database pentru backup manual
gcloud sql export sql app-postgres gs://your-bucket/backup-$(date +%Y%m%d).sql \
    --database=tenant_registry

# Delete tot (cleanup)
gcloud run services delete app-backend --region=europe-west1 --quiet
gcloud sql instances delete app-postgres --quiet
```

---

## Resurse

- **[GCP-INFRASTRUCTURE-SETUP.md](GCP-INFRASTRUCTURE-SETUP.md)** - Ghid complet (inclusiv Private IP)
- **[AUTHENTICATION-FLOW-EXPLAINED.md](AUTHENTICATION-FLOW-EXPLAINED.md)** - Flow autentificare
- **[MULTI-TENANT-ARCHITECTURE.md](MULTI-TENANT-ARCHITECTURE.md)** - Arhitectura completă

### Documentație GCP:
- [Cloud Run](https://cloud.google.com/run/docs)
- [Cloud SQL](https://cloud.google.com/sql/docs/postgres)
- [Connecting to Cloud SQL from Cloud Run](https://cloud.google.com/sql/docs/postgres/connect-run)

---

**Document Version:** 1.0  
**Last Updated:** Februarie 13, 2026  
**Author:** GitHub Copilot  

**💡 Acest setup este perfect pentru development/testing. Pentru production, vezi ghidul complet de migrare la Private IP în [GCP-INFRASTRUCTURE-SETUP.md](GCP-INFRASTRUCTURE-SETUP.md).**
