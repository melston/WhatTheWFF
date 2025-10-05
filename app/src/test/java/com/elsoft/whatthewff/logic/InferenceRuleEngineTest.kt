package com.elsoft.whatthewff.logic

import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceRuleEngineTest : LogicTestBase() {
    // Absorption: (P → Q) |- P → (P ∧ Q)
    @Test
    fun `test absorption rule validation`() {
        val premises = createFormulas("(p -> q)", "p")
        val conclusion = createFormula("p -> (p & q)")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.ABSORPTION, premises, conclusion))
    }

    @Test
    fun `test getPossibleApplications() absorption`() {
        val premise = createFormula("(p -> q)")
        val conclusion = createFormula("p -> (p & q)")
        val appls = InferenceRuleEngine.getPossibleApplications(InferenceRule.ABSORPTION,
                                                                listOf(premise))
        assertTrue(appls.size == 1)
        assertTrue(appls.any { compareFormulas(it.conclusion, conclusion) })
        assertTrue(appls.get(0).premises.contains(premise.normalize()) )
    }

    // Addition: P |- P ∨ Q
    @Test
    fun `test addition rule validation`() {
        val premises = createFormulas("p", "q")
        val conclusion = createFormula("p | q")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.ADDITION,
                                                        premises, conclusion))
    }

    @Test
    fun `test getPossibleApplications() for addition`() {
        val premises = createFormulas("p", "q")
        val conclusionP = createFormula("p | q")
        val appls = InferenceRuleEngine.getPossibleApplications(InferenceRule.ADDITION,
                                                                premises)
        // This will produce 2 distinct Application objects:
        // 1. Conclusion: "p | q", Premise: ["p"]
        assertTrue("Addition returns ${appls.size} applications instead of 2", appls.size == 2)
        // Find the application that concludes "p | q"
        val appForP = appls.find { compareFormulas(it.conclusion, conclusionP) }
        Assert.assertNotNull(appForP)
        // Check that its premise list is correct (only "p")
        assertTrue(appForP!!.premises.size == 2)
        assertTrue(compareFormulas(appForP.premises[0], premises[0]))

        val appForQ = appls.find { compareFormulas(it.conclusion, conclusionP) }
        Assert.assertNotNull(appForQ)
        // Check that its premise list is correct (only "p")
        assertTrue(appForQ!!.premises.size == 2)
        assertTrue(compareFormulas(appForQ.premises[0], premises[0]))
    }

    // TODO:  Add more InferenceRuleEngine.getPossibleApplications() tests.

    // Conjunction: P, Q |- P ∧ Q
    @Test
    fun `test conjunction rule validation`() {
        val premises = createFormulas("p", "q")
        val conclusion = createFormula("p & q")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.CONJUNCTION, premises, conclusion))
    }

    // Constructive Dilemma: ((P → Q) ∧ (R → S)), P ∨ R |- (Q ∨ S)
    @Test
    fun `test constructive dilemma rule validation`() {
        val premises = createFormulas("(p -> q) & (r -> s)", "p | r")
        val conclusion = createFormula("q | s")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.CONSTRUCTIVE_DILEMMA, premises, conclusion))
    }

    // Disjunctive Syllogism: (P ∨ Q), ¬P |- Q
    @Test
    fun `test disjunctive syllogism rule validation`() {
        val premises = createFormulas("(p | q)", "¬p")
        val conclusion = createFormula("q")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.DISJUNCTIVE_SYLLOGISM, premises, conclusion))
    }

    // Hypothetical Syllogism: (P → Q), (Q → R) |- P → R
    @Test
    fun `test hypothetical syllogism rule validation`() {
        val premises = createFormulas("(p -> q)", "q -> r")
        val conclusion = createFormula("p -> r")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.HYPOTHETICAL_SYLLOGISM, premises, conclusion))
    }

    // Modus Ponens: (P → Q), P |- Q
    @Test
    fun `test modus ponens rule validation`() {
        val premises = createFormulas("p", "p -> q")
        val conclusion = createFormula("q")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.MODUS_PONENS, premises, conclusion))
    }

    // Modus Tollens: (P → Q), ¬Q |- ¬P
    @Test
    fun `test modus tollens rule validation`() {
        val premises = createFormulas("~q", "p -> q")
        val conclusion = createFormula("~p")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.MODUS_TOLLENS, premises, conclusion))
    }

    // Simplification: (P ∧ Q) |- P
    @Test
    fun `test simplification rule validation`() {
        val premises = createFormulas("p & q")
        val conclusion = createFormula("p")
        assertTrue(InferenceRuleEngine.isValidInference(InferenceRule.SIMPLIFICATION, premises, conclusion))
    }

}