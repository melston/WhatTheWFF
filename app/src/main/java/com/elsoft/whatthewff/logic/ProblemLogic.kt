// File: logic/ProblemLogic.kt
// This file contains the data structures and logic for creating and managing
// both curated and procedurally generated logic problems.

package com.elsoft.whatthewff.logic

import android.os.Build
import androidx.annotation.RequiresApi

//import com.elsoft.whatthewff.logic.AvailableTiles.implies
//import com.elsoft.whatthewff.logic.AvailableTiles.or
//import com.elsoft.whatthewff.logic.AvailableTiles.and
//import com.elsoft.whatthewff.logic.AvailableTiles.not
//import com.elsoft.whatthewff.logic.AvailableTiles.leftParen
//import com.elsoft.whatthewff.logic.AvailableTiles.rightParen
//import com.elsoft.whatthewff.logic.AvailableTiles.iff

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
            difficulty = 2
        )
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


//    private fun forwardConjunction(f1: Formula, f2: Formula) = Formula(listOf(leftParen) + f1.tiles + listOf(and) + f2.tiles + listOf(rightParen))
//    private fun forwardDisjunction(f1: Formula, f2: Formula) = Formula(listOf(leftParen) + f1.tiles + listOf(or) + f2.tiles + listOf(rightParen))
//    private fun forwardImplication(f1: Formula, f2: Formula) = Formula(listOf(leftParen) + f1.tiles + listOf(implies) + f2.tiles + listOf(rightParen))

//    private fun forwardNegation(f1: Formula) = Formula(listOf(not) + f1.tiles)
//
//    private val forwardBinaryStrategies = listOf(::forwardConjunction, ::forwardDisjunction, ::forwardImplication)

    private val variables = AvailableTiles.variables

    private val backwardStrategies = listOf(
        RuleGenerators.modusPonens,
        RuleGenerators.modusTollens,
        RuleGenerators.conjunction,
        RuleGenerators.hypotheticalSyllogism,
        RuleGenerators.disjunctiveSyllogism
    )

    /**
     * Generates a new problem of a given difficulty.
     *
     * The main generate function is responsible for the structure of the proof.
     * It decides the chain of variables first (e.g., the goal is r, which will be
     * derived from q, which will be derived from p).
     *
     * The generator takes a goal, finds a strategy to break it down, and addis the new
     * sub-goals into the list of goals to solve.
     *
     * Each strategy is responsible for consuming variables from the single pool of
     * available variables (availableVars).  If it can't generate a step because the
     * pool is empty, it simply fails and the generator tries a different strategy.
     *
     * TODO: This generator will never produce a compound WFF as a final goal.
     * This needs to be fixed in the future.  Gemini suggests the following:
     *
     * # Phase 1: Goal Construction
     *
     * The sole purpose of this phase is to create an interesting finalConclusion that is an
     * implication. We aren't building the whole proof yet, just the target.
     *
     * 1. Start with a few simple formulas (e.g., p, q, r).
     *
     * 2. Use our forward-building rules to combine them into a more complex
     *    formula (e.g., (p ∧ q)).
     *
     * 3. Finally, use the forwardImplication rule to create the final goal,
     *    for example: (r → (p ∧ q)).
     *
     * Now we have a goal that requires the user to use Implication Introduction.
     *
     * # Phase 2: Premise Deconstruction
     *
     * This phase uses the exact same queue-based generator we have now, but it starts with
     * the complex implication goal we just created.
     *
     * 1. The goalsToSolve queue is initialized with our goal: [ (r → (p ∧ q)) ].
     *
     * 2. The generator pulls this goal. The hypotheticalSyllogism strategy (or a new, more
     *    specific "Reverse Implication Introduction" strategy) would apply.
     *
     * 3. This strategy would say, "To prove (r → (p ∧ q)), the user needs to assume r and
     *    derive (p ∧ q)." It would then create a new sub-goal of (p ∧ q).
     *
     * 4. The generator would then continue to work backward from (p ∧ q), breaking it down
     *    further until the difficulty budget is spent.
     *
     * This two-phase approach is the key to creating problems that require the full range of
     * logical rules. It ensures that the generator can create complex, interesting goals and
     * then build a solvable set of premises for them.
     *
     * @param difficulty The desired difficulty, controlling the number of backward steps.
     * @return A new Problem object.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun generate(difficulty: Int): Problem {
        val availableVars = variables.shuffled().toMutableList()
        var stepsRemaining = difficulty

        // Start with a simple variable as the final goal.
        val finalConclusion = Formula(listOf(availableVars.removeFirst()))

        val goalsToSolve = mutableListOf(finalConclusion)
        val premises = mutableListOf<Formula>()

        while (goalsToSolve.isNotEmpty() && stepsRemaining > 0) {
            val currentGoal = goalsToSolve.removeFirst()

            var stepTaken = false
            val successfulStep =
                backwardStrategies.shuffled()
                    .asSequence()
                    .filter { it.canApply(currentGoal) }
                    .mapNotNull { it.generate(currentGoal, availableVars) }
                    .firstOrNull()

            if (successfulStep != null) {
                premises.addAll(successfulStep.newPremises)
                goalsToSolve.addAll(successfulStep.nextGoals)
                stepsRemaining--
                stepTaken = true
            }

            // If no strategy could be applied, add the goal as a premise.
            if (!stepTaken) {
                premises.add(currentGoal)
            }
        }

        // Any remaining goals also become premises.
        premises.addAll(goalsToSolve)

        return Problem(
            id = "gen_${System.currentTimeMillis()}",
            name = "Generated Problem (Lvl $difficulty)",
            premises = premises.distinct().shuffled(),
            conclusion = finalConclusion,
            difficulty = difficulty
        )
    }
}
