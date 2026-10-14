#!/usr/bin/env sh
set -eu
SERVICE="human-appeal-media-plan"
REGION="${REGION:-me-central1}"
if [ -z "${PROJECT_ID:-}" ]; then
  printf "Google Cloud Project ID: "
  read PROJECT_ID
fi
gcloud config set project "$PROJECT_ID"
gcloud run deploy "$SERVICE" --source . --region "$REGION" --allow-unauthenticated
