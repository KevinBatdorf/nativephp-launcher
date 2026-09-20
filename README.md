# Launcher for NativePHP Mobile

Make your NativePHP app the Android home screen — on every display the device has.

Built to prove that a Laravel app can be a real launcher on a dual-screen handheld (the AYN Thor), and verified there and on a single-screen AYANEO.

## Install

```bash
composer require kevinbatdorf/nativephp-launcher
php artisan native:plugin:register kevinbatdorf/nativephp-launcher
```

Requires NativePHP Mobile v4 and Android 11+. Installing it makes your app show up in Android's home-app picker; only the user can pick it (Settings → Apps → Default apps → Home app), and `Launcher::openHomeSettings()` takes them there.

## A home screen in one screen

```php
use KevinBatdorf\Launcher\Concerns\IsHomeScreen;
use KevinBatdorf\Launcher\Events\HomeRequested;
use KevinBatdorf\Launcher\Facades\Launcher;

class HomeScreen extends NativeComponent
{
    use IsHomeScreen;                       // BACK at the root stays put instead of minimising

    public array $apps = [];

    public function mount(): void
    {
        $this->apps = Launcher::apps();     // label, package, activity, icon path
    }

    public function open(string $package, string $activity): void
    {
        Launcher::open($package, activity: $activity);
    }
}
```

```blade
<native:lazy-grid :columns="5">
    @foreach ($apps as $app)
        <native:pressable @tap="open('{{ $app['package'] }}', '{{ $app['activity'] }}')">
            <native:image :src="$app['icon']" class="w-16 h-16" />
            <native:text>{{ $app['label'] }}</native:text>
        </native:pressable>
    @endforeach
</native:lazy-grid>
```

Once the user makes your app the default home, HOME brings it forward, it never shows up in recents, and it comes straight back after being killed.

## More than one display

Pair it with [nativephp-screens](https://github.com/KevinBatdorf/nativephp-screens): your Blade puts a `<native:screen>` on each other display, `Launcher::open($package, $displayId)` opens an app on the display it was tapped on, and one handler keeps every display's home screen up:

```php
#[On(HomeRequested::class)]
public function homeRequested(int $displayId): void
{
    Screens::show($displayId);
}
```

Want the gesture bar gone too? [nativephp-fullscreen](https://github.com/KevinBatdorf/nativephp-fullscreen) and `Fullscreen::enter()` in `mount()`.

## What's in the box

- **HOME on every display**: your app on its own display, whatever you render on the others.
- **`Launcher::apps()`**: every launchable app with label, package, entry point and a PNG icon path for `<native:image>`.
- **`Launcher::open($package, $display, $activity)`**: open an app, on a chosen display, by a chosen entry point.
- **`Launcher::isDefaultHome()`**, **`Launcher::currentHome()`**, **`Launcher::openHomeSettings()`**.
- **`HomeRequested($displayId)`**: fired whenever a display wants its home screen — HOME pressed, last app backed out of or swiped away.
- **`IsHomeScreen`**: a trait for the home screen component so BACK at the root does nothing.
- A JavaScript export per function for Inertia/SPA apps: `resources/js/Launcher.js`.

## Permissions

Android asks for `QUERY_ALL_PACKAGES`, which Google Play accepts for launchers as a declared use case. iOS has no home screen to replace; every function there reports unsupported.

## The deep end

How it works — the resident home activity, why the app lives inside the home task, the task-affinity split between displays, the upstream quirks it works around, and how to test it — lives in [AGENTS.md](AGENTS.md).

<details>
<summary>AI Disclosure</summary>

This project was built by the developer using AI tooling and autonomous coding agents. Design, architecture, and product decisions are human; implementation was AI-assisted under direction, with every change reviewed and verified on real hardware before shipping.

However, AI wrote the above too, so use your own judgement.

</details>
