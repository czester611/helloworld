package com.fitlab.core.enclosure

import com.fitlab.core.design.PrintSettings
import com.fitlab.core.export.PartExporter
import com.fitlab.core.fit.CompensationKind
import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.geom.Bounds2
import com.fitlab.core.geom.GeometryOps
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Shape2
import com.fitlab.core.geom.Shapes
import com.fitlab.core.geom.Vec2
import com.fitlab.core.mesh.Solid
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

class EnclosureBuilderTest {
    private val print = PrintSettings(0.2, 0.0)
    private val zero = CompensationTable.ZERO

    private fun box() = EnclosureSpec.forBox(50.0, 30.0, 15.0)

    private fun build(spec: EnclosureSpec, comp: CompensationTable = zero) = EnclosureBuilder.build(spec, comp, print)

    private fun volume(s: Solid) = PartExporter.validate(s).volumeMm3

    private fun bounds(s: Solid): Bounds2 {
        val bs = s.prisms.map { it.shape.outer.bounds() }
        return Bounds2(bs.minOf { it.minX }, bs.minOf { it.minY }, bs.maxOf { it.maxX }, bs.maxOf { it.maxY })
    }

    private fun materialAt(s: Solid, x: Double, y: Double, z: Double): Boolean {
        val dot = Shape2(Shapes.rect(x - 0.01, y - 0.01, x + 0.01, y + 0.01))
        return s.prisms.any { z >= it.z0 && z < it.z1 && GeometryOps.contains(it.shape, dot) }
    }

    /** Pole zaokrąglonego prostokąta 55×35 z r = 2. */
    private val outerArea = 55.0 * 35.0 - (4.0 - PI) * 4.0

    @Test
    fun cavityAndOuterDimensionsWithoutCompensation() {
        val r = build(box())
        val cav = r.geometry.cavity.bounds()
        assertClose(51.0, cav.width, 1e-6)
        assertClose(31.0, cav.height, 1e-6)
        val outer = r.geometry.outer.bounds()
        assertClose(55.0, outer.width, 1e-6)
        assertClose(35.0, outer.height, 1e-6)
        assertEquals(51.0, r.features.first { it.id == "cavityX" }.cadMm, 1e-6)
        assertEquals(CompensationKind.SLOT, r.features.first { it.id == "cavityX" }.kind)
    }

    @Test
    fun boundingBoxesAndHeightsOfBothParts() {
        val r = build(box())
        assertClose(55.0, r.baseSizeMm.x, 1e-6)
        assertClose(35.0, r.baseSizeMm.y, 1e-6)
        assertClose(1.6 + 17.0, r.baseSizeMm.z, 1e-6)
        assertClose(17.0, r.cavityHeightMm, 1e-6)
        assertClose(55.0, r.lidSizeMm.x, 1e-6)
        assertClose(35.0, r.lidSizeMm.y, 1e-6)
        assertClose(1.6 + 2.0, r.lidSizeMm.z, 1e-6)
        // części leżą obok siebie, bez nakładania
        assertTrue(bounds(r.lid).minX >= bounds(r.base).maxX + 9.99)
    }

    @Test
    fun volumesMatchAnalyticalValues() {
        val r = build(box())
        val cavityArea = 51.0 * 31.0
        val baseExpected = outerArea * 1.6 + (outerArea - cavityArea) * 17.0
        assertClose(baseExpected, volume(r.base), 3.0)
        val lipArea = 50.85 * 30.85 - 48.45 * 28.45
        val lidExpected = outerArea * 1.6 + lipArea * 2.0
        assertClose(lidExpected, volume(r.lid), 3.0)
    }

