# Chettinad Care Android / iOS parity

Work started 15 October 2026. This file supersedes the earlier Android gap matrices and integration reports for the Android source on this branch.

## Source of truth

- iOS reference: `satyanarayanar17-dev/Chettinsd-care-IOS`, master, inspected locally.
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
| Doctor | Assigned live queue, identity-safe dossier, start consultation, encrypted local draft recovery, catalogue diagnoses/medicines, hospital draft save, sign/complete, follow-up slots, lab orders/review |
| Nurse | Live queue, start triage, validated measured vitals, priority, handoff, care-plan/trend/record visibility, specimen collection/processing and explicitly verified/released results |
| Care management | Doctor plan/task creation, prescription-linked medication tasks, task replacement/stopping with reason, effective date and revision checks; doctor/nurse review acknowledgement |
| Admin | Operational metrics, patient search/registration, booking/check-in, weekly doctor schedules, staff creation/access control and audit trail |
| Sessions | Encrypted persistent tokens/user/cookies, single-flight refresh, bounded retries, logout, device revocation, staff password change |

## Behavioral details

Patient, account and encounter IDs are distinct throughout the active native flows. The new portals replace the old speculative navigation; the old screen classes remain for historical tests and compatibility but are not reachable from the launchers.

Backend authorization is authoritative. The current deployed backend allows appointment confirmation/check-in for Admin/Doctor; Nurse sees requests but is not offered those actions. This differs from older iOS audit claims and does not weaken server roles.

All writes are explicit and single flight. Version conflicts are surfaced rather than silently overwriting records. Clinical notes are saved locally using app-private encrypted preferences scoped to staff account and encounter, then explicitly saved or signed against the hospital's note version. Local draft recovery is offered after reloading a visit.

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

All three modules compiled locally. Expanded contract, concurrency, encrypted-session, cookie-boundary and native UI tests are being run; final counts and APK checksums will be written after the final verification run. Handset acceptance and Google Play release signing are separate from a debug APK build.

The iOS-configured server health check reported a development SQLite fixture, not a production PostgreSQL deployment. The Android code does not change that deployment. Real-patient acceptance still needs production configuration/SMS delivery plus physical-device workflow checks.

Reminder limitations match an app-refreshed daily model: notification permission is opt-in; alarms are inexact, only cover loaded upcoming tasks and are refreshed on app use. Reopening after a reboot refreshes them. No continuous background sync is claimed.
