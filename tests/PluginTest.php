<?php

beforeEach(function () {
    $this->pluginPath = dirname(__DIR__);
    $this->manifest = json_decode(file_get_contents($this->pluginPath.'/nativephp.json'), true);
    $this->kotlin = collect(glob($this->pluginPath.'/resources/android/src/*.kt'))
        ->map(fn ($f) => file_get_contents($f))
        ->implode("\n");
    $this->swift = file_get_contents($this->pluginPath.'/resources/ios/Sources/LauncherFunctions.swift');
    $this->xml = simplexml_load_file($this->pluginPath.'/resources/android/AndroidManifest.xml');
    $this->xml->registerXPathNamespace('android', 'http://schemas.android.com/apk/res/android');
});

function attr(SimpleXMLElement $el, string $name): ?string
{
    $value = $el->attributes('http://schemas.android.com/apk/res/android')[$name] ?? null;

    return $value === null ? null : (string) $value;
}

describe('Plugin Manifest', function () {
    it('has the expected shape', function () {
        expect($this->manifest['namespace'])->toBe('Launcher')
            ->and($this->manifest)->toHaveKeys(['bridge_functions', 'events', 'android', 'ios']);
    });

    it('exposes the four launcher functions', function () {
        $names = collect($this->manifest['bridge_functions'])->pluck('name')->sort()->values()->all();

        expect($names)->toBe(['Launcher.Apps', 'Launcher.IsDefaultHome', 'Launcher.Open', 'Launcher.OpenHomeSettings']);
    });

    it('declares the home activities in XML, where taskAffinity is possible, not in nativephp.json', function () {
        expect($this->manifest['android'])->not->toHaveKey('activities')
            ->and(count($this->xml->application->activity))->toBe(2);
    });

    it('answers HOME with an activity sharing the app task affinity, so the app can live in its task', function () {
        $home = $this->xml->xpath('//activity[@android:name="com.kevinbatdorf.plugins.launcher.LauncherHomeActivity"]')[0];
        $categories = array_map(fn ($c) => attr($c, 'name'), iterator_to_array($home->{'intent-filter'}->category, false));

        expect(attr($home, 'taskAffinity'))->toBeNull()
            ->and(attr($home, 'exported'))->toBe('true')
            ->and(attr($home, 'launchMode'))->toBe('singleTop')
            ->and(attr($home, 'theme'))->toContain('Black')
            ->and($categories)->toContain('android.intent.category.HOME', 'android.intent.category.DEFAULT');
    });

    it('answers SECONDARY_HOME with its own task affinity, or Android evicts one home task for the other', function () {
        $secondary = $this->xml->xpath('//activity[@android:name="com.kevinbatdorf.plugins.launcher.LauncherSecondaryHomeActivity"]')[0];
        $categories = array_map(fn ($c) => attr($c, 'name'), iterator_to_array($secondary->{'intent-filter'}->category, false));

        expect(attr($secondary, 'taskAffinity'))->toBe('${applicationId}.launcher.secondary')
            ->and(attr($secondary, 'exported'))->toBe('true')
            ->and(attr($secondary, 'launchMode'))->toBe('singleTop')
            ->and($categories)->toContain('android.intent.category.SECONDARY_HOME', 'android.intent.category.DEFAULT');
    });

    it('follows the live app instance itself, since upstream drops its holder when a replaced instance dies', function () {
        expect($this->manifest['android']['init_function'])->toBe('com.kevinbatdorf.plugins.launcher.registerLauncher')
            ->and($this->kotlin)->toContain('fun registerLauncher(context: Context)')
            ->and($this->kotlin)->toContain('MainActivity.instance?.let { current = it }')
            ->and(file_get_contents($this->pluginPath.'/resources/android/src/LauncherHomeActivity.kt'))->toContain('val app = LauncherApp.live()')
            ->and(file_get_contents($this->pluginPath.'/resources/android/src/LauncherEvents.kt'))->toContain('LauncherApp.live()');
    });

    it('announces the HomeRequested event', function () {
        expect($this->manifest['events'])->toBe(['KevinBatdorf\\Launcher\\Events\\HomeRequested']);
    });

    it('may query every package, or the app list is incomplete on Android 11+', function () {
        expect($this->manifest['android']['permissions'])->toContain('android.permission.QUERY_ALL_PACKAGES');
    });
});

