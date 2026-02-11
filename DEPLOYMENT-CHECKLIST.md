# GitHub Actions Deployment Checklist

Use this checklist to ensure you complete all setup steps correctly.

---

## ☐ Phase 1: GCP Project Setup

### ☐ 1.1 Enable Required APIs
```bash
gcloud services enable \
  run.googleapis.com \
  sqladmin.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  iam.googleapis.com \
  iamcredentials.googleapis.com
```

### ☐ 1.2 Set Default Project
```bash
gcloud config set project fastesttest
```

### ☐ 1.3 Verify Billing is Enabled
- Go to: https://console.cloud.google.com/billing
- Ensure project `fastesttest` has billing enabled

---

## ☐ Phase 2: Create GCP Resources

### ☐ 2.1 Create Artifact Registry Repository
```bash
gcloud artifacts repositories create fasttest-repo \
  --repository-format=docker \
  --location=europe-central2 \
  --description="FastTest Docker images"
```
**Verify:** `gcloud artifacts repositories list`

### ☐ 2.2 Create Cloud SQL Instance
```bash
gcloud sql instances create fasttest-db-instance \
  --database-version=POSTGRES_15 \
  --tier=db-f1-micro \
  --region=europe-central2 \
  --root-password=CHANGE_ME_ROOT_PASSWORD
```
**Verify:** `gcloud sql instances list`

⏱️ **Note:** This takes 5-10 minutes

### ☐ 2.3 Create Database
```bash
gcloud sql databases create fasttest_db \
  --instance=fasttest-db-instance
```
**Verify:** `gcloud sql databases list --instance=fasttest-db-instance`

### ☐ 2.4 Create Database User
```bash
gcloud sql users create fasttest \
  --instance=fasttest-db-instance \
  --password=CHANGE_ME_USER_PASSWORD
```
**Verify:** `gcloud sql users list --instance=fasttest-db-instance`

### ☐ 2.5 Get Cloud SQL Connection Name
```bash
gcloud sql instances describe fasttest-db-instance \
  --format="value(connectionName)"
```
**Save this value:** ___________________________________
**Example:** `fastesttest:europe-central2:fasttest-db-instance`

---

## ☐ Phase 3: Service Account Setup

### Choose ONE method:

---

### Option A: Service Account Key (Simpler - Recommended for Getting Started)

#### ☐ 3A.1 Create Service Account
```bash
gcloud iam service-accounts create github-actions-deployer \
  --display-name="GitHub Actions Deployer"
```

#### ☐ 3A.2 Set Environment Variables
```bash
export PROJECT_ID=fastesttest
export SA_EMAIL=github-actions-deployer@$PROJECT_ID.iam.gserviceaccount.com
```

#### ☐ 3A.3 Grant Cloud Run Admin
```bash
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/run.admin"
```

#### ☐ 3A.4 Grant Artifact Registry Writer
```bash
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/artifactregistry.writer"
```

#### ☐ 3A.5 Grant Cloud SQL Client
```bash
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/cloudsql.client"
```

#### ☐ 3A.6 Grant Service Account User
```bash
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/iam.serviceAccountUser"
```

#### ☐ 3A.7 Create Service Account Key
```bash
gcloud iam service-accounts keys create key.json \
  --iam-account=$SA_EMAIL
```

#### ☐ 3A.8 View Key Contents (for GitHub Secret)
```bash
cat key.json
```
**Copy entire JSON content for GitHub Secrets**

#### ☐ 3A.9 Update Workflow File
Edit `.github/workflows/deploy-to-gcp.yml`, line 48-53:
```yaml
# Comment out these lines:
# workload_identity_provider: ${{ secrets.GCP_WORKLOAD_IDENTITY_PROVIDER }}
# service_account: ${{ secrets.GCP_SERVICE_ACCOUNT }}

# Uncomment this line:
credentials_json: ${{ secrets.GCP_SA_KEY }}
```

---

### Option B: Workload Identity Federation (More Secure - No Keys!)

#### ☐ 3B.1 Create Service Account
```bash
gcloud iam service-accounts create github-actions-deployer \
  --display-name="GitHub Actions Deployer"
```

