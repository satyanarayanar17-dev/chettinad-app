# Android v2 API Integration Gap Matrix

| Workflow | Current Retrofit | Actual Backend Route | Gap | Status |
| -------- | ---------------- | -------------------- | --- | ------ |
| Staff Login | `login` (`POST api/auth/login`) | `POST /api/v1/auth/login/staff` | Endpoint path, request/response schema | RETROFIT CHANGE REQUIRED |
| Patient Login | (None) | `POST /api/v1/auth/opd/otp/request` & `verify` | Missing | ANDROID FEATURE MISSING |
| Refresh Token | (None) | `POST /api/v1/auth/refresh` | Missing | ANDROID FEATURE MISSING |
| Logout | (None) | `POST /api/v1/auth/logout` | Missing | ANDROID FEATURE MISSING |
| Doctor/Nurse Queue | `getDoctorQueue` / `getTriageQueue` | `GET /api/v1/opd/queue` | Shared endpoint, no `page`/`size` | RETROFIT CHANGE REQUIRED |
| Patient Record | `getPatientDossier` | `GET /api/v1/opd/patients/{id}/record` | Endpoint path, response schema | DTO CHANGE REQUIRED |
| Start Triage | (None) | `POST /api/v1/opd/encounters/{id}/start-triage` | Missing | UI WORKFLOW CHANGE REQUIRED |
| Submit Triage | `submitTriage` | `POST /api/v1/opd/encounters/{id}/triage` | Endpoint path, schema, no Idempotency | MAPPER CHANGE REQUIRED |
| Save Note | `saveClinicalNote` | `PUT /api/v1/opd/encounters/{id}/consultation` | Endpoint path, `__v` instead of ETag | MAPPER CHANGE REQUIRED |
| Finalize Note/Rx | `updateClinicalNote` / `createPrescription` | `POST /api/v1/opd/encounters/{id}/complete` | Single endpoint for both, no Idempotency | VIEWMODEL CHANGE REQUIRED |
| Search Meds/Catalog | `searchMedications` | `GET /api/v1/opd/catalogues` | Fetches all active catalogues, no pagination | RETROFIT CHANGE REQUIRED |
| Admin Staff List | `getAllStaff` | `GET /api/v1/opd/staff` | Endpoint path, no pagination | RETROFIT CHANGE REQUIRED |
| Admin Create Staff | `createStaffAccount` | `POST /api/v1/opd/staff` | Endpoint path | MAPPER CHANGE REQUIRED |
| Admin Update Staff | `updateStaffStatus` | `PATCH /api/v1/opd/staff/{id}` | Endpoint path | MAPPER CHANGE REQUIRED |
| Order Labs | (None) | `POST /api/v1/opd/encounters/{id}/labs` | Missing | ANDROID FEATURE MISSING |
| View Labs | (None) | `GET /api/v1/opd/labs` | Missing | ANDROID FEATURE MISSING |
| Appointments | (None) | `GET /api/v1/opd/appointments` | Missing | ANDROID FEATURE MISSING |

## Backend Limitations
*   **Result Cap 100**: The backend uses `LIMIT 100` natively. `BACKEND LIMITATION — RESULT CAP 100`.
*   **No Server Idempotency**: The backend relies on state transitions and `__v` optimistic locking instead of `Idempotency-Key`.
