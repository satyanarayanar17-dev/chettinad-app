# Android vs Web Feature Parity Audit

| Workflow | Web | Android | Backend API | Mobile Required? | Status |
| -------- | --- | ------- | ----------- | ---------------- | ------ |
| Authentication | Yes | Partial (Staff only) | `POST /auth/login/staff` | Yes | PENDING |
| Patient Registration | Yes | No | `POST /opd/patients` | No (Reception task usually) | SKIPPED |
| Booking/Appointments | Yes | No | `GET /opd/appointments`, `POST /opd/appointments` | Yes | PENDING |
| Triage | Yes | Yes (Speculative) | `POST /opd/encounters/{id}/triage` | Yes | UPDATE REQUIRED |
| Consultations | Yes | Yes (Speculative) | `PUT /opd/encounters/{id}/consultation` | Yes | UPDATE REQUIRED |
| Clinical Notes | Yes | Yes (Speculative) | `POST /opd/encounters/{id}/complete` | Yes | UPDATE REQUIRED |
| Prescriptions | Yes | Yes (Speculative) | `POST /opd/encounters/{id}/complete` | Yes | UPDATE REQUIRED |
| Prescription Printing | Yes | No | Client-side generation | Yes | PENDING |
| Labs | Yes | No | `POST /opd/encounters/{id}/labs` | Yes | PENDING |
| Follow-up | Yes | Partial | Nested in Complete/Consultation | Yes | UPDATE REQUIRED |
| Patient Records | Yes | Yes (Speculative) | `GET /opd/patients/{id}/record` | Yes | UPDATE REQUIRED |
| Staff Administration | Yes | Yes (Speculative) | `GET/POST /opd/staff` | Yes | UPDATE REQUIRED |
| Localization | Yes | Partial | N/A (Client-side) | Yes | PENDING |
