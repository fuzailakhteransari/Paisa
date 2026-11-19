# Build and testing

## Requirements

- JDK 17 or newer, with `JAVA_HOME` configured.
- Android SDK platform 35 and build-tools 35.0.0, with `ANDROID_HOME` configured.
- Node.js 22 or newer and npm for desktop checks.
- Android 8+ with an updated Android System WebView for device testing.

## Android build

```sh
./gradlew --no-daemon :app:assembleDebug :app:lintDebug
```

On Windows, use `gradlew.bat`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`; lint reports are under `app/build/reports/`.

Release downloads are development signed. A fresh local or CI build normally uses a different debug key and cannot update an already installed release-signed copy. Keep the original signing key private; it is not in this repository. Do not uninstall an existing app just to test another key without exporting a full backup first.

## Desktop checks

```sh
npm ci
npx playwright install chromium
npm test
```

Individual suites:

| Command | Coverage | Checks |
|---|---|---:|
| `npm run test:core` | Amount validation, cash/card/loan accounting, custom cycles, budgets, renewals, note search and inclusive date boundaries | 97 |
| `npm run test:native` | Native parser/domain rules, duplicate matching, encrypted backup and Undo guard | 91 |
| `npm run test:ui` | Notes, captured-payment linking, budgets, Undo, source configuration, search totals, tags, date ranges, persistence and responsive flows | 103 |
| Total | Desktop automated checks | 291 |

The native runner downloads a pinned JSON-java test jar from Maven Central, checks its SHA-256 and caches it under `.cache/`. It is never bundled in the APK. Playwright is a desktop-only development dependency.

The UI runner starts and closes a local-only server on port 8774 automatically. Fixtures use a fixed November 2026 date and Asia/Kolkata timezone. Screenshots are written to ignored `test-results/`. The new search suites cover person/loan separation, Fuel/Petrol matching, original-note preservation, inclusive end dates, invalid ranges and daylight-saving boundaries. To use an existing local Chrome installation, set `PAISA_BROWSER_CHANNEL=chrome`; the default is Playwright Chromium.

## Approved private SMS regressions

The user's approved XML snapshot is kept outside the checkout in the sibling `../private-paisa-sms/` directory. `PAISA_SMS_DATA_DIR` can point to a different private directory. `npm test` runs this suite when available; a local release must explicitly require it:

```sh
npm run test:real -- --required
```

The directory has an immutable source XML, received/sent message JSONL, independently reviewed expected JSONL, a frozen 0.4 baseline and an integrity manifest. Source, parsed input, expectations and baseline are SHA-256 verified before a run. Never derive expected labels from the current parser. Never put raw messages, OTPs, account identifiers, actual-data screenshots or transaction exports in Git, release archives or CI logs.

The 0.5.0 approved corpus contains 14,072 SMS: 8,638 received and 5,434 sent. It yields 1,583 bank transaction alerts and one statement notice, with zero comparison mismatches and zero parser exceptions. Expectations include amount, direction/type, account, available references and 74 explicit bank-balance snapshots. Coverage is for the reviewed layouts; this is not a claim that every future bank format is supported.

`RealSmsRunner` scans every record, including negative cases. `RealSmsScenarios` runs the shared Java parser, capture compatibility policy, reference matcher, transfer-link policy, ledger validation, Undo guard and encrypted backup on actual records. It checks full replay, 0.4 re-import compatibility, reviewed-user protection and conservative repair. Its in-memory import model does not replace Android SQLite integration testing. `test-real-sms-ui.cjs` uses the imported actual state in an isolated browser to exercise reports, search/tags, dates, budgets, account history, links and bulk/Undo. Sandbox purpose labels establish test intent only; they do not assert the user's actual loan, subscription or bill purpose. All private snapshots and diagnostics remain outside the checkout.

The same approved corpus is required for future SMS/capture/accounting edits. Public fictional regressions remain useful for boundary cases. CI deliberately has no private corpus and reports the real-data suite as skipped, rather than claiming this coverage.

## CI

GitHub Actions runs two jobs on pushes to `main` and pull requests: Android build/lint plus domain checks, and browser flow/layout checks. Actions are pinned to verified commit SHAs and the workflow has read-only repository permissions. CI APKs are disposable development artifacts, not the downloadable update build.

## Real-device checklist

These checks require an actual phone and have not been established by desktop tests:

- Update an existing signed build while preserving records and notes.
- Grant/deny SMS and notification permissions; import actual supported messages without duplicates.
- Capture Gmail/bank notification text, including truncated/grouped alerts and background delivery.
- Review and remember a merchant category after a real payment.
- Verify reminder delivery after reboot and under the device vendor's battery settings.
- Save, restore and export a safety copy through Android's document picker.
- Compare account totals with actual records and review unsupported bank formats.

The 0.5.0 local verification includes a successful APK build and zero lint errors. The two acknowledged lint warnings concern the target API level and JavaScript enabled for the bundled local UI.
