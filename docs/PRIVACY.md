# Privacy and permissions

Paisa processes supported financial text on the Android device. It has no backend, analytics or advertising SDK, INTERNET permission or banking credential collection.

| Access | Purpose | Optional? |
|---|---|---|
| Read/receive SMS | Import recent supported bank messages and capture new payments | Yes |
| Android notification access | Process visible financial text from explicitly selected Gmail/bank/payment apps | Yes |
| Post notifications | Payment-review prompts, bill reminders, renewals and budget alerts | Yes |
| Boot completed | Re-schedule the app's inexact daily reminder check | System permission |
| Android document picker | Save a backup/CSV or explicitly choose a backup/email file | Per action |

Android SMS and notification permissions are broad. Paisa filters selected apps before extracting notification text and ignores unsupported personal/OTP content during financial parsing. Full imported email text is not retained or rendered as executable HTML. Truncated or hidden notifications can be missed.

Financial records, optional notes, source information and capture fingerprints remain in app-private SQLite storage. Deleted-record tombstones and fingerprints may remain for Undo and duplicate prevention. Uninstalling removes private records. Android automatic backup and device transfer are excluded.

Full backups are password encrypted. CSV exports are readable and are not a recovery format. Files are saved to a location selected by the user; a selected third-party storage provider handles those files under its own policies. Keep the backup password separately because it cannot be recovered.

Before restore, Paisa keeps a password-encrypted local safety copy. It uses the password entered for that restore and can be exported from Settings → Backup & export. See the [user guide](USER_GUIDE.md) for recovery details and current limitations.
