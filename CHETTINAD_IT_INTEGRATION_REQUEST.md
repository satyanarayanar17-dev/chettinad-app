# Chettinad Care IT Integration Request

This document outlines the exact technical information required from the Chettinad Care IT and Backend team to connect the Android clinical application to the hospital's staging and production environments.

## 1. Staging Environment
* What is the staging API base URL? (e.g., `https://staging-api.chettinad.example.com`)
* Are there any VPN or internal-network requirements to reach the staging endpoint?
* Does the staging environment use a publicly trusted TLS/SSL certificate?

## 2. Authentication
* **Login Endpoint:** Is it `POST /api/auth/login`?
* **Session Type:** Does the server return a JWT Bearer token?
* **Token Expiry:** What is the lifespan of the access token?
* **Refresh Tokens:** Is a refresh token flow implemented, and what is the endpoint?
* **Logout:** Does the backend require explicit revocation on logout?

## 3. Test Accounts
Please provide credentials for synthetic test accounts (no real patient data):
* 1x Doctor account
* 1x Nurse account
* 1x Patient account
* 1x Admin account

## 4. API Documentation
Please provide one of the following:
* OpenAPI/Swagger specification
* Postman collection
* Internal API documentation/wiki
* Backend Express.js source code / routes (if applicable)

## 5. Roles
What are the exact string values used by the backend to define user roles? (Currently Android expects: `DOCTOR`, `NURSE`, `PATIENT`, `ADMIN`).

## 6. Departments
What is the endpoint to fetch the list of clinical departments, and what does the schema look like?

## 7. Clinical Notes
* **Create/Update:** What are the routes for saving and updating notes?
* **Finalization:** How does the backend distinguish between a draft and a signed/finalized note?
* **Concurrency:** Does the backend use ETags or version numbers to prevent stale updates? (Android is prepared to send `If-Match` headers).

## 8. Prescriptions
* **Create Route:** What is the endpoint for submitting prescriptions?
* **Catalog:** Is there an endpoint to search the hospital formulary/medication catalog?
* **Idempotency:** Does the backend support `Idempotency-Key` headers to prevent duplicate prescription records on network retries?

## 9. Triage
* **Queue:** Route for nurses to fetch the current patient triage queue?
* **Submit:** Route to submit a completed triage assessment?

## 10. Patient Access
How does the server scope data for patients? (e.g., does the JWT enforce that patients can only query their own MRN?)

## 11. Admin
* **Staff Management:** What is the contract for creating, updating, and deactivating staff accounts?

## 12. Errors
What is the standard error response structure?
(Android proposes: `{ "code": "...", "message": "...", "fieldErrors": { ... } }`)

## 13. Pagination
What pagination mechanism does the backend currently use?
(Android proposes: `?page=1&size=20`, but can adapt to cursor or offset-based if required).

## 14. Audit
Are there any specific HTTP headers or metadata fields required by the hospital's audit logging compliance system on critical writes?
