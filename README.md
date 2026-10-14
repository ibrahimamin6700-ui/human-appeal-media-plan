# Human Appeal Media Plan

Installable PWA shell for the Human Appeal USA Media Plan dashboard.

## Architecture

- **Frontend / PWA:** this GitHub repository
- **Live dashboard:** Google Apps Script
- **Data source:** Google Sheets
- **Optional hosting:** GitHub Pages, Firebase Hosting, or Google Cloud Run

## First setup

1. Deploy the Apps Script Web App from `apps-script/`.
2. Open the PWA and paste the Apps Script `/exec` URL in Settings.
3. The URL is saved locally on the device.

## Main files

- `index.html` — PWA shell
- `config.js` — app configuration
- `manifest.webmanifest` — install metadata
- `sw.js` — offline shell/service worker
- `assets/` — Human Appeal logo and app icons
- `apps-script/` — Google Apps Script dashboard/backend source
- `Dockerfile` / `server.js` — Cloud Run hosting

Private project repository for the Human Appeal Media Plan dashboard.
