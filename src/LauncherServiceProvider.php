<?php

namespace KevinBatdorf\Launcher;

use Illuminate\Support\ServiceProvider;
use KevinBatdorf\Launcher\Commands\CopyAssetsCommand;

class LauncherServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(Launcher::class, fn () => new Launcher);
    }

    public function boot(): void
    {
        if ($this->app->runningInConsole()) {
            $this->commands([CopyAssetsCommand::class]);
        }
    }
}
