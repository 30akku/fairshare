# FairShare

FairShare is an open-source Android expense-sharing beta with a Flutter client and a Java/Spring Boot API. It supports multiple payers, equal/exact/percentage splits, per-currency balances, settlement suggestions, group invite links, recurring expenses, activity history, notifications, and profile images.

## Repository layout

- `backend/` — Java 21, Spring Boot 4.1.1, PostgreSQL, Flyway, Firebase Admin, and OpenAPI.
- `mobile/` — Flutter Android application (`com.anshi.fairshare`).
- `docs/` — GitHub Pages download, invitation, and privacy pages.

## Run locally

Requirements: Java 21, Maven 3.9+, PostgreSQL 16+ (or Docker), Flutter stable, and the Android SDK.

1. Start PostgreSQL with `docker compose up -d postgres` or provide an existing database.
2. Start the API from `backend/` using `mvn -s .mvn/settings.xml spring-boot:run -Dspring-boot.run.profiles=local`.
3. Local authentication uses `X-Dev-User: you@example.com`. Swagger UI is available at `http://localhost:8080/swagger-ui.html`.
4. Create a free Firebase project and place its Android configuration at `mobile/android/app/google-services.json`; the Flutter shell initializes Firebase even when the API uses its local development identity.
5. In `mobile/`, run `flutter pub get`, then start an emulator with:

   ```text
   flutter run --dart-define=DEV_USER=you@example.com --dart-define=API_BASE_URL=http://10.0.2.2:8080
   ```

For real authentication, create a Firebase project, enable Email/Password and Google providers, run `flutterfire configure`, place the generated Android configuration at `mobile/android/app/google-services.json`, and provide `FIREBASE_SERVICE_ACCOUNT_JSON` to the backend. Register the release signing certificate SHA-1 and SHA-256 fingerprints in Firebase for Google sign-in.

## Backend configuration

Copy `backend/.env.example` values into your environment. Production requires a Neon pooled PostgreSQL URL, a random maintenance key, Firebase service-account credentials, and Cloudinary credentials. The database schema is created only through Flyway migrations.

All authenticated API routes use `/api/v1`. Financial create requests require an `Idempotency-Key` header. Updates and deletes require the current numeric version in `If-Match`. Money and percentage values are decimal strings, never floating-point JSON numbers.

## Free deployment

1. Push the repository publicly to GitHub and replace the example `anshi/fairshare` URLs in `docs/` with the actual repository owner/name if different.
2. Create a free Neon project and a Render free web service using `render.yaml`. Do not add a payment method if hard zero-cost behavior is required.
3. Add Render environment values listed in `backend/.env.example`; set `PUBLIC_BASE_URL` to the GitHub Pages project URL, such as `https://USER.github.io/fairshare`.
4. Enable GitHub Pages with **GitHub Actions** as the source. Add `ANDROID_SHA256_FINGERPRINT` so the Pages workflow produces a verified `assetlinks.json`.
5. Add repository secrets: `API_BASE_URL`, `MAINTENANCE_KEY`, `GOOGLE_SERVICES_JSON`, `ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, and `KEY_ALIAS`.
6. Tag a release such as `v0.1.0`. The release workflow builds `fairshare.apk`, publishes its SHA-256 checksum, and makes it available to the website.

Never commit Firebase service-account JSON, Cloudinary secrets, passwords, or the Android signing keystore. Keep the keystore backed up separately; losing it prevents compatible APK updates.

## Testing

- Backend: `mvn -s .mvn/settings.xml verify`
- Flutter: `flutter analyze && flutter test`

The GitHub workflows run these checks for every relevant pull request. The daily recurrence workflow safely retries the idempotent maintenance endpoint; the application also performs a daily catch-up after waking from a free-hosting cold start.

## Known beta limitations

The app is online-only. It does not convert currencies, support weighted/share-unit splits, process real payments, scan receipts, or provide a production uptime/backup SLA. Render can take roughly a minute to wake after inactivity, and all free providers may pause service when quotas are exhausted.
