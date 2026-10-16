> Historical snapshot. See [ANDROID_IOS_PARITY.md](ANDROID_IOS_PARITY.md) and the current PR workflow results for the 16 October integration recheck.

# Android verification — 15 October 2026

The source audit covers the active native Patient and Staff portals against iOS revision `649d9f5fa2fe1eaac354e7670871bb677d3bb240` and backend revision `cd3256223d32527a249e6c4b02b434b04035bcb0`. Workflow coverage is not a claim of identical screens or production acceptance.

## Confirmed issues fixed in this audit

| Finding | Correction and check |
| --- | --- |
| Signing omitted the patient version required by staging/pilot/production | Send the reviewed patient version independently of the note version; assert both in view-model and HTTP contract tests. |
| Optional consultation text serialized as JSON null, which the backend schema rejects | Normalize blank and recovered-draft text to empty strings when writing, while preserving an explicit null follow-up removal. Wire regression covers the actual serialized request. |
| Triage began with empty allergies and could change the record without review metadata | Prefill the existing allergy record; require explicit review, a reason for changes and the patient version. Test preserved UI content and outgoing review metadata. |
| Follow-up booking could display another doctor/date's slots | Use the same doctor/date-scoped availability as normal booking; UI regression covers both wrong doctor and wrong date. |
| A slower patient-directory search could replace newer identity results | Cancel superseded searches and clear prior results; delayed-response regression confirms the selected query wins. |
| Profile contact editing existed in iOS but was absent from Android | Add versioned contact/address updates, validate basic inputs and preserve sign-in mobile/identity fields. Verify the PATCH path and payload. |
| Wrong current password could trigger authentication refresh/replay and sign the user out | Do not replay password-change credential failures; retain the session for correction. Match the backend's 12–72 character password policy. |
| Nullable device metadata from older sessions could prevent the device list from loading | Accept missing metadata, display a fallback device label and retain session revocation; HTTP regression covers loading and revoking a legacy session. |
| Trend charts could connect different units and omitted diastolic history | Group by measurement component and unit; show both blood-pressure series. Tests cover mixed glucose units, both BP components and irrelevant/non-finite values. |
| Prescription printing could substitute current patient demographics for the signed identity | Retain and print the backend's signed patient snapshot, with a fallback for older prescriptions without snapshots. |
| Backup XML retained default allow-all transfer rules despite the disabled-backup manifest flag | Replace templates with explicit exclusions for all nine storage domains in legacy/cloud/device-transfer policies; both apps use the shared policy. XML structure/domain checks pass. |

Android documents manufacturer-dependent device-transfer behavior for the manifest flag: https://developer.android.com/identity/data/autobackup

## Evidence

- Final unit/contract/Compose UI suite: core 38, Patient 7, Staff 6 (51 total). Pass/fail results for the final revision are recorded on PR #1 and its workflow artifacts.
- Both demonstration APKs build. Static analysis and final native emulator results are recorded on PR #1 and its workflow artifacts.
- Ten additional backend pilot-safety/CORS tests pass with isolated synthetic SQLite data. These cover allergy review, patient-version signing, preserved prescription identity and role/department restrictions.
- Earlier published baseline `ef113e9` passed both Android 35 Keystore/session/cookie tests, both application launches and coexistence. Those results do not substitute for the final audit commit's workflow.
- The live development API health requests timed out during this audit. This does not establish an outage, and current live-service availability is unconfirmed.
- No real patient records were changed, and no real SMS was sent by these tests.

## Limits and remaining acceptance

- Android and iOS use native platform navigation and differ in tab order and placement of secondary destinations. Pixel-for-pixel or screen-by-screen identity has not been established; an iOS Simulator runtime is unavailable here.
- Android exposes daily task creation and preserves other recurrence patterns when editing existing tasks. It does not yet expose a full recurrence builder, signed-note amendment UI or every backend administration endpoint.
- Reminders are optional, inexact and refreshed from loaded daily tasks. Unopened-app delivery across reboot and continuous background refresh are not verified.
- Physical-phone camera/file-picker/viewer, printing, notification permission/battery behavior, accessibility/font scaling and complete multi-role hospital acceptance still need device-level review.
- Production backend/database configuration, real SMS/DLT delivery, release signing and Play distribution remain deployment work. The included APKs are debug-signed demonstration builds.

Source and current workflow evidence: https://github.com/satyanarayanar17-dev/chettinad-app/pull/1
