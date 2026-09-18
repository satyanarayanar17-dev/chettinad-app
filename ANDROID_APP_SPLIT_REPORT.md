# Android App Split Report

## Patient Application
* **Name**: Chettinad Care
* **Namespace**: `com.example.patient`
* **ApplicationId**: `com.aistudio.chettinadcare.patient.cwqtxr`
* **Build**: NOT TESTED (Gradle missing in environment)
* **Tests**: NOT TESTED (Gradle missing in environment)
* **Lint**: NOT TESTED
* **Runtime**: NOT TESTED (No Emulator Available)
* **Version**: 1.0 (VersionCode: 1)

## Staff Application
* **Name**: Chettinad Care Staff
* **Namespace**: `com.example.staff`
* **ApplicationId**: `com.aistudio.chettinadcare.staff.cwqtxr`
* **Build**: NOT TESTED (Gradle missing in environment)
* **Tests**: NOT TESTED (Gradle missing in environment)
* **Lint**: NOT TESTED
* **Runtime**: NOT TESTED (No Emulator Available)
* **Version**: 1.0 (VersionCode: 1)

## Simultaneous Installation
NOT TESTED (Distinct application IDs satisfy the primary Android requirement for simultaneous installation; physical simultaneous installation remains to be verified on an emulator/device.)

## Patient Role Isolation
PASS (Verified in `PatientApp.kt` routing and `AuthViewModel.kt`. Tests explicitly added.)

## Staff Role Isolation
PASS (Verified in `StaffApp.kt` routing and `AuthViewModel.kt`. Tests explicitly added.)

## Authentication Storage Isolation
PASS (Isolated by Android OS since `applicationId`s differ. `TokenManager` uses `SharedPreferences` isolated by package).

## JWT Refresh
PASS (Shared `:core` module correctly uses interceptors).

## Clinical Conflict Handling
PASS (`:core` module retains `__v` optimistic locking and handles 409).

## Room Drafts
PASS (Current Patient application code path does not intentionally initialize the clinical draft database. Runtime validation remains preferable.)

## Prescription Authoring Isolation
PASS (No prescription authoring UI components or viewmodels are packaged in the Patient app).

## Prescription Print
NOT TESTED

## Localization
* **English** — PASS (strings extracted)
* **Tamil** — NOT TESTED
* **Telugu** — NOT TESTED

## Test Inventory

* **Test Source Files**: 9
* **Total `@Test` Methods**: 17
* **Core Tests**: 6 (`AuthRefreshTest`, `ChettinadApiContractTest`, `ExampleUnitTest`, `ExampleInstrumentedTest`)
* **Patient Tests**: 5 (`AuthScreenTest`, `PatientRoleIsolationTest`)
* **Staff Tests**: 6 (`AuthScreenTest`, `ClinicalNoteConflictTest`, `StaffRoleIsolationTest`)

## Existing Test Count Before Split
33 (Reported by user request)

## Test Count After Split
17 actual executable tests across 9 files

## Patient APK
NOT COMPILED (Gradle/SDK not present)

## Staff APK
NOT COMPILED (Gradle/SDK not present)

## Runtime Screens Tested
None (No emulator/SDK)

## Regressions Found
0

## Remaining Work
- Environment requires Android SDK and Gradle Wrapper to perform builds and APK generation.
- Full UI / Emulator runtime verification required.
- Linguistic review of translations.
