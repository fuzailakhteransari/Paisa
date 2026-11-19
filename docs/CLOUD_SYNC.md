# Optional Google sync — completed delivery

Part 1 shipped mode controls in 0.6.0. At the user's request, Parts 2 and 3 ship together in 0.7.0: Google connection, confirmed backups and the web companion. Local mode remains the default. A preference or login alone never uploads records.

## User flow

Android: Settings → Account & sync → Continue with Google. Match the connection code in the system browser, sign in and approve the phone. Return to Paisa and confirm a first backup or restore. On a computer, open [Paisa web](https://dir-dash-app.vercel.app/paisa/), sign in with the same account and restore its cloud copy.

Independent changes merge. Conflicting fields need review. Offline edits stay locally until reconnected. Disconnect preserves records; cloud deletion is separate and confirmed.

## Deployment and boundaries

- Android's local WebView does not navigate to OAuth or the cloud. Platform HTTPS handles sync. Credentials, baselines and pending operations use Android Keystore AES-GCM files, separate from financial exports.
- The companion shares the existing trusted DirDash origin and Google Identity Services client. `/api/paisa` verifies Google's token audience, issuer, expiry, verified email and per-login nonce through google-auth-library. Paisa uses separate sessions and storage, never the business workspace or Sheets dataset. Shared origin is not a browser security boundary against other trusted DirDash code.
- Browser sessions use Secure, HttpOnly, SameSite=Lax cookies, CSRF protection and a server-checked expected Google subject. Records use IndexedDB. Web Locks allows one writable tab; others are read-only.
- Phone pairing uses a random S256 verifier, ten-minute approval link, six-digit code and explicit native initialization. The custom URI is a navigation hint and conveys no credentials.
- The owner-private Sites D1 service accepts server-to-server access through the Sites dispatch bearer. Ledger operations derive ownership from the session hash. Preserve owner-only access. Cloud financial snapshots are not end-to-end encrypted.
- Shared fields are whitelisted by both SyncCore implementations. Raw SMS/email, OTPs, credentials and device permission preferences are excluded. Parsed financial descriptions, notes, account labels and capture fingerprints are included.

## Source and configuration

`cloud/storage-rpc.mjs` implements private storage; `cloud/schema.sql` is its migration. Deploy a Worker-backed Sites POST `/api/rpc` route with D1 binding `DB`, preserving owner-only access. Apply migrations during deployment, never during requests.

`cloud/paisa-api.mjs` implements the Vercel identity/API. Generate companion files with `npm run build:web`, or `node scripts/prepare-web.cjs <public/paisa destination>`. Static files belong under `/paisa/`. Use the generated web CSP and external boot module; never cache the API.

Host configuration names; values are never committed:

| Variable | Purpose |
|---|---|
| GOOGLE_CLIENT_ID | Existing Google web audience authorized for the canonical host |
| DIRDASH_SESSION_SECRET | Existing server secret; Paisa uses its own signing namespace |
| PAISA_ORIGIN | `https://dir-dash-app.vercel.app` |
| PAISA_STORAGE_URL | Private Sites storage origin |
| PAISA_STORAGE_TOKEN | Server-only Sites dispatch bearer |

## Consistency and recovery

Uploads are chunked, hash-verified, sealed against part writers and published with revision compare-and-swap. Up to five recent revisions remain. Stale abort/delete operations cannot remove newer committed copies. Expired staging uploads are cleaned during subsequent activity.

Clients three-way merge the confirmed baseline, current local state and cloud revision. Related payment-accounting fields are resolved together. Capture identities stay append-only. Pending acknowledgements rebase edits/captures that arrive during sync before advancing the baseline. Android uses guarded SQLite transactions and preserves Undo; web applies workspace/baseline/metadata atomically in IndexedDB.

Replacement first saves local state. Android restore uses a durable intent and database marker. Browser replacement keeps an IndexedDB safety record exportable as an Android-compatible encrypted backup. Manual Android file restore stops cloud sync. A failed first backup never deletes local records; if its acknowledgement is lost, review the cloud copy and deliberately restore/resume or replace.

## Verification and limits

Public checks cover storage/auth races and empty-ledger web setup. Local tests cover the approved 14,072 SMS, 1,583 parsed transaction rows, Java/web merge agreement, independent edits, offline persistence, conflicts, tab locking and account switches. Actual records are used only on localhost/in-memory storage, never on live services or CI.

Sync checks after edits and about every 30 seconds while open. Android background scheduling depends on network and battery policy. Interactive Google selection, APK installation, browser return-to-app and background behavior require real-device acceptance; desktop tests do not establish those outcomes.

Live OAuth setup check: Google currently rejects the canonical host with “origin is not allowed”. The existing web OAuth client must authorize `https://dir-dash-app.vercel.app` in Google Cloud Console → Google Auth Platform → Clients → Authorized JavaScript origins. The client ID is supplied by the existing host configuration. This external setting is required before live Google connection works; local mode and localhost tests do not depend on it.
