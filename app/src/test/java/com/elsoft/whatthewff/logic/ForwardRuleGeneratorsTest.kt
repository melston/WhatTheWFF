// File: test/com/elsoft/whatthewff/logic/ForwardRuleGeneratorsTest.kt
// This file contains unit tests for the ForwardRuleGenerators object.

package com.elsoft.whatthewff.logic

import org.junit.Assert.*
import org.junit.Test

class ForwardRuleGeneratorsTest {

    // Helper function to create Formula objects from strings for testing.
    private fun f(formulaString: String): Formula {
        val tileMap = AvailableTiles.allTiles.associateBy { it.symbol }
        val tiles = formulaString.mapNotNull { char -> tileMap[char.toString()] }
        return Formula(tiles)
    }

    @Test
    fun `conjunction canApply is correct`() {
        assertTrue("Conjunction should apply with 2 formulas",
                   ForwardRuleGenerators.conjunction.canApply(listOf(f("p"),
                                                                     f("q"))))
        assertFalse("Conjunction should not apply with 1 formula",
                    ForwardRuleGenerators.conjunction.canApply(listOf(f("p"))))
    }

    @Test
    fun `conjunction generates a valid conjunction`() {
        val known = listOf(f("p"), f("q"))
        val steps = ForwardRuleGenerators.conjunction.generate(known)
        assertNotNull("Generate should produce a step", steps)
        steps?.forEach { step ->
            assertEquals(
                "Justification should be Conj.",
                "Conj.", step.justification
            )
            // The result could be (p∧q) or (q∧p)
            val possibleOutcomes = setOf("(p∧q)", "(q∧p)")
            assertTrue(
                "Result formula is not a valid conjunction",
                possibleOutcomes.contains(step.formula.stringValue)
            )
        }
    }

    @Test
    fun `modusPonens canApply is correct`() {
        assertTrue("MP should apply when premises exist",
                   ForwardRuleGenerators.modusPonens.canApply(listOf(f("(p→q)"),
                                                                     f("p"))))
        assertFalse("MP should not apply without antecedent",
                    ForwardRuleGenerators.modusPonens.canApply(listOf(f("(p→q)"),
                                                                      f("r"))))
        assertFalse("MP should not apply without implication",
                    ForwardRuleGenerators.modusPonens.canApply(listOf(f("q"),
                                                                      f("p"))))
    }

    @Test
    fun `modusPonens generates correct consequent`() {
        val known = listOf(f("(p→q)"), f("p"))
        val steps = ForwardRuleGenerators.modusPonens.generate(known)
        assertNotNull("Generate should produce a step", steps)
        steps?.forEach { step ->
            assertEquals(
                "Justification should be MP",
                "MP", step.justification
            )
            assertEquals(
                "Result should be the consequent 'q'",
                "q", step.formula.stringValue
            )
        }
    }

    @Test
    fun `modusTollens canApply is correct`() {
        assertTrue("MT should apply when premises exist",
                   ForwardRuleGenerators.modusTollens.canApply(listOf(f("(p→q)"),
                                                                      f("¬q"))))
        assertFalse("MT should not apply without negated consequent",
                    ForwardRuleGenerators.modusTollens.canApply(listOf(f("(p→q)"),
                                                                       f("q"))))
    }

    @Test
    fun `modusTollens generates correct negated antecedent`() {
        val known = listOf(f("(p→q)"), f("¬q"))
        val steps = ForwardRuleGenerators.modusTollens.generate(known)
        assertNotNull("Generate should produce a step", steps)
        steps?.forEach { step ->
            assertEquals(
                "Justification should be MT",
                "MT", step.justification
            )
            assertEquals(
                "Result should be the negated antecedent '¬p'",
                "¬p", step.formula.stringValue
            )
        }
    }

    @Test
    fun `hypotheticalSyllogism canApply is correct`() {
        assertTrue("HS should apply when premises exist",
                   ForwardRuleGenerators.hypotheticalSyllogism.canApply(listOf(f("(p→q)"),
                                                                               f("(q→r)"))))
        assertFalse("HS should not apply without a valid chain",
                    ForwardRuleGenerators.hypotheticalSyllogism.canApply(listOf(f("(p→q)"),
                                                                                f("(r→s)"))))
    }

