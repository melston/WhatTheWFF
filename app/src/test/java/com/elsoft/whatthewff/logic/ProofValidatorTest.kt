package com.elsoft.whatthewff.logic

import org.junit.Assert.*
import org.junit.Test

class ProofValidatorTest : LogicTestBase() {
    @Test
    fun `test validate() with valid simple proof`() {
        val proofLines = listOf(
                LineInfo("p -> q", Justification.Premise),
                LineInfo("p", Justification.Premise),
                LineInfo("q", Justification.Inference(InferenceRule.MODUS_PONENS, listOf(1, 2)))
        )
        val proof = createProof(proofLines)
        assertTrue(ProofValidator.validate(proof).isValid)
    }
}