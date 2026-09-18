# Android App Split Plan

## Strategy
1. **Core Module (`:core`)**: Extracted all shared UI components (`AppShell`, `AppTheme`, `AppLogo`), `domain` layer (`Environment`, `Models`), `data` layer (`ChettinadApiService`, `ChettinadDatabase`, repositories), and DI (`AppContainer`) from `:app` into a shared library module.
2. **Patient App (`:app-patient`)**: Created an independent Android application. Moved `ui/patient` here. Updated the `LoginScreen` and `AuthViewModel` to explicitly handle patient flows and reject staff accounts. Created a specialized `PatientApp` navigation component.
3. **Staff App (`:app-staff`)**: Created an independent Android application. Moved `ui/doctor`, `ui/nurse`, and `ui/admin` here. Updated the `LoginScreen` and `AuthViewModel` to specifically cater to staff authentication and reject patient accounts. Created a specialized `StaffApp` navigation flow spanning Doctor, Nurse, and Admin features.
4. **Dependencies**: Defined `:core` as an `android-library` and updated `libs.versions.toml`.

## File Classification and Movement

| File/Folder | Destination | Reason | Code Changed? |
|-------------|-------------|--------|---------------|
| `com.example.ui.patient.*` | `:app-patient` | Patient-only UI. | Package names updated. |
| `com.example.ui.doctor.*` | `:app-staff` | Staff-only (Doctor) UI. | Package names updated. |
| `com.example.ui.nurse.*` | `:app-staff` | Staff-only (Nurse) UI. | Package names updated. |
| `com.example.ui.admin.*` | `:app-staff` | Staff-only (Admin) UI. | Package names updated. |
| `com.example.ui.auth.*` | Split to both | Needs to be tailored per app. | Customized titles and RBAC logic. |
| `com.example.navigation.ChettinadApp` | Split to both | Unique routing per app. | Hard-forked into `PatientApp` & `StaffApp`. |
| `com.example.domain.*` | `:core` | Shared domain objects. | None. |
| `com.example.data.*` | `:core` | Shared network, API, repositories. | None. |
| `com.example.di.AppContainer` | `:core` | Shared container. | None. |
| `com.example.ui.theme.*` | `:core` | Shared branding. | None. |

