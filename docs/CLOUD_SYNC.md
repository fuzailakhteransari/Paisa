# Optional Google sync — three-part delivery

The Android app must stay fully usable in Local mode without Google sign-in. Keep account controls in Settings → Account & sync. All built-in UI remains English.

## Part 1 — mode controls (0.6.0)

- Local only is the default for both new and existing records.
- Settings shows Local only and Google backup & sync as preferences.
- Saving Google preference records intent only. Active mode stays Local, the account stays Not connected, and cloud backup stays Not enabled.
- Google sign-in is explicitly unavailable in this version; its button is disabled. There are no OAuth tokens, network permissions, cloud SDKs, upload requests or account sessions in this delivery.
- Choices persist on Android and in the browser preview, including encrypted file backup/restore. Selecting a preference never edits financial records, notes, budgets or rules.
- Existing manual backup/export remains available. Home and the four primary destinations stay focused.

## Part 2 — Google identity and cloud backup (awaiting user instruction)

- Configure Android and web Google clients and a separate private Paisa storage namespace. Reuse the DirDash sign-in approach where useful; personal finances must not enter its shared business workspace or Sheets dataset.
- Explicit Google sign-in and opt-in link the existing local ledger to the selected account. Preview the first upload or restore; preserve IDs, notes, source deduplication evidence and accounting links.
- Separate preference, authenticated account, active sync state, error state and last successful backup. Never claim a successful backup before the server confirms it.
- Disconnect stops cloud work and preserves local records. Cloud-backup deletion is a separate explicit action. Account changes require deliberate handling, so another account's ledger is never silently merged.
- Credentials/session state are stored separately from ordinary settings and exported financial backups. Local mode remains usable without a connection.

## Part 3 — desktop web and two-way sync (awaiting user instruction)

- Same Google account provides private access to its Paisa ledger in a browser.
- Include notes/search, totals/date ranges, account history and editing using the shared accounting rules.
- Queue offline changes and show the last confirmed sync. Protect against concurrent edits, silent overwrites, duplicate imports and cross-account leakage.
- Test using the approved private actual-data corpus locally; never upload the test corpus or raw messages for development validation.

Do not start the next part until the user asks. Part 1 is not an operational Google-login or cloud-backup release.