    @Test
    fun lipFitsInsideCavityWithSlideClearance() {
        val r = build(box())
        val lipPrism = r.lid.prisms.single { it.z0 >= 1.6 - 1e-9 }
        val lipRing = Shape2(lipPrism.shape.outer)
        // pokrywa jest lustrzana (do druku) — dla prostokąta wymiary się nie zmieniają
        assertClose(50.85, lipRing.bounds().width, 1e-6)
        assertClose(30.85, lipRing.bounds().height, 1e-6)
        val cavity = Shape2(r.geometry.cavity)
        val back = EnclosureBuilder.mirrorY(Shape2(lipPrism.shape.outer, emptyList()))
        // przesunięcie układu na stole: porównaj po wyśrodkowaniu
        val c = back.bounds().center
        val centered = back.translated(Vec2(-c.x, -c.y))
        assertTrue(GeometryOps.contains(cavity, centered))
        assertClose(0.075, (51.0 - lipRing.bounds().width) / 2.0, 1e-6)
        assertClose(2.0, lipPrism.z1 - lipPrism.z0, 1e-9)
    }

    @Test
    fun compensationAppliedToCavityAndLip() {
        val comp = CompensationTable(mapOf(CompensationKind.SLOT to -0.2, CompensationKind.OUTLINE to 0.1))
        val r = build(box(), comp)
        // cad = cel − bias
        assertClose(51.2, r.features.first { it.id == "cavityX" }.cadMm, 1e-6)
        val lip = r.features.first { it.id == "lip" }
        assertClose(50.85, lip.targetMm, 1e-9)
        assertClose(50.75, lip.cadMm, 1e-6)
        assertClose(54.9, r.baseSizeMm.x, 1e-6)
        val wall = r.base.prisms.last()
        val inner = wall.shape.holes.single().bounds()
        assertClose(51.2, inner.width, 1e-6)
    }

    @Test
    fun rectCutoutRemovesMaterialOnlyInGivenHeightRange() {
        val plain = build(box())
        val cut = Cutout("1", CutoutSide.NORTH, positionMm = 25.0, widthMm = 10.0, heightMm = 5.0, centerZMm = 6.0)
        val r = build(box().copy(cutouts = listOf(cut)))
        // środek N: x = 0, ściana y ∈ (15,5; 17,5); zakres z = 1,6 + (3,5..8,5) = 5,1..10,1; układ dna: x od 0
        val base = r.base
        val cx = 27.5
        // w układzie dna obrys zewnętrzny to [0,55]×[0,35], ściana N to y ∈ [33,35] w środku 34
        assertFalse(materialAt(base, cx, 34.0, 7.0))
        assertTrue(materialAt(base, cx, 34.0, 3.0))
        assertTrue(materialAt(base, cx, 34.0, 12.0))
        assertTrue(materialAt(base, cx + 8.0, 34.0, 7.0))
        val removed = volume(plain.base) - volume(r.base)
        assertClose(10.0 * 5.0 * 2.0, removed, 0.5)
    }

    @Test
    fun circleCutoutRemovesCylinderVolume() {
        val cut = Cutout("c", CutoutSide.EAST, positionMm = 15.0, widthMm = 6.0, heightMm = 0.0, centerZMm = 8.0, shape = CutoutShape.CIRCLE)
        val r = build(box().copy(cutouts = listOf(cut)))
        val removed = volume(build(box()).base) - volume(r.base)
        assertClose(PI * 9.0 * 2.0, removed, 3.0)
        // środek otworu: x = 55 − 1, y = 17,5, z = 1,6 + 8
        assertFalse(materialAt(r.base, 54.0, 17.5, 9.6))
        assertTrue(materialAt(r.base, 54.0, 17.5, 14.0))
        assertEquals(CompensationKind.HOLE, r.features.first { it.id == "cut-c" }.kind)
    }

    @Test
    fun edgeIndexCutoutWorksLikeCompassSide() {
        // krawędzie prostokąta CCW: 0 = południe (E), 1 = wschód (N), 2 = północ (W), 3 = zachód (S)
        val cut = Cutout("e", CutoutSide.EDGE, edgeIndex = 2, positionMm = 10.0, widthMm = 8.0, heightMm = 4.0, centerZMm = 5.0)
        val r = build(box().copy(cutouts = listOf(cut)))
        val removed = volume(build(box()).base) - volume(r.base)
        assertClose(8.0 * 4.0 * 2.0, removed, 0.5)
        assertTrue(r.warnings.none { it.contains("pominięte") })
    }