#### ☐ 3B.2 Set Environment Variables
```bash
export PROJECT_ID=fastesttest
export SA_EMAIL=github-actions-deployer@$PROJECT_ID.iam.gserviceaccount.com
export REPO_OWNER=YOUR_GITHUB_USERNAME
export REPO_NAME=fasttest-main
```

#### ☐ 3B.3 Grant Permissions (same as 3A.3-3A.6)
Run commands from 3A.3 through 3A.6 above

#### ☐ 3B.4 Create Workload Identity Pool
```bash
gcloud iam workload-identity-pools create github-pool \
  --location="global" \
  --display-name="GitHub Actions Pool"
```

#### ☐ 3B.5 Create Workload Identity Provider
```bash
gcloud iam workload-identity-pools providers create-oidc github-provider \
  --location="global" \
  --workload-identity-pool=github-pool \
  --display-name="GitHub Provider" \
  --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository" \
  --issuer-uri="https://token.actions.githubusercontent.com"
```

#### ☐ 3B.6 Allow GitHub to Impersonate Service Account
```bash
export PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')

gcloud iam service-accounts add-iam-policy-binding $SA_EMAIL \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/attribute.repository/$REPO_OWNER/$REPO_NAME"
```

#### ☐ 3B.7 Get Workload Identity Provider Name
```bash
gcloud iam workload-identity-pools providers describe github-provider \
  --location="global" \
  --workload-identity-pool=github-pool \
  --format="value(name)"
```
**Save this value:** ___________________________________
**Example:** `projects/123456789/locations/global/workloadIdentityPools/github-pool/providers/github-provider`

---

## ☐ Phase 4: Configure GitHub Secrets

### ☐ 4.1 Navigate to GitHub Secrets
1. Go to: `https://github.com/YOUR_USERNAME/fasttest-main`
2. Click: **Settings**
3. Click: **Secrets and variables** → **Actions**
4. Click: **New repository secret**

### If Using Service Account Key (Option A):

#### ☐ 4.2 Add GCP_SA_KEY
- Name: `GCP_SA_KEY`
- Value: Paste entire contents of `key.json`
- Click: **Add secret**

### If Using Workload Identity (Option B):

#### ☐ 4.2 Add GCP_WORKLOAD_IDENTITY_PROVIDER
- Name: `GCP_WORKLOAD_IDENTITY_PROVIDER`
- Value: Paste the provider name from step 3B.7
- Click: **Add secret**

#### ☐ 4.3 Add GCP_SERVICE_ACCOUNT
- Name: `GCP_SERVICE_ACCOUNT`
- Value: `github-actions-deployer@fastesttest.iam.gserviceaccount.com`
- Click: **Add secret**

### For Both Options:

#### ☐ 4.4 Add GCP_DB_INSTANCE_CONNECTION_NAME
- Name: `GCP_DB_INSTANCE_CONNECTION_NAME`
- Value: Connection name from step 2.5
- Example: `fastesttest:europe-central2:fasttest-db-instance`
- Click: **Add secret**

#### ☐ 4.5 Add GCP_DB_PASSWORD
- Name: `GCP_DB_PASSWORD`
- Value: Password you used in step 2.4
- Click: **Add secret**

### ☐ 4.6 Verify All Secrets
Should see:
- Option A: `GCP_SA_KEY`, `GCP_DB_INSTANCE_CONNECTION_NAME`, `GCP_DB_PASSWORD`
- Option B: `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_SERVICE_ACCOUNT`, `GCP_DB_INSTANCE_CONNECTION_NAME`, `GCP_DB_PASSWORD`

---

## ☐ Phase 5: Push Workflow to GitHub

### ☐ 5.1 Commit and Push Workflow Files
```bash
git add .github/workflows/deploy-to-gcp.yml
git add src/main/resources/application-gcp.properties
git add GITHUB-ACTIONS-DEPLOYMENT.md
git add DEPLOYMENT-SUMMARY.md
git add QUICK-REFERENCE.md
git add SPRING-PROFILES-GUIDE.md
git add .gcloudignore

git commit -m "Add GitHub Actions deployment workflow"
git push origin main
```

### ☐ 5.2 Verify Workflow Exists in GitHub
1. Go to repository on GitHub
2. Click **Actions** tab
3. Should see: **Deploy to Google Cloud Run** workflow

