
# GitHub Actions Deployment Setup Guide

This guide will help you set up automatic deployment from GitHub to Google Cloud Run.

## Overview

The GitHub Actions workflow (`deploy-to-gcp.yml`) is configured for:
- ✅ **Manual trigger** (currently active) - Run from GitHub Actions tab
- 🔄 **Automatic on PR merge** (commented out) - Uncomment to enable

## Prerequisites

Before running the deployment, you need to set up:

1. **Google Cloud Project** with billing enabled
2. **Cloud SQL PostgreSQL instance** created
3. **Artifact Registry repository** created
4. **Service Account** with appropriate permissions
5. **GitHub Secrets** configured

---

## Step-by-Step Setup

### 1. Create GCP Resources

#### Create Artifact Registry Repository
```bash
gcloud artifacts repositories create fasttest-repo \
  --repository-format=docker \
  --location=europe-central2 \
  --description="FastTest Docker images"
```

#### Create Cloud SQL Instance
```bash
gcloud sql instances create fasttest-db-instance \
  --database-version=POSTGRES_15 \
  --tier=db-f1-micro \
  --region=europe-central2 \
  --root-password=YOUR_ROOT_PASSWORD
```

#### Create Database and User
```bash
# Create database
gcloud sql databases create fasttest_db --instance=fasttest-db-instance

# Create user
gcloud sql users create fasttest \
  --instance=fasttest-db-instance \
  --password=YOUR_SECURE_PASSWORD
```

#### Get Connection Name
```bash
gcloud sql instances describe fasttest-db-instance --format="value(connectionName)"
# Output example: fastesttest:europe-central2:fasttest-db-instance
```

---

### 2. Set Up Service Account (Workload Identity Federation - Recommended)

#### Option A: Workload Identity Federation (Most Secure - No Keys!)

**Step 1: Create Service Account**
```bash
export PROJECT_ID=fastesttest
export SA_NAME=github-actions-deployer

gcloud iam service-accounts create $SA_NAME \
  --display-name="GitHub Actions Deployer"
```

**Step 2: Grant Permissions**
```bash
# Cloud Run Admin
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/run.admin"

# Artifact Registry Writer
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"

# Cloud SQL Client
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"

# Service Account User (to deploy as this SA)
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser"
```

**Step 3: Create Workload Identity Pool**
```bash
export POOL_NAME=github-pool
export PROVIDER_NAME=github-provider
export REPO_OWNER=YOUR_GITHUB_USERNAME
export REPO_NAME=fasttest-main

# Create pool
gcloud iam workload-identity-pools create $POOL_NAME \
  --location="global" \
  --display-name="GitHub Actions Pool"

# Create provider
gcloud iam workload-identity-pools providers create-oidc $PROVIDER_NAME \
  --location="global" \
  --workload-identity-pool=$POOL_NAME \
  --display-name="GitHub Provider" \
  --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository" \
  --issuer-uri="https://token.actions.githubusercontent.com"

# Allow GitHub repo to impersonate service account
gcloud iam service-accounts add-iam-policy-binding \
  $SA_NAME@$PROJECT_ID.iam.gserviceaccount.com \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')/locations/global/workloadIdentityPools/$POOL_NAME/attribute.repository/$REPO_OWNER/$REPO_NAME"
```

**Step 4: Get Workload Identity Provider**
```bash
gcloud iam workload-identity-pools providers describe $PROVIDER_NAME \
  --location="global" \
  --workload-identity-pool=$POOL_NAME \
  --format="value(name)"

# Copy the output - looks like:
# projects/123456789/locations/global/workloadIdentityPools/github-pool/providers/github-provider
```

---

#### Option B: Service Account Key (Simpler but Less Secure)

```bash
export PROJECT_ID=fastesttest
export SA_NAME=github-actions-deployer

# Create service account and grant permissions (same as above)
gcloud iam service-accounts create $SA_NAME --display-name="GitHub Actions Deployer"

# Grant the same roles as shown in Option A

# Create and download key
gcloud iam service-accounts keys create key.json \
  --iam-account=$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com

# View the key (you'll paste this into GitHub Secrets)
cat key.json
```

---

### 3. Configure GitHub Secrets

Go to your GitHub repository: **Settings → Secrets and variables → Actions → New repository secret**

#### Required Secrets:

**For Workload Identity Federation (Option A):**
1. `GCP_WORKLOAD_IDENTITY_PROVIDER`
   - Value: The full provider name from Step 4 above
   - Example: `projects/123456789/locations/global/workloadIdentityPools/github-pool/providers/github-provider`

2. `GCP_SERVICE_ACCOUNT`
   - Value: Service account email
   - Example: `github-actions-deployer@fastesttest.iam.gserviceaccount.com`

**For Service Account Key (Option B):**
1. `GCP_SA_KEY`
   - Value: Entire contents of `key.json` file
   - Paste the full JSON content

