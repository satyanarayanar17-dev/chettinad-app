# Android patient access — 15 October 2026

Branch: `feat/patient-otp-access`, based on Android main `8d22542`.

Implemented the first patient-access work package in the existing Kotlin/Compose app:

- Replaced the patient screen's staff-password login with OTP request/verification.
- Forward optional challenge IDs; accept backend responses without token_type.
- Load the authenticated session and use patient_id for record ownership, never userId.
- Collect the required registration demographics after PROFILE_REQUIRED, retaining the OTP in memory for resubmission.
- Handle invalid codes, SMS availability, rate limits, and duplicate in-flight submissions.
- Do not refresh/replay failed OTP requests or password logins.
- Added repository tests for identity ownership, missing ownership, and PROFILE_REQUIRED; updated role isolation and screen tests; added phone normalization tests.

Contract checked against `CHINAD123` branch `fix/pre-pilot-blockers-v2` at `b88eb92`, specifically backend/opd/auth.ts and backend/opd/core.ts. The September local integration report describes newer unpushed Mac changes; they are unavailable in this checkout and were not overwritten. Challenge ID support is optional because the available backend revision does not emit it.

Validation: `git diff --check` passes. Android build, unit tests, emulator, handset, and live SMS integration have NOT passed in this workspace. It initially has JDK 17 but no Gradle executable, Android SDK, or adb. Downloaded Gradle 9.3.1 and attempted `:core:testDemoDebugUnitTest :app-patient:testDemoDebugUnitTest :app-staff:testDemoDebugUnitTest --no-daemon`. The run failed during settings/plugin resolution: `org.gradle.toolchains.foojay-resolver-convention:1.0.0` could not be resolved from the configured repositories, before compilation or tests. CI remains the next build/test gate. No APK from this change is claimed ready.

Next: run Patient/Core/Staff regression tests and APK builds, verify on an Android device against a dedicated synthetic-data backend, then patient booking and records/prescriptions parity. Real-patient use remains dependent on the backend's production SMS/DLT and deployment validation.

Remote publication: push was rejected by automatic approval review because publication to the public repository needs explicit authorization. Changes remain committed locally.
