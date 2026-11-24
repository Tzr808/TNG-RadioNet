package com.armanmaurya.internetradio.custom.artwork

import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StationAvatarTest {

    @Test
    fun `seed is stable for the same key`() {
        assertEquals(StationAvatar.seed("station-uuid-1"), StationAvatar.seed("station-uuid-1"))
        assertNotEquals(StationAvatar.seed("station-uuid-1"), StationAvatar.seed("station-uuid-2"))
    }

    @Test
    fun `initials use the first two meaningful words`() {
        assertEquals("RI", StationAvatar.initials("Radio Italia"))
        assertEquals("RI", StationAvatar.initials("Radio Italia - La più bella"))
        assertEquals("R", StationAvatar.initials("Radio 105"))
        assertEquals("?", StationAvatar.initials("   "))
    }

    @Test
    fun `colours are deterministic and differ between stations`() {
        val first = StationAvatar.backgroundColors(StationAvatar.seed("uuid-a"))
        val again = StationAvatar.backgroundColors(StationAvatar.seed("uuid-a"))
        val other = StationAvatar.backgroundColors(StationAvatar.seed("uuid-b"))

        assertEquals(first[0], again[0])
        assertEquals(first[1], again[1])
        assertNotEquals(first[0], other[0])
    }

    @Test
    fun `text has readable contrast on every generated background`() {
        for (index in 0 until 200) {
            val seed = StationAvatar.seed("station-$index")
            val background = StationAvatar.backgroundColors(seed)[0]
            val text = StationAvatar.textColor(seed)
            val contrast = ColorUtils.calculateContrast(text, background)
            assertTrue("Contrast too low ($contrast) for seed $seed", contrast >= 4.5)
        }
    }
}
