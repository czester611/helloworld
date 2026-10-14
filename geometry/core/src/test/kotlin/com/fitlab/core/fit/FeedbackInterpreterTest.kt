package com.fitlab.core.fit

import com.fitlab.core.fit.CompensationKind.HOLE
import com.fitlab.core.fit.CompensationKind.OUTLINE
import com.fitlab.core.fit.CompensationKind.PIN
import com.fitlab.core.testutil.assertClose
import org.junit.jupiter.api.Test

class FeedbackInterpreterTest {
    @Test
    fun internalAndExternal() {
        assertClose(-0.45, FeedbackInterpreter.sample(HOLE, -0.25, Feedback.Fit(Verdict.TOO_TIGHT, 0.2)))
        assertClose(-0.15, FeedbackInterpreter.sample(HOLE, -0.25, Feedback.Fit(Verdict.TOO_LOOSE, 0.1)))
        assertClose(0.30, FeedbackInterpreter.sample(PIN, 0.10, Feedback.Fit(Verdict.TOO_TIGHT, 0.2)))
        assertClose(0.05, FeedbackInterpreter.sample(OUTLINE, 0.10, Feedback.Fit(Verdict.TOO_LOOSE, 0.05)))
        assertClose(-0.25, FeedbackInterpreter.sample(HOLE, -0.25, Feedback.Fit(Verdict.OK, 0.0)))
    }

    @Test
    fun measured() {
        assertClose(-0.20, FeedbackInterpreter.sample(HOLE, -0.25, Feedback.Measured(10.05), cadMm = 10.25))
    }
}
