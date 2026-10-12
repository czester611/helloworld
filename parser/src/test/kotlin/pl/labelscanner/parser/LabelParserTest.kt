package pl.labelscanner.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LabelParserTest {
    private fun l(t: String, x: Int, y: Int, w: Int = 200, h: Int = 30) = OcrLine(t, x, y, x + w, y + h)

    @Test fun `etykieta Mitsubishi - wartosci w osobnych liniach`() {
        val lines = listOf(
            l("MITSUBISHI ELECTRIC", 520, 530, 230),
            l("ROOM AIR CONDITIONER", 1120, 515, 430),
            l("MODEL", 1115, 565, 90),
            l("MSZ-EF25VE3W", 1260, 560, 290, 40),
            l("VOLTAGE", 390, 660, 160),
            l("230 V", 890, 675, 100),
            l("YEAR OF MANUFACTURE 2017.11", 415, 1050, 410),
            l("SERIAL NO.", 415, 1090, 150),
            l("7013350 T", 615, 1085, 280, 40),
            l("JG79B597H21", 1380, 1090, 180),
        )
        assertEquals(LabelInfo("MSZ-EF25VE3W", "7013350 T"), LabelParser.parse(lines))
    }

    @Test fun `etykieta Mitsubishi - wartosci w tej samej linii`() {
        val info = LabelParser.parseText(
            """
            ROOM AIR CONDITIONER
            MODEL MSZ-EF25VE3W -E1
            VOLTAGE 230 V
            SERIAL NO. 7013350 T
            """.trimIndent(),
        )
        assertEquals("MSZ-EF25VE3W", info.model)
        assertEquals("7013350 T", info.serial)
    }

    @Test fun `wartosc pod etykieta`() {
        val lines = listOf(
            l("Model No.", 100, 100, 150), l("XR-500B", 100, 140, 150),
            l("S/N", 400, 100, 60), l("A1B2C3D4", 400, 140, 150),
        )
        assertEquals(LabelInfo("XR-500B", "A1B2C3D4"), LabelParser.parse(lines))
    }

    @Test fun `polskie etykiety i dwukropki`() {
        val info = LabelParser.parseText("Typ: WM-1200X\nNr seryjny: 123456789")
        assertEquals(LabelInfo("WM-1200X", "123456789"), info)
    }

    @Test fun `brak danych`() {
        assertEquals(LabelInfo(null, null), LabelParser.parseText("HELLO WORLD"))
        assertNull(LabelParser.parseText("SERIAL NO.").serial)
    }
}
