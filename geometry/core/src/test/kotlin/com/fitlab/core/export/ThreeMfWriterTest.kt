package com.fitlab.core.export

import com.fitlab.core.mesh.MeshBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ThreeMfWriterTest {
    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val m = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                m[e.name] = z.readBytes()
            }
        }
        return m
    }

    private fun write(name: String = "Test"): ByteArray {
        val bos = ByteArrayOutputStream()
        ThreeMfWriter.write(listOf(MeshBuilder.build(ExportTestSupport.cube())), name, bos)
        return bos.toByteArray()
    }

    @Test
    fun zipStructureAndXml() {
        val e = entries(write())
        assertEquals(listOf("[Content_Types].xml", "_rels/.rels", "3D/3dmodel.model"), e.keys.toList())
        val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(e.getValue("3D/3dmodel.model")))
        assertEquals("millimeter", doc.documentElement.getAttribute("unit"))
        assertEquals(8, doc.getElementsByTagNameNS("*", "vertex").length)
        assertEquals(12, doc.getElementsByTagNameNS("*", "triangle").length)
        assertEquals(1, doc.getElementsByTagNameNS("*", "component").length)
    }

    @Test
    fun polishLocaleDoesNotProduceCommas() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale("pl", "PL"))
            val xml = String(entries(write()).getValue("3D/3dmodel.model"), Charsets.UTF_8)
            assertTrue(xml.contains("x=\"10.0000\""), xml.take(600))
            assertFalse(Regex("(x|y|z)=\"[0-9-]+,[0-9]").containsMatchIn(xml))
        } finally {
            Locale.setDefault(old)
        }
    }

    @Test
    fun nameIsEscaped() {
        val xml = String(entries(write("A&B <\"x\">")).getValue("3D/3dmodel.model"), Charsets.UTF_8)
        assertTrue(xml.contains("A&amp;B &lt;&quot;x&quot;&gt;"))
    }
}
