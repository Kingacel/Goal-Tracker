# Goal & Deadline Tracker (Flutter)

Goals, task lists, deadline countdown and progress. Everything auto-saves on
device (`shared_preferences`). Home-screen widgets (Android + iOS) show only the
goal name, days left and number of lists.

## Project layout
```
goal_tracker/
├─ lib/main.dart                       app + auto-save store
├─ pubspec.yaml
├─ android/app/src/main/
│  ├─ kotlin/com/example/goal_tracker/GoalWidgetProvider.kt
│  └─ res/{layout,drawable,xml}/       widget layout, background, config
└─ ios/
   ├─ GoalWidget/                      WidgetKit extension sources
   │  ├─ GoalWidget.swift
   │  ├─ Info.plist
   │  └─ GoalWidget.entitlements
   └─ Runner/Runner.entitlements       App Group for the main app
```

## Setup
1. `flutter create --org com.example goal_tracker`, then unzip this over it
   (overwrite `lib/main.dart` and `pubspec.yaml`).
2. `flutter pub get`

### Android widget
Paste inside `<application>` in `android/app/src/main/AndroidManifest.xml`:
```xml
<receiver android:name=".GoalWidgetProvider" android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider"
               android:resource="@xml/goal_widget_info" />
</receiver>
```
If your package isn't `com.example.goal_tracker`, update the `package` line in
`GoalWidgetProvider.kt` and move it to the matching folder.

### iOS widget (Xcode, one-time)
1. Open `ios/Runner.xcworkspace`. Set Runner's iOS Deployment Target to 14.0+.
2. File ▸ New ▸ Target ▸ **Widget Extension**, name it `GoalWidget`
   (untick "Include Live Activity" / "Configuration App Intent").
3. Delete the Swift files Xcode generates, then add `ios/GoalWidget/GoalWidget.swift`
   to the target (and use the provided `Info.plist` / `GoalWidget.entitlements`).
4. Signing & Capabilities ▸ **+ Capability ▸ App Groups** on BOTH the Runner and
   GoalWidget targets, using `group.com.example.goalTracker`
   (use `Runner.entitlements` for Runner). If you change the group id, also change
   `widgetAppGroup` in `lib/main.dart` and `appGroup` in `GoalWidget.swift`.
5. In Runner ▸ Build Phases, make sure "Embed Foundation Extensions" is placed
   above "Thin Binary" if Xcode reports a build cycle.
6. Run the app once, then add the widget from the home-screen widget gallery.
