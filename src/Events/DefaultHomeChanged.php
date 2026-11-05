<?php

namespace KevinBatdorf\Launcher\Events;

use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

/** Checked whenever the app resumes, so it follows the dialog, settings, or any other change. */
class DefaultHomeChanged
{
    use Dispatchable, SerializesModels;

    public function __construct(
        public bool $isDefault,
    ) {}
}
