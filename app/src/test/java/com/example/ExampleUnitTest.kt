package com.example

import com.example.data.TemplateCatalog
import com.example.model.MotionEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun templateCatalog_hasValidTemplatesAndMusic() {
        assertTrue(TemplateCatalog.templates.isNotEmpty())
        TemplateCatalog.templates.forEach { tpl ->
            assertTrue(tpl.clips.isNotEmpty())
            assertTrue(tpl.totalDurationMs > 0L)
            assertTrue(tpl.music.beatMarksMs.isNotEmpty())
        }
    }

    @Test
    fun motionEffects_hasExactlyFiftyDistinctAnimations() {
        assertEquals(50, MotionEffect.entries.size)
        assertEquals(50, MotionEffect.entries.map { it.label }.distinct().size)
    }
}
