// File: logic/ProblemLogic.kt
// This file contains the data structures and logic for creating and managing
// both curated and procedurally generated logic problems.

package com.elsoft.whatthewff.logic

import android.os.Build
import androidx.annotation.RequiresApi

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
    public fun f(formulaString: String): Formula {
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
    private val strategies = listOf(
        RuleGenerators.modusPonens,
        RuleGenerators.modusTollens
    )

    /**
     * Generates a new problem of a given difficulty.
     *
     * The main generate function is responsible for the structure of the proof.
     * It decides the chain of variables first (e.g., the goal is r, which will be
     * derived from q, which will be derived from p).
     *
     * @param difficulty The desired difficulty, controlling the number of backward steps.
     * @return A new Problem object.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun generate(difficulty: Int): Problem {
        val varsToUse = variables.shuffled().toMutableList()
        val cappedDifficulty = difficulty.coerceAtMost(varsToUse.size - 1)

        // Start with a goal that can be positive or negative.
        var firstVar = varsToUse.removeFirst()
        var currentGoal = if (Math.random() > 0.5) {
            Formula(listOf(firstVar))
        } else {
            Formula(listOf(AvailableTiles.not, firstVar))
        }

        val finalConclusion = currentGoal
        val premises = mutableListOf<Formula>()

        for (i in 0 until cappedDifficulty) {
            if (varsToUse.isEmpty()) break

            // Find all strategies that can logically apply to the current goal.
            val applicableStrategies = strategies.filter { it.canApply(currentGoal) }
            val strategy = applicableStrategies.randomOrNull() ?: RuleGenerators.modusPonens

            val step = strategy.generate(currentGoal, varsToUse)
            if (step != null) {
                premises.addAll(step.newPremises)
                currentGoal = step.nextGoal
                // Remove the variable(s) used by the step to prevent reuse.
                val nextGoalVar = WffParser.parse(step.nextGoal)?.let {
                    if (it is FormulaNode.VariableNode) it.tile else if (it is FormulaNode.UnaryOpNode && it.child is FormulaNode.VariableNode) it.child.tile else null
                }
                nextGoalVar?.let { varsToUse.remove(it) }
            } else {
                break
            }
        }

        premises.add(currentGoal)

        return Problem(
            id = "gen_${System.currentTimeMillis()}",
            name = "Generated Problem (Lvl $cappedDifficulty)",
            premises = premises.shuffled(),
            conclusion = finalConclusion,
            difficulty = cappedDifficulty
        )
    }
}
