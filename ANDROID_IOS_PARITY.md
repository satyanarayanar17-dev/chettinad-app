# Chettinad Care Android / iOS parity

Work started 15 October 2026. This file supersedes the earlier Android gap matrices and integration reports for the Android source on this branch.

## Source of truth

- iOS reference: `satyanarayanar17-dev/Chettinsd-care-IOS`, master at `649d9f5fa2fe1eaac354e7670871bb677d3bb240`, inspected locally.
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

Final test suite (the workflow results on PR #1 record pass/fail status):

| Suite | Tests |
| --- | ---: |
| Core contracts, state and native UI | 23 |
| Patient | 7 |
| Staff | 6 |
| Total | 36 |

Both APKs and the instrumentation test APK build successfully. Both application signatures verify with `apksigner`; package IDs are distinct, minSdk 24 and targetSdk 36. Rendered Patient Home and Staff Queue screens were inspected and the bottom-navigation labels corrected for compact widths.

The backend's patient portal/document, care-plan and authentication/session suites pass against isolated synthetic SQLite databases. The additional OTP suite passes 16 tests, including the production route with simulated external SMS transport. None of these tests sent real SMS or changed the deployed database.

Android Keystore persistence passed both native tests on an Android 35 emulator in CI. The initial launcher run installed both apps and captured both login screens, but its staff assertion used `Password / PIN` instead of the actual `Password / Secure PIN` label. The corrected check requires each app's own package, title, input label and sign-in action, verifies launch status/process presence and both installed package IDs, and captures screenshots/logs on failure. Replaying the recovered screen dumps confirms it accepts the correct screens and rejects a wrong screen, a missing package and a failed launch. The full emulator rerun status is recorded on PR #1; physical-handset acceptance and Google Play release signing remain separate from debug APK builds.

The iOS-configured server health check reported a development SQLite fixture, not a production PostgreSQL deployment. The Android code does not change that deployment. Real-patient acceptance still needs production configuration/SMS delivery plus physical-device workflow checks.

Reminder limitations match an app-refreshed daily model: notification permission is opt-in; alarms are inexact, only cover loaded upcoming tasks and are refreshed on app use. Reopening after a reboot refreshes them. No continuous background sync is claimed.
