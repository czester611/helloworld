package com.fitlab.core.export

import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.mesh.Prism
import com.fitlab.core.mesh.Solid
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class PartExporterTest {
    @Test
    fun exportsTwoShellsToBothFormats() {
        val s = Solid(
            listOf(
                Prism(Shape2(Shapes.rect(0.0, 0.0, 10.0, 10.0)), 0.0, 2.0),
                Prism(Shape2(Shapes.rect(0.0, 0.0, 10.0, 10.0)), 2.0, 5.0),
            ),
        )
        val stl = ByteArrayOutputStream()
        val r = PartExporter.export(s, ExportFormat.STL, "x", stl)
        assertTrue(r.isClosed)
        assertClose(500.0, r.volumeMm3, 1e-9)
        assertEquals(84 + 50 * 24, stl.size())
        val mf = ByteArrayOutputStream()
        PartExporter.export(s, ExportFormat.THREE_MF, "x", mf)
        assertTrue(mf.size() > 0)
    }

    @Test
    fun dimensionsOfCube() {
        val d = PartExporter.dimensions(ExportTestSupport.cube())
        assertClose(10.0, d.x, 1e-9)
        assertClose(10.0, d.y, 1e-9)
        assertClose(5.0, d.z, 1e-9)
    }
}
