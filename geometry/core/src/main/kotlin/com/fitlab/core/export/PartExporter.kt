package com.fitlab.core.export

import com.fitlab.core.geom.Vec3
import com.fitlab.core.mesh.Mesh
import com.fitlab.core.mesh.MeshBuilder
import com.fitlab.core.mesh.MeshReport
import com.fitlab.core.mesh.MeshValidator
import com.fitlab.core.mesh.Solid
import java.io.OutputStream

/** Eksport bryły do STL lub 3MF z walidacją siatki. */
object PartExporter {
    /** Komunikat błędu walidacji siatki. */
    const val MESH_ERROR = "Błąd siatki — zgłoś projekt"

    /** Siatki powłok przesunięte wspólnie (minZ = 0, środek XY w (0,0)). */
    fun preparedShells(solid: Solid): List<Mesh> {
        val shells = MeshBuilder.buildShells(solid)
        require(shells.isNotEmpty()) { "Pusta bryła" }
        val all = shells.map { it.bounds() }
        val minX = all.minOf { it.first.x }
        val maxX = all.maxOf { it.second.x }
        val minY = all.minOf { it.first.y }
        val maxY = all.maxOf { it.second.y }
        val minZ = all.minOf { it.first.z }
        val d = Vec3(-(minX + maxX) / 2.0, -(minY + maxY) / 2.0, -minZ)
        return shells.map { it.translated(d) }
    }

    /** Wymiary eksportowanej bryły (X, Y, Z w mm) po normalizacji. */
    fun dimensions(solid: Solid): Vec3 {
        val all = preparedShells(solid).map { it.bounds() }
        return Vec3(
            all.maxOf { it.second.x } - all.minOf { it.first.x },
            all.maxOf { it.second.y } - all.minOf { it.first.y },
            all.maxOf { it.second.z } - all.minOf { it.first.z },
        )
    }

    /** Waliduje siatkę bez zapisu. */
    fun validate(solid: Solid): MeshReport =
        MeshValidator.check(preparedShells(solid).fold(Mesh.EMPTY) { acc, m -> acc + m })

    /**
     * Zapisuje bryłę do [out] w formacie [format]. Rzuca [IllegalStateException] (bez zapisu), gdy siatka jest
     * nieszczelna, niespójnie zorientowana lub ma niedodatnią objętość.
     */
    fun export(solid: Solid, format: ExportFormat, name: String, out: OutputStream): MeshReport {
        val shells = preparedShells(solid)
        val combined = shells.fold(Mesh.EMPTY) { acc, m -> acc + m }
        val report = MeshValidator.check(combined)
        if (!report.isClosed || !report.isConsistentlyOriented || report.volumeMm3 <= 0.0) {
            throw IllegalStateException(MESH_ERROR)
        }
        when (format) {
            ExportFormat.STL -> StlWriter.write(combined, out)
            ExportFormat.THREE_MF -> ThreeMfWriter.write(shells, name, out)
        }
        return report
    }
}
