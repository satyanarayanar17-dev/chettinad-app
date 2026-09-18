# Chettinad Care Android Backend Integration Status

This document maps the proposed Android API contract to the existing Chettinad Care backend capabilities.

## Endpoint Gap Matrix

| Feature | Android method | Proposed endpoint | Backend endpoint | Request match | Response match | Auth | Status |
| ------- | -------------- | ----------------- | ---------------- | ------------- | -------------- | ---- | ------ |
| Auth Login | `login()` | `POST /api/auth/login` | TBD | TBD | TBD | None | `UNKNOWN — IT INPUT REQUIRED` |
| Doctor Queue | `getDoctorQueue()` | `GET /api/patients/queue` | TBD | TBD | TBD | Bearer | `UNKNOWN — IT INPUT REQUIRED` |
| Patient Dossier | `getPatientDossier()`| `GET /api/patients/{id}/dossier`| TBD | TBD | TBD | Bearer | `UNKNOWN — IT INPUT REQUIRED` |
| Save Note | `saveClinicalNote()` | `POST /api/clinical/notes` | TBD | TBD | TBD | Bearer | `UNKNOWN — IT INPUT REQUIRED` |
| Update Note | `updateClinicalNote()` | `PUT /api/clinical/notes/{id}`| TBD | TBD | TBD | Bearer | `UNKNOWN — IT INPUT REQUIRED` |
| Create Rx | `createPrescription()` | `POST /api/clinical/prescriptions`| TBD | TBD | TBD | Bearer | `UNKNOWN — IT INPUT REQUIRED` |
| Search Meds | `searchMedications()`| `GET /api/clinical/medications` | TBD | TBD | TBD | Bearer | `UNKNOWN — IT INPUT REQUIRED` |
| Triage Queue | `getTriageQueue()` | `GET /api/triage/queue` | TBD | TBD | TBD | Bearer | `UNKNOWN — IT INPUT REQUIRED` |
| Submit Triage | `submitTriage()` | `POST /api/triage` | TBD | TBD | TBD | Bearer | `UNKNOWN — IT INPUT REQUIRED` |
| Get All Staff | `getAllStaff()` | `GET /api/admin/staff` | TBD | TBD | TBD | Bearer | `UNKNOWN — IT INPUT REQUIRED` |
| Create Staff | `createStaffAccount()` | `POST /api/admin/staff` | TBD | TBD | TBD | Bearer | `UNKNOWN — IT INPUT REQUIRED` |
| Update Staff | `updateStaffStatus()`| `PUT /api/admin/staff/{id}/status`| TBD | TBD | TBD | Bearer | `UNKNOWN — IT INPUT REQUIRED` |

## Required Backend Capabilities

The Android client incorporates robust mobile engineering practices that require specific backend capabilities to function correctly. If these capabilities are missing from the Node.js backend, they must be implemented for safety and compliance.

### 1. Idempotency (CLIENT READY — BACKEND CAPABILITY REQUIRED)
Critical writes (Prescriptions, Triage, Clinical Notes) send an `Idempotency-Key` HTTP header. 
*   **Android Behavior:** Re-uses the exact same UUID for a given logical operation if a network timeout occurs, preventing duplicate database entries upon retry.
*   **Backend Requirement:** The Node.js server must cache the response for a given key and replay it if the same key is received again, instead of creating a duplicate record.

### 2. Concurrency / ETags (CLIENT READY — BACKEND CAPABILITY REQUIRED)
Updates to clinical notes send an `If-Match: <etag>` HTTP header.
*   **Android Behavior:** Preserves local draft and surfaces a conflict UI state if the backend responds with HTTP 409 or 412.
*   **Backend Requirement:** The Node.js server must include a version token/ETag when serving a clinical note, and reject updates where the provided `If-Match` header does not match the latest database version.

### 3. Pagination (ANDROID ADAPTER REQUIRED)
*   **Android Proposal:** `?page=1&size=20`
*   **Backend Reality:** Pending IT confirmation. The Android repository layer is designed to adapt to offset, cursor, or continuation token pagination without changing the Compose UI.

### 4. Error Envelope (ANDROID ADAPTER REQUIRED)
*   **Android Proposal:** Structured JSON `{ "code": "...", "message": "..." }`
*   **Backend Reality:** Pending IT confirmation. Android will gracefully fall back to parsing standard HTTP status codes (401, 403, 404, 409, 412, 422, 5xx) if the body format does not match.
