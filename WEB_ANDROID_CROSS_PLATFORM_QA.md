# Chettinad Care Web/Android Cross-Platform QA Matrix

| Workflow | Android → Backend | Visible on Web | Web → Backend | Visible on Android | Result |
| -------- | ----------------: | -------------: | ------------: | -----------------: | ------ |
| Patient | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | FAILED (No live backend URL) |
| Appointment | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | FAILED (No live backend URL) |
| Triage | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | FAILED (No live backend URL) |
| Consultation | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | FAILED (No live backend URL) |
| Prescription | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | FAILED (No live backend URL) |
| Labs | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | FAILED (No live backend URL) |
| Follow-up | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | FAILED (No live backend URL) |
| Staff/Admin | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | FAILED (No live backend URL) |

**Note**: Live testing was unable to be performed as no accessible Chettinad Care v2 Staging API URL is present in the secure environment or `.env` configuration. The local development environment proxy (`APP_URL`) blocks native API requests without a browser cookie, and no unauthenticated backend is exposed on `localhost`.
