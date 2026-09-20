<?php

namespace KevinBatdorf\Launcher\Concerns;

/**
 * For the screen that is the home screen. At the root of its stack a normal
 * screen minimises the app on BACK (the back gesture on the edge of the
 * screen); a home screen has nowhere to go and stays. Screens pushed on
 * top of it still pop.
 */
trait IsHomeScreen
{
    public function onBackPressed(): void
    {
        if ($this->nativeRouter !== null && ! $this->nativeRouter->isRootScreen()) {
            $this->back();
        }
    }
}
