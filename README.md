# Sketch

A one-page-a-day sketchbook for the Boox Go6 Gen2 (Android 14).

You get **one page per day**. It opens on a blank page with a fixed 0.2 mm pen, and what you draw on it stays there. There is no eraser, no clear and no undo: a day's page can only be added to, never taken back.

## How it works

- **One page per day.** Today's page is the only one you can draw on.
- **Notes renew at 00:00 local time.** When the date changes, a new blank page starts. If you are looking at the page of the day that just ended when midnight passes, the app moves you to the new one.
- **Old days are read-only.** You can go back to any day in the past to revisit your notes, but you can't edit them.
- **Empty days give you a koan.** Going back to a day where you did not draw anything shows a koan instead of a blank page. The same day always shows the same koan.
- **Nothing is erased.** There is no way to erase or clear a page, in the app or by gesture.

## Using it

| Do this | To get this |
| --- | --- |
| Draw with the pen | 0.2 mm black line |
| Tap the round button (top right), or tap with three fingers | Switch between pen (small black dot) and highlighter (larger colored circle) |
| Touch the screen with the pen's side button held | Same switch, from the pen |
| Hold one finger on the screen | Open the menu |

The highlighter is a 4 mm #ffcc00 stroke (a light gray on the black-and-white panel) that keeps your ink readable underneath. It goes back to the pen by itself 0.8 s after a highlight stroke, so you can highlight several things in quick succession.

The menu has a **calendar** to jump to any day up to today (past days open read-only), and:

- Copy to clipboard (the page as a PNG)
- Save as PDF (to Downloads)
- Share… (Android share sheet, for example LocalSend)
- Print
- Back to today (when you are on a past day)

The date of the page you are on is shown next to the round button.

## Building

Open the project in Android Studio, or from the command line:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A GitHub Action (`.github/workflows/build.yml`) builds the debug APK on every push to `main` and on pull requests, and attaches it to the run as an artifact.

### Releases

Pushing a tag such as `v1.0` runs `.github/workflows/release.yml`, which builds a signed release APK and publishes it as a GitHub release:

```bash
git tag v1.0 && git push origin v1.0
```

The signing key is stored in the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. A copy of the key and its password is in `~/.config/boox-sketch/` on the machine that created it; keep a backup, because an app can only be updated by APKs signed with the same key. A local release build uses the same key when `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` are set in the environment, and falls back to the debug key otherwise.

The Onyx pen libraries are downloaded from Onyx's Maven repository (`http://repo.boox.com/repository/maven-public/`), so the build needs network access to it. The reference implementation for the Onyx SDK usage is [booxday](https://github.com/fortolano/booxday).

## Where things are

Pages are stored as one small file per day (`yyyy-MM-dd.bin`) in the app's private storage. Code is in `app/src/main/java/dev/sketch`:

- `SketchView.kt`: the canvas, the Onyx pen input, the daily pages and saving
- `MainActivity.kt`: the menu, the calendar and the midnight rollover
- `Exporter.kt`: clipboard, PDF, share and print
- `Koans.kt`: the koans for empty days
