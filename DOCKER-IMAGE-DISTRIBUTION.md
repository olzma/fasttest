# Docker Image Distribution Guide

## Your Docker Image Location

Your Docker image `myapp:latest` has been exported to:

**File Location:** `C:\work\git\personal\spring-boot-fasttest\myapp-latest.tar`
**File Size:** ~113 MB

## What Are Docker Images?

Docker images are **not stored as regular files** on disk. They're stored in Docker's internal storage system (usually in `C:\ProgramData\Docker` on Windows, but in a special format).

To share or upload images, you need to **export** them first.

---

## Option 1: Upload TAR File to Cloud Storage (Simple)

### Step 1: Export Image (Already Done ✅)
```powershell
docker save -o myapp-latest.tar myapp:latest
```

### Step 2: Upload to Cloud
Upload `myapp-latest.tar` to your cloud storage:
- Google Drive
- Dropbox
- OneDrive
- AWS S3
- Azure Blob Storage
- Any file sharing service

### Step 3: Download and Load on Another Machine
```powershell
# On the target machine, download the tar file, then:
docker load -i myapp-latest.tar

# Verify the image is loaded
docker images myapp

# Run the container
docker run -d --name myapp-container -p 8079:8079 -e SPRING_PROFILES_ACTIVE=docker myapp:latest
```

**Pros:** ✅ Simple, no registry needed
**Cons:** ❌ Large file size, manual process

---

## Option 2: Push to Docker Hub (Recommended)

Docker Hub is a free container registry (similar to GitHub for Docker images).

### Step 1: Create Docker Hub Account
1. Go to https://hub.docker.com
2. Sign up for free account
3. Note your username (e.g., `yourusername`)

### Step 2: Login to Docker Hub
```powershell
docker login
# Enter your Docker Hub username and password
```

### Step 3: Tag Your Image
```powershell
# Format: docker tag <local-image> <dockerhub-username>/<image-name>:<tag>
docker tag myapp:latest yourusername/spring-boot-fasttest:latest
docker tag myapp:latest yourusername/spring-boot-fasttest:v1.0.0
```

### Step 4: Push to Docker Hub
```powershell
docker push yourusername/spring-boot-fasttest:latest
docker push yourusername/spring-boot-fasttest:v1.0.0
```

### Step 5: Pull on Any Machine
```powershell
# Anyone can now pull and run your image (if public)
docker pull yourusername/spring-boot-fasttest:latest
docker run -d --name myapp -p 8079:8079 -e SPRING_PROFILES_ACTIVE=docker yourusername/spring-boot-fasttest:latest
```

**Pros:** ✅ Industry standard, version control, automatic compression, easy to share
**Cons:** ❌ Public by default (private repos require paid plan)

---

## Option 3: Push to GitHub Container Registry (GHCR)

GitHub also offers free container registry with **unlimited private repositories**.

### Step 1: Create GitHub Personal Access Token
1. Go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token with `write:packages` and `read:packages` permissions
3. Copy the token (e.g., `ghp_xxxxxxxxxxxx`)

### Step 2: Login to GHCR
```powershell
$env:GITHUB_TOKEN = "ghp_xxxxxxxxxxxx"
echo $env:GITHUB_TOKEN | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

### Step 3: Tag Your Image
```powershell
# Format: docker tag <local-image> ghcr.io/<github-username>/<image-name>:<tag>
docker tag myapp:latest ghcr.io/yourgithubusername/spring-boot-fasttest:latest
```

### Step 4: Push to GHCR
```powershell
docker push ghcr.io/yourgithubusername/spring-boot-fasttest:latest
```

### Step 5: Pull on Any Machine
```powershell
docker pull ghcr.io/yourgithubusername/spring-boot-fasttest:latest
docker run -d --name myapp -p 8079:8079 -e SPRING_PROFILES_ACTIVE=docker ghcr.io/yourgithubusername/spring-boot-fasttest:latest
```

**Pros:** ✅ Free private repositories, integrated with GitHub
**Cons:** ❌ Requires GitHub account and token setup

---

## Option 4: Private Container Registry (AWS ECR, Azure ACR, GCP GCR)

If you're using cloud providers:

### AWS ECR (Elastic Container Registry)
```powershell
# Login
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin YOUR_AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com

# Tag
docker tag myapp:latest YOUR_AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/spring-boot-fasttest:latest

# Push
docker push YOUR_AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/spring-boot-fasttest:latest
```

### Azure ACR (Azure Container Registry)
```powershell
# Login
az acr login --name yourregistryname

# Tag
docker tag myapp:latest yourregistryname.azurecr.io/spring-boot-fasttest:latest

# Push
docker push yourregistryname.azurecr.io/spring-boot-fasttest:latest
```

---

## Comparison Table

| Method | Cost | Privacy | Ease of Use | Best For |
|--------|------|---------|-------------|----------|
| **TAR File** | Free | Private | Easy | One-time sharing, no internet on target |
| **Docker Hub** | Free (public) | Public by default | Very Easy | Open source projects, public sharing |
| **GitHub CR** | Free | Private available | Medium | Private projects, GitHub users |
| **AWS/Azure/GCP** | Paid | Private | Complex | Production deployments |

---

## Recommended Workflow

### For Learning/Testing:
1. Export to TAR file (`docker save`)
2. Upload to Google Drive/Dropbox
3. Share link

### For Real Projects:
1. Push to Docker Hub (public) or GitHub CR (private)
2. Use version tags (`:v1.0.0`, `:latest`)
3. Pull on deployment servers

---

## Quick Commands Reference

```powershell
# Export image to file
docker save -o myapp-latest.tar myapp:latest

# Compress the tar file (optional, saves space)
Compress-Archive -Path myapp-latest.tar -DestinationPath myapp-latest.tar.gz

# Load image from file (on another machine)
docker load -i myapp-latest.tar

# View all images
docker images

# Remove local image
docker rmi myapp:latest

# View image size and details
docker images myapp --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.ID}}"
```

---

## Your Current Setup

✅ **Image built:** `myapp:latest`
✅ **Exported to:** `C:\work\git\personal\spring-boot-fasttest\myapp-latest.tar`
✅ **File size:** 113.19 MB
✅ **Ready to upload:** Yes!

### Next Steps:
1. **For quick sharing:** Upload `myapp-latest.tar` to Google Drive/Dropbox
2. **For production:** Create Docker Hub account and push image
3. **For GitHub integration:** Use GitHub Container Registry

---

## Troubleshooting

### TAR file too large?
```powershell
# Compress it
Compress-Archive -Path myapp-latest.tar -DestinationPath myapp-latest.tar.gz
# This can reduce size by 30-50%
```

### Can't push to registry?
```powershell
# Make sure you're logged in
docker login

# Check your image name format
docker images
# Format must be: registry/username/image:tag
```

### Image not loading?
```powershell
# Verify tar file integrity
docker load -i myapp-latest.tar
# Should output: Loaded image: myapp:latest
```
