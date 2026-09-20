# Changelog

All notable changes to `kevinbatdorf/nativephp-launcher` are documented here.

## 0.1.0 — 2026-09-20

Initial release. Android only.

- Makes the app eligible as the device's home screen on every display:
  a resident home activity answers HOME on the app's display and
  SECONDARY_HOME on the others, and the app runs inside the home task so
  recents never lists it.
- `Launcher::apps()`, `Launcher::open($package, $display, $activity)`,
  `Launcher::isDefaultHome()`, `Launcher::currentHome()`,
  `Launcher::openHomeSettings()`.
- `HomeRequested($displayId)` fires whenever a display wants its home
  screen; with the screens plugin, `Screens::show($displayId)` answers it.
- `IsHomeScreen` trait: BACK at the root of the home screen stays put.
- Verified on a dual-screen AYN Thor (Android 13) and an AYANEO
  (Android 11).
