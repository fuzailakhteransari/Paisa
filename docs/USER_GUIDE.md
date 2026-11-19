# Paisa 0.7.0 — user guide

Android development build, 19 November 2026. The existing four main destinations remain Home, Activity, Loans and Bills. Accounts, Budgets, Subscriptions, Tracking and Backup are reached through Settings. Home adds only one small budget summary, and only after an overall budget is configured.

## Install or update

Install **Paisa-0.7.0-debug.apk** on Android 8+ with an updated Android System WebView. Install over your existing Paisa build without uninstalling to retain existing records. The application ID stays `in.paisa.personal`, the version code increases to 10, and the signing certificate is checked against the earlier build. This is a development-signed APK, not a Play Store release.

All built-in interface copy is English. Your existing names, notes and descriptions are preserved as entered.

The database upgrade creates new tables without dropping existing entries or bills. Existing notes, merchant identities and safe remembered category rules are carried forward. Old account labels are preserved as legacy accounts rather than guessing which real bank they belong to. Rename/correct these in Settings → Accounts when necessary.

## Recover missed HDFC card purchases

Version 0.3.2 supports HDFC purchase SMS beginning with `Txn Rs… On HDFC Bank Card… by UPI…`, using the matching `BLOCK CC` marker to identify the credit card. Install this update over the existing app, then use **Settings → Tracking & reminders → Import last 30 days**. The original SMS dates are retained. Previously recognized records keep their duplicate fingerprints; newly supported purchases appear for review. A partial/masked payee is not a reliable remembered merchant identity.

## Notes, tags and search totals

Use **Notes & tags** while reviewing or editing a payment. Write a sentence naturally, such as a lunch with a person or a petrol purchase. The original note is preserved. Tags are derived locally, so old notes work immediately without re-entering them. Add an optional `#tag` when useful.

In **Activity**, search a name, note word, category, description, amount or hashtag. Partial names work while typing, and multiple words narrow the result. Fuel, Petrol and Peetrol are grouped. Open **Browse note tags** to tap an existing note keyword. The keyboard Search action hides the keyboard and reveals the result summary.

Search totals apply to the matching transactions in the selected period and account filter. Spending is net of matching refunds. Money lent, money borrowed and repayments are shown separately; they are not added to ordinary spending. These are period totals rather than outstanding loan balances. Use Loans for balances. Set the correct transaction type when lending/borrowing; a note does not reclassify a payment.

Other is available in the quick category review and full entry form.

## Date filters

Home and Activity offer Today, Week, Month and Range; Activity also offers All. Choose **Range**, enter **From** and **To**, and apply. Both dates are included, even for a single-day selection. An inverted or invalid range is rejected without changing the active report.

The selected range applies to Home spending/category totals, Activity rows, matching search totals and account-history reports. Returning between Home and Activity retains an active custom range. Opening account history starts at All; you can then select its reporting range. The existing monthly salary-cycle setting still applies to Month. Budgets remain monthly, and bills/subscriptions keep their own schedules.

## Features

