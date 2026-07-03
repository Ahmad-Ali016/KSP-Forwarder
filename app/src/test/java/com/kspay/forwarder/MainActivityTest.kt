package com.kspay.forwarder

import android.view.WindowManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @Test
    fun `keeps the screen on so a POS terminal never sleeps mid-sale`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val flags = activity.window.attributes.flags
        assert(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
    }
}
