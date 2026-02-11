# GitHub Actions Deployment - Quick Reference

## 🚀 Deploy Manually from GitHub

1. Go to: `https://github.com/YOUR_USERNAME/fasttest-main/actions`
2. Click: **Deploy to Google Cloud Run**
3. Click: **Run workflow**
4. Select: **production**
5. Click: **Run workflow** button

---

## 🔄 Enable Automatic Deployment on PR Merge

Edit `.github/workflows/deploy-to-gcp.yml` and uncomment:

```yaml
push:
  branches:
    - main
```

---

## 📋 Required GitHub Secrets

Go to: **Settings → Secrets and variables → Actions**

### Workload Identity (Recommended):
- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_SERVICE_ACCOUNT`

### OR Service Account Key:
- `GCP_SA_KEY`

### Database:
- `GCP_DB_INSTANCE_CONNECTION_NAME`
- `GCP_DB_PASSWORD`

---

## ⚡ Quick Setup Commands

```bash
# 1. Create Artifact Registry
gcloud artifacts repositories create fasttest-repo \
  --repository-format=docker \
  --location=europe-central2

# 2. Create Cloud SQL Instance
gcloud sql instances create fasttest-db-instance \
  --database-version=POSTGRES_15 \
  --tier=db-f1-micro \
  --region=europe-central2

# 3. Create Database
gcloud sql databases create fasttest_db \
  --instance=fasttest-db-instance

# 4. Create User
gcloud sql users create fasttest \
  --instance=fasttest-db-instance \
  --password=YOUR_PASSWORD

# 5. Get Connection Name (save for GitHub Secrets)
gcloud sql instances describe fasttest-db-instance \
  --format="value(connectionName)"

# 6. Create Service Account
gcloud iam service-accounts create github-actions-deployer

# 7. Grant Permissions
export PROJECT_ID=fastesttest
export SA_EMAIL=github-actions-deployer@$PROJECT_ID.iam.gserviceaccount.com

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/run.admin"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/artifactregistry.writer"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/cloudsql.client"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/iam.serviceAccountUser"

# 8. Create Service Account Key (Simple Method)
gcloud iam service-accounts keys create key.json \
  --iam-account=$SA_EMAIL

# Copy contents of key.json to GitHub Secret: GCP_SA_KEY
cat key.json
```

---

## 🧪 Test Deployment

```bash
# Get service URL
gcloud run services describe spring-boot-fastesttest \
  --region europe-central2 \
  --format="value(status.url)"

# Test API
curl https://YOUR-SERVICE-URL/api/v1/engineers/dummy
```

---

## 📊 Monitor

```bash
# View logs
gcloud logging read "resource.type=cloud_run_revision" \
  --limit 50

# View service details
gcloud run services describe spring-boot-fastesttest \
  --region europe-central2
```

---

## 🔧 Update Configuration

### Change environment variables:
```bash
gcloud run services update spring-boot-fastesttest \
  --region europe-central2 \
  --set-env-vars "KEY=VALUE"
```

### Rollback deployment:
```bash
gcloud run services update-traffic spring-boot-fastesttest \
  --region europe-central2 \
  --to-revisions PREVIOUS_REVISION=100
```

---

## 📁 File Structure

```
fasttest-main/
├── .github/
│   └── workflows/
│       └── deploy-to-gcp.yml          # GitHub Actions workflow
├── src/
│   └── main/
│       └── resources/
│           ├── application.properties
│           ├── application-docker.properties  # Local
│           └── application-gcp.properties     # GCP
├── gcp-deploy.ps1                     # Local deployment script
├── GITHUB-ACTIONS-DEPLOYMENT.md       # Full setup guide
├── SPRING-PROFILES-GUIDE.md           # Profile documentation
└── QUICK-REFERENCE.md                 # This file
```