---

## ☐ Phase 6: Run First Deployment

### ☐ 6.1 Trigger Manual Deployment
1. Go to: **Actions** tab
2. Click: **Deploy to Google Cloud Run**
3. Click: **Run workflow** button (right side)
4. Select: **production**
5. Click: **Run workflow** green button

### ☐ 6.2 Monitor Deployment
- Watch the workflow run in real-time
- Each step should turn green ✅
- Total time: ~5-7 minutes

### ☐ 6.3 Get Service URL
- At the end of the deployment, you'll see the service URL
- Or run: `gcloud run services describe spring-boot-fastesttest --region europe-central2 --format="value(status.url)"`
**Service URL:** ___________________________________

---

## ☐ Phase 7: Test Deployment

### ☐ 7.1 Test Root Endpoint
```bash
curl https://YOUR_SERVICE_URL/
```
**Expected:** "Hello World Spring Boot!"

### ☐ 7.2 Test Dummy Engineers Endpoint
```bash
curl https://YOUR_SERVICE_URL/api/v1/engineers/dummy
```
**Expected:** JSON array with 3 engineers

### ☐ 7.3 Create an Engineer
```bash
curl -X POST https://YOUR_SERVICE_URL/api/v1/engineers \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Engineer","techStack":"Java, Spring Boot, GCP"}'
```
**Expected:** JSON with created engineer (includes ID)

### ☐ 7.4 Get All Engineers
```bash
curl https://YOUR_SERVICE_URL/api/v1/engineers
```
**Expected:** JSON array with the engineer you just created

---

## ☐ Phase 8: Enable Auto-Deployment (Optional)

### ☐ 8.1 Edit Workflow File
Open `.github/workflows/deploy-to-gcp.yml`

### ☐ 8.2 Uncomment Auto-Deploy Trigger
Find lines 15-18 and uncomment:
```yaml
push:
  branches:
    - main
    - master
```

### ☐ 8.3 Commit and Push
```bash
git add .github/workflows/deploy-to-gcp.yml
git commit -m "Enable automatic deployment on push to main"
git push origin main
```

### ☐ 8.4 Verify Auto-Deploy
- This push should trigger an automatic deployment
- Go to **Actions** tab to see it running

---

## ☐ Phase 9: Clean Up (Optional)

### ☐ 9.1 Delete Service Account Key (if using Option A)
```bash
rm key.json
```

### ☐ 9.2 Add key.json to .gitignore (already done in .gcloudignore)

---

## ✅ Completion Checklist

After completing all phases, you should have:

- ✅ GCP project with billing enabled
- ✅ Cloud SQL instance running
- ✅ Database and user created
- ✅ Artifact Registry repository created
- ✅ Service account with correct permissions
- ✅ GitHub secrets configured
- ✅ Workflow file in repository
- ✅ Successful manual deployment
- ✅ Working application on Cloud Run
- ✅ Tested all API endpoints
- ✅ (Optional) Auto-deploy enabled

---

## 🆘 Troubleshooting

### Deployment Fails with "Permission Denied"
→ Check service account has all 4 roles (run.admin, artifactregistry.writer, cloudsql.client, iam.serviceAccountUser)

### "Cloud SQL instance not found"
→ Verify `GCP_DB_INSTANCE_CONNECTION_NAME` secret matches exactly the connection name

### "Authentication failed"
→ Verify `GCP_SA_KEY` is the complete JSON (starts with `{` and ends with `}`)

### Application crashes after deploy
→ Run: `gcloud logging read "resource.type=cloud_run_revision" --limit 50`

### Can't see workflow in Actions tab
→ Ensure `.github/workflows/deploy-to-gcp.yml` is pushed to `main` or `master` branch

---

## 📞 Need Help?

Refer to these files:
1. **Full Setup Guide:** `GITHUB-ACTIONS-DEPLOYMENT.md`
2. **Quick Commands:** `QUICK-REFERENCE.md`
3. **Overview:** `DEPLOYMENT-SUMMARY.md`
4. **Spring Profiles:** `SPRING-PROFILES-GUIDE.md`

---

**Last Updated:** February 11, 2026
**Time to Complete:** ~30-45 minutes (first time)
