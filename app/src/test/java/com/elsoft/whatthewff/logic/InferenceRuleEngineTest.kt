package com.elsoft.whatthewff.logic

import org.junit.Assert.*
import org.junit.Test

class InferenceRuleEngineTest : LogicTestBase() {
    @Test
    fun `test absorption rule`() {
        val premises = createFormulas("(p -> q)", "p")
        val conclusion = f("p -> (p & q)")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.ABSORPTION, premises, conclusion))
    }

    @Test
    fun `test addition rule`() {
        val premises = createFormulas("p", "q")
        val conclusion = f("p | q")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.ADDITION, premises, conclusion))
    }

    @Test
    fun `test conjunction rule`() {
        val premises = createFormulas("p", "q")
        val conclusion = f("p & q")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.CONJUNCTION, premises, conclusion))
    }

    @Test
    fun `test constructive dilemma rule`() {
        val premises = createFormulas("(p -> q) & (r -> s)", "p | r")
        val conclusion = f("q | s")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.CONSTRUCTIVE_DILEMMA, premises, conclusion))
    }

    @Test
    fun `test disjunctive syllogism rule`() {
        val premises = createFormulas("(p | q)", "¬p")
        val conclusion = f("q")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.DISJUNCTIVE_SYLLOGISM, premises, conclusion))
    }

    @Test
    fun `test hypothetical syllogism rule`() {
        val premises = createFormulas("(p -> q)", "q -> r")
        val conclusion = f("p -> r")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.HYPOTHETICAL_SYLLOGISM, premises, conclusion))
    }

    @Test
    fun `test modus ponens rule`() {
        val premises = createFormulas("p", "p -> q")
        val conclusion = f("q")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.MODUS_PONENS, premises, conclusion))
    }

    @Test
    fun `test modus tollens rule`() {
        val premises = createFormulas("~q", "p -> q")
        val conclusion = f("~p")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.MODUS_TOLLENS, premises, conclusion))
    }

    @Test
    fun `test simplification rule`() {
        val premises = createFormulas("p & q")
        val conclusion = f("p")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.SIMPLIFICATION, premises, conclusion))
    }


}