    @Test
    fun cutoutOutsideEdgeIsSkippedWithWarning() {
        val cut = Cutout("x", CutoutSide.NORTH, positionMm = 80.0, widthMm = 8.0, heightMm = 4.0, centerZMm = 5.0)
        val r = build(box().copy(cutouts = listOf(cut)))
        assertTrue(r.warnings.any { it.contains("pominięte") })
        assertClose(volume(build(box()).base), volume(r.base), 1e-6)
    }

    @Test
    fun screwModeInsidePostsWhenClearanceLargeEnough() {
        val spec = box().copy(join = EnclosureJoin.SCREWS, clearanceMm = 6.0)
        val r = build(spec)
        assertEquals(4, r.geometry.posts.size)
        assertTrue(r.geometry.posts.none { it.ear })
        // cavity 62×42 → słupek NE: środek (31−2,75; 21−2,75) względem środka obrysu
        val ne = r.geometry.posts.first { it.label == "NE" }
        assertClose(28.25, ne.center.x, 1e-6)
        assertClose(18.25, ne.center.y, 1e-6)
        // otwór w słupku jest pusty, materiał wokół jest
        val width = 62.0 + 4.0
        val px = width / 2.0 + 28.25
        val py = (42.0 + 4.0) / 2.0 + 18.25
        assertFalse(materialAt(r.base, px, py, 10.0))
        assertTrue(materialAt(r.base, px + 2.0, py, 10.0))
        // otwory przelotowe i zagłębienie w pokrywie: 4 × Ø3,4, brak zakładki
        assertEquals(1.6, r.lidSizeMm.z, 1e-6)
        assertEquals(4, r.lid.prisms.first().shape.holes.size)
        assertClose(3.4, r.lid.prisms.last().shape.holes.first().bounds().width, 0.05)
        assertClose(1.9 * 3.0 + 0.4, r.lid.prisms.first().shape.holes.first().bounds().width, 0.05)
    }

    @Test
    fun screwPostsCollidingWithObjectMoveToEarsOrAreSkipped() {
        val ears = build(box().copy(join = EnclosureJoin.SCREWS))
        assertEquals(4, ears.geometry.posts.size)
        assertTrue(ears.geometry.posts.all { it.ear })
        assertTrue(ears.warnings.any { it.contains("koliduje z obiektem") })
        assertTrue(ears.baseSizeMm.x > 55.0)
        val skipped = build(box().copy(join = EnclosureJoin.SCREWS, screwEars = false))
        assertEquals(0, skipped.geometry.posts.size)
        assertTrue(skipped.warnings.any { it.contains("Pominięto słupek") })
        assertClose(55.0, skipped.baseSizeMm.x, 1e-6)
    }

    @Test
    fun postsStayOutsideObjectAndPilotHolesAreOpen() {
        val r = build(box().copy(join = EnclosureJoin.SCREWS))
        val obj = Shape2(r.geometry.objectOutline)
        for (p in r.geometry.posts) {
            val hole = Shape2(Shapes.circle(p.center, p.pilotDiameterMm))
            assertFalse(GeometryOps.intersects(obj, hole))
        }
        // wszystkie słupki to ucho na zewnątrz; każdy ma otwór przez całą wysokość słupka
        val wallPrisms = r.base.prisms.filter { it.z0 >= 1.6 - 1e-9 }
        assertTrue(wallPrisms.all { it.shape.holes.size == 5 }) // wnęka + 4 otwory
    }

    @Test
    fun standoffsRaiseCavityAndStayUnderObject() {
        val r = build(box().copy(standoffs = true))
        assertEquals(4, r.geometry.standoffs.size)
        assertClose(1.6 + 3.0 + 15.0 + 2.0, r.baseSizeMm.z, 1e-6)
        val obj = Shape2(r.geometry.objectOutline)
        for (s in r.geometry.standoffs) {
            assertTrue(GeometryOps.contains(obj, Shape2(Shapes.circle(s.center, s.diameterMm))))
        }
        val plain = build(box().copy(standoffs = false))
        assertTrue(volume(r.base) > volume(plain.base))
    }

