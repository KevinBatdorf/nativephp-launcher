# AGENTS.md

Instructions and full reference for agents working in this repo or building
apps against it. The README is the short human pitch; this file is the docs.

## What this repo is

A NativePHP Mobile (v4 / EDGE) plugin that lets a Laravel mobile app be
Android's home screen: the HOME and SECONDARY_HOME activities Android looks
for, the facts a home screen needs (installed apps, who is the default
home), the verbs (open an app on a display, open the home-app settings),
and one event (`HomeRequested`). What the home screen looks like is the
app's Blade. Android only; iOS has no replaceable home screen.

Companions, both optional: [nativephp-screens](https://github.com/KevinBatdorf/nativephp-screens)
renders on the other displays of a multi-display device;
[nativephp-fullscreen](https://github.com/KevinBatdorf/nativephp-fullscreen)
hides the gesture bar. The plugin has no code dependency on either.

## Repo map

| Path | What lives there |
|---|---|
| `nativephp.json` | Bridge functions, the `HomeRequested` event, the `QUERY_ALL_PACKAGES` permission, `init_function`, `min_version` 30 |
| `resources/android/AndroidManifest.xml` | The two home activities. Declared in XML, not in `nativephp.json`, because the secondary one needs `android:taskAffinity` and the JSON schema has no such key |
| `resources/android/src/LauncherHomeActivity.kt` | The resident home activity (HOME) and everything that keeps the app alive and in front |
| `resources/android/src/LauncherSecondaryHomeActivity.kt` | Same class, own task affinity, answers SECONDARY_HOME |
| `resources/android/src/LauncherApp.kt` | Tracks the live app Activity and which displays have a resident home; `registerLauncher(context)` is the plugin init |
| `resources/android/src/LauncherEvents.kt` | Dispatches `HomeRequested` into PHP |
| `resources/android/src/LauncherFunctions.kt` | `Apps`, `Open`, `IsDefaultHome`, `OpenHomeSettings` bridge functions |
| `resources/ios/Sources/` | Stubs; every function answers unsupported |
| `resources/js/Launcher.js` | One export per bridge function for SPA apps |
| `src/` | PHP: `Launcher`, facade, `Events\HomeRequested`, `Concerns\IsHomeScreen`, the copy-assets hook command |
| `tests/` | Pest; asserts the manifest, the XML activities and the Kotlin invariants below |

## Build and test

```bash
./vendor/bin/pest          # PHP; also parses the Kotlin and the XML manifest
```

There is no native build: the Kotlin is compiled into the consuming app by
NativePHP. To try a change, build an app that requires this plugin through a
composer path repository and run `php artisan native:run android <serial>`.
Kotlin errors do not fail that command — check
`nativephp/android-build.log` for `^e: ` lines and that the app's
`lastUpdateTime` in `dumpsys package` moved.

Test-bench commands (never something an end user is asked to run):

```bash
adb shell cmd package set-home-activity <appId>/com.kevinbatdorf.plugins.launcher.LauncherHomeActivity
adb shell cmd package set-home-activity com.android.launcher3/.uioverrides.QuickstepLauncher   # stock again
adb shell dumpsys activity recents | grep <appId>     # the app must only appear inside a type=home task
adb shell am stack list                               # taskIds with topActivity, one per line
adb shell am stack remove <taskId>                    # ≈ a recents swipe
```

Verified on an AYN Thor (Android 13, two displays: 0 top 1920x1080, 4 bottom
1240x1080) and an AYANEO GT78 (Android 11, one display). On the Thor,
`screencap -d <logical id>` writes an empty file; use the physical ids from
`dumpsys SurfaceFlinger --display-id`.

## PHP API

```php
use KevinBatdorf\Launcher\Facades\Launcher;

Launcher::apps();                                   // [['label', 'package', 'activity', 'icon'], …] sorted by label, this app excluded
Launcher::open($package, ?$displayId, ?$activity);  // display = platform display id; activity = one of a package's entry points
Launcher::isDefaultHome();                          // bool
Launcher::currentHome();                            // package of the current default home, '' if unknown
Launcher::openHomeSettings();                       // the only way to become the default: the user picks it
```

`apps()` writes each icon once to `filesDir/launcher-icons/<package>.png` and
returns absolute paths, which `<native:image :src>` accepts. The first call
on a device with many apps takes a moment; cache the result in the component.

`open()` without `$activity` uses the package's launch intent; packages with
several launcher activities (AYANEO's tools, for one) all open the same one
that way, so pass the `activity` from `apps()`.

### Event

`KevinBatdorf\Launcher\Events\HomeRequested(int $displayId)` fires whenever a
display wants its home screen: HOME pressed there, the last app on it backed
out of, its task removed. On the app's own display the plugin already brings
the app forward; on any other display nothing happens unless the app acts.
With the screens plugin the whole answer is:

```php
#[On(HomeRequested::class)]
public function homeRequested(int $displayId): void
{
    Screens::show($displayId);   // on the app's own display this brings the app forward too
}
```

### Trait

`KevinBatdorf\Launcher\Concerns\IsHomeScreen` overrides `onBackPressed()`:
at the root of the navigation stack it does nothing, where NativePHP would
otherwise minimise the app (on Android the BACK gesture is a swipe from the
screen edge, so this is what makes the launcher un-swipeable); pushed screens
still pop. Use it on the component that is the home screen.

## How the Android side works

Read this before touching `LauncherHomeActivity`. Each rule below was found
the hard way on hardware.

- **The home activity is resident and never finishes.** Android hands a
  display back to whichever home activity is still resident in that
  display's root home task; a trampoline that finished handed the bottom
  panel back to the stock launcher. A resident home also makes this process
  the "home process", which recents swipes never kill. The one `finish()` is
  for the duplicate instance a HOME press stacks above the app (see next).
- **MainActivity lives inside the main display's home task.** Recents never
  lists home-type tasks, which is the only way an Android task cannot be
  swiped away; the flag `FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS` still shows the
  current task. Same package means same default task affinity, and a new
  ActivityRecord's type is undefined, so a plain `NEW_TASK` start from the
  home activity joins its task. A HOME press while the app is on top lands a
  second home instance above it; that one brings the app forward, fires the
  event and finishes itself (`if (!isTaskRoot) finish()`).
- **App tasks outside the home task are removed.** `native:run`, adb and
  launcher icons start the app in a standard task, which is a swipeable
  card. On every home resume such tasks are finished and removed and the app
  rebooted into the home task. `ActivityManager.getAppTasks()` never lists
  home tasks, so anything it lists is one of these.
- **Never start a second app instance while one is dying.** Two
  MainActivity instances alive in one process break the PHP runtime. After
  a finish or task removal the home waits (150 ms retries) until the old
  instance is destroyed, then boots.
- **The secondary home has its own task affinity.** `RecentTasks` keeps one
  task per affinity and activity type: with the default affinity on both,
  creating the home on one display hid the other display's home task, and
  `removeUnreachableHiddenTasks` removed it the next time a home resumed
  idle while it was covered. With the app inside the main home task that
  would have killed the app whenever HOME was pressed on the bottom panel
  while another app covered the top. Hence the XML manifest and
  `android:taskAffinity` on `LauncherSecondaryHomeActivity`.
- **Single-task launch modes are refused as a secondary home.** Android's
  `canStartHomeOnDisplayArea` rejects `singleTask` and `singleInstance` on
  secondary displays and falls back to the stock secondary launcher. Both
  activities are `singleTop`.
- **Booting always happens on the main display**, through the main
  display's home. A non-resizeable MainActivity booted on another display is
  letterboxed to the main display's shape. A secondary home with no app
  alive starts the HOME intent on the main display (only a home activity may
  start another activity as home), whose home boots the app.
- **The main display's home creates the other displays' homes right away**
  (`ensureSiblingHomes`, SECONDARY_HOME intent per display), so whatever
  closes on those displays lands on our home, which fires `HomeRequested`,
  instead of on a lingering stock one.
- **Track the live MainActivity yourself.** Upstream's static
  `MainActivity.instance` is nulled unconditionally in `onDestroy`; after an
  in-process recreation the replaced instance dies after its successor
  started, leaving the holder null while the app runs. `LauncherApp.live()`
  is fed by the plugin `init_function`, which upstream calls on every
  MainActivity creation.
- **`Activity.display` is API 30**, hence `min_version` 30.

## Invariants for contributors

- Nothing Thor-specific: display ids, panel names and layouts belong to the
  app. The plugin only knows "the default display" and "the others".
- No dependency on the screens or fullscreen plugins in Kotlin or composer;
  the glue is one line of PHP in the app.
- The manifest XML and the Kotlin behaviours above are covered by Pest
  string assertions; keep the literals they look for when refactoring.
- Never commit anything under `.claude/`, `CLAUDE.md` or plan files.
