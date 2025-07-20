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

    private val variables = AvailableTiles.variables

    private val forwardStrategies = ForwardRuleGenerators.allStrategies

    private val structuralStrategies = RuleGenerators.structuralStrategies
    private val creativeStrategies = RuleGenerators.creativeStrategies

    /**
     * Executes the first phase of problem generation: constructing a complex target conclusion.
     *
     * This phase aims to create an interesting final conclusion, often an implication,
     * which might require the user to employ rules like Implication Introduction during proof.
     * It works by starting with simple formulas (variables) and iteratively applying
     * forward-generating rules to combine them into more complex structures.
     *
     * The number of construction steps is related to the overall desired difficulty, ensuring
     * the final conclusion has a certain level of complexity before the deconstruction phase begins.
     *
     * Available variables are consumed from the `availableVars` list as needed.
     * The function ensures that at least one construction step is attempted if difficulty allows.
     *
     * @param stepsBudget The desired number of construction steps for the target conclusion.
     *                   Higher numbers generally leads to a more complex conclusion.
     * @param vars A mutable list of [LogicTile]s representing variables that can be
     *                      used in the construction. Variables used are removed from this list.
     * @return The [Formula] representing the complex target conclusion generated in this phase.
     *         This formula will then be used as the starting point for the backward
     *         deconstruction phase (Phase 2) to generate the problem's premises.
     */
    private fun forwardPhase(
        stepsBudget: Int,
        vars: MutableList<LogicTile>
    ): Formula {
        // Phase 1: Build a complex goal using the new forward strategies.
        val knownFormulas = mutableSetOf<Formula>()
        val constructionSteps = stepsBudget

        // Start with a base of simple variables.
        for (i in 0..constructionSteps) {
            if (vars.isNotEmpty()) {
                knownFormulas.add(Formula(listOf(vars.removeAt(0))))
            }
        }

        var lastForwardStrategy: ForwardGenerationStrategy? = null
        for (i in 0 until constructionSteps) {
            val possibleStrategies = forwardStrategies.filter { it != lastForwardStrategy }
            if (possibleStrategies.isEmpty()) break

            val strategy = possibleStrategies.random()

            val requiredFormulas = if (strategy == ForwardRuleGenerators.negation) 1 else 2
            if (knownFormulas.size < requiredFormulas) continue

            val sourceFormulas = knownFormulas.shuffled().take(requiredFormulas)
            val newFormula = strategy(sourceFormulas)
            if (newFormula != null) {
                knownFormulas.add(newFormula)
                lastForwardStrategy = strategy
            }
        }
        return knownFormulas.last()
    }

    /**
     * Executes the second phase of problem generation: deconstructing the target conclusion into premises.
     *
     * This phase starts with the `finalConclusion` (generated by `forwardPhase` or provided)
     * and works backward, applying inverse rule strategies to break it down into simpler
     * formulas. These simpler formulas, along with any unresolvable sub-goals, form
     * the premises of the generated problem.
     *
     * The process continues until the `stepsRemaining` (difficulty budget) is exhausted
     * or no more backward rules can be applied.
     *
     * @param finalConclusion The [Formula] that the user will be tasked to prove. This is the
     *                        starting point for the backward deconstruction.
     * @param stepsBudget An integer representing the initial steps budget,
     *                       which corresponds to the number of backward
     *                       decomposition steps to perform.
     * @param vars A mutable list of [LogicTile]s representing variables that can be
     *                      used by strategies if they need to introduce new variables
     *                      (e.g., for rules like Existential Introduction backwards).
     *                      Variables used might be consumed from this list.
     * @return A mutable list of [Formula] objects representing the premises generated
     *         for the problem. These are the formulas the user will start with.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun backwardPhase(
        finalConclusion: Formula,
        stepsBudget: Int,
        vars: MutableList<LogicTile>
    ): MutableList<Formula> {
        var stepsRemaining = stepsBudget
        val goalsToSolve = mutableListOf(finalConclusion)
        val premises = mutableListOf<Formula>()

        while (goalsToSolve.isNotEmpty() && stepsRemaining > 0) {
            val currentGoal = goalsToSolve.removeFirst()

            var stepTaken = false

            // Prioritize structural strategies that match the goal's shape.
            // These strategies deconstruct a complex goal based on its main
            // operator (e.g., Conjunction, Hypothetical Syllogism). They are
            // the most logical choice when a goal is complex
            for (strategy in structuralStrategies.shuffled()) {
                if (strategy.canApply(currentGoal)) {
                    val step = strategy.generate(currentGoal, vars)
                    if (step != null) {
                        premises.addAll(step.newPremises)
                        goalsToSolve.addAll(step.nextGoals)
                        stepsRemaining--
                        stepTaken = true
                        break
                    }
                }
            }

            // If no structural strategy matched, fall back to the creative strategies
            // These are rules that add a new layer of complexity to a simpler
            // goal (e.g., Modus Ponens, Disjunctive Syllogism). They are best used when
            // the goal is a simple variable that can't be deconstructed further.
            if (!stepTaken) {
                for (strategy in creativeStrategies.shuffled()) {
                    val step = strategy.generate(currentGoal, vars)
                    if (step != null) {
                        premises.addAll(step.newPremises)
                        goalsToSolve.addAll(step.nextGoals)
                        stepsRemaining--
                        stepTaken = true
                        break
                    }
                }
            }

            if (!stepTaken) {
                premises.add(currentGoal)
            }
        }

        premises.addAll(goalsToSolve)
        return premises
    }

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
        val allVars = variables.shuffled().toMutableList()

        // The number of steps in the first phase (goal creation phase) of problem generation.
        val constructionSteps = (difficulty - 1).coerceAtLeast(1)

        // The number of steps to use in the second phase of problem generation.
        // This needs to be larger than the difficulty variable as it would otherwise sometimes
        // stop early with complex premises and a simple problem on the hard level.
        val backwardSteps = (difficulty *1.5).toInt().coerceAtLeast(difficulty)

        val finalConclusion = forwardPhase(constructionSteps, allVars)
        val premises = backwardPhase(finalConclusion, backwardSteps, allVars)

        return Problem(
            id = "gen_${System.currentTimeMillis()}",
            name = "Generated Problem (Lvl $difficulty)",
            premises = premises.distinct().shuffled(),
            conclusion = finalConclusion,
            difficulty = difficulty
        )
    }
}
