# PII-Redactor

A Spring Boot command-line tool that scans a `.docx` document, detects personally identifiable information (PII), and writes out a redacted copy with every PII item replaced by a consistent, realistic fake value — the same real name, email, or number always maps to the same fake one everywhere it appears.

Built and tested against a real Red Herring Prospectus (an Indian IPO offer document), but the detection layer is generic and works on any `.docx`.

---

## Screenshots

**Before redaction**

<img width="581" height="301" alt="image" src="https://github.com/user-attachments/assets/b955e2a2-8ff6-48be-9772-2f41b5eacfaa" />

**After redaction**

<img width="590" height="346" alt="image" src="https://github.com/user-attachments/assets/ae4623f1-3f09-4581-a52d-79d3901d31e4" />

**Console output**

<img width="880" height="143" alt="image" src="https://github.com/user-attachments/assets/fb463c2d-ef19-466a-a859-6945d68a6adf" />

---

## What it does

- Reads every `.docx` file placed in `input/`
- Detects 9 categories of PII across the document body, tables, headers, and footers
- Replaces each item with a fake value, kept consistent across the whole document
- Writes the redacted copy to `output/`
- Prints a per-type count of what was replaced

No command-line arguments, no configuration step place a file in `input/` and run the jar.

---

## PII types detected

| Type | Detection method | Example (before → after) |
|---|---|---|
| Email | Regex | `rohan.mehta@examplecorp.com` → `user1@example.com` |
| Phone number | Regex (Indian formats) | `+91 98765 43210` → `+91 9000000001` |
| SSN | Regex + SSA allocation-rule checks | `123-45-6789` → `123-45-0001` |
| Credit card | Regex + Luhn checksum | `4532 0151 1283 0366` → `4000 0000 0000 0001` |
| IP address | Regex + octet range check | `192.168.10.55` → `10.0.0.1` |
| Date of birth | Regex, only near a birth-context cue | `Date of Birth: 4 May 1985` → `Date of Birth: 05 January 1990` |
| Physical address | Anchored on a 6-digit PIN code | `201, Tower 2, Montreal Business Centre, Off Pallod Farms, Baner Pune – 411045 Maharashtra, India` → `19 Example Street, Sample City 100019 Maharashtra, India` |
| Person name | Two-pass gazetteer | `Sarthak Malvadkar` → `Jane Smith` |
| Organization name | Two-pass gazetteer | `Sunrise Textiles Private Limited` → `Acme Metals Limited`|

---

## Approach

**Structured PII** (email, phone, SSN, credit card, IP, date of birth) is matched with regex, each backed by a validation step that plain pattern matching can't express on its own:

- Credit cards are checked against a **Luhn checksum** without it, long digit runs in financial tables (share counts, capital figures) get flagged as cards.
- IP addresses are checked for **valid octet ranges** (≤ 255) without it, version like strings such as `2.16.1.3` match the pattern just as well as a real address.
- SSNs are checked against the **SSA's own allocation rules** (area 000/666/900–999, group 00, and serial 0000 are never issued).
- Dates are only redacted as a **date of birth** if they sit within ~40 characters of an explicit cue ("date of birth", "born on", "DOB"). The source document has 200+ unrelated dates filings, board resolutions, bid windows and blanket-redacting all of them would make the document unreadable for no privacy benefit.

**Unstructured PII** (person names, organization names) uses a **two-pass gazetteer** instead of a general purpose NER model:

1. **Harvest** scan the whole document once for the handful of fixed phrasings a legal document like this introduces its people and companies with: `Contact Person: X`, `Mr./Ms./Dr. X`, `X Private Limited`, `X LLP`, and so on. Collect every name found this way into a dictionary.
2. **Match & replace** scan every paragraph and replace any occurrence of a harvested name, **longest name first**, so `Kushal Subbayya Hegde` is consumed before the shorter `Kushal Hegde` can be mistaken for a different person.

This was a deliberate choice over spaCy/Presidio/OpenNLP: a prospectus *declares* its own entities in structured form, and reading those declarations directly is more reliable here than a statistical model guessing from surrounding context.

**Consistency** a `PseudonymGenerator` remembers every fake value it hands out, keyed by the normalized real value, so the same person or company reads the same way everywhere in the document. Person names are keyed by first + last name specifically, so a full name and its shortened form later in the text still resolve to the same alias.

