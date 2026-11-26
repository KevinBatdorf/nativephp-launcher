<?php

namespace KevinBatdorf\Launcher\Events;

use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

/**
 * A display wants its home screen: the user pressed HOME there, backed out
 * of the last app, or swiped it away. Fires while this app is the home app.
 *
 * On the display the app itself is on, the plugin already brings the app
 * forward. Elsewhere nothing happens unless the app acts; with the screens
 * plugin that is `Screens::show($displayId)`.
 *
 * `$wasInFront`: that display already showed the app, so the press brought nothing back.
 */
class HomeRequested
{
    use Dispatchable, SerializesModels;

    public function __construct(
        public int $displayId,
        public bool $wasInFront = false,
    ) {}
}
