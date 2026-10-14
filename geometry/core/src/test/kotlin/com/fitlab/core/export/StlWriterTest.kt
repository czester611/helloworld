package com.fitlab.core.export

import com.fitlab.core.geom.Vec3
import com.fitlab.core.mesh.MeshBuilder
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StlWriterTest {
    @Test
    fun cubeLayoutAndRoundTrip() {
        val mesh = MeshBuilder.build(ExportTestSupport.cube())
        val bos = ByteArrayOutputStream()
        StlWriter.write(mesh, bos)
        val bytes = bos.toByteArray()
        assertEquals(684, bytes.size)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(12, bb.getInt(80))
        assertFalse(String(bytes, 0, 5, Charsets.US_ASCII) == "solid")
        // odczyt zwrotny
        var off = 84
        for (t in mesh.triangles) {
            off += 12
            for (idx in listOf(t.a, t.b, t.c)) {
                val v: Vec3 = mesh.vertices[idx]
                assertClose(v.x, bb.getFloat(off).toDouble(), 1e-5)
                assertClose(v.y, bb.getFloat(off + 4).toDouble(), 1e-5)
                assertClose(v.z, bb.getFloat(off + 8).toDouble(), 1e-5)
                off += 12
            }
            off += 2
        }
    }

    @Test
    fun solidHeaderRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StlWriter.write(MeshBuilder.build(ExportTestSupport.cube()), ByteArrayOutputStream(), "solid x")
        }
    }

    @Test
    fun writesCubeFilesForManualInspection() {
        val dir = File("build/test-output").also { it.mkdirs() }
        File(dir, "cube.stl").outputStream().use { PartExporter.export(ExportTestSupport.cube(), ExportFormat.STL, "cube", it) }
        File(dir, "cube.3mf").outputStream().use { PartExporter.export(ExportTestSupport.cube(), ExportFormat.THREE_MF, "cube", it) }
        assertEquals(684L, File(dir, "cube.stl").length())
    }
}
