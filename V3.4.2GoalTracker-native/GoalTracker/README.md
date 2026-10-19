# Goal & Deadline Tracker — native Android (Java, no Flutter)

A native rewrite of the Flutter "goal_tracker" app. Plain Android framework APIs only:
**no Flutter, no AndroidX, no Gradle, no third-party libraries.** It builds into an APK
entirely inside Termux with `aapt2 → javac → d8 → apksigner`.

## Features (same as the Flutter app)
- Multiple goals as scrolling tabs, `+ New goal`, editable goal title, delete goal
- Deadline picker with live countdown (days / h / m / s ticking every second), total days, deadline date
- Overall progress bar and "N% · X/Y tasks done"
- Task lists per goal: rename inline, add / delete lists
- Tasks: name, description, start & due date, priority (High/Medium/Low pill), status, progress slider (5% steps),
  checkbox toggle, move up/down, edit, delete, overdue highlighting
- Everything auto-saves on every change (SharedPreferences, same JSON structure/key as the original)
- First-launch sample goal "Build an Android App"
- Light/dark theme follows the system, same colour palette
- Home-screen widget: a vertically scrollable list of all goals (swipe up/down; soonest deadline first), each with days remaining / total days (start → deadline), and a live % ((total − remaining) ÷ total × 100); refreshes when the date changes; tap a goal to open it, **+** to add a new goal, **•••** for the menu

## Appearance, Settings and widget menu
- **Settings** (gear icon at the right of the goal tabs) → **Appearance**: Light (white background, dark text), Dark (black background, light text) or System default (the default, so existing users see no change). The choice is saved in its own preferences file (`goal_tracker_settings`), so goal data is never touched, and it survives restarts.
- Changing the appearance re-themes every screen, dialog and date picker, and refreshes the widget immediately. In System mode the widget follows day/night through colour resources; Light/Dark force fixed colours.
- Widget **•••** menu (replaces "View all goals"): Open Goal Tracker, Add new goal, Settings, Refresh widget. Android widgets cannot show real popups, so the button opens a small transparent activity (`WidgetMenuActivity`) drawn as a dropdown next to the button.

## Local storage folder, Export and Import (Settings → Data & storage)
- **Storage folder:** pick any folder with Android's folder picker (no storage permission needed). The app creates a `GoalTracker` folder there containing `goal-tracker-data.json` and rewrites it after every add / edit / delete (on a background thread, newest data wins) and on app start. If the folder already holds Goal Tracker data you are asked whether to use it or overwrite it, so a backup is never overwritten silently. **Sync now** and **Stop syncing** are in the same card; stopping keeps the files.
- **Export data:** saves the full data as one JSON file wherever you choose, then reads it back to verify it.
- **Import data:** accepts exports, the folder's data file and older backups (same JSON format). The file is validated first; then choose **Replace** or **Merge** (adds only goals whose id is not already present, so nothing is duplicated). A safety copy of the current data is saved before every import (last 5 kept in the app's `backup` folder).
- Data stays inside the app as before (same storage key and automatic backups); the folder and export are extra copies.

## Dates, backup and widget (v2.8)
- Deadline validation: the deadline must be on or after the start date. The date picker disables earlier days, and an invalid pick shows "Invalid deadline. The deadline cannot be earlier than the start date." If a new start date makes the current deadline invalid, you must pick a new deadline before the change is saved.
- The start and due dates are not shown in the widget (the start date is still stored and used for the calculations).
- Start date is stored once and only changes when you pick a new one (**Start** button on the goal card). Total duration is stored with the goal and recalculated only when the start date or deadline is changed.
- Remaining days and % are computed from today's calendar date (time-zone independent): `% = (total - remaining) / total x 100` (0% on the start date, 100% at the deadline and after, clamped 0-100, no division by zero), expired goals show 0 days left and "Expired", finished goals show "Completed".
- Every save writes an atomic backup (`files/backup/latest.json` and `last-nonempty.json`). If the main data is missing/corrupt it is restored automatically; **Restore from backup** at the bottom of the app does it manually.
- Updates keep your data: same storage key, schema version `v` with a safety copy (`pre-migration-v1.json`) taken before migrating old data.
- The widget reads the same store the app writes, refreshes on every change, at midnight, and on date/time-zone/boot/update events.

