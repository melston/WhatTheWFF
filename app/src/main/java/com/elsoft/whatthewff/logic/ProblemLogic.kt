// File: logic/ProblemLogic.kt
// This file contains the data structures and logic for creating and managing
// both curated and procedurally generated logic problems.

package com.elsoft.whatthewff.logic

/**
 * Represents a single logic problem, containing the initial premises,
 * the goal conclusion, and some metadata.
 *
 * @property id A unique identifier for the problem.
 * @property name A user-friendly name for the problem.
 * @property premises The list of formulas given at the start of the proof.
 * @property conclusion The goal formula that the user must derive.
 * @property difficulty A rating from 1 (easiest) upwards.
 */
data class Problem(
    val id: String,
    val name: String,
    val premises: List<Formula>,
    val conclusion: Formula,
    val difficulty: Int
)

/**
 * Contains curated, hard-coded lists of problems for a structured learning
 * experience or a "campaign" mode.
 */
object ProblemSets {

    // Helper to create formulas from strings, reducing boilerplate.
    // NOTE: This is a simple helper and assumes valid input.
    // In a real app, you might want more robust error handling here.
    private fun f(formulaString: String): Formula {
        val tiles = formulaString.mapNotNull { char ->
            AvailableTiles.allTiles.find { it.symbol == char.toString() }
        }
        return Formula(tiles)
    }

    val chapter1_ModusPonens = listOf(
        Problem(
            id = "1-1",
            name = "Simple Modus Ponens",
            premises = listOf(f("(p→q)"), f("p")),
            conclusion = f("q"),
            difficulty = 1
        ),
        Problem(
            id = "1-2",
            name = "Modus Ponens with Negation",
            premises = listOf(f("(¬r→s)"), f("¬r")),
            conclusion = f("s"),
            difficulty = 2
        )
    )

    val chapter2_ModusTollens = listOf(
        Problem(
            id = "2-1",
            name = "Simple Modus Tollens",
            premises = listOf(f("(p→q)"), f("¬q")),
            conclusion = f("¬p"),
            difficulty = 2
        )
    )
}

/**
 * Generates new logic problems procedurally by working backward from a conclusion.
 */
object ProblemGenerator {

    /**
     * Generates a new problem of a given difficulty.
     *
     * @param difficulty The desired difficulty, controlling the number of backward steps.
     * @return A new Problem object.
     */
    fun generate(difficulty: Int): Problem {
        // Start with a simple conclusion.
        var conclusion = Formula(listOf(AvailableTiles.q))
        var premises = mutableListOf<Formula>()

        // Apply rules in reverse for each level of difficulty.
        for (i in 1..difficulty) {
            // For now, we'll just use Modus Ponens in reverse.
            // This can be expanded with more rules.
            val newPremise1 = Formula(listOf(AvailableTiles.p, AvailableTiles.implies, conclusion.tiles.first()))
            val newPremise2 = Formula(listOf(AvailableTiles.p))

            premises.add(newPremise1)
            conclusion = newPremise2 // The new goal is to prove the antecedent.
        }

        // The final step is to add the last conclusion as a premise
        premises.add(conclusion)

        // The final conclusion is the original 'q'
        val finalConclusion = Formula(listOf(AvailableTiles.q))

        return Problem(
            id = "gen_${System.currentTimeMillis()}",
            name = "Generated Problem",
            premises = premises.distinct(), // Ensure no duplicate premises
            conclusion = finalConclusion,
            difficulty = difficulty
        )
    }
}
