# Python Debugging for PDF Jobs Stuck in Progress (3 Terminal Outcomes)

The least complex reliable fix for a PDF redaction task that remains `in_progress` is a bounded watcher with three local outcomes: `succeeded`, `failed`, and `unknown`. The last outcome says the caller's observation budget ended without pretending the renderer failed, and it prevents an infinite polling loop from becoming an accidental retention system.

The bill is shaped by everything kept alive around each task: worker time, status reads, logs, rendered artifacts, source documents, and the personal data inside them. Model it as `observation cost + retained bytes x retention time + repeated render cost`, applying the team's own rates to make the units comparable. Changing a two-second poll to ten seconds cuts observation frequency by a factor of five, but it does nothing to bound storage duration.

**Short answer:** set a watcher deadline, make rendering independent of watching, record durable final results, and expire sensitive inputs under a policy owned by the template's controller. Stop retaining full responses and unredacted documents merely to investigate ambiguity. The cost is explicit: after expiry, an operator can prove an attempt occurred but may be unable to replay its exact input.

## How Do You Debug a PDF Job Stuck in Progress Forever?

Very little.

It can mean a worker still holds a lease, a worker died after claiming work, rendering finished but its state write failed, a state update committed while a read replica remains stale, or the caller is reading the wrong task identifier. Treating those conditions as one diagnosis is the first design error.

Start at the boundary.

Separate execution from observation. Execution data includes attempt number, lease owner, lease expiry, and last durable transition. Observation data includes the caller's deadline, response time, and last state seen. A watcher timeout changes only its observation result; it must not mark execution failed unless it owns that transition and can prove the execution deadline elapsed.

This matters during personal-data redaction. A source can contain names, addresses, account identifiers, and free-form notes. A syntactically complete PDF does not prove that an application's privacy rule ran. ISO 32000-2 specifies the document format, not a SaaS application's redaction policy.

Show three clocks in diagnostics: render deadline, worker lease expiry, and watcher deadline. One timestamp named `timeout` cannot explain whether execution was cancelled, ownership expired, or a client merely stopped waiting.

## Template ownership changes the retry rule

Templates may be controlled by the platform team, each tenant, or both through a base and overlay. That decision controls who can change field placement and redaction markers, so it belongs in the task contract.

| Template model | Revision authority | Failure to expose | Safe retry condition |
|---|---|---|---|
| Platform-owned | Service operator | Deployment and queued task disagree | Use the immutable revision captured at submission |
| Tenant-owned | Tenant administrator | Revision was deleted or access changed | Revalidate authorization and revision existence |
| Base plus overlay | Both parties | An untested version pair is selected | Use the recorded pair, or create a new validated task |

Capture immutable identifiers for the policy, template revision, and input object version. A mutable name such as `latest` makes recovery unsafe: retrying can create a different document under the same task identity.

The renderer owns execution transitions. The template controller owns revisions. The watcher owns neither; it reports what it established before its deadline.

## How should a bounded watcher terminate?

Use a monotonic clock so wall-clock correction cannot extend the local deadline. The values below are explicit example policy inputs, not universal defaults: twelve observations, a 2.0-second initial delay, and a 15.0-second ceiling.

```python
from dataclasses import dataclass
import random
import time
from typing import Any, Callable, Mapping


@dataclass(frozen=True)
class WatchResult:
    outcome: str
    task_id: str
    remote_state: str
    observations: int


def watch_pdf(
    task_id: str,
    fetch: Callable[[str], Mapping[str, Any]],
    deadline_seconds: float = 90.0,
    max_observations: int = 12,
) -> WatchResult:
    deadline = time.monotonic() + deadline_seconds
    delay = 2.0
    last_state = "unobserved"

    for count in range(1, max_observations + 1):
        if time.monotonic() >= deadline:
            break
        response = fetch(task_id)
        if str(response["task_id"]) != task_id:
            raise ValueError("status response belongs to another task")

        last_state = str(response["state"])
        if last_state == "succeeded":
            return WatchResult("succeeded", task_id, last_state, count)
        if last_state in {"failed", "cancelled"}:
            return WatchResult("failed", task_id, last_state, count)
        if last_state not in {"queued", "in_progress"}:
            raise ValueError(f"unknown remote state: {last_state}")

        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        time.sleep(min(delay * random.uniform(0.8, 1.2), remaining))
        delay = min(delay * 2, 15.0)

    return WatchResult("unknown", task_id, last_state, max_observations)
```

Do not collapse `unknown` into `failed`. That shortcut can trigger duplicate rendering when a worker finishes after observation stops, and two outputs may use different template revisions. If the workflow requires certainty, reconcile asynchronously under a separate durable budget.

Validate the returned identifier and reject unknown states. Authentication errors, malformed bodies, and transport failures are not `in_progress`; labeling them that way conceals the boundary that broke. Status reads may be repeatable, but render submissions require a separately established idempotency contract.

## Diagnose the missing transition

Begin with the last transition that can be proved. Check that submission returned one stable identifier, a worker claim exists, its lease is current, an artifact was produced, and the final update committed. Follow that causal chain instead of scanning dashboards for correlated symptoms.

Record task and tenant-scoped correlation identifiers, template and policy revisions, attempt number, state, transition time, lease expiry, artifact checksum, and a categorical error code. Do not log source text, extracted personal data, signed download locations, or whole renderer responses. A checksum compares artifacts; it does not establish semantic redaction.

An artifact can exist while the task remains active. Consider the full sequence before changing anything: the worker claims attempt 2, reads template revision 17, writes the PDF, records its checksum, and then loses database connectivity before committing `succeeded`; meanwhile, polling keeps returning `in_progress`, the lease expires, and another worker becomes eligible to claim the same job. Publishing the first artifact bypasses the state machine, while immediate deletion destroys evidence. Starting another render without checking the attempt and immutable revisions risks a second, different output. Quarantine the artifact beside the protected source, compare its recorded task, attempt, policy, template, and input versions, reconcile the missing transition, and release it only after independent output validation. This sequence is also why a debug view needs transition evidence rather than a single current-state label.

**A terminal row is necessary, not sufficient.** The shared document must match the requested input version, use the intended revisions, and pass checks independent of the renderer's success flag. Useful checks include document-class page-count bounds, absence tests using seeded personal-data fixtures, and structural parsing for the PDF version the pipeline supports.

## Test ambiguity and retention together

Test a worker that never changes state, a final response immediately before the deadline, an unrecognized state, a mismatched identifier, and repeated transport failure. Run two watchers concurrently. Neither may submit replacement work merely because the other stopped observing.

For deployment, compare task age by state and template revision. Alert when the oldest active task exceeds execution policy, expired leases accumulate, or artifacts and final records disagree. Task identifiers belong in trace events or sampled diagnostics, not metric labels with unbounded cardinality.

Retention needs failure-path tests too. Verify expiry of the unredacted source, intermediate assets, quarantined artifact, logs, and retry metadata after success, failure, cancellation, and unknown observation. A watcher deadline must never justify indefinite source retention.

The trade-off is blunt.

This is the deliberate trade-off: immutable revisions, transitions, checksums, and categorical errors preserve evidence for most control-plane failures without preserving document bodies. After source expiry, exact replay may require tenant resubmission. That operational cost is easier to govern than an undefined archive of personal data.

## Further reading

## References

- ISO 32000-2, Portable Document Format: https://www.iso.org/standard/75839.html
