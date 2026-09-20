<?php

namespace KevinBatdorf\Launcher\Facades;

use Illuminate\Support\Facades\Facade;

/**
 * @method static array<int, array<string, mixed>> apps()
 * @method static bool open(string $package, ?int $display = null, ?string $activity = null)
 * @method static bool isDefaultHome()
 * @method static string currentHome()
 * @method static bool openHomeSettings()
 *
 * @see \KevinBatdorf\Launcher\Launcher
 */
class Launcher extends Facade
{
    protected static function getFacadeAccessor(): string
    {
        return \KevinBatdorf\Launcher\Launcher::class;
    }
}
