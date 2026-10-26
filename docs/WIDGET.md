# Prayer-times home-screen widget (Android, Jetpack Glance)

## Data source (`times.db`, real schema)
Table `pray_times(id, month, day, fasting, morning, sunrise, noon, afternoon, evening, night, province)`;
times are `HH:MM` text. 1830 rows = 5 provinces x 366 days (leap day included), provinces `ag, bn, dz, lb, mr`
(assumed Ahal, Balkan, Daşoguz, Lebap, Mary - the DB stores only the codes).
Every row is strictly ascending across the 7 columns. The widget reads this file directly: Gradle copies the
repo-root `times.db` into the APK assets at build time (no second copy in git) and it is unpacked once to app storage.

Column -> label (editable in `res/values*/strings.xml`): fasting = Sahar/Imsak, morning = Daň/Fajr, sunrise,
noon = Öýle/Dhuhr, afternoon = Ikindi/Asr, evening = Agşam/Maghrib (also used as sunset for the scene),
night = Ýassy/Isha. Strings exist in Turkmen (default), English, Russian.

## What it does
* Responsive Glance widget (`SizeMode.Responsive`) with 5 layouts: 2x1, 4x1, 2x2, 4x2, 4x4.
* Current period, next event + time, today's full schedule, location.
* Illustrated scene drawn from the day's real times: dawn/day/afternoon/sunset/dusk/night sky gradients,
  sun on an arc between sunrise and sunset, moon crossing the sky at night, stars, clouds, mosque skyline with lit windows.
* Live per-second countdown using the platform `Chronometer` (counts down with no widget updates).
* Interactions: tap location chip = next province; tap ↻ = redraw; tap elsewhere = open the app; long-press >
  reconfigure = location picker (config activity).
* Flutter integration: `namazym/widget` MethodChannel (`getProvince`, `setProvince`, `requestPinWidget`), used by `lib/widget_bridge.dart`
  and a small picker/"add widget" button in `lib/main.dart`. Setting the location in the app applies to all widgets.

## Refresh strategy / platform limits
* Widgets are RemoteViews: no continuous animation, no per-second redraws. The scene is a snapshot; only the countdown ticks (Chronometer).
* Re-render triggers: one alarm at the next prayer boundary (exact if allowed, else `setAndAllowWhileIdle` - may be late in Doze),
  WorkManager every 15 min (platform minimum) for sky drift, and TIME_SET / TIMEZONE_CHANGED / DATE_CHANGED / app update broadcasts.
  `updatePeriodMillis` is 0 (the OS minimum is 30 min).
* If the alarm is late the Chronometer keeps running past zero until the next redraw; the next 15-min work run corrects it.
* Custom fonts are unsupported by Glance; text uses the system font. `cornerRadius` only applies on Android 12+, so the scene bitmap carries its own rounded corners.
* No SCHEDULE_EXACT_ALARM permission is requested (Play policy); add it if exact boundaries matter.
* Bitmaps are capped at 480 px wide because all responsive variants share one RemoteViews memory budget.

## Test on a device
1. `flutter build apk --debug` (or `flutter run`), install on Android 8+ (minSdk 24; responsive layouts need Android 12+, older versions get the nearest size).
2. Long-press home screen > Widgets > "namazym" / Prayer times; pick a location.
3. Resize the widget through its sizes; tap the chip to cycle locations; open the app and change the location there.
4. Change the device time/timezone to check the scene and countdown re-render.

## Checks
`flutter analyze`, `flutter test`, `cd android && gradle :app:testDebugUnitTest` (JVM + Robolectric tests against the real `times.db`; scene PNGs are written to `android/app/build/scenes`).
