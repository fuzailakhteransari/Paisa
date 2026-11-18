# Contributing to Paisa

Keep routine tracking quick and Home focused. Put advanced controls in Settings or contextual forms. All built-in interface copy is English; user-entered notes and names may use any language.

1. Use a focused branch and describe the user-visible problem.
2. Keep amounts as integer paise. Preserve captured transaction IDs, notes, repayment links and duplicate evidence.
3. Use fictional bank messages in tests. Do not commit credentials, signing keys, personal messages, financial backups or databases.
4. Run the relevant checks in [Testing](docs/TESTING.md). Accounting, parsing and persistence changes need meaningful regression coverage.
5. Include before/after screenshots for visible changes and identify actual Android device testing separately from browser previews.

Runtime code uses Android platform APIs and local WebView assets. Playwright and JSON-java are desktop test dependencies only.

Repository access does not grant an open-source license; no open-source license has been assigned to this project.
