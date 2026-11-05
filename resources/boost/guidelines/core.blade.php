## kevinbatdorf/nativephp-launcher

Makes the app eligible to be Android's home screen and gives it what a home screen needs. Android only.

```php
use KevinBatdorf\Launcher\Facades\Launcher;

Launcher::apps();                 // [['label', 'package', 'activity', 'icon' => '/abs/path.png'], …]
Launcher::open($package);         // wherever Android puts it
Launcher::open($package, 0);      // on platform display 0
Launcher::open($package, 0, $activity); // a specific entry point, as listed by apps()
Launcher::isDefaultHome();        // bool
Launcher::requestHome();          // Android's "Set as default home app?" dialog; the settings picker if already default
Launcher::openHomeSettings();     // the settings picker
```

The home screen's component should `use KevinBatdorf\Launcher\Concerns\IsHomeScreen;`: at the root of the stack BACK then does nothing instead of minimising the app; pushed screens still pop.

Installing the plugin adds a home activity for HOME (the app's display) and one for SECONDARY_HOME (every other display), so the app appears in Android's home-app picker. Nothing switches the default without the user: `requestHome()` asks them. `KevinBatdorf\Launcher\Events\DefaultHomeChanged(bool $isDefault)` fires when the answer changes; read `isDefaultHome()` in `mount()` for the starting value.

Whenever a display wants its home screen the plugin brings the app forward if it lives there, and fires an event either way; on other displays the app decides:

```php
use KevinBatdorf\Launcher\Events\HomeRequested;

#[On(HomeRequested::class)]
public function homeRequested(int $displayId): void
{
    Screens::show($displayId); // with kevinbatdorf/nativephp-screens: bring that display's screen back
}
```

The event also carries `bool $wasInFront`: true when HOME was pressed while that display already showed the app. Take it as a second parameter to treat that press like BACK.
