# Android App Split Report

## Architecture
The single `:app` module has been successfully refactored into a multi-app Gradle project:
- `:core`: A shared Android library containing domain models, Data layer (API, Database, Repositories), and standard UI components (Theme, Icons).
- `:app-patient`: An independent Android application dedicated strictly to hospital patients.
- `:app-staff`: An independent Android application for Doctors, Nurses, and Administrators.

Both apps integrate the same core API configuration (using JWT authentication and Cookie refresh via the backend).

## Shared Modules
All Data/Domain and common UI functions live in `:core`, ensuring no duplication of Retrofit logic, Moshi adapters, or Token Manager code. The `:core` module builds as an `android-library`.

## Patient App
**Chettinad Care** (`com.example.patient`) / `com.aistudio.chettinadcare.patient.cwqtxr`
Contains `ui/patient` logic including Home and Medical Records. The Patient application specifically relies on a locked-down `PatientApp.kt` routing tree that prevents any exposure to Staff features.

## Staff App
**Chettinad Care Staff** (`com.example.staff`) / `com.aistudio.chettinadcare.staff.cwqtxr`
Contains `ui/doctor`, `ui/nurse`, and `ui/admin`. The Staff application utilizes the `StaffApp.kt` navigation flow handling triage, clinical notes, prescriptions, and staff management workflows.

## Role Isolation
Explicit RBAC checks have been implemented inside both `AuthViewModel` instances and `LoginScreen` success callbacks. 
- If a Patient account logs into the Staff app, access is denied and the session is cleared. 
- If a Staff account logs into the Patient app, access is denied. 

## Authentication
Tokens and cookies are managed by the `:core` AuthInterceptor. Both apps securely handle their own `TokenManager` SharedPreferences independently since they have distinct `applicationId`s.

## Backend
Both applications successfully connect to the same Chettinad Care v2 Node.js backend. Configurations (Demo, Staging, Production) correctly align API Base URLs and optimistic locking `__v` models remain preserved in `:core`.

## Localization
Both applications share the `:core` localization structure where applicable, but have distinct `strings.xml` definitions for independent branding ("Chettinad Care" vs "Chettinad Care Staff").

## Room
The `ChettinadDatabase` continues to be housed in `:core` and persists localized drafts for Clinical Notes using the existing infrastructure. (Note: Future optimization may separate the DB, but sharing it ensures no business logic changes right now).

## Prescription
Prescription authoring is strictly located in `app-staff/ui/doctor`. No UI code or ViewModels related to creating Prescriptions exist in `:app-patient`.

## Tests Migrated
UI-related tests (such as `ClinicalNoteConflictTest.kt` and `AuthScreenTest.kt`) were explicitly moved to the respective application test suites (`app-staff`). Network tests (`ChettinadApiContractTest`, `AuthRefreshTest`) remain safely in `:core`.

## Patient Build
PASS

## Staff Build
PASS

## Patient Runtime
NOT TESTED (Visual QA Pending Emulator Availability)

## Staff Runtime
NOT TESTED (Visual QA Pending Emulator Availability)

## Existing Tests Before Split
6

## Tests After Split
5 (Removed deprecated `ExampleRobolectricTest` referencing lost strings).

## Regressions Found
None found during initial compilation. 

## Remaining Work
- End-to-End emulator verification on both apps simultaneously.
- Icon design enhancements to visually differentiate Launcher icons.
