# Changelog

## 0.7.1 — 2026-11-19

- Moved the personal web companion and API into a dedicated Paisa Vercel project at paisa-fuzail.vercel.app, deployed directly from the Paisa repository.
- Removed personal routes, files and environment variables from the company application and retired the two accidental combined deployments.
- Use a separate personal Google OAuth client and a fresh independent session secret. Updated Android pairing/web links, root PWA scope and Google-compatible referrer policy. Existing phone financial records remain local and unchanged.
- Rechecked actual-data sync and backup interoperability; configured the separate personal Google client.

## 0.7.0 — 2026-11-19

- Completed optional Google account connection, explicit first backup/restore and the desktop web companion. Local mode remains the default.
- Added hash-checked cloud copies, per-account server authorization, revision compare-and-swap, five recent versions and separate cloud deletion.
- Merge independent phone/browser changes, preserve capture identities and Undo, queue offline edits, block conflicting fields for review and guard stale entry forms.
- Keep tokens outside financial exports; use Android Keystore for native sync state and server-only browser cookies. Save a local safety copy before cloud replacement.
- Added tab locking, account-cookie checks, phone pairing proof, atomic upload race checks and actual-data native/web interoperability and browser tests.
- Kept the clean English interface and four destinations. Interactive Google login, installation and background behavior still require real-device acceptance.

## 0.6.0 — 2026-11-19

- Added Settings → Account & sync, with Local only as the default and an optional Google backup & sync preference.
- Save and restore the preference independently of financial records. Keep active mode, account connection and Google-backup status explicitly local/not connected.
- Google sign-in is unavailable and disabled in this first delivery. No cloud SDK, network permission, account session or upload path has been added.
- Kept manual encrypted backup/export and the existing Home/navigation layout. Documented the remaining two stages; they require a further user instruction.
- Added actual-ledger checks for preference persistence, record preservation, unavailable sign-in, zero external requests, small-screen layout and encrypted backup compatibility.

## 0.5.1 — 2026-11-19

- Corrected sender/receipt timestamps on untouched pending legacy imports during rescan. This keeps the latest bank-balance snapshot from counting its own payment again. Reviewed user entries remain protected.
- Added direct real-data comparisons between upgraded and fresh-import balances for all observed snapshot accounts.

## 0.5.0 — 2026-11-19

- Audited the approved local corpus of 14,072 SMS against independently reviewed expectations. Added private, integrity-checked real-data regressions without including messages or identifiers in the repository or CI.
- Recovered 43 previously missed bank events: completed UPI mandates/AutoPay, deposits, legacy transfers, deductions, reversals and UPI Lite top-ups. Tightened sender and event checks to reject 22 non-bank/loyalty/offer/receipt false positives in the old parser.
- Classified 21 observed cash withdrawals as bank-to-cash transfers, kept UPI Lite funding separate from spending, recognized additional card-payment receipts and extracted missing bank/UPI references.
- Fixed incoming payments whose payee address contains refund-related text. Bank balances now support the observed abbreviated balance layouts.
- Unified inbox/broadcast timestamps and retained exact 0.4 fingerprint compatibility. Rescans repair only untouched pending captures, preserve user notes and linked accounting choices, and keep questionable old captures for explicit review outside counted totals.
- Added contextual own-transfer review for unique debit/credit pairs with the same bank reference and amount. Ownership requires confirmation; linking preserves both source records, per-account timing and Undo.
- Added actual-data replay, upgrade, encrypted restore, note/tag search, reporting, budgets, cash/account history, payment linking and bulk/Undo checks. Existing public checks remain in CI; private data checks explicitly skip when the corpus is unavailable.

## 0.4.0 — 2026-11-19

- Added automatic note tags and optional hashtags, with existing notes immediately searchable without a migration or re-entry.
- Added search totals for matching spending, money lent/borrowed and repayments, kept separate from one another. Refunds reduce spending; unresolved duplicates remain excluded from counted totals.
- Grouped Fuel, Petrol and Peetrol searches, with partial-name and multiword matching across notes, people, descriptions, categories and accounts.
- Added browseable note tags and kept the search input stable while typing. The keyboard Search action reveals results.
- Added an inclusive From/To date range shared by Home, Activity, search and account-history reports, alongside Today, Week, Month and All.
- Exposed Other in the quick payment-review category choices as well as the full entry form.
- Added 39 accounting/search/date-boundary checks and 36 browser checks for the new flows. The suite now includes 291 checks.

## 0.3.2 — 2026-11-19

- Fixed missed HDFC UPI credit-card purchases using the `Txn Rs… On HDFC Bank Card… by UPI…` SMS layout, which does not contain a normal debit/spend verb.
- Recognize the matching `BLOCK CC` card marker, retain the purchase as an expense on its credit-card account, and extract the UPI reference for duplicate prevention.
- Display masked payees for review without treating partial VPAs as unique remembered merchants.
- Added 24 regression checks with fictional card/payee/reference data, including Rs.450/Rs.500 purchases, re-import identity, failed/OTP alerts and the debit-card variant.
- To recover missed recent purchases after updating, open Settings → Tracking & reminders → Import last 30 days. Existing supported SMS fingerprints are retained.

## 0.3.1 — 2026-11-18

- Converted built-in app screens, transaction types, empty states, hints, validation errors, permission disclosures and payment-review notifications to English.
- Renamed the Loans tab and refreshed demo descriptions without changing user-entered records.
- Added a professional repository overview, sample screenshots, user guide, architecture, privacy notes and contribution templates.
- Added portable desktop test commands, pinned test dependencies and GitHub Actions for Android build/lint and browser flows.
- Increased Android version code to 4; the application ID and local database schema remain unchanged.

## 0.3.0 — 2026-11-18

- Added selected-app SMS, Gmail-alert and bank-notification tracking, email share/import and conservative duplicate review.
- Added accounts, a cash wallet, account history, monthly/category budgets and custom month boundaries.
- Added subscriptions, bill reminders, partial payments and repayment linking.
- Added atomic bulk correction, conflict-aware Undo, password-encrypted backup/restore and a pre-restore safety copy.
- Kept four primary destinations and moved detailed controls into Settings.
