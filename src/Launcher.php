<?php

namespace KevinBatdorf\Launcher;

class Launcher
{
    /**
     * Every launchable app except this one, sorted by label.
     *
     * Each entry is `['label' => string, 'package' => string,
     * 'activity' => string, 'icon' => string]`; `icon` is an absolute PNG
     * path for `<native:image>`, or '' when the app has none.
     *
     * @return array<int, array<string, mixed>>
     */
    public function apps(): array
    {
        return $this->call('Launcher.Apps', [])['apps'] ?? [];
    }

    /**
     * Open an app by package, on a platform display id when given. A package
     * can list several entry points; `$activity` picks one, as returned by apps().
     */
    public function open(string $package, ?int $display = null, ?string $activity = null): bool
    {
        $parameters = ['package' => $package];

        if ($display !== null) {
            $parameters['display'] = $display;
        }

        if ($activity !== null) {
            $parameters['activity'] = $activity;
        }

        return (bool) ($this->call('Launcher.Open', $parameters)['ok'] ?? false);
    }

    public function isDefaultHome(): bool
    {
        return (bool) ($this->call('Launcher.IsDefaultHome', [])['default'] ?? false);
    }

    /** The package currently serving as the home screen, or '' when unknown. */
    public function currentHome(): string
    {
        return (string) ($this->call('Launcher.IsDefaultHome', [])['current'] ?? '');
    }

    /** Only the user can pick a home app; this opens the system screen for it. */
    public function openHomeSettings(): bool
    {
        return (bool) ($this->call('Launcher.OpenHomeSettings', [])['ok'] ?? false);
    }

    /**
     * @param  array<string, mixed>  $parameters
     * @return array<string, mixed>
     */
    private function call(string $function, array $parameters): array
    {
        if (! function_exists('nativephp_call')) {
            return [];
        }

        $result = nativephp_call($function, json_encode($parameters) ?: '{}');

        return $result ? (json_decode($result, true) ?: []) : [];
    }
}
