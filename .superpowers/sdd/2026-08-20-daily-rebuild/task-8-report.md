# Task 8 report

## Scope delivered

- Added local CSV parsing for WeChat and Alipay-style headers, including UTF-8/GBK decoding, dates, signed amounts, direction, status, order identifiers, raw-row retention, warnings, and skipped-row counts.
- Added Excel parsing through Apache POI with `use` blocks for both the input stream and workbook.
- Added deterministic offline classification for food, transport, shopping, entertainment, bills, health, education, transfers, income, and other.
- Added duplicate fingerprints and visible duplicate candidates in import previews.
- Added a repository that keeps previews in memory and writes transactions plus an import log only after explicit confirmation, in one Room transaction.
- Added cent-based statistics with month/week/year periods, direction/category/search filters, budget progress, editable transaction state, and budget threshold persistence for 50/70/90/110 percent.
- Added bill ViewModel, import preview, editor, statistics screen, and navigation/container wiring for the existing `账单` route.

## Tests and fixtures

- Added `BillParserTest`, `ExcelBillParserTest`, `BillClassifierTest`, `BudgetThresholdTest`, `BillRepositoryTest`, and the Compose `BillImportTest`.
- Added UTF-8 synthetic WeChat and Alipay CSV fixtures under `app/src/test/resources/fixtures`.
- The Excel test creates and closes a synthetic `XSSFWorkbook` in memory. A committed binary `bills-synthetic.xlsx` was not generated because this change is being applied with `apply_patch` only and the environment has no working JDK/POI runtime available to generate a binary fixture safely. The parser still exercises the real POI XLSX code path.

## Verification attempted

- `git diff --check` completed without whitespace errors.
- Focused Gradle tests were attempted after the tests were added and again for the Task 8 scope.

## Verification limitation

Gradle cannot start because the existing environment variable points to a missing JDK:

`JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`

No alternative JDK was available in the checked common install locations, so unit tests, Android tests, and APK compilation could not be executed in this environment.
