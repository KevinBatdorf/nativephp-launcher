<?php

namespace KevinBatdorf\Launcher\Events;

use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

/** An app was installed, updated, removed, or had a component switched on or off. */
class AppsChanged
{
    use Dispatchable, SerializesModels;

    public function __construct(
        public string $package = '',
    ) {}
}
