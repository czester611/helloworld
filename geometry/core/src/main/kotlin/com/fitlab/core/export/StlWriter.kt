package com.fitlab.core.export

import com.fitlab.core.mesh.Mesh
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Zapis binarnego STL (little-endian). */
object StlWriter {
    private const val HEADER_BYTES = 80

    /**
     * Zapisuje [mesh] jako binarny STL: 80 B nagłówka, uint32 liczba trójkątów, po 50 B na trójkąt.
     * Nagłówek nie może zaczynać się od „solid”.
     */
    fun write(mesh: Mesh, out: OutputStream, header: String = "FitLab") {
        require(!header.trimStart().startsWith("solid", ignoreCase = true)) { "Nagłówek STL nie może zaczynać się od \"solid\"" }
        val hb = ByteArray(HEADER_BYTES)
        val src = header.toByteArray(Charsets.US_ASCII)
        System.arraycopy(src, 0, hb, 0, minOf(src.size, HEADER_BYTES))
        out.write(hb)
        val head = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        head.putInt(mesh.triangles.size)
        out.write(head.array())
        val buf = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN)
        for (t in mesh.triangles) {
            val a = mesh.vertices[t.a]
            val b = mesh.vertices[t.b]
            val c = mesh.vertices[t.c]
            val n = (b - a).cross(c - a)
            val len = sqrt(n.dot(n))
            buf.clear()
            if (len > 1e-12) {
                buf.putFloat((n.x / len).toFloat()).putFloat((n.y / len).toFloat()).putFloat((n.z / len).toFloat())
            } else {
                buf.putFloat(0f).putFloat(0f).putFloat(0f)
            }
            for (v in listOf(a, b, c)) buf.putFloat(v.x.toFloat()).putFloat(v.y.toFloat()).putFloat(v.z.toFloat())
            buf.putShort(0)
            out.write(buf.array(), 0, 50)
        }
        out.flush()
    }
}
