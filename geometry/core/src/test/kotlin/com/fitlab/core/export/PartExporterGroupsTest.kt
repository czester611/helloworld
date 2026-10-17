package com.fitlab.core.export

import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.mesh.Prism
import com.fitlab.core.mesh.Solid
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class PartExporterGroupsTest {
    private fun cube(x0: Double, name: String) =
        NamedSolid(name, Solid(listOf(Prism(Shape2(Shapes.rect(x0, 0.0, x0 + 10.0, 10.0)), 0.0, 5.0))))

    private fun model(bytes: ByteArray): String {
        val zin = ZipInputStream(ByteArrayInputStream(bytes))
        var e = zin.nextEntry
        while (e != null) {
            if (e.name == "3D/3dmodel.model") return zin.readBytes().toString(Charsets.UTF_8)
            e = zin.nextEntry
        }
        error("brak modelu")
    }

    @Test
    fun threeMfHasOneBuildItemPerPart() {
        val groups = listOf(cube(0.0, "base"), cube(20.0, "lid"))
        val out = ByteArrayOutputStream()
        val rep = PartExporter.exportGroups3mf(groups, "Obudowa", out)
        assertTrue(rep.isClosed && rep.isConsistentlyOriented)
        assertClose(1000.0, rep.volumeMm3, 1e-6)
        val xml = model(out.toByteArray())
        assertEquals(2, Regex("<item ").findAll(xml).count())
        assertTrue(xml.contains("name=\"base\"") && xml.contains("name=\"lid\""))
        assertEquals(2, Regex("<components>").findAll(xml).count())
    }

    @Test
    fun relativePositionIsPreservedAndZIsGrounded() {
        val d = PartExporter.dimensionsGroups(listOf(cube(0.0, "a"), cube(20.0, "b")))
        assertClose(30.0, d.x, 1e-9)
        assertClose(10.0, d.y, 1e-9)
        assertClose(5.0, d.z, 1e-9)
        val prepared = PartExporter.preparedGroups(listOf(cube(0.0, "a"), cube(20.0, "b")))
        val aMax = prepared[0].second.maxOf { it.bounds().second.x }
        val bMin = prepared[1].second.minOf { it.bounds().first.x }
        assertClose(10.0, bMin - aMax, 1e-9)
    }

    @Test
    fun emptyGroupsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PartExporter.preparedGroups(emptyList()) }
    }
}