    @Test
    fun `hypotheticalSyllogism generates correct chained implication`() {
        val known = listOf(f("(p→q)"), f("(q→r)"))
        val steps = ForwardRuleGenerators.hypotheticalSyllogism.generate(known)
        assertNotNull("Generate should produce a step", steps)
        assertTrue("Generate should produce a single step", steps!!.size == 1)
        steps.forEach { step ->
            assertEquals(
                "Justification should be HS",
                "HS", step.justification
            )
            assertEquals(
                "Result should be the chained implication '(p→r)'",
                "(p→r)", step.formula.stringValue
            )
        }
    }

    @Test
    fun `disjunctiveSyllogism canApply is correct`() {
        assertTrue("DS should apply with (p∨q) and ¬p",
                   ForwardRuleGenerators.disjunctiveSyllogism.canApply(listOf(f("(p∨q)"),
                                                                              f("¬p"))))
        assertTrue("DS should apply with (p∨q) and ¬q",
                   ForwardRuleGenerators.disjunctiveSyllogism.canApply(listOf(f("(p∨q)"),
                                                                              f("¬q"))))
        assertFalse("DS should not apply without negation",
                    ForwardRuleGenerators.disjunctiveSyllogism.canApply(listOf(f("(p∨q)"),
                                                                               f("p"))))
    }

    @Test
    fun `disjunctiveSyllogism generates correct conclusion`() {
        val known1 = listOf(f("(p∨q)"), f("¬p"))
        val step1s = ForwardRuleGenerators.disjunctiveSyllogism.generate(known1)
        assertNotNull("Generate should produce a step", step1s)
        assertTrue( "Result of (p∨q), ¬p should have size 1", step1s!!.size == 1)
        step1s.forEach { step1 ->
            assertEquals(
                "Result of (p∨q), ¬p should be q",
                "q", step1.formula.stringValue
            )
        }

        val known2 = listOf(f("(p∨q)"), f("¬q"))
        val step2s = ForwardRuleGenerators.disjunctiveSyllogism.generate(known2)
        assertNotNull("Generate should produce a step", step2s)
        assertTrue( "Result of (p∨q), ¬p should have size 1", step2s!!.size == 1)
        step2s.forEach { step2 ->
            assertEquals(
                "Result of (p∨q), ¬p should be p",
                "p", step2.formula.stringValue
            )
        }
    }

    @Test
    fun `simplification canApply is correct`() {
        assertTrue("Simplification should apply when a conjunction exists",
                   ForwardRuleGenerators.simplification.canApply(listOf(f("(p∧q)"))))
        assertFalse("Simplification should not apply without a conjunction",
                    ForwardRuleGenerators.simplification.canApply(listOf(f("(p∨q)"))))
    }

    @Test
    fun `simplification generates one of the conjuncts`() {
        val known = listOf(f("(p∧q)"))
        val steps = ForwardRuleGenerators.simplification.generate(known)
        assertNotNull("Generate should produce a step", steps)
        assertTrue("Generate should produce two possible steps", steps!!.size == 2)
        val possibleOutcomes = setOf("p", "q")
        steps.forEach { step ->
            assertTrue("Result must be one of the conjuncts",
                       possibleOutcomes.contains(step.formula.stringValue))
        }
    }

    @Test
    fun `addition canApply is correct`() {
        assertTrue("Addition should apply with 2 formulas",
                   ForwardRuleGenerators.addition.canApply(listOf(f("p"),
                                                                  f("q"))))
        assertFalse("Addition should not apply with 1 formula",
                    ForwardRuleGenerators.addition.canApply(listOf(f("p"))))
    }

    @Test
    fun `addition generates a valid disjunction`() {
        val known = listOf(f("p"), f("q"))
        val steps = ForwardRuleGenerators.addition.generate(known)
        assertNotNull("Generate should produce a step", steps)
        assertTrue("Generate should produce two possible steps", steps!!.size == 2)
        val possibleOutcomes = setOf("(p∨q)", "(q∨p)")
        steps.forEach { step ->
            assertTrue(
                "Result formula is not a valid disjunction",
                possibleOutcomes.contains(step.formula.stringValue)
            )
            assertTrue(
                "Premise for addition must be one of the disjuncts",
                step.premises.size == 1 &&
                        (step.premises[0] == f("p") ||
                                step.premises[0] == f("q"))
            )
        }
    }
}
