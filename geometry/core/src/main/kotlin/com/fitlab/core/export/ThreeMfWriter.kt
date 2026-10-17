package com.fitlab.core.export

import com.fitlab.core.mesh.Mesh
import java.io.OutputStream
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Zapis 3MF (ZIP z trzema plikami XML). */
object ThreeMfWriter {
    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>
</Types>
"""
    private const val RELS = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Target="/3D/3dmodel.model" Id="rel0" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>
</Relationships>
"""

    /**
     * Zapisuje każdą siatkę z [solidMeshes] jako osobny obiekt oraz obiekt-kompozyt do budowy.
     * Liczby formatowane z [Locale.ROOT] (kropka dziesiętna).
     */
    fun write(solidMeshes: List<Mesh>, name: String, out: OutputStream) {
        val zip = ZipOutputStream(out)
        zip.setLevel(Deflater.DEFAULT_COMPRESSION)
        fun entry(path: String, content: String) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", CONTENT_TYPES)
        entry("_rels/.rels", RELS)
        entry("3D/3dmodel.model", model(solidMeshes, escape(name)))
        zip.finish()
        zip.flush()
    }

    /**
     * Zapisuje kilka nazwanych części jako osobne obiekty (kompozyty) w jednym pliku, z zachowaniem ich położenia
     * względem siebie. Każda część to lista powłok; do budowy trafia po jednym elemencie na część.
     */
    fun writeParts(parts: List<Pair<String, List<Mesh>>>, title: String, out: OutputStream) {
        val zip = ZipOutputStream(out)
        zip.setLevel(Deflater.DEFAULT_COMPRESSION)
        fun entry(path: String, content: String) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", CONTENT_TYPES)
        entry("_rels/.rels", RELS)
        entry("3D/3dmodel.model", modelParts(parts, escape(title)))
        zip.finish()
        zip.flush()
    }

    private fun modelParts(parts: List<Pair<String, List<Mesh>>>, title: String): String {
        val sb = StringBuilder(1 shl 16)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<model unit=\"millimeter\" xml:lang=\"en-US\" xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">\n")
        sb.append("  <metadata name=\"Title\">").append(title).append("</metadata>\n")
        sb.append("  <metadata name=\"Application\">FitLab</metadata>\n")
        sb.append("  <resources>\n")
        var nextId = 1
        val composites = ArrayList<Pair<Int, List<Int>>>()
        val names = ArrayList<String>()
        for ((name, meshes) in parts) {
            val ids = ArrayList<Int>()
            for (m in meshes) {
                val id = nextId++
                ids.add(id)
                sb.append("    <object id=\"").append(id).append("\" type=\"model\">\n      <mesh>\n        <vertices>\n")
                for (v in m.vertices) {
                    sb.append("          <vertex x=\"").append(f(v.x)).append("\" y=\"").append(f(v.y))
                        .append("\" z=\"").append(f(v.z)).append("\"/>\n")
                }
                sb.append("        </vertices>\n        <triangles>\n")
                for (t in m.triangles) {
                    sb.append("          <triangle v1=\"").append(t.a).append("\" v2=\"").append(t.b)
                        .append("\" v3=\"").append(t.c).append("\"/>\n")
                }
                sb.append("        </triangles>\n      </mesh>\n    </object>\n")
            }
            composites.add(nextId++ to ids)
            names.add(escape(name))
        }
        composites.forEachIndexed { i, (compId, ids) ->
            sb.append("    <object id=\"").append(compId).append("\" type=\"model\" name=\"").append(names[i]).append("\">\n")
            sb.append("      <components>\n")
            for (id in ids) sb.append("        <component objectid=\"").append(id).append("\"/>\n")
            sb.append("      </components>\n    </object>\n")
        }
        sb.append("  </resources>\n  <build>\n")
        for ((compId, _) in composites) sb.append("    <item objectid=\"").append(compId).append("\"/>\n")
        sb.append("  </build>\n</model>\n")
        return sb.toString()
    }

    private fun model(meshes: List<Mesh>, name: String): String {
        val sb = StringBuilder(1 shl 16)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<model unit=\"millimeter\" xml:lang=\"en-US\" xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">\n")
        sb.append("  <metadata name=\"Title\">").append(name).append("</metadata>\n")
        sb.append("  <metadata name=\"Application\">FitLab</metadata>\n")
        sb.append("  <resources>\n")
        meshes.forEachIndexed { i, m ->
            sb.append("    <object id=\"").append(i + 1).append("\" type=\"model\">\n      <mesh>\n        <vertices>\n")
            for (v in m.vertices) {
                sb.append("          <vertex x=\"").append(f(v.x)).append("\" y=\"").append(f(v.y))
                    .append("\" z=\"").append(f(v.z)).append("\"/>\n")
            }
            sb.append("        </vertices>\n        <triangles>\n")
            for (t in m.triangles) {
                sb.append("          <triangle v1=\"").append(t.a).append("\" v2=\"").append(t.b)
                    .append("\" v3=\"").append(t.c).append("\"/>\n")
            }
            sb.append("        </triangles>\n      </mesh>\n    </object>\n")
        }
        val compId = meshes.size + 1
        sb.append("    <object id=\"").append(compId).append("\" type=\"model\" name=\"").append(name).append("\">\n")
        sb.append("      <components>\n")
        for (i in meshes.indices) sb.append("        <component objectid=\"").append(i + 1).append("\"/>\n")
        sb.append("      </components>\n    </object>\n  </resources>\n  <build>\n")
        sb.append("    <item objectid=\"").append(compId).append("\"/>\n  </build>\n</model>\n")
        return sb.toString()
    }

    private fun f(v: Double): String = String.format(Locale.ROOT, "%.4f", v)

    private fun escape(s: String): String = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}
