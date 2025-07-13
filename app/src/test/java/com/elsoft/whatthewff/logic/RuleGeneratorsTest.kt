// File: test/com/elsoft/whatthewff/logic/RuleGeneratorsTest.kt
// This file contains unit tests for the RuleGenerators object.

package com.elsoft.whatthewff.logic

import org.junit.Assert.*
import org.junit.Test

class RuleGeneratorsTest {

    // A helper function to easily create Formula objects from strings for testing.
    private fun f(formulaString: String): Formula {
        val tileMap = AvailableTiles.allTiles.associateBy { it.symbol }
        val tiles = formulaString.mapNotNull { char -> tileMap[char.toString()] }
        return Formula(tiles)
    }

    // A helper to make set comparisons in tests more readable.
    private fun assertFormulaSetsEqual(message: String, expected: Set<String>, actual: List<Formula>) {
        val actualStrings = actual.map { it.stringValue }.toSet()
        assertEquals(message, expected, actualStrings)
    }

    @Test
    fun `modusPonens strategy generates correct premise and next goal`() {
        val goal = f("q")
        val availableVars = mutableListOf(AvailableTiles.p)

        val step = RuleGenerators.modusPonens.generate(goal, availableVars)

        assertNotNull("Generation step should not be null", step)
        assertEquals("Should generate 1 new premise", 1, step!!.newPremises.size)
        assertEquals("Generated premise should be (p→q)", "(p→q)", step.newPremises.first().stringValue)
        assertEquals("Should generate 1 next goal", 1, step.nextGoals.size)
        assertEquals("Next goal should be p", "p", step.nextGoals.first().stringValue)
    }

    @Test
    fun `conjunction strategy generates correct sub-goals`() {
        val goal = f("(p∧q)")
        val availableVars = mutableListOf<LogicTile>() // Not needed for this rule

        val step = RuleGenerators.conjunction.generate(goal, availableVars)

        assertNotNull("Generation step should not be null", step)
        assertTrue("Should generate 0 new premises. Actual: ${step!!.newPremises.map { it.stringValue }}", step.newPremises.isEmpty())
        assertFormulaSetsEqual(
            "Next goals for (p∧q) should be p and q.",
            expected = setOf("p", "q"),
            actual = step.nextGoals
        )
    }

    @Test
    fun `hypotheticalSyllogism strategy generates correct sub-goals`() {
        val goal = f("(p→r)")
        val availableVars = mutableListOf(AvailableTiles.q) // The intermediate variable

        val step = RuleGenerators.hypotheticalSyllogism.generate(goal, availableVars)

        assertNotNull("Generation step should not be null", step)
        assertTrue("Should generate 0 new premises. Actual: ${step!!.newPremises.map { it.stringValue }}", step.newPremises.isEmpty())
        assertFormulaSetsEqual(
            "Next goals for (p→r) should be (p→q) and (q→r).",
            expected = setOf("(p→q)", "(q→r)"),
            actual = step.nextGoals
        )
    }

    @Test
    fun `disjunctiveSyllogism strategy generates correct premise and next goal`() {
        val goal = f("q")
        val availableVars = mutableListOf(AvailableTiles.p)

        val step = RuleGenerators.disjunctiveSyllogism.generate(goal, availableVars)

        assertNotNull("Generation step should not be null", step)
        assertEquals("Should generate 1 new premise", 1, step!!.newPremises.size)
        assertEquals("Generated premise should be (p∨q)", "(p∨q)", step.newPremises.first().stringValue)
        assertEquals("Should generate 1 next goal", 1, step.nextGoals.size)
        assertEquals("Next goal should be ¬p", "¬p", step.nextGoals.first().stringValue)
    }

    @Test
    fun `modusPonens canApply is correct`() {
        val goal = f("(p→q)")
        val result = RuleGenerators.modusPonens.canApply(goal)
        assertFalse("MP should not apply to implication goals like '(p→q)'. Actual: $result", result)
    }

    @Test
    fun `conjunction canApply is correct`() {
        val goal = f("(p∨q)")
        val result = RuleGenerators.conjunction.canApply(goal)
        assertFalse("Conjunction should not apply to non-conjunction goals like '(p∨q)'. Actual: $result", result)
    }

    @Test
    fun `hypotheticalSyllogism canApply is correct`() {
        val goal = f("(p∨q)")
        val result = RuleGenerators.hypotheticalSyllogism.canApply(goal)
        assertFalse("HS should not apply to non-implication goals like '(p∨q)'. Actual: $result", result)
    }
}