| Area | Behaviour in 0.7.0 |
|---|---|
| SMS capture | Supported bank/card transaction messages are processed locally. Import the last 30 days; already-seen messages are skipped, including fingerprints retained from earlier versions. |
| Gmail email tracking | Optional automatic tracking from visible Gmail payment-email notifications. This does not sign in to Gmail, read the complete inbox, or fetch past email. Truncated/grouped/hidden alerts can be missed. |
| Email share/import | Share bank-email text to Paisa, paste it, or import common .eml/plain-text messages. Preview before saving. Supported card-bill notices can become bill reminders; paid transactions become reviewable entries. Full original email text is not retained. |
| Bank/payment-app notifications | User selects allowed apps and separately grants Android notification access. Current/classic HDFC and Kotak packages are grouped behind one bank choice; ICICI, SBI, Axis, GPay, PhonePe and Paytm presets are included. Other packages can be added explicitly. |
| Duplicate handling | Strong matching references, amounts, types and compatible account identity are merged. Possible cross-source matches wait for review and stay outside totals until resolved. Keep separate or merge, with Undo. |
| Cash management | A dedicated cash wallet and bank-to-cash ATM transfers. Withdrawal itself is not an expense; the later cash spend counts once. |
| Account history | Separate bank/cash/card accounts, account-specific transaction history, opening/corrected balances and SMS balance snapshots. Provenance and last SMS time are shown; these are not verified live banking balances. |
| Monthly/category budgets | Optional overall limit and optional per-category limits. Net expenses, remaining amount and progress are shown separately from cash flow. |
| Custom month start | Choose any day 1–31. Shorter months clamp the boundary to their last day. The same cycle is used for monthly totals, budget usage and budget alerts. |
| Repayment linking | Link a captured incoming/outgoing payment to the original loan without adding another transaction. Partial repayments reduce that loan and do not count as regular income/expense. Person-total repayments can allocate oldest loans first when no specific loan is selected. |
| Subscriptions | Manual schedules plus suggestions from repeated recorded expenses. Weekly/monthly/yearly cycles, next due date, edit/pause and existing-payment linking. A schedule never creates a paid expense by itself. Strong merchant/account/amount/date matches can link an actual captured payment and advance the renewal. |
| Bill reminders | Card statements and utility bills, partial payments and existing-payment linking. Some supported total-due card alerts can create a bill; minimum-only notices are not mistaken for the complete statement amount. |
| Bulk correction + Undo | Select records in Activity, change categories or delete together. Each batch is validated and committed atomically. Undo restores notes and previous fields while preserving unrelated new captures; later conflicting user edits are not overwritten. Native Undo history retains up to 20 actions. |
| Full backup/restore | Password-encrypted local backup includes transactions, notes, accounts, rules, bills, loan links, budgets, settings, subscriptions and capture fingerprints. Restore validates before a replacement confirmation and makes an encrypted local safety copy first. |

## One-time setup

1. **SMS:** Home → Enable SMS tracking, then Settings → Tracking → Import last 30 days. Permissions are optional; manual entries still work.
2. **Gmail/bank alerts:** Settings → Tracking → select Gmail and your bank/payment apps, save, then open Android notification-access settings and enable Paisa. Permit Paisa to post notifications if you want review prompts and reminders. Android grants broad listener access; Paisa filters selected apps before extracting financial text.
3. **Accounts/Cash:** Settings → Accounts → enter an opening/corrected balance and its effective date/time if your history is incomplete. Choose the correct account for manual spends. For a transfer, select distinct source and destination accounts.
4. **Budgets:** Settings → Budgets → set a monthly limit and cycle start day. Add category limits only if useful. Enter zero to remove a category limit.
5. **Subscriptions:** Settings → Subscriptions → add a schedule or review a recurring-payment suggestion. Use “Link / record payment” when paid; select an existing payment before creating a manual one.
6. **Bills:** Bills → add a card statement or utility bill with a due date. Link actual payments or record them manually. These buttons record accounting; they never send money.
7. **Backup:** Settings → Backup & export → create a full encrypted backup. Keep its password separately. CSV is for analysis, not full recovery.

## Reminder behaviour

The Android app uses an inexact daily alarm around 9 AM, with re-scheduling after reboot, app update and timezone change. Supported new captures and app activity also trigger checks. Unpaid bills can prompt before the due date, on the due date and periodically overdue; subscriptions prompt around upcoming renewals. Budget alerts are limited to 80% and 100% milestones per cycle. They are grouped instead of showing an independent notification for every item.

