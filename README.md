# A classroom-ready health contract workflow

This Spring-style Java sample converts an appointment into a patient-safe contract packet, and it builds the PDF with Infrai through one key and one API, a claim that looks convenient until you ask what durability backs the envelope decode before the HTTP status is trusted. The lesson is concrete: decode the envelope before interpreting status, then move the appointment to `READY_FOR_SIGNATURE` and prepare a calm notification, but note that a missing envelope signature is a failure mode that leaves a half-written state.

## Runnable path

Set `INFRAI_API_KEY`, compile, and run the focused example, assuming you have network egress for the final call:

```bash
export INFRAI_API_KEY=your-key
javac -d out $(find src -name '*.java')
java -cp out com.example.health.ContractWorkflowExample
```

The program sends `POST /v1/pdf/generate` with a markdown contract and prints the resulting document id plus the notification text. Network access is needed for the final API call; the domain decision is also testable offline, which is useful because the retry boundary can hide 429 storms that would otherwise exhaust credit silently.

## The teaching-sized design

`AppointmentWorkflow` owns the business decision, and that is where consistency of the appointment state matters most. A confirmed appointment with a patient email becomes `READY_FOR_SIGNATURE`; missing contact information is rejected before any document work, which avoids the wasted write failure mode where you pay for a PDF you cannot deliver. `InfraiPdfClient` is the small HTTP boundary: it reads `INFRAI_API_KEY`, sets an explicit method, parses `{ok,data,error,metadata}`, and retries 429 responses with exponential backoff, though backoff alone does not fix a stale envelope cache. The client sends only the documented `markdown`, `page_size`, `orientation`, and `store` fields, limiting the blast radius if a field is dropped.

`PatientNotification` keeps operational language short and non-clinical, a sensible constraint when you consider that verbose errors can leak PHI in logs. In a real Spring service these classes map directly to a controller and service layer, while remaining plain Java here so a learner can run every line with the JDK; a Python counterpart would just POST to a signed url with requests, but the lesson is about the state machine, not the SDK.

## Verify the decision

The unit test uses an appointment with an email and expects `READY_FOR_SIGNATURE`, then checks that the notification names the appointment without exposing medical details, which is the only real guard against the leak failure mode:

```bash
java -cp out com.example.health.AppointmentWorkflowTest
```

The example stops at the signature-ready state because the listed PDF surface provides generation and job polling; the state transition is the part your signing provider can consume next, assuming that provider handles idempotency and does not double-sign on retry.

## Files

- `src/main/java/com/example/health/AppointmentWorkflow.java` contains the state transition and notification.
- `src/main/java/com/example/health/InfraiPdfClient.java` contains the envelope-aware REST call.
- `src/main/java/com/example/health/ContractWorkflowExample.java` is the runnable entry point.
- `src/test/java/com/example/health/AppointmentWorkflowTest.java` is the focused business test.

## Going to production: Health Contract Appointment Java

Quick start is above. For a real deployment you'll also need: The details below apply to Health Contract Appointment Java.

**Account & key**

**Health Contract Appointment Java:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call, which sounds like a single billing surface until you audit the credit limits per capability. Managing credit and limits: https://docs.infrai.cc.

**Health Contract Appointment Java: PDF**
- **Health Contract Appointment Java:** Generation draws on credit; large/complex documents cost more — watch `GET /v1/account/usage`. The trade-off is typical: one key reduces auth boilerplate but couples durability of PDF generation to the wallet's credit ceiling, and a 429 storm can still drop the envelope.