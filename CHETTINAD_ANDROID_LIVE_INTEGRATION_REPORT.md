# Chettinad Care Android v2 Backend Integration Report

## 1. Environment Details
*   **Android Build:** Staging
*   **Target Backend:** Chettinad Care v2 Node.js/Express
*   **Backend Connection:** FAILED
*   **Error:** No valid staging API URL is configured in the environment. The `APP_URL` preview endpoint is protected by AI Studio authentication, preventing native native Android HTTP requests.

## 2. List-Cap Inconsistency Resolution
*   **Finding:** The actual backend uses `LIMIT 100` natively on SQL queries for lists (e.g., patient queues, catalogs, staff list). 
*   **Impact on Android:** The Android client receives a capped, unpaginated result set (maximum 100 records). 
*   **Accessibility:** All matching records beyond the 100 limit are NOT reachable, as the backend does not expose `page` or `size` pagination parameters for these endpoints. The previous assumption that Android receives complete unpaginated lists is FALSE for lists exceeding 100 items.

## 3. Workflow Execution Status
Due to the inability to connect to a live backend, all workflow tests are pending:

*   **Authentication (Admin, Doctor, Nurse, Patient):** NOT TESTED LIVE. (Verified via local `AuthRefreshTest`).
*   **Single-Flight Refresh Live:** NOT TESTED LIVE. (Verified via local `AuthRefreshTest`).
*   **Patient Registration & Booking:** NOT TESTED LIVE.
*   **Nurse Triage & Doctor Queue:** NOT TESTED LIVE.
*   **Patient Dossier & Clinical Notes:** NOT TESTED LIVE.
*   **Optimistic Locking (409 STALE_STATE):** NOT TESTED LIVE.
*   **Prescription submission & printing:** NOT TESTED LIVE.
*   **Lab workflows:** NOT TESTED LIVE.
*   **Patient App authorization:** NOT TESTED LIVE.
*   **Admin staff management:** NOT TESTED LIVE.

## 4. Governance & Technical Debt
*   **Duplicate Logic:** The Android client relies on the backend for all business logic (e.g., triage prioritization, scheduling rules). No logic was duplicated.
*   **API Ownership:** The backend remains the source of truth. The Android client will adapt to any future pagination enhancements (e.g., cursor-based pagination) implemented by the backend team.

## 5. Next Steps
To proceed with live integration:
1.  **IT Action Required:** Provide a dedicated API gateway URL or internal endpoint that is accessible to the Android client without AI Studio cookie authentication, or provide an automated service account/API key that can bypass the proxy.
2.  **Environment Configuration:** Inject this URL into the AI Studio secure environment (e.g., as `STAGING_API_URL` in secrets).
3.  **Backend Enhancements:** Implement pagination (cursor or offset) to resolve the hard `LIMIT 100` cap, allowing Android to fetch the entirety of large datasets (like medications or staff).