    @Test
    fun ventsCutThroughLidPlate() {
        val r = build(box().copy(ventCount = 3, ventWidthMm = 1.5))
        assertEquals(3, r.geometry.vents.size)
        val plate = r.lid.prisms.first()
        assertEquals(3, plate.shape.holes.size)
        val plain = build(box())
        assertClose(3 * 1.5 * 30.0 * 1.6, volume(plain.lid) - volume(r.lid), 0.5)
    }

    @Test
    fun tooManyVentsAreReduced() {
        val r = build(box().copy(ventCount = 20, ventWidthMm = 3.0))
        assertTrue(r.geometry.vents.size < 20)
        assertTrue(r.warnings.any { it.contains("Szczeliny wentylacyjne") })
    }

    @Test
    fun meshesAreClosedAndNondegenerate() {
        val variants = listOf(
            box(),
            box().copy(join = EnclosureJoin.SCREWS),
            box().copy(join = EnclosureJoin.SCREWS, clearanceMm = 6.0, standoffs = true, ventCount = 2),
            box().copy(
                cutouts = listOf(
                    Cutout("a", CutoutSide.WEST, positionMm = 10.0, widthMm = 9.0, heightMm = 3.5, centerZMm = 5.0),
                    Cutout("b", CutoutSide.SOUTH, positionMm = 20.0, widthMm = 6.0, heightMm = 6.0, centerZMm = 8.0, shape = CutoutShape.CIRCLE),
                ),
            ),
        )
        for (spec in variants) {
            val r = build(spec, CompensationTable(mapOf(CompensationKind.SLOT to -0.1, CompensationKind.OUTLINE to 0.05)))
            for (p in r.parts) {
                val rep = PartExporter.validate(p.solid)
                assertTrue(rep.isClosed, p.name)
                assertTrue(rep.isConsistentlyOriented, p.name)
                assertEquals(0, rep.degenerateTriangles, p.name)
                assertTrue(rep.volumeMm3 > 0.0, p.name)
            }
            assertTrue(r.warnings.none { it.startsWith("Siatka") }, r.warnings.toString())
        }
    }

    @Test
    fun warnsWhenExceedingBuildVolumeOrSmallObject() {
        val big = build(EnclosureSpec.forBox(280.0, 100.0, 20.0))
        assertTrue(big.warnings.any { it.contains("przekracza pole druku") })
        val small = build(EnclosureSpec.forBox(8.0, 30.0, 10.0))
        assertTrue(small.warnings.any { it.contains("mniejszy niż 10 mm") })
        assertTrue(build(box()).warnings.none { it.contains("pole druku") || it.contains("mniejszy") })
    }

    @Test
    fun wallNotMultipleOfNozzleWarns() {
        val r = build(box().copy(wallMm = 1.7))
        assertTrue(r.warnings.any { it.contains("wielokrotnością") })
        assertTrue(build(box().copy(wallMm = 1.6)).warnings.none { it.contains("wielokrotnością") })
    }

