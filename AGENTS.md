# Paisa development checks

- Use the user's approved local SMS corpus for every SMS/capture/accounting change, alongside the public regression checks. `npm test` includes `test:real` when the private corpus is available; run `npm run test:real -- --required` for local release validation.
- The default private corpus directory is the sibling `../private-paisa-sms/`. Override with `PAISA_SMS_DATA_DIR` when needed. Its immutable XML snapshot, independent expected labels, baseline and result files stay outside this repository.
- Never commit, upload, include in an APK/source archive, or paste raw private SMS, OTPs, identifiers or transaction exports into public logs. CI has no private corpus and must report that fact rather than claim real-data coverage.
- Do not regenerate expected labels from current parser output. Review the source layouts independently before changing private expectations. Keep failure logs limited to local message IDs and field names; write detailed diagnostics only inside the private directory.
- `PreviousSmsParser` is frozen 0.4 fingerprint compatibility code. It must never classify new records. Bump `SmsParser.REVISION` for subsequent changes that need a conservative rescan.
- Automatic rescans may revise only untouched pending captured SMS. Preserve user edits, notes and linked accounting records. Exclude questionable old captures pending confirmation instead of silently deleting them.
- Test sent/received timestamp compatibility, replay idempotence, classification after user linking, cash/UPI Lite transfers and backup/restore using the real local dataset. Use real parsed monetary values for sandbox workflows; test-only purpose labels do not establish the user's actual lending or spending intent.

- Optional sync is staged per `docs/CLOUD_SYNC.md`. Local mode remains the default. A stored Google preference is not authentication or active sync. Do not start a later delivery until the user asks.

- Public fixtures, demo data, screenshots, filenames and documentation must use synthetic identities (for example Alice/Bob), generic notes and fictional amounts. Never copy contact names, locations, transactions or wording from a user prompt into distributed examples. Keep approved actual-data testing local and outside published artifacts.
