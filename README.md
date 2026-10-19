# Goal Tracker — Native Android

A native Android goal and deadline tracker built with **Java and the Android
framework**. This project is designed to build directly in **Termux on Android**,
without Flutter, Gradle, Android Studio, AndroidX, or third-party libraries.

> **Current project:** Native Android implementation  
> **Package:** `com.example.goaltracker`  
> **Minimum Android version:** Android 8.0 (API 26)  
> **Target SDK:** API 34

## Features

### Goals and tasks
- Create, rename, switch between, and delete goals.
- Organize tasks into task lists for each goal.
- Add, edit, delete, reorder, and complete tasks.
- Store task descriptions, start dates, due dates, and priorities.
- Set task progress in 5% increments.
- Highlight overdue tasks.
- View overall goal progress and completed-task counts.

### Deadlines and progress
- Live countdown showing days, hours, minutes, and seconds.
- Show the goal's start date, deadline, and total duration.
- Calculate remaining days and time-based progress.
- Validate dates so a deadline cannot be earlier than the start date.
- Display completed or expired goal states.

### Appearance and Android home-screen widget
- Light, dark, and system-default appearance settings.
- Android home-screen widget showing goals and deadline progress.
- Scroll through goals in the widget.
- Sort widget goals by the soonest deadline.
- Open a goal, create a goal, open settings, or refresh the widget.
- Refresh widget information when dates, time zones, device startup, or app
  updates require it.

### Local storage, export, and import
- Automatically save changes on the device.
- Choose a folder for an additional JSON data copy.
- Sync data to the selected folder or stop syncing without deleting its files.
- Export goal data to a JSON file.
- Import a JSON backup using **Replace** or **Merge**.
- Validate imported data before applying it.
- Create safety backups before imports and retain recent backups.
- Restore data from an available backup if necessary.

## Screenshots

Add screenshots of the app here when available. For example:

```markdown
![Goal Tracker home screen](docs/screenshots/home.png)
![Goal Tracker settings](docs/screenshots/settings.png)
![Goal Tracker widget](docs/screenshots/widget.png)
```

Create the `docs/screenshots/` directory and add your screenshots before enabling
these image links.

## Requirements

