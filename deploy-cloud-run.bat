@echo off
setlocal
set SERVICE=human-appeal-media-plan
set REGION=me-central1
set /p PROJECT_ID=Enter Google Cloud Project ID: 
if "%PROJECT_ID%"=="" (
  echo Project ID is required.
  pause
  exit /b 1
)

gcloud config set project %PROJECT_ID%
gcloud run deploy %SERVICE% --source . --region %REGION% --allow-unauthenticated

echo.
echo Deployment finished. Open the Cloud Run URL and connect your Apps Script /exec URL.
pause