    @Test
    fun invalidParametersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { build(box().copy(wallMm = 0.2)) }
        assertThrows(IllegalArgumentException::class.java) { build(box().copy(objectHeightMm = 0.0)) }
        assertTrue(EnclosureBuilder.validate(box()).isEmpty())
        assertTrue(EnclosureBuilder.validate(box().copy(cutouts = listOf(Cutout("z", CutoutSide.EDGE, edgeIndex = 9, positionMm = 1.0, widthMm = 3.0, heightMm = 3.0, centerZMm = 2.0)))).isNotEmpty())
    }

    @Test
    fun nonRectangularOutlineGetsRoundedOuterCorners() {
        val l = Ring(
            listOf(
                Vec2(0.0, 0.0), Vec2(40.0, 0.0), Vec2(40.0, 15.0), Vec2(20.0, 15.0), Vec2(20.0, 30.0), Vec2(0.0, 30.0),
            ),
        )
        val r = build(EnclosureSpec(l, 12.0))
        assertClose(45.0, r.geometry.outer.bounds().width, 1e-6) // 40 + 2·0,5 + 2·2
        assertTrue(GeometryOps.contains(Shape2(r.geometry.outer), Shape2(r.geometry.cavity)))
        assertTrue(r.warnings.none { it.startsWith("Siatka") })
    }

    @Test
    fun fitTestIsSmallCornerWithLip() {
        val r = EnclosureBuilder.buildFitTest(
            box().copy(cutouts = listOf(Cutout("a", CutoutSide.WEST, positionMm = 10.0, widthMm = 9.0, heightMm = 3.5, centerZMm = 5.0))),
            zero, print,
        )
        assertTrue(r.baseSizeMm.x <= 25.0 + 1e-6 && r.baseSizeMm.y <= 25.0 + 1e-6)
        assertTrue(r.lidSizeMm.x <= 25.0 + 1e-6 && r.lidSizeMm.y <= 25.0 + 1e-6)
        assertClose(1.6 + 2.0 + 1.0, r.baseSizeMm.z, 1e-6)
        assertClose(1.6 + 2.0, r.lidSizeMm.z, 1e-6)
        for (p in r.parts) {
            val rep = PartExporter.validate(p.solid)
            assertTrue(rep.isClosed && rep.isConsistentlyOriented && rep.degenerateTriangles == 0, p.name)
        }
        assertTrue(r.features.any { it.id == "lip" })
        // pasuje do pełnej obudowy: pasek zakładki ma tę samą grubość
        assertTrue(volume(r.base) < volume(build(box()).base) / 5.0)
    }

    @Test
    fun printOrientationPutsFlatSideOnBedAndLipUp() {
        val r = build(box())
        val plate = r.lid.prisms.first()
        assertEquals(0.0, plate.z0, 0.0)
        assertClose(1.6, plate.z1, 1e-9)
        val lip = r.lid.prisms.last()
        assertClose(1.6, lip.z0, 1e-9)
        assertClose(3.6, lip.z1, 1e-9)
        assertEquals(0.0, r.base.prisms.minOf { it.z0 }, 0.0)
    }

    @Test
    fun elephantFootInsetsFirstLayerOfBothParts() {
        val r = EnclosureBuilder.build(box(), zero, PrintSettings(0.2, 0.2))
        val first = r.base.prisms.first()
        assertClose(0.2, first.z1, 1e-9)
        assertClose(55.0 - 0.4, first.shape.bounds().width, 1e-6)
        assertClose(55.0 - 0.4, r.lid.prisms.first().shape.bounds().width, 1e-6)
    }

    @Test
    fun specJsonRoundTripThroughDesignJson() {
        val spec = box().copy(
            join = EnclosureJoin.SCREWS,
            cutouts = listOf(Cutout("1", CutoutSide.EAST, positionMm = 5.0, widthMm = 9.0, heightMm = 3.0, centerZMm = 4.0, shape = CutoutShape.CIRCLE)),
            ventCount = 2, standoffs = true, outdoor = true,
        )
        val d = com.fitlab.core.design.EnclosureDesign(spec)
        val text = com.fitlab.core.design.DesignJson.encode(d)
        assertTrue(text.contains("\"type\":\"enclosure\""), text)
        assertEquals(d, com.fitlab.core.design.DesignJson.decode(text))
        assertEquals(50.0 to 30.0, spec.rectDimensions())
    }

    @Test
    fun designBuilderBuildsEnclosurePlate() {
        val d = com.fitlab.core.design.EnclosureDesign(box())
        val full = com.fitlab.core.design.DesignBuilder.build(d, zero, print)
        val test = com.fitlab.core.design.DesignBuilder.buildFitTest(d, zero, print)
        assertTrue(PartExporter.dimensions(full.solid).x > 100.0)
        assertTrue(PartExporter.dimensions(test.solid).x < 70.0)
    }
}
