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
    /**
     * A simple DSL/parser function that converts a readable string into a Formula object.
     * This allows for clear and concise problem definitions.
     * Example: f("(p→q)")
     */
    private fun f(formulaString: String): Formula {
        // Create a quick lookup map for mapping characters to their LogicTile objects.
        val tileMap = AvailableTiles.allTiles.associateBy { it.symbol }

        // Map each character in the string to its corresponding tile.
        // If a character isn't a valid symbol (like whitespace), it's ignored.
        val tiles = formulaString.mapNotNull { char ->
            tileMap[char.toString()]
        }
        return Formula(tiles)
    }

    // MODIFIED: All problems now use the highly readable string-based DSL.
    val chapter1_ModusPonens = listOf(
        Problem(
            id = "1-1",
            name = "Simple Modus Ponens",
            premises = listOf(f("(p→q)"),
                              f("p")),
            conclusion = f("q"),
            difficulty = 1),
        Problem(
            id = "1-2",
            name = "Modus Ponens with Negation",
            premises =  listOf(f("(¬r→s)"),
                               f("¬r")),
            conclusion = f("s"),
            difficulty = 2)
    )

    val chapter2_ModusTollens = listOf(
        Problem(
            id = "2-1",
            name = "Simple Modus Tollens",
            premises = listOf(f("(p→q)"),
                              f("q")),
            conclusion = f("p"),
            difficulty = 1
        ),
        Problem(
            id = "2-2",
            name = "Modus Tollens with Negation",
            premises = listOf(f("(p→q)"),
                              f("¬q")),
            conclusion = f("¬p"),
            difficulty = 2)
    )

    // Example of a more complex problem definition using the DSL.
    val chapter3_MixedRules = listOf(
        Problem(
            id = "3-1",
            name = "Multi-Step Proof",
            premises = listOf(f("(p→q)"),
                              f("(q→r)"),
                              f("p")),
            conclusion = f("r"),
            difficulty = 3
        )
    )
}

/**
 * Generates new logic problems procedurally by working backward from a conclusion.
 */
object ProblemGenerator {

    private val variables = AvailableTiles.variables

    /**
     * Generates a new problem of a given difficulty.
     * @param difficulty The desired difficulty, controlling the number of backward steps.
     * @return A new Problem object.
     */
    fun generate(difficulty: Int): Problem {
        // Use a shuffled list of variables to ensure a clean, non-cyclical chain.
        val varsToUse = variables.shuffled()
        // Cap difficulty to prevent running out of variables.
        val cappedDifficulty = difficulty.coerceAtMost(varsToUse.size)

        val finalConclusion = Formula(listOf(varsToUse[0]))
        var currentGoal = finalConclusion
        val premises = mutableListOf<Formula>()

        // Build the chain of implications backward.
        for (i in 1..cappedDifficulty) {
            val antecedent = Formula(listOf(varsToUse[i]))
            val consequent = currentGoal

            // Build the implication premise: (antecedent → consequent)
            val implicationTiles = mutableListOf<LogicTile>()
            implicationTiles.add(AvailableTiles.leftParen)
            implicationTiles.addAll(antecedent.tiles)
            implicationTiles.add(AvailableTiles.implies)
            implicationTiles.addAll(consequent.tiles)
            implicationTiles.add(AvailableTiles.rightParen)

            premises.add(Formula(implicationTiles))

            // The new goal is the antecedent of the implication we just created.
            currentGoal = antecedent
        }

        // The last "goal" becomes the starting premise that unlocks the whole chain.
        premises.add(currentGoal)

        return Problem(
            id = "gen_${System.currentTimeMillis()}",
            name = "Generated Problem (Lvl $cappedDifficulty)",
            premises = premises.shuffled(), // Shuffle for variety
            conclusion = finalConclusion,
            difficulty = cappedDifficulty
        )
    }
}
