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
| `npm run test:core` | Amount validation, cash/card/loan accounting, custom cycles, budgets and renewals | 58 |
| `npm run test:native` | Native parser/domain rules, duplicate matching, encrypted backup and Undo guard | 91 |
| `npm run test:ui` | Notes, captured-payment linking, budgets, Undo, source configuration, persistence and responsive flows | 67 |
| Total | Desktop automated checks | 216 |

The native runner downloads a pinned JSON-java test jar from Maven Central, checks its SHA-256 and caches it under `.cache/`. It is never bundled in the APK. Playwright is a desktop-only development dependency.

The UI runner starts and closes a local-only server on port 8774 automatically. Fixtures use a fixed November 2026 date and Asia/Kolkata timezone. Screenshots are written to ignored `test-results/`. To use an existing local Chrome installation, set `PAISA_BROWSER_CHANNEL=chrome`; the default is Playwright Chromium.

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

The 0.3.2 local verification includes a successful APK build and zero lint errors. The two acknowledged lint warnings concern the target API level and JavaScript enabled for the bundled local UI.
