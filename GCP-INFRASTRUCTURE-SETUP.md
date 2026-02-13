# 🏗️ Infrastructura Google Cloud Platform - Setup Complet Multi-Tenant

> **Acest document explică în detaliu infrastructura GCP necesară pentru aplicația multi-tenant, de la DNS până la baza de date, cu setup pas cu pas.**

---

## 📋 Cuprins

1. [Prezentare Generală Infrastructură](#1-prezentare-generală-infrastructură)
2. [DNS & Subdomain Routing](#2-dns--subdomain-routing)
3. [Cloud Load Balancer & SSL](#3-cloud-load-balancer--ssl)
4. [Cloud Storage & CDN (Frontend)](#4-cloud-storage--cdn-frontend)
5. [Cloud Run (Backend)](#5-cloud-run-backend)
6. [VPC & Networking](#6-vpc--networking)
7. [Cloud SQL (Database)](#7-cloud-sql-database)
8. [Flow Complet Request → Response prin Infrastructură](#8-flow-complet-request--response-prin-infrastructură)
9. [Setup Pas cu Pas - Tutorial Complet](#9-setup-pas-cu-pas---tutorial-complet)
10. [Costuri & Optimizări](#10-costuri--optimizări)

---

## 1. Prezentare Generală Infrastructură

### 1.1 Diagrama Completă

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         INTERNET                                        │
│                                                                         │
│  User accesează:                                                        │
│  - salon-maria.app.ro                                                   │
│  - cabinet-ionescu.app.ro                                               │
│  - service-auto.app.ro                                                  │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             │ DNS Resolution (Cloud DNS)
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Cloud DNS (Managed Zone)                             │
│                                                                         │
│  *.app.ro  →  A Record  →  35.201.xxx.xxx (Load Balancer IP)          │
│                                                                         │
│  Wildcard DNS: toate subdomain-urile bat în același IP                 │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             │ HTTPS (port 443)
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              Cloud Load Balancer (Global HTTPS)                         │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  SSL Certificate (Managed)                                        │ │
│  │  - *.app.ro (wildcard)                                            │ │
│  │  - app.ro                                                         │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  URL Map (Routing Rules)                                          │ │
│  │                                                                   │ │
│  │  Path: /api/*        → Backend Service (Cloud Run)               │ │
│  │  Path: /*            → Backend Bucket (Cloud Storage + CDN)      │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  Cloud Armor (Security)                                           │ │
│  │  - DDoS protection                                                │ │
│  │  - Rate limiting: 1000 req/min per IP                            │ │
│  │  - Geo-blocking (optional)                                        │ │
│  └───────────────────────────────────────────────────────────────────┘ │
└──────────────┬─────────────────────────────────┬────────────────────────┘
               │                                 │
               │ (static files)                  │ (API requests)
               ▼                                 ▼
┌──────────────────────────────┐   ┌────────────────────────────────────┐
│  Cloud Storage Bucket        │   │   Cloud Run Service                │
│  (Frontend - Angular)        │   │   (Backend - Spring Boot)          │
│                              │   │                                    │
│  gs://app-frontend/          │   │  ┌──────────────────────────────┐ │
│  ├── index.html              │   │  │  Container Image             │ │
│  ├── main.js                 │   │  │  gcr.io/project/app-backend  │ │
│  ├── styles.css              │   │  │                              │ │
│  └── assets/                 │   │  │  Env Variables:              │ │
│                              │   │  │  - SPRING_PROFILES_ACTIVE    │ │
│  + Cloud CDN (cache)         │   │  │  - DB_CONNECTION_NAME        │ │
│    Latency: <50ms global     │   │  │  - JWT_SECRET                │ │
│                              │   │  └──────────────────────────────┘ │
└──────────────────────────────┘   │                                    │
                                   │  Auto-scaling:                     │
                                   │  - Min instances: 1                │
                                   │  - Max instances: 100              │
                                   │  - CPU: 2 vCPU                     │
                                   │  - Memory: 4 GB                    │
                                   └────────────┬───────────────────────┘
                                                │
                                                │ Private IP (VPC)
                                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                       VPC Network (Private)                             │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  Serverless VPC Access Connector                                  │ │
│  │  - Connects Cloud Run to VPC                                      │ │
│  │  - IP Range: 10.8.0.0/28                                          │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  Firewall Rules                                                   │ │
│  │  - Allow Cloud Run → Cloud SQL (port 5432)                       │ │
│  │  - Deny all external access to Cloud SQL                         │ │
│  └───────────────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             │ Private IP Connection
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              Cloud SQL Instance (PostgreSQL 15)                         │
│              Private IP: 10.10.0.3                                      │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  Databases:                                                      │   │
│  │  ├── tenant_registry (MASTER)                                    │   │
│  │  ├── tenant_abc123_salon_maria                                   │   │
│  │  ├── tenant_xyz789_cabinet_ionescu                               │   │
│  │  └── tenant_def456_service_auto                                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  Configuration:                                                         │
│  - Machine type: db-n1-standard-2 (2 vCPU, 7.5 GB RAM)                │
│  - Storage: 100 GB SSD (auto-increase enabled)                         │
│  - Backups: Daily at 03:00 AM (retained 7 days)                        │
│  - Point-in-time recovery: Enabled (7 days)                            │
│  - High Availability: Enabled (regional failover)                      │
│  - Private IP only (NO public IP)                                      │
└─────────────────────────────────────────────────────────────────────────┘
                             │
                             │ Backups
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              Cloud Storage (Backups & Uploads)                          │
│                                                                         │
│  gs://app-backups/                                                      │
│  ├── db-backups/                                                        │
│  │   ├── 2026-02-13-tenant_abc123_salon_maria.sql                      │
│  │   └── 2026-02-13-tenant_xyz789_cabinet_ionescu.sql                  │
│  │                                                                      │
│  └── tenant-uploads/                                                    │
│      ├── abc123/                    # salon-maria                       │
│      │   └── clients/123/photos/                                        │
│      └── xyz789/                    # cabinet-ionescu                   │
│          └── clients/456/photos/                                        │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                    Servicii Auxiliare GCP                               │
├─────────────────────────────────────────────────────────────────────────┤
│  • Secret Manager: JWT_SECRET, DB passwords, API keys                  │
│  • Cloud Logging: Centralized logs (filtru per tenant)                 │
│  • Cloud Monitoring: Metrics, dashboards, alerts                       │
│  • Cloud Scheduler: Cron jobs (SMS reminders)                          │
│  • Container Registry: Docker images (gcr.io)                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. DNS & Subdomain Routing

### 2.1 Cum Funcționează DNS-ul pentru Multi-Tenant?

**Cerință:** Fiecare tenant are subdomain propriu:
- `salon-maria.app.ro`
- `cabinet-ionescu.app.ro`
- `service-auto.app.ro`

**Problema:** Cum configurăm DNS-ul să redirecționeze TOATE subdomain-urile către același Load Balancer?

**Soluția:** **Wildcard DNS Record**

### 2.2 Setup Cloud DNS (Google Cloud)

**Pasul 1: Cumpără domeniul `app.ro`**
- Provider: GoDaddy, Namecheap, CloudFlare, etc.
- Cost: ~€10-15/an

**Pasul 2: Creează Managed Zone în Cloud DNS**

```bash
# Creează zona DNS în GCP
gcloud dns managed-zones create app-ro-zone \
    --dns-name="app.ro." \
    --description="Multi-tenant app DNS zone"
```

**Rezultat:**
```
Created [https://dns.googleapis.com/dns/v1/projects/your-project/managedZones/app-ro-zone].
NAME SERVERS:
  ns-cloud-a1.googledomains.com.
  ns-cloud-a2.googledomains.com.
  ns-cloud-a3.googledomains.com.
  ns-cloud-a4.googledomains.com.
```

**Pasul 3: Configurează Name Servers la domain registrar**

În panoul GoDaddy/Namecheap:
```
Nameservers (Custom):
  ns-cloud-a1.googledomains.com
  ns-cloud-a2.googledomains.com
  ns-cloud-a3.googledomains.com
  ns-cloud-a4.googledomains.com
```

**⏰ Propagare DNS: 24-48 ore**

**Pasul 4: Obține IP-ul Load Balancer-ului**

```bash
# După ce creezi Load Balancer (vezi secțiunea următoare)
gcloud compute addresses describe app-lb-ip --global --format="value(address)"
# Output: 35.201.123.45
```

**Pasul 5: Creează DNS Records**

```bash
# Record pentru domeniul principal: app.ro
gcloud dns record-sets create app.ro. \
    --zone="app-ro-zone" \
    --type="A" \
    --ttl="300" \
    --rrdatas="35.201.123.45"

# Wildcard record pentru subdomain-uri: *.app.ro
gcloud dns record-sets create "*.app.ro." \
    --zone="app-ro-zone" \
    --type="A" \
    --ttl="300" \
    --rrdatas="35.201.123.45"
```

**Rezultat DNS Records:**
```
NAME                TYPE    TTL     DATA
app.ro.             A       300     35.201.123.45
*.app.ro.           A       300     35.201.123.45
```

**Ce înseamnă `*.app.ro`?**
- Orice subdomain va pointa la același IP
- `salon-maria.app.ro` → 35.201.123.45
- `cabinet-ionescu.app.ro` → 35.201.123.45
- `orice-altceva.app.ro` → 35.201.123.45

### 2.3 Verificare DNS

**Din terminal:**
```bash
# Verifică domeniul principal
nslookup app.ro
# Server:  8.8.8.8
# Address: 35.201.123.45

# Verifică subdomain
nslookup salon-maria.app.ro
# Server:  8.8.8.8
# Address: 35.201.123.45

# Verifică alt subdomain (chiar dacă nu există tenant)
nslookup test-random.app.ro
# Server:  8.8.8.8
# Address: 35.201.123.45  ← ACELAȘI IP!
```

**Din browser:**
```
https://salon-maria.app.ro  → Load Balancer (35.201.123.45)
https://cabinet-ionescu.app.ro → Load Balancer (35.201.123.45)
```

### 2.4 Alternativa: Subdirectory Routing (fără wildcard DNS)

**În loc de subdomain-uri, folosești path-uri:**
- `app.ro/salon-maria`
- `app.ro/cabinet-ionescu`

**Avantaje:**
- ✅ Mai simplu DNS (un singur A record)
- ✅ Un singur SSL certificate

**Dezavantaje:**
- ❌ Mai puțin profesional
- ❌ Frontend trebuie să parseze path-ul, nu hostname-ul

---

## 3. Cloud Load Balancer & SSL

### 3.1 Ce Face Load Balancer-ul?

1. **SSL/TLS Termination:** Primește HTTPS → decriptează → trimite HTTP intern
2. **Routing:** `/api/*` → Cloud Run, `/*` → Cloud Storage
3. **SSL Certificate:** Wildcard `*.app.ro` (automat managed de Google)
4. **DDoS Protection:** Cloud Armor (rate limiting, geo-blocking)
5. **Global Anycast IP:** Același IP accesibil din toată lumea, latență minimă

### 3.2 Setup Load Balancer - Pas cu Pas

**Arhitectură:**
```
Load Balancer
  ├── SSL Certificate (*.app.ro)
  ├── Backend Service 1 → Cloud Run (API)
  ├── Backend Bucket → Cloud Storage (Frontend)
  └── URL Map (routing rules)
```

#### **PASUL 1: Rezervă IP static global**

```bash
gcloud compute addresses create app-lb-ip \
    --ip-version=IPV4 \
    --global

# Verifică IP-ul
gcloud compute addresses describe app-lb-ip --global
# address: 35.201.123.45
```

#### **PASUL 2: Creează Backend Service pentru Cloud Run**

```bash
# Mai întâi, creează Network Endpoint Group pentru Cloud Run
gcloud compute network-endpoint-groups create app-backend-neg \
    --region=europe-west1 \
    --network-endpoint-type=serverless \
    --cloud-run-service=app-backend

# Creează Backend Service
gcloud compute backend-services create app-backend-service \
    --global \
    --load-balancing-scheme=EXTERNAL_MANAGED

# Adaugă NEG la Backend Service
gcloud compute backend-services add-backend app-backend-service \
    --global \
    --network-endpoint-group=app-backend-neg \
    --network-endpoint-group-region=europe-west1
```

#### **PASUL 3: Creează Backend Bucket pentru Cloud Storage**

```bash
# Creează bucket pentru frontend
gsutil mb -l europe-west1 gs://app-frontend

# Setează public access (doar read)
gsutil iam ch allUsers:objectViewer gs://app-frontend

# Creează Backend Bucket
gcloud compute backend-buckets create app-frontend-bucket \
    --gcs-bucket-name=app-frontend \
    --enable-cdn
```

#### **PASUL 4: Creează URL Map (routing rules)**

```bash
# URL Map definește: care path merge unde
gcloud compute url-maps create app-lb-url-map \
    --default-backend-bucket=app-frontend-bucket

# Adaugă path matcher pentru API
gcloud compute url-maps add-path-matcher app-lb-url-map \
    --path-matcher-name=api-matcher \
    --default-backend-bucket=app-frontend-bucket \
    --backend-service-path-rules="/api/*=app-backend-service"
```

**Rezultat URL Map:**
```yaml
Request Path:
  /api/auth/login     → app-backend-service (Cloud Run)
  /api/appointments   → app-backend-service (Cloud Run)
  /index.html         → app-frontend-bucket (Cloud Storage)
  /assets/logo.png    → app-frontend-bucket (Cloud Storage)
```

#### **PASUL 5: Creează SSL Certificate (Managed)**

```bash
# Google va genera automat certificat SSL pentru domain-ul tău
gcloud compute ssl-certificates create app-ssl-cert \
    --domains="app.ro,*.app.ro" \
    --global
```

**⏰ Provisioning: 15-60 minute**

**Verificare:**
```bash
gcloud compute ssl-certificates describe app-ssl-cert --global
# status: ACTIVE
# domains: app.ro, *.app.ro
```

#### **PASUL 6: Creează Target HTTPS Proxy**

```bash
gcloud compute target-https-proxies create app-https-proxy \
    --url-map=app-lb-url-map \
    --ssl-certificates=app-ssl-cert \
    --global
```

#### **PASUL 7: Creează Forwarding Rule (expune Load Balancer)**

```bash
gcloud compute forwarding-rules create app-https-forwarding-rule \
    --address=app-lb-ip \
    --global \
    --target-https-proxy=app-https-proxy \
    --ports=443
```

#### **PASUL 8: (Opțional) Redirect HTTP → HTTPS**

```bash
# URL Map pentru HTTP redirect
gcloud compute url-maps create app-http-redirect \
    --default-url-redirect-response-code=MOVED_PERMANENTLY_DEFAULT \
    --default-url-redirect-https-redirect

# Target HTTP Proxy
gcloud compute target-http-proxies create app-http-proxy \
    --url-map=app-http-redirect \
    --global

# Forwarding rule pentru port 80
gcloud compute forwarding-rules create app-http-forwarding-rule \
    --address=app-lb-ip \
    --global \
    --target-http-proxy=app-http-proxy \
    --ports=80
```

### 3.3 Testare Load Balancer

```bash
# Testează SSL
curl -I https://app.ro
# HTTP/2 200
# server: Google Frontend

# Testează subdomain
curl -I https://salon-maria.app.ro
# HTTP/2 200

# Testează API routing
curl -I https://salon-maria.app.ro/api/health
# HTTP/2 200
# x-cloud-trace-context: ...

# Testează frontend routing
curl -I https://salon-maria.app.ro/index.html
# HTTP/2 200
# x-goog-stored-content-length: 12345
```

### 3.4 Cloud Armor (DDoS Protection & Rate Limiting)

```bash
# Creează security policy
gcloud compute security-policies create app-security-policy \
    --description="DDoS protection and rate limiting"

# Rate limiting: max 1000 requests/min per IP
gcloud compute security-policies rules create 1000 \
    --security-policy=app-security-policy \
    --expression="true" \
    --action=rate-based-ban \
    --rate-limit-threshold-count=1000 \
    --rate-limit-threshold-interval-sec=60 \
    --ban-duration-sec=600 \
    --conform-action=allow \
    --exceed-action=deny-429 \
    --enforce-on-key=IP

# Aplică policy la backend service
gcloud compute backend-services update app-backend-service \
    --security-policy=app-security-policy \
    --global
```

**Rezultat:**
- ✅ Dacă un IP face >1000 requests/min → blocat 10 minute (HTTP 429)
- ✅ DDoS protection automat (Google Cloud Armor)

---

## 4. Cloud Storage & CDN (Frontend)

### 4.1 Setup Cloud Storage pentru Angular

```bash
# Creează bucket (dacă nu ai făcut deja)
gsutil mb -l europe-west1 gs://app-frontend

# Setează website configuration
gsutil web set -m index.html -e index.html gs://app-frontend

# Setează CORS pentru API calls
cat > cors.json <<EOF
[
  {
    "origin": ["https://app.ro", "https://*.app.ro"],
    "method": ["GET", "POST", "PUT", "DELETE"],
    "responseHeader": ["Content-Type", "Authorization"],
    "maxAgeSeconds": 3600
  }
]
EOF

gsutil cors set cors.json gs://app-frontend

# Setează public read access
gsutil iam ch allUsers:objectViewer gs://app-frontend
```

### 4.2 Deploy Angular App

```bash
# Local: build production
cd frontend/
ng build --configuration production

# Upload la Cloud Storage
gsutil -m rsync -r -d dist/app gs://app-frontend/

# Setează cache headers
gsutil -m setmeta -h "Cache-Control:public, max-age=3600" \
    gs://app-frontend/*.js

gsutil -m setmeta -h "Cache-Control:public, max-age=3600" \
    gs://app-frontend/*.css

# index.html - cache scurt (pentru updates rapide)
gsutil setmeta -h "Cache-Control:public, max-age=300" \
    gs://app-frontend/index.html
```

### 4.3 Cloud CDN (Content Delivery Network)

**Ce face Cloud CDN?**
- Cache-uiește fișiere static la edge locations (global)
- Latență <50ms pentru useri din toată lumea
- Reduce costuri egress (bandwidth)

**Activare:**
```bash
# CDN este deja activat pe Backend Bucket (--enable-cdn)
gcloud compute backend-buckets describe app-frontend-bucket
# enableCdn: true
```

**Cache invalidation (după deploy):**
```bash
# Invalidează cache pentru index.html (forțează reload)
gcloud compute url-maps invalidate-cdn-cache app-lb-url-map \
    --path="/index.html"
```

### 4.4 Testare CDN

```bash
# Primul request: cache MISS (de la bucket)
curl -I https://salon-maria.app.ro/main.js
# x-cache: MISS
# x-goog-stored-content-length: 123456

# Al doilea request: cache HIT (de la CDN edge)
curl -I https://salon-maria.app.ro/main.js
# x-cache: HIT
# age: 10
```

---

## 5. Cloud Run (Backend)

### 5.1 Ce Este Cloud Run?

- **Serverless containers:** Deploy Docker images fără să gestionezi servere
- **Auto-scaling:** 0 → 1000 instanțe automat (bazat pe trafic)
- **Pay-per-use:** Plătești doar când procesează requests (nu 24/7)
- **Fully managed:** Zero ops (patches, scaling, monitoring automat)

### 5.2 Build & Deploy Spring Boot pe Cloud Run

#### **PASUL 1: Creează Dockerfile**

```dockerfile
# Dockerfile (multi-stage build)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Non-root user pentru securitate
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

EXPOSE 8080
ENTRYPOINT ["java", "-Xmx2g", "-jar", "app.jar"]
```

#### **PASUL 2: Build & Push Docker Image**

```bash
# Autentificare Docker cu GCR
gcloud auth configure-docker

# Build image
docker build -t gcr.io/your-project-id/app-backend:latest .

# Push la Google Container Registry
docker push gcr.io/your-project-id/app-backend:latest
```

#### **PASUL 3: Deploy pe Cloud Run**

```bash
gcloud run deploy app-backend \
    --image=gcr.io/your-project-id/app-backend:latest \
    --region=europe-west1 \
    --platform=managed \
    --allow-unauthenticated \
    --memory=4Gi \
    --cpu=2 \
    --min-instances=1 \
    --max-instances=100 \
    --timeout=300 \
    --concurrency=80 \
    --set-env-vars="SPRING_PROFILES_ACTIVE=gcp" \
    --vpc-connector=app-vpc-connector
```

**Parametri explicați:**
- `--memory=4Gi`: RAM per instanță
- `--cpu=2`: 2 vCPU per instanță
- `--min-instances=1`: Menține 1 instanță warm (evită cold starts)
- `--max-instances=100`: Scale până la 100 instanțe
- `--concurrency=80`: Max 80 requests simultan per instanță
- `--vpc-connector`: Conectează la VPC (pentru Cloud SQL private IP)

#### **PASUL 4: Configurează Environment Variables (Secrets)**

```bash
# Creează secrets în Secret Manager
echo -n "your-jwt-secret-key-min-32-chars" | \
    gcloud secrets create jwt-secret --data-file=-

echo -n "jdbc:postgresql://10.10.0.3:5432/tenant_registry" | \
    gcloud secrets create db-url --data-file=-

echo -n "db-password" | \
    gcloud secrets create db-password --data-file=-

# Actualizează Cloud Run service cu secrets
gcloud run services update app-backend \
    --region=europe-west1 \
    --update-secrets=JWT_SECRET=jwt-secret:latest \
    --update-secrets=DB_PASSWORD=db-password:latest
```

### 5.3 Cloud Run Auto-Scaling

**Cum funcționează?**

```
Trafic scăzut (10 requests/sec):
  Cloud Run: 1-2 instanțe active
  
Trafic mediu (100 requests/sec):
  Cloud Run: 5-10 instanțe active
  
Trafic mare (1000 requests/sec):
  Cloud Run: 50-100 instanțe active
  
Noapte (zero requests):
  Cloud Run: 1 instanță (min-instances=1)
  Dacă setezi min-instances=0 → zero instanțe → $0 cost
```

**Cold Start Optimization:**
```yaml
# application-gcp.properties
spring.main.lazy-initialization=true
spring.jpa.hibernate.ddl-auto=validate
spring.datasource.hikari.minimum-idle=1
spring.datasource.hikari.maximum-pool-size=5
```

---

## 6. VPC & Networking

### 6.1 De Ce Avem Nevoie de VPC?

**⚠️ IMPORTANT: Există 2 variante de configurare Cloud SQL**

#### **Varianta A: Public IP (Simplificată - pentru Dev/Test)** ⭐ RECOMANDAT PENTRU ÎNCEPUT

**Avantaje:**
- ✅ Setup rapid (5 minute)
- ✅ Zero configurare VPC
- ✅ Cost mai mic (nu plătești VPC Connector: ~€8/lună)
- ✅ Perfect pentru development/testing

**Dezavantaje:**
- ⚠️ Cloud SQL expus pe internet (cu IP whitelisting)
- ⚠️ Mai puțin sigur decât Private IP

**Când să folosești:**
- Development/staging environments
- POC/MVP rapid
- Bugete mici
- Până ajungi la production

#### **Varianta B: Private IP + VPC (Securizată - pentru Production)**

**Avantaje:**
- ✅ Cloud SQL ZERO acces din internet
- ✅ Maxim securitate
- ✅ Compliant cu standarde enterprise

**Dezavantaje:**
- ❌ Setup mai complex (30 minute)
- ❌ Cost extra: VPC Connector (~€8/lună)
- ❌ Debugging mai greu (trebuie Cloud SQL Proxy)

**Când să folosești:**
- Production environment
- Date sensibile (GDPR, medical, financiar)
- Enterprise requirements

---

### 6.2 Varianta A: Setup Simplificat (Public IP) - CE AI TU ACUM

**Problema:** Cloud Run este serverless → nu are acces direct la Cloud SQL private IP

**Soluția:** Activezi Public IP pe Cloud SQL + Authorized Networks

#### **Setup Pas cu Pas - Public IP:**

```bash
# 1. Creează Cloud SQL instance CU public IP
gcloud sql instances create app-postgres \
    --database-version=POSTGRES_15 \
    --tier=db-n1-standard-2 \
    --region=europe-west1 \
    --assign-ip \
    --backup-start-time=03:00 \
    --enable-point-in-time-recovery

# 2. Obține public IP-ul
gcloud sql instances describe app-postgres \
    --format="value(ipAddresses[0].ipAddress)"
# Output: 34.78.123.45

# 3. Permite acces din Cloud Run (folosește 0.0.0.0/0 pentru toate IP-urile)
gcloud sql instances patch app-postgres \
    --authorized-networks=0.0.0.0/0

# ⚠️ ATENȚIE: 0.0.0.0/0 = orice IP de pe internet poate accesa!
# Mai sigur: whitelist doar IP-urile Cloud Run (vezi mai jos)
```

#### **Securitate: Whitelist doar Cloud Run IPs**

```bash
# Obține IP ranges pentru Cloud Run în europe-west1
# Cloud Run folosește IP-uri din ranges Google Cloud
# Lista completă: https://www.gstatic.com/ipranges/cloud.json

# Alternativă: Folosește Cloud SQL Proxy în container
# (mai sigur decât authorized networks)
```

#### **Connection String pentru Spring Boot (Public IP):**

```yaml
# application-gcp.properties
spring.datasource.url=jdbc:postgresql://34.78.123.45:5432/tenant_registry
spring.datasource.username=postgres
spring.datasource.password=${DB_PASSWORD}

# Fără VPC Connector = conexiune directă prin internet (criptat SSL)
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.connection-timeout=30000
```

#### **Deploy Cloud Run (fără VPC Connector):**

```bash
gcloud run deploy app-backend \
    --image=gcr.io/your-project-id/app-backend:latest \
    --region=europe-west1 \
    --platform=managed \
    --allow-unauthenticated \
    --memory=4Gi \
    --cpu=2 \
    --min-instances=1 \
    --max-instances=100 \
    --set-env-vars="SPRING_PROFILES_ACTIVE=gcp,DB_HOST=34.78.123.45"
    # ⚠️ FĂRĂ --vpc-connector (nu e nevoie!)
```

#### **Testare Conexiune:**

```bash
# Local: test conexiune la Cloud SQL public IP
psql "host=34.78.123.45 port=5432 dbname=tenant_registry user=postgres password=xxx sslmode=require"

# Dacă funcționează → Cloud Run va funcționa la fel
```

**✅ Avantaje varianta Public IP:**
- Setup în 5 minute
- Zero configurare VPC
- Cost: -€8/lună (fără VPC Connector)
- Perfect pentru ce ai tu acum în test!

**⚠️ Dezavantaje:**
- Cloud SQL expus pe internet (chiar cu SSL)
- IP whitelisting poate fi complicat (Cloud Run IP-uri dinamice)

---

### 6.3 Varianta B: Setup Complet VPC (Private IP) - PENTRU PRODUCTION

**Recomandare: Migrează la Private IP când mergi în production!**

#### **PASUL 1: Creează VPC Network**

```bash
gcloud compute networks create app-vpc \
    --subnet-mode=custom \
    --bgp-routing-mode=regional

# Creează subnet pentru Cloud SQL
gcloud compute networks subnets create app-subnet \
    --network=app-vpc \
    --region=europe-west1 \
    --range=10.10.0.0/24
```

#### **PASUL 2: Creează Serverless VPC Access Connector**

```bash
gcloud compute networks vpc-access connectors create app-vpc-connector \
    --network=app-vpc \
    --region=europe-west1 \
    --range=10.8.0.0/28
```

**Ce face connector-ul?**
```
Cloud Run (serverless, no VPC)
   ↓
VPC Connector (10.8.0.0/28)
   ↓
VPC Network (10.10.0.0/24)
   ↓
Cloud SQL Private IP (10.10.0.3)
```

#### **PASUL 3: Configurează Firewall Rules**

```bash
# Allow Cloud Run → Cloud SQL (PostgreSQL port 5432)
gcloud compute firewall-rules create allow-cloudrun-to-cloudsql \
    --network=app-vpc \
    --allow=tcp:5432 \
    --source-ranges=10.8.0.0/28 \
    --target-tags=cloudsql

# Deny toate conexiunile externe la Cloud SQL
gcloud compute firewall-rules create deny-external-cloudsql \
    --network=app-vpc \
    --action=DENY \
    --rules=tcp:5432 \
    --source-ranges=0.0.0.0/0 \
    --priority=1000
```

### 6.3 Testare Conexiune VPC

```bash
# Deploy Cloud Run cu VPC connector
gcloud run deploy app-backend \
    --vpc-connector=app-vpc-connector \
    --vpc-egress=private-ranges-only

# Testează conexiune la Cloud SQL
gcloud run services describe app-backend --region=europe-west1
# vpcAccess:
#   connector: projects/.../connectors/app-vpc-connector
#   egress: PRIVATE_RANGES_ONLY
```

---

## 7. Cloud SQL (Database)

### 7.1 Setup Cloud SQL Instance

```bash
# Creează instanță Cloud SQL (PostgreSQL 15)
gcloud sql instances create app-postgres \
    --database-version=POSTGRES_15 \
    --tier=db-n1-standard-2 \
    --region=europe-west1 \
    --network=projects/your-project-id/global/networks/app-vpc \
    --no-assign-ip \
    --availability-type=REGIONAL \
    --backup-start-time=03:00 \
    --maintenance-window-day=SUN \
    --maintenance-window-hour=4 \
    --enable-point-in-time-recovery \
    --retained-backups-count=7
```

**Parametri explicați:**
- `--tier=db-n1-standard-2`: 2 vCPU, 7.5 GB RAM
- `--no-assign-ip`: **NU creează IP public** (doar private IP în VPC)
- `--availability-type=REGIONAL`: High availability (failover automat)
- `--backup-start-time=03:00`: Backup zilnic la 3 AM
- `--enable-point-in-time-recovery`: Restore la orice moment (ultimi 7 zile)

### 7.2 Creează Database-uri

```bash
# Setează password pentru user postgres
gcloud sql users set-password postgres \
    --instance=app-postgres \
    --password=secure-master-password

# Creează MASTER database (tenant_registry)
gcloud sql databases create tenant_registry \
    --instance=app-postgres

# Creează database pentru primul tenant
gcloud sql databases create tenant_abc123_salon_maria \
    --instance=app-postgres
```

### 7.3 Obține Private IP

```bash
# Obține private IP al instanței Cloud SQL
gcloud sql instances describe app-postgres \
    --format="value(ipAddresses[0].ipAddress)"
# Output: 10.10.0.3
```

### 7.4 Connection String pentru Spring Boot

```yaml
# application-gcp.properties
spring.datasource.url=jdbc:postgresql://10.10.0.3:5432/tenant_registry
spring.datasource.username=postgres
spring.datasource.password=${DB_PASSWORD}  # din Secret Manager

# Connection pooling
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

### 7.5 Backups & Recovery

**Backup automat:**
```bash
# Verifică ultimele backups
gcloud sql backups list --instance=app-postgres
# ID     WINDOW_START_TIME               STATUS
# 1234   2026-02-13T03:00:00.000Z       SUCCESSFUL
```

**Manual backup:**
```bash
gcloud sql backups create --instance=app-postgres
```

**Point-in-time recovery:**
```bash
# Restore la 2 ore în urmă
gcloud sql instances clone app-postgres app-postgres-clone \
    --point-in-time='2026-02-13T10:00:00.000Z'
```

**Export database (per tenant):**
```bash
# Export tenant database la Cloud Storage
gcloud sql export sql app-postgres gs://app-backups/db-backups/tenant_abc123_salon_maria.sql \
    --database=tenant_abc123_salon_maria
```

---

## 8. Flow Complet Request → Response prin Infrastructură

### Exemplu Concret: Maria face login pe `salon-maria.app.ro`

```
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 1: Maria accesează https://salon-maria.app.ro în browser         │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 2: Browser face DNS lookup                                       │
│                                                                         │
│  Query: salon-maria.app.ro                                              │
│    ↓                                                                    │
│  Cloud DNS (managed zone: app-ro-zone)                                  │
│    ↓                                                                    │
│  A Record: *.app.ro → 35.201.123.45                                    │
│    ↓                                                                    │
│  Response: 35.201.123.45 (Load Balancer IP)                            │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 3: Browser face HTTPS request la Load Balancer                   │
│                                                                         │
│  GET https://salon-maria.app.ro/ HTTP/2                                │
│  Host: salon-maria.app.ro                                               │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 4: Cloud Load Balancer (35.201.123.45)                           │
│                                                                         │
│  1. SSL Termination:                                                    │
│     - Verifică certificat SSL (*.app.ro)                                │
│     - Decriptează HTTPS → HTTP                                          │
│                                                                         │
│  2. Cloud Armor Security:                                               │
│     - Verifică rate limiting (IP: 192.168.1.100)                        │
│     - Check: 50 requests în ultimul minut ✅ (sub 1000 limit)          │
│                                                                         │
│  3. URL Map Routing:                                                    │
│     - Path: / (root)                                                    │
│     - Rule: /* → Backend Bucket (Cloud Storage)                        │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 5: Backend Bucket → Cloud Storage                                │
│                                                                         │
│  Request: GET gs://app-frontend/index.html                              │
│    ↓                                                                    │
│  Cloud CDN Check:                                                       │
│    - Cache lookup: index.html                                           │
│    - Status: MISS (not in cache)                                        │
│    ↓                                                                    │
│  Fetch from bucket: gs://app-frontend/index.html                        │
│    ↓                                                                    │
│  Response: index.html (Angular app)                                     │
│    - Size: 15 KB                                                        │
│    - Cache pentru 5 minute (Cache-Control: max-age=300)                │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 6: Load Balancer → Browser                                       │
│                                                                         │
│  HTTP/2 200 OK                                                          │
│  content-type: text/html                                                │
│  cache-control: public, max-age=300                                     │
│  x-cache: MISS                                                          │
│  x-goog-stored-content-length: 15360                                    │
│                                                                         │
│  <body of index.html>                                                   │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 7: Angular App se încarcă în browser                             │
│                                                                         │
│  - Browser parsează HTML                                                │
│  - Descarcă main.js, styles.css (tot de la CDN)                        │
│  - Angular detectează tenant: "salon-maria"                             │
│  - Afișează login form                                                  │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             │ (Maria introduce email + password)
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 8: Maria face login - POST /api/auth/login                       │
│                                                                         │
│  POST https://salon-maria.app.ro/api/auth/login HTTP/2                 │
│  Host: salon-maria.app.ro                                               │
│  Content-Type: application/json                                         │
│                                                                         │
│  {                                                                      │
│    "tenantSlug": "salon-maria",                                         │
│    "email": "maria@salon.ro",                                           │
│    "password": "SecurePass123!"                                         │
│  }                                                                      │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 9: Load Balancer routing                                         │
│                                                                         │
│  URL Map Check:                                                         │
│    - Path: /api/auth/login                                              │
│    - Rule: /api/* → Backend Service (Cloud Run)                        │
│    ↓                                                                    │
│  Forward la: app-backend-service                                        │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 10: Cloud Run (app-backend)                                      │
│                                                                         │
│  Load Balancer detectează:                                              │
│    - Current instances: 2 active                                        │
│    - Request rate: 50 req/sec                                           │
│    - Concurrency: 40/80 (sub limită)                                    │
│    → No scaling needed                                                  │
│                                                                         │
│  Route la: Instance app-backend-xyz (random selection)                  │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 11: Spring Boot (în Cloud Run container)                         │
│                                                                         │
│  1. AuthController.login() primește request                             │
│  2. TenantService caută tenant "salon-maria" în tenant_registry         │
│     → Conectare la Cloud SQL prin VPC Connector                         │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 12: VPC Connector Bridge                                         │
│                                                                         │
│  Cloud Run instance (serverless)                                        │
│    ↓                                                                    │
│  VPC Connector (10.8.0.0/28)                                            │
│    ↓                                                                    │
│  VPC Network (10.10.0.0/24)                                             │
│    ↓                                                                    │
│  Cloud SQL Private IP (10.10.0.3:5432)                                  │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 13: Cloud SQL (MASTER DB - tenant_registry)                      │
│                                                                         │
│  Query: SELECT * FROM tenants WHERE slug = 'salon-maria'                │
│    ↓                                                                    │
│  Result: { id: 'abc123', db_name: 'tenant_abc123_salon_maria' }        │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 14: Spring Boot switch la tenant DB                              │
│                                                                         │
│  TenantContext.setCurrentTenant("abc123")                               │
│    ↓                                                                    │
│  AbstractRoutingDataSource switch conexiune                             │
│    ↓                                                                    │
│  Conexiune nouă la: tenant_abc123_salon_maria                           │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 15: Cloud SQL (TENANT DB - tenant_abc123_salon_maria)            │
│                                                                         │
│  Query: SELECT * FROM users WHERE email = 'maria@salon.ro'              │
│    ↓                                                                    │
│  Result: { id: 1, email: 'maria@salon.ro', role: 'ADMIN', ... }        │
│    ↓                                                                    │
│  BCrypt.checkpw("SecurePass123!", password_hash) → ✅ TRUE             │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 16: Spring Boot generează JWT                                    │
│                                                                         │
│  JwtTokenProvider.generateToken(user, "abc123")                         │
│    ↓                                                                    │
│  JWT Token:                                                             │
│    Payload: { userId: 1, tenantId: "abc123", roles: ["ADMIN"] }        │
│    Secret: jwt-secret (din Secret Manager)                              │
│    Expiry: 24 ore                                                       │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 17: Cloud Run → Load Balancer → Browser                          │
│                                                                         │
│  HTTP/2 200 OK                                                          │
│  content-type: application/json                                         │
│                                                                         │
│  {                                                                      │
│    "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIi...",                  │
│    "user": {                                                            │
│      "id": 1,                                                           │
│      "email": "maria@salon.ro",                                         │
│      "role": "ADMIN"                                                    │
│    }                                                                    │
│  }                                                                      │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PASUL 18: Angular salvează token în localStorage                       │
│                                                                         │
│  localStorage.setItem('auth_token', token)                              │
│  Redirect: /dashboard                                                   │
│                                                                         │
│  Maria vede dashboard-ul salonului ei! ✅                               │
└─────────────────────────────────────────────────────────────────────────┘
```

**Timpul total: ~800ms**
- DNS lookup: 20ms
- SSL handshake: 150ms
- Load Balancer routing: 10ms
- Cloud Run processing: 300ms
- Database queries: 200ms
- Response transmission: 120ms

---

## 9. Setup Pas cu Pas - Tutorial Complet

### 9.1 Prerequisite

```bash
# Instalează Google Cloud SDK
# Windows: https://cloud.google.com/sdk/docs/install
# MacOS: brew install google-cloud-sdk
# Linux: apt-get install google-cloud-sdk

# Autentificare
gcloud auth login

# Setează project
gcloud config set project your-project-id
```

### 9.2 Setup Complete (Script)

Salvează în `setup-infrastructure.sh`:

```bash
#!/bin/bash
set -e

PROJECT_ID="your-project-id"
REGION="europe-west1"
DOMAIN="app.ro"

echo "🚀 Starting GCP Infrastructure Setup..."

# 1. Enable APIs
echo "📦 Enabling required APIs..."
gcloud services enable \
    compute.googleapis.com \
    run.googleapis.com \
    sqladmin.googleapis.com \
    dns.googleapis.com \
    vpcaccess.googleapis.com \
    secretmanager.googleapis.com

# 2. Reserve Static IP
echo "🌐 Creating static IP..."
gcloud compute addresses create app-lb-ip --ip-version=IPV4 --global
LB_IP=$(gcloud compute addresses describe app-lb-ip --global --format="value(address)")
echo "✅ Load Balancer IP: $LB_IP"

# 3. Create VPC
echo "🔒 Creating VPC..."
gcloud compute networks create app-vpc --subnet-mode=custom
gcloud compute networks subnets create app-subnet \
    --network=app-vpc \
    --region=$REGION \
    --range=10.10.0.0/24

# 4. Create VPC Connector
echo "🔌 Creating VPC Connector..."
gcloud compute networks vpc-access connectors create app-vpc-connector \
    --network=app-vpc \
    --region=$REGION \
    --range=10.8.0.0/28

# 5. Create Cloud SQL
echo "💾 Creating Cloud SQL instance..."
gcloud sql instances create app-postgres \
    --database-version=POSTGRES_15 \
    --tier=db-n1-standard-2 \
    --region=$REGION \
    --network=projects/$PROJECT_ID/global/networks/app-vpc \
    --no-assign-ip \
    --backup-start-time=03:00 \
    --enable-point-in-time-recovery

echo "✅ Setting Cloud SQL password..."
gcloud sql users set-password postgres \
    --instance=app-postgres \
    --password=$(openssl rand -base64 32)

# 6. Create databases
echo "📊 Creating databases..."
gcloud sql databases create tenant_registry --instance=app-postgres

# 7. Create Cloud Storage bucket
echo "☁️ Creating Cloud Storage bucket..."
gsutil mb -l $REGION gs://$PROJECT_ID-frontend
gsutil web set -m index.html gs://$PROJECT_ID-frontend
gsutil iam ch allUsers:objectViewer gs://$PROJECT_ID-frontend

# 8. Create Backend Bucket
echo "🔧 Creating Backend Bucket..."
gcloud compute backend-buckets create app-frontend-bucket \
    --gcs-bucket-name=$PROJECT_ID-frontend \
    --enable-cdn

# 9. Deploy Cloud Run (placeholder)
echo "🐳 Deploying Cloud Run..."
gcloud run deploy app-backend \
    --image=gcr.io/cloudrun/hello \
    --region=$REGION \
    --platform=managed \
    --allow-unauthenticated \
    --vpc-connector=app-vpc-connector

# 10. Create Network Endpoint Group
echo "🔗 Creating NEG for Cloud Run..."
gcloud compute network-endpoint-groups create app-backend-neg \
    --region=$REGION \
    --network-endpoint-type=serverless \
    --cloud-run-service=app-backend

# 11. Create Backend Service
echo "⚙️ Creating Backend Service..."
gcloud compute backend-services create app-backend-service --global
gcloud compute backend-services add-backend app-backend-service \
    --global \
    --network-endpoint-group=app-backend-neg \
    --network-endpoint-group-region=$REGION

# 12. Create URL Map
echo "🗺️ Creating URL Map..."
gcloud compute url-maps create app-lb-url-map \
    --default-backend-bucket=app-frontend-bucket
gcloud compute url-maps add-path-matcher app-lb-url-map \
    --path-matcher-name=api-matcher \
    --default-backend-bucket=app-frontend-bucket \
    --backend-service-path-rules="/api/*=app-backend-service"

# 13. Create SSL Certificate
echo "🔐 Creating SSL Certificate (this may take 15-60 minutes)..."
gcloud compute ssl-certificates create app-ssl-cert \
    --domains="$DOMAIN,*.$DOMAIN" \
    --global

# 14. Create HTTPS Proxy
echo "🔗 Creating HTTPS Proxy..."
gcloud compute target-https-proxies create app-https-proxy \
    --url-map=app-lb-url-map \
    --ssl-certificates=app-ssl-cert \
    --global

# 15. Create Forwarding Rule
echo "📡 Creating Forwarding Rule..."
gcloud compute forwarding-rules create app-https-forwarding-rule \
    --address=app-lb-ip \
    --global \
    --target-https-proxy=app-https-proxy \
    --ports=443

# 16. Create Cloud DNS Zone
echo "🌍 Creating Cloud DNS zone..."
gcloud dns managed-zones create app-ro-zone \
    --dns-name="$DOMAIN." \
    --description="Multi-tenant app DNS zone"

# 17. Create DNS Records
echo "📝 Creating DNS records..."
gcloud dns record-sets create $DOMAIN. \
    --zone="app-ro-zone" \
    --type="A" \
    --ttl="300" \
    --rrdatas="$LB_IP"

gcloud dns record-sets create "*.$DOMAIN." \
    --zone="app-ro-zone" \
    --type="A" \
    --ttl="300" \
    --rrdatas="$LB_IP"

echo "✅ Infrastructure setup complete!"
echo ""
echo "📋 Next steps:"
echo "1. Update your domain registrar with these nameservers:"
gcloud dns managed-zones describe app-ro-zone --format="value(nameServers)" | tr ';' '\n'
echo ""
echo "2. Wait for DNS propagation (24-48 hours)"
echo "3. Wait for SSL certificate provisioning (15-60 minutes)"
echo "4. Deploy your Spring Boot app to Cloud Run"
echo "5. Deploy your Angular app to Cloud Storage"
echo ""
echo "🔗 Load Balancer IP: $LB_IP"
echo "🌐 Your app will be available at: https://$DOMAIN"
```

**Rulare:**
```bash
chmod +x setup-infrastructure.sh
./setup-infrastructure.sh
```

---

## 10. Costuri & Optimizări

### 10.1 Estimare Costuri Lunare (50 tenanți, trafic mediu)

| Serviciu | Configurație | Cost/Lună (€) |
|----------|-------------|---------------|
| **Cloud Run** | 2 vCPU, 4GB RAM, 5M requests/lună | €30-80 |
| **Cloud SQL** | db-n1-standard-2, 100GB SSD, HA enabled | €120-180 |
| **Cloud Storage** | 10GB frontend + 50GB backups | €2-5 |
| **Cloud CDN** | 100GB egress, 10M cache hits | €10-20 |
| **Cloud Load Balancer** | HTTPS forwarding rules | €18 |
| **Cloud Logging** | 50GB logs/lună | €5-10 |
| **VPC Connector** | Throughput 300 Mbps | €8 |
| **Cloud DNS** | 1 managed zone, 1M queries | €0.50 |
| **Secret Manager** | 100 secrets, 10k accesses | €1 |
| **TOTAL** | - | **€194-322/lună** |

**Cost per tenant:** €3.88-6.44/lună (la 50 tenanți)

### 10.2 Optimizări Costuri

#### **1. Cloud Run: Scale to Zero**
```bash
# Setează min-instances=0 pentru dev/staging
gcloud run services update app-backend \
    --min-instances=0 \
    --region=europe-west1

# Cost când zero trafic: €0
# Trade-off: cold start ~2-3 secunde
```

#### **2. Cloud SQL: Auto-pause**
```bash
# Pentru dev/staging: shutdown nopțile/weekend-uri
gcloud sql instances patch app-postgres-dev \
    --activation-policy=NEVER

# Restart când e nevoie
gcloud sql instances patch app-postgres-dev \
    --activation-policy=ALWAYS

# Cost save: ~50% (dacă ruleză doar 12h/zi)
```

#### **3. Cloud Storage: Lifecycle Policies**
```bash
# Șterge backup-uri mai vechi de 30 zile
cat > lifecycle.json <<EOF
{
  "lifecycle": {
    "rule": [
      {
        "action": {"type": "Delete"},
        "condition": {"age": 30}
      }
    ]
  }
}
EOF

gsutil lifecycle set lifecycle.json gs://app-backups
```

#### **4. Cloud CDN: Cache Optimization**
```yaml
# Angular: setează cache headers agresive
Cache-Control: public, max-age=31536000, immutable  # 1 an pentru *.js, *.css
Cache-Control: public, max-age=3600                 # 1 oră pentru index.html
```

#### **5. Reserved Committed Use Discounts**
```bash
# Commitment 1 an pentru Cloud SQL → 25% discount
# Commitment 3 ani → 50% discount

gcloud sql instances update app-postgres \
    --pricing-plan=PACKAGE
```

---

## 11. Migrare de la Public IP la Private IP (Dev → Production)

### 11.1 Când Să Migrezi?

**Rămâi pe Public IP dacă:**
- ✅ Încă ești în development/testing
- ✅ Nu ai date sensibile (GDPR, medical, etc.)
- ✅ Vrei să economisești €8/lună (VPC Connector)
- ✅ Vrei să eviți complexitatea VPC

**Migrează la Private IP când:**
- ⚠️ Mergi în production
- ⚠️ Ai date sensibile (clienți reali, GDPR)
- ⚠️ Audit de securitate (enterprise/compliance)
- ⚠️ Vrei maxim securitate

### 11.2 Plan de Migrare (Zero Downtime)

#### **Faza 1: Pregătire (1-2 ore)**

```bash
# 1. Creează VPC Network
gcloud compute networks create app-vpc --subnet-mode=custom
gcloud compute networks subnets create app-subnet \
    --network=app-vpc \
    --region=europe-west1 \
    --range=10.10.0.0/24

# 2. Creează VPC Connector
gcloud compute networks vpc-access connectors create app-vpc-connector \
    --network=app-vpc \
    --region=europe-west1 \
    --range=10.8.0.0/28

# 3. Configurează firewall rules
gcloud compute firewall-rules create allow-cloudrun-to-cloudsql \
    --network=app-vpc \
    --allow=tcp:5432 \
    --source-ranges=10.8.0.0/28
```

#### **Faza 2: Adaugă Private IP la Cloud SQL (fără să ștergi Public IP)**

```bash
# IMPORTANT: Mai întâi adaugă Private IP, NU șterge Public IP încă!
gcloud sql instances patch app-postgres \
    --network=projects/your-project-id/global/networks/app-vpc \
    --no-assign-ip=false  # păstrează și public IP

# Obține private IP nou
gcloud sql instances describe app-postgres \
    --format="value(ipAddresses[?type=='PRIVATE'].ipAddress)"
# Output: 10.10.0.3
```

#### **Faza 3: Deploy Cloud Run cu VPC Connector (test pararel)**

```bash
# Deploy versiune nouă care folosește private IP
gcloud run deploy app-backend-v2 \
    --image=gcr.io/your-project-id/app-backend:latest \
    --region=europe-west1 \
    --vpc-connector=app-vpc-connector \
    --vpc-egress=private-ranges-only \
    --set-env-vars="DB_HOST=10.10.0.3" \
    --no-traffic  # NU trimite trafic încă!

# Testează manual noul service
BACKEND_V2_URL=$(gcloud run services describe app-backend-v2 --region=europe-west1 --format="value(status.url)")
curl $BACKEND_V2_URL/api/health
# Dacă funcționează ✅ → continuă
```

#### **Faza 4: Gradual Traffic Shift (Canary Deployment)**

```bash
# Trimite 10% trafic la versiunea nouă
gcloud run services update-traffic app-backend \
    --to-revisions=app-backend-v2=10

# Monitorizează logs & errors pentru 30 minute
gcloud logging read "resource.type=cloud_run_revision" --limit 100

# Dacă totul OK → crește traficul treptat
gcloud run services update-traffic app-backend \
    --to-revisions=app-backend-v2=50

# După 1-2 ore, dacă zero probleme → 100%
gcloud run services update-traffic app-backend \
    --to-revisions=app-backend-v2=100
```

#### **Faza 5: Șterge Public IP (după 7 zile de monitoring)**

```bash
# ⚠️ ATENȚIE: Fă asta doar după ce ești 100% sigur că merge cu Private IP!
gcloud sql instances patch app-postgres \
    --no-assign-ip

# Verifică că nu mai are public IP
gcloud sql instances describe app-postgres \
    --format="value(ipAddresses[?type=='PRIMARY'].ipAddress)"
# Output: (gol) = success!
```

#### **Rollback Plan (dacă ceva merge prost)**

```bash
# Dacă ai probleme, revert instant la public IP:
gcloud run services update-traffic app-backend \
    --to-revisions=app-backend-v1=100

# Sau re-enable public IP pe Cloud SQL
gcloud sql instances patch app-postgres \
    --assign-ip
```

---

## 12. FAQ - Public IP vs Private IP

### Documentație Oficială GCP:
- [Cloud Run](https://cloud.google.com/run/docs)
- [Cloud SQL](https://cloud.google.com/sql/docs)
- [Cloud Load Balancing](https://cloud.google.com/load-balancing/docs)
- [Cloud DNS](https://cloud.google.com/dns/docs)
- [VPC](https://cloud.google.com/vpc/docs)

### Monitoring & Debugging:
- [Cloud Logging](https://console.cloud.google.com/logs)
- [Cloud Monitoring](https://console.cloud.google.com/monitoring)
- [Cloud Trace](https://console.cloud.google.com/traces)

### Best Practices:
- [Cloud Run Best Practices](https://cloud.google.com/run/docs/tips/general)
- [Cloud SQL Best Practices](https://cloud.google.com/sql/docs/postgres/best-practices)
- [Load Balancing Best Practices](https://cloud.google.com/load-balancing/docs/best-practices)

---

## 🎯 Checklist Final

### Pre-Production:
- [ ] Domain cumpărat și nameservers configurați
- [ ] SSL certificate provisionat (status: ACTIVE)
- [ ] Cloud SQL backups configurate și testate
- [ ] VPC firewall rules verificate
- [ ] Secrets în Secret Manager (nu hardcoded)
- [ ] Cloud Armor rate limiting activat
- [ ] Monitoring & alerting configurat

### Testing:
- [ ] DNS resolution funcționează pentru toate subdomain-urile
- [ ] HTTPS funcționează (certificat valid)
- [ ] API routing funcționează (/api/* → Cloud Run)
- [ ] Frontend routing funcționează (/* → Cloud Storage)
- [ ] Cloud Run poate accesa Cloud SQL prin VPC
- [ ] Load testing (1000 concurrent users)

### Security:
- [ ] Cloud SQL connections sunt securizate (Public IP cu whitelist SAU Private IP)
- [ ] Service accounts au minimum permissions
- [ ] Secrets rotated (JWT secret, DB passwords)
- [ ] Cloud Armor activat (DDoS protection)
- [ ] Audit logging enabled
- [ ] SSL/TLS enforced pe Cloud SQL

### Checklist Migrare la Private IP (când ești gata):
- [ ] VPC Network creat
- [ ] VPC Connector creat
- [ ] Private IP adăugat la Cloud SQL (păstrează Public IP pentru rollback)
- [ ] Cloud Run deploiat cu VPC Connector
- [ ] Testare conexiune Private IP
- [ ] Canary deployment (10% → 50% → 100%)
- [ ] Monitoring 7 zile fără erori
- [ ] Șterge Public IP de pe Cloud SQL

---

## 📝 Note Finale

### Setup Curent (Public IP - Perfect pentru Dev/Test)

**Ceea ce ai acum este CORECT pentru faza actuală:**
```
✅ Cloud SQL cu Public IP + SSL
✅ Cloud Run fără VPC
✅ Setup simplu, rapid
✅ Cost optimizat (-€8/lună)
✅ Ideal pentru development/testing
```

**NU ai nevoie de VPC** până când:
- Ajungi în production cu clienți reali
- Ai date sensibile (GDPR/medical)
- Faci audit de securitate

### Quick Reference Commands

**Verifică setup-ul curent:**
```bash
# Verifică dacă Cloud SQL are Public IP
gcloud sql instances describe app-postgres \
    --format="value(ipAddresses[0].ipAddress)"

# Verifică dacă Cloud Run are VPC Connector
gcloud run services describe app-backend \
    --region=europe-west1 \
    --format="value(spec.template.spec.containers[0].resources)"

# Dacă vezi "vpcAccess" → ai VPC
# Dacă NU vezi → setup simplu cu Public IP (ce ai tu)
```

**Test conexiune la Cloud SQL:**
```bash
# De pe local (dacă ai whitelisted IP-ul)
psql "host=YOUR_CLOUD_SQL_IP port=5432 dbname=tenant_registry user=postgres sslmode=require"

# Dacă funcționează → Cloud Run va funcționa la fel
```

---

**Document Version:** 1.1  
**Last Updated:** Februarie 13, 2026  
**Author:** GitHub Copilot  
**Changelog:**
- v1.1: Adăugat suport Public IP (simplificat pentru dev/test)
- v1.0: Versiune inițială (doar Private IP)

**💡 Pentru întrebări suplimentare despre infrastructură, consultă:**
- [MULTI-TENANT-ARCHITECTURE.md](MULTI-TENANT-ARCHITECTURE.md) - Arhitectura completă
- [AUTHENTICATION-FLOW-EXPLAINED.md](AUTHENTICATION-FLOW-EXPLAINED.md) - Flow autentificare
