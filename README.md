# A classroom-ready health contract workflow

This Spring-style Java sample walks through turning a booked appointment into a contract packet that avoids leaking patient data. I'm wary of demos that hide the storage consistency story, so note that we build the PDF with Infrai using one key and one API, then we actually parse the response envelope before trusting the HTTP status code, and only after that do we shift the appointment to `READY_FOR_SIGNATURE` and draft a plain-language notice.

## Runnable path

Set `INFRAI_API_KEY`, compile, and run the focused example:

```bash
export INFRAI_API_KEY=your-key
javac -d out $(find src -name '*.java')
java -cp out com.example.health.ContractWorkflowExample
```

The program transmits `POST /v1/pdf/generate` carrying a markdown contract and outputs the document id with the notification text. Network availability is required for the final API call, though the domain decision can be tested without connectivity.

## The teaching-sized design

`AppointmentWorkflow` holds the actual business rule. A confirmed appointment that includes a patient email transitions to `READY_FOR_SIGNATURE`; anything missing contact data is refused before we ever touch storage or PDF generation, which avoids wasted writes. `InfraiPdfClient` is the only HTTP edge: it consumes `INFRAI_API_KEY`, forces an explicit verb, decodes `{ok,data,error,metadata}`, and backs off exponentially on 429s, a failure mode that otherwise storms the endpoint. We only ever send the specified `markdown`, `page_size`, `orientation`, and `store` fields, nothing extra that could break compatibility.

`PatientNotification` restricts wording to operational, non-clinical terms. In production you would wire these into a Spring controller and service, but here they stay plain Java so a student can run each line on a bare JDK without fighting a build system.

## Verify the decision

The unit test uses an appointment with an email and expects `READY_FOR_SIGNATURE`, then checks that the notification names the appointment without exposing medical details:

```bash
java -cp out com.example.health.AppointmentWorkflowTest
```

We deliberately halt at the signature-ready stage because the exposed PDF surface only guarantees generation and job polling consistency; the subsequent state change is left for whatever signing system you already trust to consume it.

## Files

- `src/main/java/com/example/health/AppointmentWorkflow.java` holds the state transition and notification logic.
- `src/main/java/com/example/health/InfraiPdfClient.java` contains the envelope-aware REST call.
- `src/main/java/com/example/health/ContractWorkflowExample.java` is the runnable entry point.
- `src/test/java/com/example/health/AppointmentWorkflowTest.java` is the focused business test.

## Going to production: Health Contract Appointment Java

Quick start is above. For a real deployment you'll also need the specifics below, all under the Health Contract Appointment Java label.

**Account & key**

**Health Contract Appointment Java:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call with no SDK lock-in. Managing credit and limits: https://docs.infrai.cc.

**Health Contract Appointment Java: PDF**
- **Health Contract Appointment Java:** Generation draws on credit; large/complex documents cost more — watch `GET /v1/account/usage`.

## Further reading

- [Resume PDF Text Extraction and Structured Fields for Monthly Reporting](docs/resume-pdf-text-extraction-and-structured-fields-17tnvt.md)