Delivery depends on notification permission, OEM battery policies and Android scheduling. Force-stopping the app can stop background operation until it is opened again. An inexact alarm is not an exact-time guarantee. [Android alarm scheduling documentation](https://developer.android.com/develop/background-work/services/alarms)

## Accounting and accuracy

- Amounts are integer paise. Purchases minus refunds form net expenses; loans, repayments and own transfers do not become ordinary income/expense.
- A card purchase is an expense; paying its statement is cash out, not another expense. Card payment receipts without a known funding account are not converted into a fictitious bank account using the card's last four digits.
- Bank-to-cash transfers change account balances without increasing expense totals. Bank and cash purchases reduce the appropriate account.
- Account balances use the opening/corrected balance plus later recorded movements, or the latest available SMS balance snapshot plus movements after that snapshot. Missing history and unassigned accounts need correction; the app does not connect to a bank-balance API.
- Incoming payments require a purpose choice; an account credit is not automatically salary. Matching a repayment or bill updates the original record instead of creating a second bank movement.
- Unresolved possible duplicates remain visible in Activity but do not inflate totals. Ambiguous matches require a decision. Matching is conservative, not guaranteed across every bank format.
- Subscription predictions do not add transactions. A real captured/linked payment advances the schedule, including supported early-renewal cases. Weekly and month-end dates retain their intended anchor.
- Transfers, repayments, bills and bulk changes are checked against the complete ledger. Deleting a loan that still has repayments is rejected unless the dependent repayments are also correctly handled.

## Backup details and privacy

Backup files use AES-256-GCM with a random salt/nonce and PBKDF2-HMAC-SHA256 password derivation, then authenticated decryption before import. The password is not stored. Wrong passwords, modified ciphertext and oversized files are rejected before any database replacement. A forgotten password cannot be recovered.

Restore replaces the current dataset after confirmation. A pre-restore safety copy stays in private app storage, encrypted with the password entered for that restore; it can be exported from Backup & export afterward. Undo history is not included in exported backups. Deleted-record tombstones and capture fingerprints can remain for Undo/deduplication; they are not displayed in normal lists.

Internet access is used after you explicitly start Google connection or cloud sync. There is no analytics SDK, advertising SDK or banking-login credential collection. Financial parsing and imported text processing stay on-device. Backup/CSV files are saved where the user chooses through Android's file picker; any selected third-party storage provider has its own data handling. Android automatic backup/device transfer is excluded. Uninstalling removes the app's private records.

Notification access does not establish a full Gmail connection. The app does not read Gmail inbox history, silently fetch mail, or claim every bank notification is parseable. The email decoder handles common plain-text/HTML and base64/quoted-printable input; complex MIME messages may need relevant text pasted manually. Imported email is never rendered as executable HTML or loaded with remote images.

Common English INR/Rs/₹ formats and alphanumeric bank SMS senders are supported. Unsupported/local-language messages, numeric sender formats, missing or truncated notifications and bank-specific variations can be missed or misclassified. Review categories and balances on a real phone before relying on automatic totals.

Google Play distribution and approval for SMS access are separate from producing this APK. [Google's SMS-permission policy](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en)

## Build and checks

Requires JDK 17+, Android SDK platform 35, and an updated WebView on the test phone. Set `JAVA_HOME` and `ANDROID_HOME`, then:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
node tests/core.test.cjs
node tests/finance-v3.test.cjs
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. The runtime has no third-party application dependencies; it uses native Java/SQLite/services and local WebView assets `finance.js`, `app-v3.js` and `style.css`.

Additional native domain tests use the standard JSON-java jar only on the desktop test classpath, not in the APK. Download `org.json:json:20240303` from Maven Central, compile `Ledger`, `SmsParser`, `NoticeParser`, `EmailText`, `BackupCipher`, `MutationGuard`, `CaptureMatcher`, `ParserTest`, `UpgradeTest` and `CardSmsTest`, then run the three test mains. Use `npm ci`, `npx playwright install chromium`, then `npm test` to run all 291 desktop checks. The UI runner starts a local server on port 8774 and closes it afterward. See [Testing](TESTING.md) for reproducible commands.

Validation covers salary-cycle edges, month-end renewals, ATM/cash/card accounting, partial/linked repayments, conservative duplicate matches, backup password/tamper handling, notes, bulk corrections/Undo, persistence, responsive layouts, dark mode, reduced motion and native-bridge UI contracts.

No Android phone was connected during development. The APK build, lint, desktop native-domain checks and browser UI checks do not establish real-device SMS delivery, Gmail/bank notification contents, background alarm timing, OEM battery behaviour, the Android document picker or an installed upgrade/restore. Those device checks remain to be performed. No phone was installed or modified automatically.

## Recheck captured SMS after this update

Install 0.7.0 over your existing release build without uninstalling. Open **Settings → Tracking & reminders → Import last 30 days** to recover supported missed alerts and recheck recent captures. The import covers the last 30 days, even though development checks use the complete approved backup.

Only untouched pending captures can be automatically revised. Your reviewed notes, categories, loan links and other accounting choices remain protected. An older entry that is no longer recognized as a completed bank payment appears as **Confirm SMS entry**, outside totals. Compare the original message, then keep/edit it or remove it. Undo is available.

When a debit and credit in different bank accounts have a unique matching reference and amount, the transaction details offer **Review own transfer**. Confirm only if both accounts are yours. This replaces their expense/income counting with one own transfer, keeps the source records and account flows, and supports Undo. Matching amount alone never proves an own transfer or a card-bill funding source.

ATM withdrawals move money to Cash. Successful UPI Lite top-ups move money to the UPI Lite wallet; record spending from that wallet when there is no separate supported alert. Credit-card payment receipts reduce the card liability without becoming salary/income; review the funding account when the receipt does not identify it. Masked payees still need your note or category choice.

## Optional Google backup and web sync

Local only remains the default. Entry, capture, notes, budgets and reports work without login. Saving the Google preference alone does not connect or upload anything.

1. On Android open **Settings → Account & sync → Continue with Google**. A system browser opens. Match the six-digit code, sign in and explicitly approve that phone. Return to Paisa.
2. Check the displayed Google email. Choose **Back up this device** to upload your ledger. If the account already has records, choose **Restore cloud copy**. Replacement needs confirmation and saves an encrypted on-device safety copy first.
3. On your computer open [Paisa web](https://dir-dash-app.vercel.app/paisa/). In Account & sync, sign in with the same Google account and choose **Restore cloud copy**. Existing browser records are saved before replacement.
4. Edit payments, categories, notes, budgets, accounts or schedules on either device. **Sync now** is available in Settings. While open, checks run about every 30 seconds and after edits. Android background checks depend on its network and battery scheduling.

Offline edits stay on that device. Reconnect and keep Paisa open to merge them. Last confirmed sync appears in Settings. If the same field changes on both devices, compare the conflict details and choose device or cloud for the displayed conflicts. Independent changes still merge. Reopen a stale entry form before saving. One browser tab can edit; another tab is read-only until the first is closed and the page reloaded.

**Disconnect** stops sync on that device and keeps local records and cloud copies. Reconnect the same account and **Resume sync** to merge offline changes. Another account cannot upload the previous account's ledger; deliberately open its cached browser workspace, restore it or start empty.

**Saved cloud copies** retains up to five recent revisions. Restore needs confirmation and preserves current local records. **Delete cloud copies** removes saved versions and signs synced devices out; their local records remain.

Use **Export pre-sync safety copy** to create a password-encrypted recovery file. Browser exports are compatible with Android file restore. Browser file import is not included. Android manual file restore stops Google sync first. Clearing browser site data can erase cached edits; keep an encrypted export when needed.

Raw SMS/email bodies, OTPs and device permissions are excluded from cloud snapshots. Recorded descriptions, account labels, notes, amounts, links, budgets, rules and capture fingerprints do sync. Cloud storage uses HTTPS and authenticated private service access and is **not end-to-end encrypted**. Google login asks for identity, not Gmail inbox or banking access.

Desktop release checks establish local data behavior, API publication, Android build and lint. Actual Google account selection, phone installation, return-to-app and Android background delivery require an interactive real-phone check.

Live OAuth setup check: Google currently rejects the canonical host with “origin is not allowed”. The existing web OAuth client must authorize `https://dir-dash-app.vercel.app` in Google Cloud Console → Google Auth Platform → Clients → Authorized JavaScript origins. The client ID is supplied by the existing host configuration. This external setting is required before live Google connection works; local mode and localhost tests do not depend on it.
