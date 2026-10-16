# Chettinad Care Android / iOS parity

Updated 16 October 2026. Work started 15 October 2026. This file supersedes the earlier Android gap matrices and integration reports for the Android source on this branch.

## Source of truth

- iOS reference: `satyanarayanar17-dev/Chettinsd-care-IOS`, review branch `fix/shared-client-integration`. This includes the newer controlled pilot workflows at `61a13ee3b6b3a3b4eb564c1f7fe512eaa54febde` and the shared-client fixes. Both patient and staff schemes are verified by `.github/workflows/shared-client-ios.yml`.
- Shared backend: `satyanarayanar17-dev/CHINAD123`, deployment revision `cd3256223d32527a249e6c4b02b434b04035bcb0`.
- Android: existing `app-patient`, `app-staff` and `core` modules, Kotlin and Jetpack Compose.

## Implemented screens and workflows

| Area | Android implementation |
| --- | --- |
| Brand and navigation | Official iOS logo/icon, navy `#000666`, white cards, Home/Care plan/Appointments/Records plus Queue/Trends/Notifications/Profile |
| Patient access | Indian mobile OTP, challenge ID, expiry, resend cooldown, new-patient registration, session patient ID |
| Home | Next appointment, care-task progress and record shortcuts |
| Appointments | Directory, available slots, request, versioned cancellation/rescheduling, hospital confirmation state |
| Queue | Real token, status, patients ahead and estimated wait when the server returns them |
| Records | Signed prescriptions and printing/PDF, clinical notes, released lab results, private documents |
| Documents | PDF/JPEG/PNG upload with 10 MB limit, metadata, authenticated opening and appointment attachment/detachment |
| Care plan | Today's tasks, due-only patient responses, measurement/activity/medication/insulin status, optimistic versions, conflict reload |
| Measurements | Patient-reported glucose, blood pressure, weight, temperature, pulse, SpO2 and activities |
| Trends | Recorded values, charts, activity history and medication/insulin adherence history |
| Reminders | Opt-in Android notifications with generic private text; inexact alarms for today's loaded upcoming tasks |
| Doctor | Assigned live queue, historical patient-record search, identity-safe dossier, start consultation, encrypted local draft recovery, catalogue diagnoses/medicines, hospital draft save, sign/complete, assigned-doctor amendments with a reason, follow-up slots, lab orders/review |
| Nurse | Live queue, start triage, validated measured vitals, priority, handoff, care-plan/trend/record visibility, specimen collection/processing and explicitly verified/released results |
| Care management | Doctor plan/task creation, prescription-linked medication tasks, daily, selected-weekday, every-N-days and once-only schedules, response windows/reminders, task replacement/stopping with reason, effective date and reviewed plan/task revision checks; doctor/nurse review acknowledgement |
| Admin | Operational metrics, patient search/registration, booking/check-in, weekly doctor schedules, staff creation/access control and audit trail |
| Sessions | Encrypted persistent tokens/user/cookies, single-flight refresh, bounded retries, logout, device revocation, staff password change |

## Behavioral details

Patient, account and encounter IDs are distinct throughout the active native flows. The new portals replace the old speculative navigation; the old screen classes remain for historical tests and compatibility but are not reachable from the launchers.

Backend authorization is authoritative. The current deployed backend allows appointment confirmation/check-in for Admin/Doctor; Nurse sees requests but is not offered those actions. This differs from older iOS audit claims and does not weaken server roles.

All writes are explicit and single flight. Version conflicts are surfaced rather than silently overwriting records. Clinical notes are saved locally using app-private encrypted preferences scoped to staff account and encounter, then explicitly saved or signed against the hospital's note version. Local draft recovery is offered after reloading a visit. Recovery copies retain the reviewed patient/note versions, so reopening an older draft after a web/iOS edit cannot silently adopt newer signing versions. Legacy recovery copies require explicit hospital-draft review.

Appointment slots are bound to the doctor/date search that produced them, including across booking and rescheduling forms. Cancelled older searches cannot replace a newer result. Home care-plan progress counts recorded measurements, taken medication/insulin and completed activities; due, missed and skipped tasks do not count as completed. The staff login respects Android system-bar safe areas.

No clinical request bodies or credentials are written to HTTP logs. Backups are disabled. Refresh cookies retain secure, expiry, origin and path boundaries. Sessions and view-model stores are cleared when leaving authenticated UI. Production requires an HTTPS API URL ending in `/api/v1/`; cleartext is disabled.

## Build variants

- `demo`: configured to the same development demonstration API currently referenced by iOS. Debug builds may display a backend-issued development OTP, explicitly labelled synthetic testing only. This does not bypass the backend.
- `staging`: requires `STAGING_API_URL`/`-PSTAGING_API_URL`.
- `production`: requires `PRODUCTION_API_URL`/`-PPRODUCTION_API_URL`; requires HTTPS and `/api/v1/`. No development OTP is displayed in the production flavor.

Use JDK 21, Android platform 36, build tools 36 and Gradle 9.3.1:

```
gradle :core:testDemoDebugUnitTest :app-patient:testDemoDebugUnitTest :app-staff:testDemoDebugUnitTest
gradle :app-patient:assembleDemoDebug :app-staff:assembleDemoDebug
```

## Verification status

Current checks are published on [Android PR #1](https://github.com/satyanarayanar17-dev/chettinad-app/pull/1) and [iOS PR #2](https://github.com/satyanarayanar17-dev/Chettinsd-care-IOS/pull/2). Read the workflow results and artifact metadata for the exact tested commit and counts. Historical reports dated 15 October describe earlier snapshots.

The Android workflow builds both APKs, runs unit/contract/Compose/state tests, connects the real Kotlin API clients to a disposable synthetic backend, checks lint, runs native Keystore/session/cookie tests and installs/launches both apps on Android 35. The real HTTP journey covers a second client's saved-note and amendment visibility, signed identity, profile changes, recurring care tasks and patient responses, plus stale-write and role/ownership rejection. Native UI tests include an open care form becoming stale after a second app updates the plan.

The same workflow builds the web frontend and runs six Chromium clinical journeys against the same pinned API source. The iOS workflow runs both patient and staff schemes on an iPhone Simulator, including actual nurse/doctor view-model handoff, draft recovery, signing, PDF pagination, session refresh isolation and delayed-search regressions. It uses disposable SQLite fixtures. Opt-in live-account checks and UI targets that point at the public demo are excluded from automatic execution.

Live read-only verification on 16 October returned HTTP 200 for the development web app and API health endpoint after an initial API timeout. The API reports the pinned backend revision, 18/18 migrations, healthy database and clean integrity checks. The web bundle references the same API URL as the native demo configurations, and its exact-origin CORS preflight returned HTTP 204. This is a development SQLite deployment. No deployed clinical data was changed and no real SMS was sent.

These checks do not certify production PostgreSQL persistence, real SMS delivery, release signing/distribution, physical-phone file picking/printing/notifications, accessibility/font scaling, or identical iOS/Android screen layouts. Android also does not expose every backend administration endpoint. Complete hospital acceptance remains a device-level activity.

Reminder limitations match an app-refreshed daily model: notification permission is opt-in; alarms are inexact, only cover loaded upcoming tasks allowed by the doctor’s reminder setting and are refreshed on app use. Failed policy refreshes cancel local reminders until the policy is available. Reopening after a reboot refreshes them. No continuous background sync is claimed.