- An Android device running Android 8.0 (API 26) or later.
- [Termux](https://f-droid.org/packages/com.termux/) installed from F-Droid.
- An internet connection for the initial tool setup.
- Enough free storage for the Android build tools and generated files.

The build scripts use these tools:

- OpenJDK 17
- `aapt2`
- `d8`
- `apksigner`
- `zip` and `unzip`
- Android API platform stubs (`android.jar`)

## Build on Android using Termux

### 1. Install Termux

Install Termux from [F-Droid](https://f-droid.org/packages/com.termux/).
Other distributions may have outdated packages.

### 2. Get the source code

Clone this repository, replacing `YOUR_USERNAME` with your GitHub username and
`YOUR_REPOSITORY` with the repository name:

```bash
git clone https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
cd YOUR_REPOSITORY
```

If the source is inside a subdirectory, enter the directory containing
`setup.sh` and `build.sh` before continuing.

To allow Termux to access shared storage when needed:

```bash
termux-setup-storage
```

### 3. Install the build tools

Run this once:

```bash
bash setup.sh
```

The script updates Termux packages, installs the required tools, and downloads
an Android platform `android.jar` if one is not already available.

If the setup script cannot download `android.jar`, copy it from an installed
Android SDK to:

```text
$HOME/android-sdk/android.jar
```

The file must come from Android API 26 or later.

### 4. Build the APK

Run:

```bash
bash build.sh
```

If the build succeeds, the generated APK will be:

```text
GoalTracker.apk
```

When Termux shared-storage access is configured, the script also attempts to
copy the APK to the device's Downloads folder.

### 5. Install the app

You can open the APK using Android's package installer:

```bash
termux-open GoalTracker.apk
```

Alternatively, open `GoalTracker.apk` from the Downloads folder using your
file manager. Android may ask you to allow installation from this source.

To build and then attempt installation from the script:

```bash
bash build.sh --install
```

### 6. Add the home-screen widget

1. Return to the Android home screen.
2. Long-press an empty area.
3. Open **Widgets**.
4. Find **Goal Tracker**.
5. Drag the widget onto the home screen.

### Rebuild after making changes

From the directory containing `build.sh`, run:

```bash
bash build.sh
```

## Project structure

```text
.
├── AndroidManifest.xml
├── setup.sh
├── build.sh
├── README.md
├── res/
│   ├── drawable/
│   ├── layout/
│   ├── values/
│   ├── values-night/
│   ├── mipmap-anydpi-v26/
│   └── xml/
└── src/com/example/goaltracker/
    ├── MainActivity.java
    ├── SettingsActivity.java
    ├── WidgetMenuActivity.java
    ├── GoalWidgetProvider.java
    ├── GoalWidgetService.java
    ├── Store.java
    ├── LocalFolder.java
    ├── Util.java
    ├── Pal.java
    ├── FlowLayout.java
    └── BarView.java
```

| File | Purpose |
|---|---|
| `MainActivity.java` | Main app interface and goal/task interactions |
| `Store.java` | Goal data models, local persistence, and widget updates |
| `SettingsActivity.java` | Appearance and settings interface |
| `LocalFolder.java` | Folder-based local data storage |
| `GoalWidgetProvider.java` | Android home-screen widget provider and refresh events |
| `GoalWidgetService.java` | Supplies goal rows to the scrolling widget |
| `WidgetMenuActivity.java` | Menu opened from the widget |
| `Util.java` | Shared utility and date-related functions |
| `Pal.java` | Theme palette and appearance helpers |
| `FlowLayout.java` | Custom flow layout |
| `BarView.java` | Custom progress-bar view |
| `setup.sh` | Installs the Termux build environment |
| `build.sh` | Compiles, packages, and signs the APK |

## Data and backups

Goal data is saved locally on the device. The optional storage-folder feature
creates a `GoalTracker` directory containing `goal-tracker-data.json` and
updates that copy when data changes.

Use the app's export feature to create a portable JSON backup before changing
devices or reinstalling the app. To transfer data, export it on the old device,
move the file to the new device, and import it on the new device.

**Important:** The optional folder copy and exported JSON files may contain
personal information about your goals and tasks. Store and share them carefully.

## Troubleshooting

| Problem | Suggested action |
|---|---|
| A build tool such as `d8` or `aapt2` is missing | Run `bash setup.sh` again, or install the missing package with Termux's `pkg` command. |
| `android.jar` cannot be downloaded | Copy `android.jar` from an Android SDK platform (API 26 or later) to `$HOME/android-sdk/android.jar`. |
| Android says the app is not installed | Try opening the APK from Downloads, check available storage, and remove an incompatible older installation if appropriate. |
| `zipalign` is unavailable | The build script treats it as optional. |
| The build runs out of memory | Close other apps and retry. You can also try `export JAVA_TOOL_OPTIONS=-Xmx512m`. |
| The widget does not appear | Add it again from the system Widgets picker and check that the app installed successfully. |

## Technical notes

- The application uses native Android views and Java.
- The build pipeline is `aapt2 → javac → d8 → zip → apksigner`.
- Gradle, Flutter, Android Studio, AndroidX, and third-party libraries are not
  required by the provided build scripts.
- The manifest declares package `com.example.goaltracker`.
- The current manifest specifies minimum SDK 26 and target SDK 34.
- Data is stored locally; the project does not require an online account.

## Signing and release builds

The build script creates a local signing key when one does not already exist.
That key is intended for local builds and testing.

**For a public release, protect your signing key and credentials.** Do not commit
keystore files, passwords, private keys, personal backups, generated build
artifacts, or sensitive data to a public repository. Keep a secure backup of the
release signing key; future app updates must be signed with the same key.

Before publishing, review the manifest, app version information, privacy
requirements, and release-signing configuration.

## Contributing

1. Fork the repository.
2. Create a branch for your change.
3. Make and test your changes on an Android device.
4. Build the APK with `bash build.sh`.
5. Open a pull request describing the change and how it was tested.

## License

No license has been specified in this repository yet. Unless a license is added,
reuse, redistribution, and modification are subject to applicable copyright law.
Add a `LICENSE` file before inviting public contributions or permitting reuse.

---

If you find a bug, please open a GitHub issue and include the Android version,
steps to reproduce the problem, and any relevant build output. Do not include
private goal data, exported backups, passwords, or signing keys in an issue.
