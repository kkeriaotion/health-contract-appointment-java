# Resume PDF Text Extraction and Structured Fields for Monthly Reporting

Short answer: extract selectable text from each resume PDF, ask a model for a small typed record, and keep both the source file and extraction evidence until the monthly report has been rendered and archived. Do not treat a plausible JSON response as proof that the PDF contained the claimed facts. For a B2B SaaS recruiting report, the governing choice is fidelity versus render cost: use the cheap text path for readable PDFs, and send image-only pages to an OCR path only when the report requires fields that cannot otherwise be verified.

## How can Python parse a PDF resume and extract text for structured fields?

The decision record has three invariants. A field in the report must trace to the original resume, missing evidence must remain missing rather than become an inferred date or employer, and a retry must not silently change an already archived month's result. Keep the original PDF, a digest of its bytes, the extracted text or an access-controlled pointer to it, the parser version, the model schema version, and the final field record together under the report's retention policy. Resumes contain personal information; access controls and deletion schedules apply to all of these artifacts, not just the final PDF.

PDF is a page-description format, not a promise of semantic reading order. Text extraction may return fragments in an order that differs from what a person sees, and a scanned page may yield no selectable text at all. An empty extraction is a routing signal, not an empty resume. A two-column layout is another failure boundary: if education lines and employment lines interleave, a model can produce convincing fields from a corrupted sequence. The PDF specification defines the format; it does not certify that a particular file's extracted text preserves the author's intended structure.

That distinction matters.

For the monthly report, keep the input side and output side separate. Resume ingestion records what was actually observed. Reporting queries reviewed records, renders a PDF from a fixed report snapshot, and archives that rendered artifact with a digest and the snapshot identifier. Re-running extraction later may improve future reports, but should not rewrite the evidence behind a published month.

## Which extraction path earns its cost?

| Path | When it fits | Failure boundary | Cost and fidelity trade-off |
| --- | --- | --- | --- |
| Selectable-text extraction | Pages with usable embedded text | Reading order, hidden text, and missing glyph mappings | Low render cost; inspect suspicious fields against the source |
| OCR after page rendering | Image-only pages or unusable embedded text | Recognition errors, rotation, and small print | Additional rendering and OCR work; capture page-level evidence |
| Human review | Conflicting dates or report-critical missing fields | Review throughput and privacy exposure | Highest attention cost; strongest check for ambiguous evidence |

The threshold should come from the report's error budget, not an arbitrary number of extracted characters. A page can have plenty of text yet scramble the relationship between an employer and its dates. Suppose the extracted stream puts an employer heading next to the dates from the adjacent education column: a syntactically valid model output can now attribute those dates to the wrong job. A length check will pass, and so will a schema check. Compare the employer-date pair with the original page before counting it in the monthly aggregation; if the evidence is ambiguous, leave the dates out and route that record for review. Record the number of pages examined, which path each page used, missing required fields, and the rate of records referred for review; investigate shifts before they become a polished but false month-end PDF. For a job title quoted in a report, store the supporting snippet or page reference. Do not copy entire resumes into application logs.

## Where should the model be allowed to decide?

Use it to map observed text into a constrained shape, not to repair absent source material by guessing. This Python critical path deliberately leaves OCR and model transport behind injected interfaces: their runtime behavior, privacy terms, and deployment cost must be evaluated separately. The model adapter must return a Python mapping decoded from its structured response; the caller checks its keys and types before treating it as report data.

```python
from dataclasses import dataclass
from hashlib import sha256
from io import BytesIO
from typing import Callable, Mapping

from pypdf import PdfReader


@dataclass(frozen=True)
class ResumeFields:
    name: str | None
    employers: tuple[str, ...]
    source_sha256: str
    status: str


def parse_resume(
    pdf: bytes,
    structure: Callable[[str], Mapping[str, object]],
) -> ResumeFields:
    digest = sha256(pdf).hexdigest()
    reader = PdfReader(BytesIO(pdf))
    pages = [(page.extract_text() or "").strip() for page in reader.pages]
    if not pages or any(not page for page in pages):
        return ResumeFields(None, (), digest, "needs_ocr_or_review")

    candidate = structure("\n\n".join(pages))
    name = candidate.get("name")
    employers = candidate.get("employers")
    if name is not None and not isinstance(name, str):
        raise ValueError("name must be a string or null")
    if not isinstance(employers, list) or any(
        not isinstance(item, str) for item in employers
    ):
        raise ValueError("employers must be a list of strings")
    return ResumeFields(name, tuple(employers), digest, "needs_evidence_review")
```

The adapter should be instructed to return null for an unsupported name and an empty list for unsupported employers, with no outside knowledge. Even valid types do not establish factual accuracy. Before a record becomes report-eligible, compare each populated field with the page text or a reviewed page image; for high-impact fields, require a page reference and supporting excerpt in the adapter contract. Password-protected, malformed, and oversized PDFs need explicit intake limits and an error state, not an unbounded retry loop. The example raises extraction errors to its caller on purpose; a production worker must capture them as a separate state and avoid marking the document parsed.

Valid JSON is not evidence.

## Why reject universal page rendering?

Rendering every resume before extraction looks consistent, but it adds a rasterization and recognition step even where embedded text is already available. That extra step can replace precise characters with OCR uncertainty, while increasing work per page. It remains a valid choice when the input set is predominantly scanned or when visual layout, rather than selectable text, is the evidence being assessed. Measure that distribution on representative, permissioned samples before selecting a default.

Test the pipeline with two-column resumes, scanned pages, rotated pages, redacted text, and conflicting employment dates. Verify that the archive operation happens only after the report snapshot and rendered PDF have durable identifiers; a render timeout must not leave a report marked complete. Then compare a small reviewed sample of structured fields with its source pages each month. A neat PDF is not an accuracy check.

## References

- ISO 32000-2, Portable Document Format: https://www.iso.org/standard/75839.html
- pypdf, Extract Text from a PDF: https://pypdf.readthedocs.io/en/stable/user/extract-text.html
- W3C, Understanding Success Criterion 1.3.1, Info and Relationships: https://www.w3.org/WAI/WCAG22/Understanding/info-and-relationships.html
