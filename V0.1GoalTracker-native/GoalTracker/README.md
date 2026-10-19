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
- Home-screen widget (goal name, days left, number of lists) that opens the app when tapped

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
| `App not installed` | Uninstall any older copy of this app first (a different signing key can't update it) |
| `zipalign` missing | Fine — it's optional (target SDK 29 doesn't need alignment) |
| Out of memory in d8/javac | Close other apps; `export JAVA_TOOL_OPTIONS=-Xmx512m` |

## Notes on differences from the Flutter version
- Flutter's Material widgets are replaced with native Android views styled to match (rounded cards, pills, chips, bottom-sheet task editor, native date picker dialog).
- Data saved by the Flutter app is **not** migrated (different storage location); the JSON format is identical.
- The Flutter app's iOS widget isn't included (Android only).
- Package name: `com.example.goaltracker` (min Android 8.0 / API 26).