**Overlap resolution** detectors sometimes disagree on the same span (an email's domain can also look like part of a company name). Matches are ranked by PII-type certainty first, then by length, and anything overlapping a match already kept is discarded.

**Docx-safe rewriting** Word can silently split one sentence across several formatting "runs" (from edits or spell-check), so a name could straddle a split invisibly. Each paragraph is stitched into a single string, redacted, and the result written back into the first run, clearing the rest. Trade-off: if a paragraph mixed formatting (part bold, part not), the redacted text takes on only the first run's formatting.

---

## Tech stack

- **Java 17**
- **Spring Boot 3.4.1** — plain `CommandLineRunner`, no embedded web server
- **Apache Maven** — build tool
- **Apache POI** (`poi-ooxml` 5.4.0) — reads and writes `.docx`

---

## Architecture

```
src/main/java/com/redactor/
├── RedactorApplication.java     entry point — finds files, runs the pipeline
├── model/
│   ├── PiiType.java              the 9 PII categories + tie-break priority
│   └── PiiMatch.java              one detected span (offsets, type, value)
├── detect/
│   ├── PiiDetector.java           the one extension point — one interface
│   ├── AbstractRegexDetector.java base class for regex-shaped PII
│   ├── EmailDetector, PhoneDetector, SsnDetector,
│   │   CreditCardDetector, IpAddressDetector,
│   │   DateOfBirthDetector, AddressDetector
│   ├── EntityGazetteer.java       harvests names/companies (two-pass)
│   ├── PersonNameDetector.java    reads from the gazetteer
│   └── OrganizationDetector.java  reads from the gazetteer
└── service/
    ├── RedactionService.java     merges detector output, resolves overlaps
    ├── PseudonymGenerator.java   generates + remembers fake values
    └── DocxProcessor.java        reads/writes the .docx, run-splitting logic
```

Every detector implements the same two-method `PiiDetector` interface. Spring collects every `@Component` that implements it into one injected list adding a new PII type is one new class, nothing else changes.

---

## How to run

```bash
mvn clean package
java -jar target/redactor-1.0.0.jar
```

Place any `.docx` file in `input/` before running. The redacted copy appears in `output/` as `<original-name>_redacted.docx`, and the console prints a summary like:

```
Redacting Red_Herring_Prospectus.docx -> Red_Herring_Prospectus_redacted.docx
Done. Replacements by type:
  EMAIL: 26
  PHONE: 18
  PERSON: 41
  ORGANIZATION: 37
  ...
```

---

## Known limitations & trade-offs

- **Organization/person redaction depends on the gazetteer harvest.** A name that only ever appears in an all  caps, comma-separated cover-page list (rather than in a `Mr. X` / `Contact Person:` form elsewhere in the document) won't be picked up a known false negative.
- **Addresses are anchored on the PIN code and expand backward only** a trailing "State, Country" that follows the PIN code isn't captured.
- **Dates of birth require a nearby cue phrase.** A birth date stated with no such cue nearby is a deliberate miss, chosen to avoid redacting the document's other few hundred unrelated dates.
- **Organizations need a legal suffix** (Private Limited / Limited / LLP / Trust / Ltd) to be recognized an informal reference to a company without that suffix nearby won't match.
- **Redacted paragraphs take on one formatting style.** If a paragraph mixed bold and non-bold text, the redacted version isn't split back out everything after redaction follows the first run's formatting.
- **SSN, credit card, and IP address detectors had zero real instances to validate against** in the prospectus used for testing expected, since none of those are typically present in this type of document. Each detector's logic (Luhn, octet range, SSA rules) was reasoned through and validated against constructed examples instead.

---

## Tested against

Manually spot-checked against a real ~56,000-word Red Herring Prospectus, which contains roughly 50+ email addresses, 30+ phone numbers, hundreds of company-name mentions, and 200+ unrelated dates and, as expected for this document type, zero SSNs, credit card numbers, or IP addresses.

---

## Possible extensions

- A formal precision/recall evaluation report against a hand-annotated sample
- Detectors for India-specific identifiers (CIN, PAN, DIN, Aadhaar) as their own PII types
- Externalizing the fake-value pools and regex patterns into a config file
- An audit log (CSV/JSON) of every replacement made, for review before the redacted file is shared