## Project layout
```
GoalTracker/
├─ AndroidManifest.xml
├─ setup.sh            one-time Termux tool setup
├─ build.sh            builds + signs GoalTracker.apk
├─ src/com/example/goaltracker/
│  ├─ MainActivity.java        all screens (tabs, hero card, lists, task sheet)
│  ├─ Store.java               models + auto-saving store + widget sync
│  ├─ GoalWidgetProvider.java  home-screen widget
│  ├─ SettingsActivity.java    Settings → Appearance
│  ├─ WidgetMenuActivity.java  popup menu behind the widget's ••• button
│  ├─ GoalWidgetService.java   feeds the widget's scrolling goal list
│  ├─ Util.java, Pal.java      date helpers, palette
│  └─ FlowLayout.java, BarView.java   small custom views
└─ res/                drawables, widget layout, themes (light/dark), adaptive icon
```

## Build in Termux — step by step

1. **Install Termux from F-Droid** (https://f-droid.org/packages/com.termux/). The Play Store version is outdated and will not work.
2. Open Termux and allow file access (optional, for copying the project/APK):
   ```
   termux-setup-storage
   ```
3. **Get the project into Termux.** Put `GoalTracker-native.zip` in your phone's Downloads, then:
   ```
   cd ~
   unzip ~/storage/downloads/GoalTracker-native.zip
   cd GoalTracker
   ```
4. **One-time tool setup** (installs JDK 17, aapt2, d8, apksigner, zip and downloads `android.jar`):
   ```
   bash setup.sh
   ```
   Needs internet and ~300 MB of storage.
5. **Build the APK:**
   ```
   bash build.sh
   ```
   Output: `~/GoalTracker/GoalTracker.apk`
6. **Install it:**
   ```
   bash build.sh --install      # or:  termux-open GoalTracker.apk
   ```
   Android will ask you to allow Termux to "install unknown apps" the first time — enable it, then tap Install.
   (Alternative: `cp GoalTracker.apk ~/storage/downloads/` and open it from your file manager.)
7. **Widget:** long-press the home screen → Widgets → "Goal Tracker" and drag it out.

Rebuilding after edits is just `bash build.sh` again.

## Troubleshooting
| Problem | Fix |
|---|---|
| `Missing tool 'd8'` / `aapt2` / etc. | `bash setup.sh` again, or `pkg install d8 aapt2 apksigner openjdk-17 zip` |
| android.jar download fails | Copy any `platforms/android-XX/android.jar` (XX ≥ 26) from an Android SDK to `~/android-sdk/android.jar` |
| `App not installed` | Install from **Downloads** (build.sh copies the APK there), not from Termux's own folder or a path with `[brackets]`. Uninstall any older copy first. Turn off Samsung Auto Blocker. To see the real error: pair wireless debugging, `adb connect localhost:PORT`, then `bash build.sh --install` prints it |
| `zipalign` missing | Fine — it's optional (target SDK 29 doesn't need alignment) |
| Out of memory in d8/javac | Close other apps; `export JAVA_TOOL_OPTIONS=-Xmx512m` |

## Notes on differences from the Flutter version
- Flutter's Material widgets are replaced with native Android views styled to match (rounded cards, pills, chips, bottom-sheet task editor, native date picker dialog).
- Data saved by the Flutter app is **not** migrated (different storage location); the JSON format is identical.
- The Flutter app's iOS widget isn't included (Android only).
- Package name: `com.example.goaltracker` (min Android 8.0 / API 26, target API 34).
