<?php

namespace KevinBatdorf\Launcher\Commands;

use Native\Mobile\Plugins\Commands\NativePluginHookCommand;

class CopyAssetsCommand extends NativePluginHookCommand
{
    protected $signature = 'nativephp:launcher:copy-assets';

    protected $description = 'Copy assets for the Launcher plugin';

    public function handle(): int
    {
        $this->info('No assets to copy for Launcher.');

        return self::SUCCESS;
    }
}
