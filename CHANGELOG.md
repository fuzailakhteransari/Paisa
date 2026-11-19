# Changelog

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
