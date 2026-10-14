package com.fitlab.core.fit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FeedbackNoteParserTest {
    @Test
    fun examplesFromDesign() {
        assertEquals(ParsedNote(Verdict.TOO_TIGHT, 0.2), FeedbackNoteParser.parse("za ciasno o 0,2 mm"))
        assertEquals(ParsedNote(Verdict.TOO_LOOSE, 0.1), FeedbackNoteParser.parse("trochę luźno, 0.1"))
        assertEquals(ParsedNote(Verdict.OK, null), FeedbackNoteParser.parse("pasuje idealnie"))
        assertEquals(ParsedNote(Verdict.TOO_TIGHT, null), FeedbackNoteParser.parse("nie wchodzi"))
        assertEquals(ParsedNote(null, null), FeedbackNoteParser.parse(""))
        assertEquals(ParsedNote(Verdict.TOO_TIGHT, null), FeedbackNoteParser.parse("za ciasno o 5 mm"))
    }
}
