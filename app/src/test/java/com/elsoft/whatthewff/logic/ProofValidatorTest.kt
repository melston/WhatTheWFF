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

    @Test
    fun `test validate() with conjunction`() {
        val proofLines = listOf(
                LineInfo("q", Justification.Premise),
                LineInfo("p", Justification.Premise),
                LineInfo("q", Justification.Premise),
                LineInfo("(p & q)",
                         Justification.Inference(InferenceRule.CONJUNCTION, listOf(2, 3))),
        )
        assertTrue(ProofValidator.validate(createProof(proofLines)).isValid)
    }

    @Test
    fun `test validate() with more complex conjunction`() {
        val proofLines = listOf(
            LineInfo("r", Justification.Premise),
            LineInfo("p->s", Justification.Premise),
            LineInfo("q->v", Justification.Premise),
            LineInfo("(p->s) & (q->v)",
                     Justification.Inference(InferenceRule.CONJUNCTION, listOf(2, 3))),
        )
        assertTrue("Expected '(p->s) & (q->v)' to be valid for Conjunction",
                   ProofValidator.validate(createProof(proofLines)).isValid)

        val proofLines2 = listOf(
            LineInfo("r", Justification.Premise),
            LineInfo("p->s", Justification.Premise),
            LineInfo("q->v", Justification.Premise),
            LineInfo("((p->s) & (q->v))",
                     Justification.Inference(InferenceRule.CONJUNCTION, listOf(2, 3))),
        )
        assertTrue("Expected '((p->s) & (q->v))' to be valid for Conjunction",
                   ProofValidator.validate(createProof(proofLines2)).isValid)

        val proofLines3 = listOf(
            LineInfo("r", Justification.Premise),
            LineInfo("p->s", Justification.Premise),
            LineInfo("q->v", Justification.Premise),
            LineInfo("(p->s) & q->v",
                     Justification.Inference(InferenceRule.CONJUNCTION, listOf(2, 3))),
        )
        assertTrue("Expected '(p->s) & q->v' to be invalid for Conjunction",
                   !ProofValidator.validate(createProof(proofLines3)).isValid)
    }
}