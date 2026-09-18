# Chettinad Care: Clinical User Acceptance Testing (UAT) Plan

## Overview
This document outlines the testing scenarios required for Clinical and IT Leadership at Chettinad Hospital to verify the safety, accuracy, and usability of the Chettinad Care Android Application connected to the Staging Environment.

**Prerequisites:**
*   Android device/emulator running the `staging` build variant.
*   VPN or network access to the Chettinad Staging API.
*   Test accounts provisioned by Hospital IT (Doctor, Nurse, Patient, Admin).
*   **All data entered must be synthetic/fake.**

---

## 1. Safety & Data Isolation (Critical Path)
*   [ ] **Patient Data Leakage:** Login as Patient A. Verify that Patient A cannot view records for Patient B by tampering with URLs or UI state.
*   [ ] **Context Preservation:** Begin writing a prescription for Patient A. Background the app. Return to the app. Verify the prescription context remains safely locked to Patient A and has not reset or swapped.
*   [ ] **Session Expiry:** Force a session timeout (or logout). Verify that all sensitive clinical data, queues, and drafts are instantly purged from the Android device memory and local database.
*   [ ] **Double Submission:** Attempt to tap "Finalize Note" or "Submit Prescription" multiple times rapidly. Verify the network layer's Idempotency Key prevents duplicate clinical records from being generated server-side.
*   [ ] **Allergy Visibility:** Ensure patient allergies remain persistently visible to clinicians throughout the Prescription and Note workflows.

---

## 2. Doctor Workflows
*   [ ] **Authentication:** Login successfully with Doctor staging credentials.
*   [ ] **Queue Rendering:** View the assigned departmental queue. Verify patient names, MRNs, and triage acuity colors are accurate.
*   [ ] **Patient Dossier:** Open a specific patient from the queue. Review triage history, vitals, and past notes.
*   [ ] **Draft Resilience:** Open Clinical Note Editor. Type clinical observations. Exit the screen without saving. Re-enter the screen. Verify the local Room database recovered the draft successfully.
*   [ ] **Submit Clinical Note:** Complete a clinical note. Tap "Submit". Verify successful network transmission and backend persistence.
*   [ ] **Medication Search:** In Prescription Builder, type a partial medication name. Verify the backend returns accurate search results.
*   [ ] **Prescription Safety:** Add a medication. Verify that Strength, Dose, Route, and Frequency fields are visually distinct and cannot be confused.
*   [ ] **Finalize Prescription:** Submit the prescription. Verify the Android client successfully routes the user back to the Dossier upon success.

---

## 3. Nurse Workflows
*   [ ] **Authentication:** Login successfully with Nurse staging credentials.
*   [ ] **Triage Queue:** Verify the queue populates with incoming patients.
*   [ ] **Vitals Entry:** Open a patient for triage assessment. Enter Blood Pressure, Heart Rate, and Temperature.
*   [ ] **Acuity Assignment:** Assign a priority (Emergency, Urgent, Routine). Verify the text label is clearly legible against the color background.
*   [ ] **Submission:** Submit triage. Verify the patient appears accurately updated in the Doctor's queue.

---

## 4. Patient Workflows
*   [ ] **Authentication:** Login successfully with Patient staging credentials.
*   [ ] **Read-Only Access:** View medical records and past prescriptions.
*   [ ] **Security Boundary:** Verify the UI does not expose any edit/write capabilities or staff queues to the patient role.

---

## 5. Admin Workflows
*   [ ] **Authentication:** Login successfully with Admin staging credentials.
*   [ ] **Staff Creation:** Create a new Nurse account. Assign department.
*   [ ] **Staff Deactivation:** Deactivate an existing Doctor account.
*   [ ] **Security Enforcement:** Attempt to log in with the deactivated Doctor account. Verify the Android client correctly surfaces a "403 Forbidden" or "Account Disabled" error.