describe('Native Code', function () {
    it('implements every manifest bridge function on both platforms', function () {
        foreach ($this->manifest['bridge_functions'] as $fn) {
            $class = str_replace('Launcher.', '', $fn['name']);

            expect($this->kotlin)->toContain("class {$class}(")
                ->and($this->swift)->toContain("class {$class}: BridgeFunction");
        }
    });

    it('stays resident: only a duplicate instance above the app ever finishes', function () {
        $home = file_get_contents($this->pluginPath.'/resources/android/src/LauncherHomeActivity.kt');

        expect($home)->toContain('open class LauncherHomeActivity : Activity()')
            ->and($this->kotlin)->toContain('class LauncherSecondaryHomeActivity : LauncherHomeActivity()')
            ->and(substr_count($home, 'finish()'))->toBe(1)
            ->and($home)->toContain('if (!isTaskRoot) finish()');
    });

    it('boots the app into the home task and brings it forward on its own display', function () {
        expect($this->kotlin)->toContain('"com.nativephp.mobile.ui.MainActivity"')
            ->and($this->kotlin)->toContain('if (app.display?.displayId == displayId) bringForward()')
            ->and($this->kotlin)->toContain('Intent.FLAG_ACTIVITY_REORDER_TO_FRONT')
            ->and($this->kotlin)->toContain('launchDisplayId = Display.DEFAULT_DISPLAY')
            ->and($this->kotlin)->toContain('if (displayId == Display.DEFAULT_DISPLAY) mainIntent() else homeIntent()')
            ->and($this->kotlin)->not->toContain('FLAG_ACTIVITY_MULTIPLE_TASK');
    });

    it('makes every other display resident with our home as soon as the main one is', function () {
        expect($this->kotlin)->toContain('if (displayId == Display.DEFAULT_DISPLAY) ensureSiblingHomes()')
            ->and($this->kotlin)->toContain('addCategory(Intent.CATEGORY_SECONDARY_HOME)')
            ->and($this->kotlin)->toContain('display.displayId in LauncherApp.homes')
            ->and($this->kotlin)->toContain('if (display.flags and Display.FLAG_PRIVATE != 0) continue');
    });

    it('draws one icon per entry point and redraws it after the app updates', function () {
        expect($this->kotlin)->toContain('File(dir, "$pkg.${info.activityInfo.name}.png")')
            ->and($this->kotlin)->toContain('file.lastModified() >= updatedAt');
    });

    it('removes app tasks outside the home task and waits for a dying instance before booting again', function () {
        expect($this->kotlin)->toContain('?.also { runCatching { task.finishAndRemoveTask() } }')
            ->and($this->kotlin)->toContain('app.isFinishing || app.taskId in strays ->');
    });

    it('tells PHP which display wants home, matching the event class', function () {
        $event = file_get_contents($this->pluginPath.'/src/Events/HomeRequested.php');

        expect($this->kotlin)->toContain('KevinBatdorf\\\\Launcher\\\\Events\\\\HomeRequested')
            ->and($this->kotlin)->toContain('put("displayId", displayId)')
            ->and($event)->toContain('public int $displayId');
    });

    it('opens an app on the display it was asked for, by entry point when one is named', function () {
        expect($this->kotlin)->toContain('launchDisplayId = display')
            ->and($this->kotlin)->toContain('getLaunchIntentForPackage(pkg)')
            ->and($this->kotlin)->toContain('.setClassName(pkg, entry)');
    });
});

describe('PHP', function () {
    it('registers the provider and manifest for discovery', function () {
        $composer = json_decode(file_get_contents($this->pluginPath.'/composer.json'), true);

        expect($composer['extra']['laravel']['providers'])->toContain('KevinBatdorf\\Launcher\\LauncherServiceProvider')
            ->and($composer['extra']['nativephp']['manifest'])->toBe('nativephp.json');
    });

    it('offers a trait that keeps the home screen put on BACK at the root', function () {
        $trait = file_get_contents($this->pluginPath.'/src/Concerns/IsHomeScreen.php');

        expect(trait_exists(KevinBatdorf\Launcher\Concerns\IsHomeScreen::class))->toBeTrue()
            ->and($trait)->toContain('public function onBackPressed(): void')
            ->and($trait)->toContain('! $this->nativeRouter->isRootScreen()');
    });

    it('reports nothing without the bridge', function () {
        $launcher = new KevinBatdorf\Launcher\Launcher;

        expect($launcher->apps())->toBe([])
            ->and($launcher->open('com.example'))->toBeFalse()
            ->and($launcher->isDefaultHome())->toBeFalse()
            ->and($launcher->currentHome())->toBe('');
    });
});
