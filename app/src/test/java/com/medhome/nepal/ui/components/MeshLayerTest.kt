package com.medhome.nepal.ui.components

import android.app.Application
import androidx.compose.ui.geometry.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.ui.theme.SoftDaylight
import com.medhome.nepal.ui.theme.WarmDusk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The mesh's gradients are built once per palette and size, never on a plain redraw. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class MeshLayerTest {

    private val phone = Size(1080f, 2400f)

    @Test
    fun `redrawing at the same size reuses the cached gradients`() {
        val layer = MeshLayer(WarmDusk)
        val first = layer.paintsFor(phone)
        assertSame(first, layer.paintsFor(phone))
        assertSame(first, layer.paintsFor(Size(1080f, 2400f)))
    }

    @Test
    fun `a new size rebuilds them once`() {
        val layer = MeshLayer(WarmDusk)
        val portrait = layer.paintsFor(phone)
        val landscape = layer.paintsFor(Size(2400f, 1080f))
        assertNotSame(portrait, landscape)
        assertSame(landscape, layer.paintsFor(Size(2400f, 1080f)))
    }

    @Test
    fun `one dithered gradient per glow`() {
        val paints = MeshLayer(SoftDaylight).paintsFor(phone)
        assertEquals(SoftDaylight.glows.size, paints.size)
        assertEquals(true, paints.all { it.isDither && it.shader != null })
    }
}
