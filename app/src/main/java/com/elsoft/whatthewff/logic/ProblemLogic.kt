// File: logic/ProblemLogic.kt
// This file contains the data structures and logic for creating and managing
// both curated and procedurally generated logic problems.

package com.elsoft.whatthewff.logic

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
    fun f(formulaString: String): Formula {
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
 * Generates new logic problems procedurally using a "Proof-First" approach.
 */
object ProblemGenerator {

    private val variables = AvailableTiles.variables

    /**
     * Phase 1: Builds a complete, solvable proof in memory.
     */
    private fun buildProof(difficulty: Int, availableVars: MutableList<LogicTile>): Pair<List<ProofStep>, List<Formula>> {
        val proof = mutableListOf<ProofStep>()
        val basePremises = mutableListOf<Formula>()
        val numBasePremises = (difficulty / 2).coerceIn(2, 4)

        (1..numBasePremises).forEach { i ->
            if (availableVars.isNotEmpty()) {
                val variable = availableVars.removeAt(0)
                // Randomly decide whether to add the variable or its negation.
                val premiseFormula = if (Math.random() > 0.5) {
                    Formula(listOf(variable))
                } else {
                    Formula(listOf(AvailableTiles.not, variable))
                }
                basePremises.add(premiseFormula)
                proof.add(ProofStep(premiseFormula, "Premise", emptyList()))
            }
        }

        val generationSteps = difficulty
        for (i in 1..generationSteps) {
            val knownFormulas = proof.map { it.formula }

            val applicableStrategies = ForwardRuleGenerators.allStrategies.filter { it.canApply(knownFormulas) }
            if (applicableStrategies.isEmpty()) break

            val strategy = applicableStrategies.random()
            val newStep = strategy.generate(knownFormulas)

            if (newStep != null && !knownFormulas.contains(newStep.formula)) {
                proof.add(newStep)
            }
        }
        return Pair(proof, basePremises)
    }

    /**
     * Phase 2: Selects which lines from the full proof to give to the user as premises.
     */
    private fun selectPremises(fullProof: List<ProofStep>, basePremises: List<Formula>, difficulty: Int): List<Formula> {
        if (fullProof.isEmpty()) return emptyList()

        val derivedSteps = fullProof.filter { it.justification != "Premise" }
        val stepsToHide = difficulty.coerceAtMost(derivedSteps.size)
        val derivedLinesToKeepCount = derivedSteps.size - stepsToHide

        val finalPremises = basePremises.toMutableList()
        if (derivedLinesToKeepCount > 0) {
            finalPremises.addAll(derivedSteps.take(derivedLinesToKeepCount).map { it.formula })
        }

        return finalPremises
    }

    fun generate(difficulty: Int): Problem {
        val availableVars = variables.shuffled().toMutableList()

        val (fullProof, basePremises) = buildProof(difficulty, availableVars)
        if (fullProof.isEmpty()) {
            return Problem("fallback",
                           "Error", listOf(Formula(listOf(AvailableTiles.p))),
                           Formula(listOf(AvailableTiles.p)),
                           1)
        }

        val finalConclusion = fullProof.last().formula
        val premises = selectPremises(fullProof, basePremises, difficulty)

        return Problem(
            id = "gen_${System.currentTimeMillis()}",
            name = "Generated Problem (Lvl $difficulty)",
            premises = premises.distinct().shuffled(),
            conclusion = finalConclusion,
            difficulty = difficulty
        )
    }
}
