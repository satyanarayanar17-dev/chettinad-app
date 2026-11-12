# Chettinad Android trial release — 12 November 2026

Publisher: **Chettinad Medical Sciences**. App support: **satyanarayanr17@gmail.com**. The authorized trial is one doctor, Dr. Goluguri Prajwala Priyanka Reddy (Obstetrics & Gynaecology), and at most 25 actual patients. Google Play organization registration and real SMS setup are still outstanding. This document is release preparation, not evidence of publication or permission to enroll patients.

## Current release

Both app IDs are retained: `com.aistudio.chettinadcare.patient.cwqtxr` and `com.aistudio.chettinadcare.staff.cwqtxr`. Version 1.6 uses code 7 for synthetic preflight, with code 8 reserved for the first clinical production flavor. Target SDK is 36; minimum SDK is 24. The signed demo artifacts display a permanent synthetic-records notice and connect to the existing demo API. Never use these artifacts for actual patient records.

The release includes doctor-created patient/mobile access, the approved teal interface, state/PIN matching, birthday selection, simplified arrival, care-plan vital/activity entry, task and overall completion indicators, and doctor-controlled medication reminders. Patient/staff appointment booking and doctor availability controls are removed. Privacy/help are readable before sign-in and from account pages.

Production login checks the server configuration before sending a phone number or staff credentials, rejecting demo OTP, absent SMS and self-enrollment. Document opening is session-bound; sign-out/account changes revoke temporary document access and remove the private document cache. Account-scoped encrypted clinical recovery drafts remain recoverable for their author. These protections have targeted regression tests.

## Repeatable build and verification

Use JDK 21, Android SDK 36 and Gradle 9.3.1. Run `gradle :core:testDemoDebugUnitTest :app-patient:testDemoDebugUnitTest :app-staff:testDemoDebugUnitTest :app-patient:lintDemoDebug :app-staff:lintDemoDebug`. CI also runs native Android 35 storage/launch tests and the web clinical journeys against backend commit `2108aefb93eac79abeb5ce1aab6bfe8c760d6335`.

Use `scripts/build-play-bundles.py --environment demo --credentials /private/upload-key.json --gradle /path/to/gradle` for synthetic preflight. The protected JSON contains `store_path`, `alias` (upload) and `password`. Never put it, the private key, credentials or patient data in Git or Play artifacts. Preserve the old upload key until the owner confirms its history; do not replace a published app's signing identity.

For clinical builds, use `--environment production --api-url https://ALLOCATED-OWNED-HOST/api/v1/ --expected-backend-commit FULL_SHA --approve-live-backend` only after actual handset OTP receipt, durable storage, independent encrypted recovery and operator acceptance are verified. The helper additionally checks restricted PostgreSQL mode, readiness, expected backend commit, real-OTP configuration and public privacy/support pages. Do not bypass these checks with placeholders.

Verify APK signatures and 16 KB zip alignment, AAB signatures, target/version/package metadata and the 16 KB load alignment of packaged native libraries. Back up the upload key in owner-controlled secret storage before uploading. Never uninstall a user's app merely to replace a different debug signing certificate: that can erase local drafts.

## Distribution and activation

Create and verify the hospital/legal entity's **organization** Play account. The owner must complete legal agreements, identity/business verification and payment; the organization name must match its evidence. The provided publisher name does not establish that verification. Set up two apps in the Medical category and use controlled internal testing initially. Tester Play-account emails are separate from hospital mobile-login numbers. Do not publish real patient data, invite patients automatically, or supply real clinical accounts to reviewers.

The reviewed Render Blueprint creates a separate Singapore PostgreSQL database and API/private disk, preserving the free demo. Base hosting is estimated at US$14.55/month before tax, SMS, independent backups and overages; do not apply billed resources without approval. Configure real SMS and template mapping through secret settings, then test delivery on a real handset. Verify persistence across restart and an independent encrypted restore before pointing the web and production Android apps at the allocated clinical API.

Complete Play's health declaration, Data safety, audience, content rating and app access forms against the activated system. Audience/under-18 enrollment requires the owner's answer. The supplied privacy notice needs operator review of actual retention, hospital authorization and service providers. A public review workflow, if required, must use isolated synthetic accounts and data.

References: [health/medical organization requirement](https://support.google.com/googleplay/android-developer/answer/13634885), [account registration](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en), [health app declaration](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en), [test tracks](https://support.google.com/googleplay/android-developer/answer/9859348?hl=en).
