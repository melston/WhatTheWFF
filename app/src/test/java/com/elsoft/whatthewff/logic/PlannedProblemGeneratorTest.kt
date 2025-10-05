package com.elsoft.whatthewff.logic

import org.junit.Assert.*
import org.junit.Test


class PlannedProblemGeneratorTest : LogicTestBase() {

    // =====================================================================================
    // STRATEGY 1: PROPERTY-BASED TESTING
    // We test that the final output has the properties we expect, regardless of what
    // the specific output is. This is the most important test for the `generate` method.
    // =====================================================================================

    /**
     * This test checks the most crucial property: that a generated problem is valid.
     * It runs the generator multiple times to account for randomness and asserts that
     * for every generated problem, the premises are internally consistent.
     * A full test would also prove the conclusion is solvable, but that's a larger task.
     */
    @Test
    fun `generate() produces a problem with consistent premises`() {
        // We run this multiple times to get a good sample of the random generation.
        val problemGenerator = PlannedProblemGenerator()
        for (i in 1..10) {
//            println("--- Generation Attempt $i ---")
            val problem = problemGenerator.generate(difficulty = 3)

            assertNotNull("Generated problem should not be null in attempt $i", problem)
            if (problem == null) return // Or fail(), but returning stops the test here.

            assertTrue("Problem should have premises", problem.premises.isNotEmpty())
            assertNotNull("Problem should have a conclusion", problem.conclusion)

//            println("Generated Premises: ${problem.premises.joinToString { it.toString() }}")
//            println("Generated Conclusion: ${problem.conclusion}")

            // Check that the set of initial premises does not contain a direct contradiction.
            // This validates that `useAtomicAssertion` is working during path selection.
            val premiseAssertions =
                problem.premises.flatMap { it.getAtomicAssertions() }.toSet()
            var hasContradiction = false
            for (assertion in premiseAssertions) {
                // We only need to check for positive atoms, e.g., 'p'
                val baseVar = assertion.getBaseVariable()
                if (baseVar == assertion) {
                    val opposite = RuleGenerators.fNeg(baseVar)
                    if (premiseAssertions.contains(opposite)) {
                        hasContradiction = true
                        break
                    }
                }
            }
            assertFalse(
                "Generated premises should not contain a direct contradiction like P and ~P. " +
                "Found a conflict in attempt ${i}.",
                hasContradiction
            )
//            println("--- Premise consistency check passed for Attempt $i ---\n")
        }
    }

    /**
     * This is a "smoke test" to see if difficulty has a noticeable effect.
     * It's not a strict test because of randomness, but a failing trend indicates a problem.
     */
    @Test
    fun `generate() generally respects difficulty`() {
        val problemGenerator = PlannedProblemGenerator()
        // A more robust test would run this many times and check the average complexity.
        val easyProblem = problemGenerator.generate(difficulty = 2)
        val hardProblem = problemGenerator.generate(difficulty = 6)

        assertNotNull("Generated easy problem should not be null", easyProblem)
        if (easyProblem == null) return // Or fail(), but returning stops the test here.
        assertNotNull("Generated hard problem should not be null", hardProblem)
        if (hardProblem == null) return // Or fail(), but returning stops the test here.

        // This property is set inside the generator based on the number of proof steps (graph nodes),
        // which is a much more reliable measure of the problem's intended difficulty than
        // counting the symbols in the premises.
        val easyComplexity = easyProblem.difficulty
        val hardComplexity = hardProblem.difficulty

        println("Easy problem complexity score: $easyComplexity")
        println("Hard problem complexity score: $hardComplexity")

        // We can't guarantee hard > easy every time, but it's a useful signal.
        assertTrue(
            "A hard problem should generally be at least as complex as an easy one.",
            hardComplexity >= easyComplexity
        )
    }


    // =====================================================================================
    // STRATEGY 2: TESTING DETERMINISTIC COMPONENTS
    // We can write precise unit tests for the helper functions that have no randomness.
    // =====================================================================================

    @Test
    fun `getAtomicAssertions works for complex formulas`() {
        val formula1 = createFormula("(~p & q) | r")
        val assertions1 = formula1.getAtomicAssertions()
        assertTrue(assertions1.size == 3)
        assertTrue(assertions1.contains(createFormula("~p")))
        assertTrue(assertions1.contains(createFormula("q")))
        assertTrue(assertions1.contains(createFormula("r")))

        val formula2 = createFormula("~(p & q)")
        val assertions2 = formula2.getAtomicAssertions()
        assertTrue(assertions2.size == 2)
        assertTrue(assertions2.contains(createFormula("p")))
        assertTrue(assertions2.contains(createFormula("q")))
    }

    @Test
    fun `VarLists useAtomicAssertion prevents contradictions`() {
        val vars = VarLists.create()

        // Use 'p' successfully
        assertNotNull(vars.useAtomicAssertion(createFormula("p")))
        assertTrue(vars.usedVars.contains(createFormula("p")))
        assertFalse(vars.availableVars.contains(createFormula("p")))

        // Try to use '~p', which should fail
        assertNull(vars.useAtomicAssertion(createFormula("~p")))

        // Re-using 'p' should succeed
        assertNotNull(vars.useAtomicAssertion(createFormula("p")))
    }
}