**Common Secrets (Required for both options):**

3. `GCP_DB_INSTANCE_CONNECTION_NAME`
   - Value: Cloud SQL connection name
   - Example: `fastesttest:europe-central2:fasttest-db-instance`

4. `GCP_DB_PASSWORD`
   - Value: Database password for user 'fasttest'
   - Example: `YourSecurePassword123!`

---

### 4. Enable Required APIs

```bash
gcloud services enable \
  run.googleapis.com \
  sqladmin.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  iam.googleapis.com \
  iamcredentials.googleapis.com
```

---

### 5. Update Workflow File (If Using Service Account Key)

If you chose **Option B** (Service Account Key), edit `.github/workflows/deploy-to-gcp.yml`:

**Line 48-53:** Comment out Workload Identity and uncomment Service Account Key:

```yaml
- name: Authenticate to Google Cloud
  id: auth
  uses: google-github-actions/auth@v2
  with:
    # workload_identity_provider: ${{ secrets.GCP_WORKLOAD_IDENTITY_PROVIDER }}
    # service_account: ${{ secrets.GCP_SERVICE_ACCOUNT }}
    
    credentials_json: ${{ secrets.GCP_SA_KEY }}
```

---

## Running the Deployment

### Manual Deployment (Current Setup)

1. Go to your GitHub repository
2. Click **Actions** tab
3. Select **Deploy to Google Cloud Run** workflow
4. Click **Run workflow** button
5. Select environment (production/staging)
6. Click **Run workflow**
7. Watch the deployment progress in real-time

### Automatic Deployment on PR Merge

To enable automatic deployment when PRs are merged to main:

1. Edit `.github/workflows/deploy-to-gcp.yml`
2. **Uncomment lines 15-18:**

```yaml
on:
  workflow_dispatch:  # Keep manual trigger
    # ...existing config...
  
  # UNCOMMENT THESE LINES:
  push:
    branches:
      - main
      - master
```

3. Commit and push the change
4. Now every push to `main` or `master` will trigger deployment

---

## Testing Your Deployment

After deployment completes, test your API:

```bash
# Get the service URL from workflow output or:
gcloud run services describe spring-boot-fastesttest \
  --region europe-central2 \
  --format="value(status.url)"

# Test endpoints
export SERVICE_URL=<your-service-url>

# Test root endpoint
curl $SERVICE_URL/

# Test engineers endpoint
curl $SERVICE_URL/api/v1/engineers/dummy

# Create an engineer
curl -X POST $SERVICE_URL/api/v1/engineers \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","techStack":"Java, Spring Boot"}'
```

---

## Troubleshooting

### Build Fails
- Check Docker build logs in Actions tab
- Verify `pom.xml` has no errors
- Ensure `application-gcp.properties` exists

### Authentication Fails
- Verify service account has correct permissions
- Check secret values are correct (no extra spaces)
- For Workload Identity: Verify pool and provider are created correctly

### Cloud SQL Connection Fails
- Verify `--add-cloudsql-instances` matches connection name
- Check database user exists and password is correct
- Ensure service account has `cloudsql.client` role

### Deployment Succeeds but App Crashes
- Check Cloud Run logs: `gcloud logging read --limit 50 --format json`
- Verify environment variables are set correctly
- Check that GCP profile enables correct features

### View Logs
```bash
# Stream logs
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=spring-boot-fastesttest" \
  --limit 50 \
  --format json

# Or use Cloud Console:
# https://console.cloud.google.com/run?project=fastesttest
```

---

## Security Best Practices

1. ✅ **Use Workload Identity Federation** (no long-lived keys)
2. ✅ **Store sensitive data in GitHub Secrets** (never in code)
3. ✅ **Use least-privilege IAM roles**
4. ✅ **Enable Cloud SQL SSL** in production
5. ✅ **Use Secret Manager** for production passwords
6. ✅ **Set up monitoring and alerting**
7. ✅ **Implement health checks**

---

## Cost Optimization

- **Cloud Run**: Pay per request, scales to zero
- **Cloud SQL**: Consider f1-micro for dev/staging
- **Artifact Registry**: Free tier available
- **Minimize container size**: Use multi-stage builds (already done!)

---

## Next Steps

1. ✅ Complete the setup steps above
2. ✅ Run a manual deployment to test
3. ✅ Verify the application works on Cloud Run
4. ✅ Set up monitoring and alerts
5. 🔄 Enable automatic deployment on PR merge
6. 🔄 Add staging environment
7. 🔄 Implement blue/green deployments

---

## Support

- **GitHub Actions Logs**: Check the Actions tab for detailed logs
- **Cloud Run Logs**: Use Cloud Console or `gcloud logging read`
- **GCP Documentation**: https://cloud.google.com/run/docs
- **GitHub Actions with GCP**: https://github.com/google-github-actions
