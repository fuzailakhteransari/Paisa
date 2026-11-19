# Privacy and permissions

Paisa processes supported financial text on the Android device. Local mode requires no login or cloud connection. Optional Google sync uses internet access to the Paisa API and private storage. There is no analytics or advertising SDK or banking credential collection.

| Access | Purpose | Optional? |
|---|---|---|
| Read/receive SMS | Import recent supported bank messages and capture new payments | Yes |
| Android notification access | Process visible financial text from explicitly selected Gmail/bank/payment apps | Yes |
| Post notifications | Payment-review prompts, bill reminders, renewals and budget alerts | Yes |
| Internet/network state | Optional Google connection, backup and sync | Cloud opt-in; no runtime permission prompt |
| Boot completed | Re-schedule the app's inexact daily reminder check | System permission |
| Android document picker | Save a backup/CSV or explicitly choose a backup/email file | Per action |

Android SMS and notification permissions are broad. Paisa filters selected apps before extracting notification text and ignores unsupported personal/OTP content during financial parsing. Full imported email text is not retained or rendered as executable HTML. Truncated or hidden notifications can be missed.

Financial records, optional notes, source information and capture fingerprints are kept in app-private SQLite storage. Explicitly enabled Google sync also stores supported financial fields in the selected account’s cloud workspace. Deleted-record tombstones and fingerprints may remain for Undo and duplicate prevention. Uninstalling removes private records. Android automatic backup and device transfer are excluded.

Full backups are password encrypted. CSV exports are readable and are not a recovery format. Files are saved to a location selected by the user; a selected third-party storage provider handles those files under its own policies. Keep the backup password separately because it cannot be recovered.

Before restore, Paisa keeps a password-encrypted local safety copy. It uses the password entered for that restore and can be exported from Settings → Backup & export. See the [user guide](USER_GUIDE.md) for recovery details and current limitations.

## Optional cloud service

Google identity is verified server-side. The Google subject owns each ledger; email is for display. Native sync files use Android Keystore encryption. Browser sessions use server-only secure cookies; cached records use IndexedDB. Per-request account checks and one writable tab prevent account-cookie mixups and stale tab overwrites.

Cloud snapshots include recorded transactions, notes, accounts, schedules, budgets, rules and capture fingerprints. Raw SMS/email bodies, OTPs and permission settings are excluded. Snapshots are compressed, integrity-checked and transmitted over HTTPS to an owner-private service. They are not end-to-end encrypted: authorized infrastructure operators can access stored financial data. Paisa uses a separate personal Vercel project, web origin, Google OAuth client and session secret. Its personal storage service does not use company business data or credentials.

Disconnect preserves records and copies. Cloud deletion removes retained versions and revokes sessions; device caches and exported files are not remotely erased. Expired staging uploads are cleaned during cloud activity. Deletion from platform operational backups is not immediate. See the guide for recovery and device limits.

## Public examples

Repository examples and screenshots use synthetic identities, notes, account labels and amounts. They do not represent a user’s relationships or transactions. Private SMS regression input and its screenshots remain outside the repository, release archives and CI uploads. CI uploads an explicit list of synthetic preview images.
