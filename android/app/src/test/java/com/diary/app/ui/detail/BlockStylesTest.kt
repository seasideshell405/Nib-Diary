package com.diary.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockStylesTest {

    @Test
    fun defaultJson_isNotEmpty_andRoundTrips() {
        val json = BlockStyles.toJson(BlockStyles.defaults)

        assertTrue("default JSON must not be empty: $json", json.isNotBlank())
        assertTrue("must contain paragraph", json.contains("paragraph"))
        assertTrue("must contain heading", json.contains("heading"))
        assertTrue("must contain image", json.contains("image"))
        assertTrue("must contain timestamp", json.contains("timestamp"))
        assertTrue("heading font size must be written out", json.contains("20"))
        assertTrue("timestamp font size must be written out", json.contains("13"))
        assertTrue("paragraph letter spacing must be written out", json.contains("0.75"))

        val parsed = BlockStyles.parse(json)
        assertNotNull(parsed)
        assertEquals(BlockStyles.defaults, parsed)
    }

    @Test
    fun emptyObject_parsesAsDefaults() {
        assertEquals(BlockStyles.defaults, BlockStyles.parse("{}"))
    }

    @Test
    fun partialConfig_missingKeysFallBackToDefaults() {
        val parsed = BlockStyles.parse(
            """{"paragraph": {"fontSize": 18, "marginTop": 20}}"""
        )
        assertNotNull(parsed)
        assertEquals(18.0, parsed!!.paragraph.fontSize)
        assertEquals(20.0, parsed.paragraph.marginTop, 0.001)
        // Untouched keys keep defaults.
        assertEquals(1.4, parsed.paragraph.lineHeight, 0.001)
        assertEquals(20.0, parsed.heading.fontSize)
    }

    @Test
    fun malformedJson_returnsNull() {
        assertEquals(null, BlockStyles.parse("{not json"))
        assertEquals(null, BlockStyles.parse("""{"paragraph": 42}"""))
    }
}
