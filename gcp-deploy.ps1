# GCP Deployment Script for Spring Boot FastTest
# This script contains the command to deploy your existing Docker image to Google Cloud Run.
# It sets the necessary environment variables to enable the Cloud SQL connection.

# ----------------- CONFIGURATION -----------------
$PROJECT_ID = "fastesttest"           # e.g., my-gcp-project
$REGION = "europe-central2"                   # e.g., us-central1
$REPO_NAME = "fasttest-repo"             # Your Artifact Registry repository name
$APP_NAME = "spring-boot-fastesttest"
$IMAGE_NAME = "$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$APP_NAME"
# Get this from GCP Console -> SQL -> Connection name
$DB_INSTANCE_NAME = "project:region:instance"
$DB_USER = "fasttest"                     # Create this user in GCP SQL Console
$DB_PASS = "password"                     # Create this user in GCP SQL Console
# -------------------------------------------------

Write-Host "Deploying $APP_NAME to Cloud Run..."

# Note: You must build and push the image first!
# docker build -t $IMAGE_NAME .
# docker push $IMAGE_NAME

gcloud run deploy $APP_NAME `
  --image $IMAGE_NAME `
  --platform managed `
  --region $REGION `
  --allow-unauthenticated `
  --port 8080 `
  --set-env-vars "SPRING_PROFILES_ACTIVE=docker" `
  --set-env-vars "SPRING_CLOUD_GCP_SQL_ENABLED=true" `
  --set-env-vars "SPRING_CLOUD_GCP_SQL_INSTANCE_CONNECTION_NAME=$DB_INSTANCE_NAME" `
  --set-env-vars "SPRING_DATASOURCE_USERNAME=$DB_USER" `
  --set-env-vars "SPRING_DATASOURCE_PASSWORD=$DB_PASS" `
  --set-env-vars "SPRING_JPA_HIBERNATE_DDL_AUTO=update"

# Explanation of Env Vars:
# SPRING_PROFILES_ACTIVE=docker          -> Uses application-docker.properties
# SPRING_CLOUD_GCP_SQL_ENABLED=true      -> Activates the Google Cloud SQL socket factory
# SPRING_DATASOURCE_USERNAME/PASSWORD    -> Overrides the local defaults
# SPRING_JPA_HIBERNATE_DDL_AUTO=update   -> Automatically creates/updates tables (Crucial! Default is create-drop)
