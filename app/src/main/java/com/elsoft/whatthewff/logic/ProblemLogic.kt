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

    private fun <T> List<T>.weightedRandomOrNull(getWeight: (T) -> Double): T? {
        if (this.isEmpty()) return null
        val totalWeight = this.sumOf(getWeight)
        if (totalWeight <= 0) return this.randomOrNull() // Fallback if all weights are zero

        var random = Math.random() * totalWeight
        for (item in this) {
            random -= getWeight(item)
            if (random <= 0) return item
        }
        return this.last() // Fallback in case of rounding errors
    }

    /**
     * Phase 1: Builds a complete, solvable proof in memory.
     */
    private fun buildProof(generationSteps: Int, availableVars: MutableList<LogicTile>): Pair<List<Formula>, Formula> {
        // --- Stage 1: Create Atomic Components ---
        val numBaseAtoms = (generationSteps / 2).coerceAtLeast(2)
        val atoms = (1..numBaseAtoms).mapNotNull {
            if (availableVars.isNotEmpty()) {
                val variable = availableVars.removeAt(0)
                if (Math.random() > 0.5) Formula(listOf(variable))
                else ForwardRuleGenerators.fNeg(Formula(listOf(variable)))
            } else null
        }.toMutableList()

        // --- Stage 2: Compose Complex Base Premises ---
        val numComplexPremises = (generationSteps / 2).coerceAtLeast(1)
        val basePremises = mutableListOf<Formula>()
        if (atoms.isNotEmpty()) basePremises.add(atoms.removeAt(0))
        if (atoms.isNotEmpty()) basePremises.add(atoms.removeAt(0))

        for (i in 0 until numComplexPremises) {
            if (atoms.size < 2) break
            val strategy = ForwardRuleGenerators.simpleCompositionStrategies.random()
            val sources = atoms.shuffled().take(2)
            atoms.remove(sources[0])
            atoms.remove(sources[1])
            val newComplexPremise = strategy.generate(sources)?.formula
            if (newComplexPremise != null) {
                basePremises.add(newComplexPremise)
            }
        }
        basePremises.addAll(atoms)

        // --- Stage 3: Build the Proof ---
        val knownFormulas = basePremises.toMutableList()
        val proofSteps = mutableListOf<ProofStep>()
        val ruleWeights = ForwardRuleGenerators.allStrategies.associateWith { it.weight }.toMutableMap()

        for (i in 0 until generationSteps) {
            val applicableRules = ForwardRuleGenerators.allStrategies.filter { it.canApply(knownFormulas) }
            if (applicableRules.isEmpty()) break

            val strategy = applicableRules.weightedRandomOrNull { ruleWeights[it]!! }
            if (strategy != null) {
                val newStep = strategy.generate(knownFormulas)
                if (newStep != null && !knownFormulas.contains(newStep.formula)) {
                    proofSteps.add(newStep)
                    knownFormulas.add(newStep.formula)

                    // Temporarily reduce the weight of the used rule to encourage variety
                    ruleWeights[strategy] = ruleWeights[strategy]!! * 0.5
                }
            }

            // Slightly recharge all weights to prevent any rule from becoming permanently unlikely
            ruleWeights.keys.forEach { rule ->
                ruleWeights[rule] = (ruleWeights[rule]!! + 0.1).coerceAtMost(rule.weight)
            }
        }

        val finalConclusion = proofSteps.lastOrNull()?.formula ?: basePremises.last()
        return Pair(basePremises, finalConclusion)
    }

    /**
     * Phase 2: Selects which lines from the full proof to give to the user as premises.
     */
    private fun selectPremises(basePremises: List<Formula>, conclusion: Formula, difficulty: Int): List<Formula> {
        // This function can be made smarter in the future to "prune" the proof tree.
        // For now, it returns all base premises needed.
        return basePremises
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun generate(difficulty: Int): Problem {
        val allVars = variables.shuffled().toMutableList()
        val generationSteps = (difficulty * 1.5).toInt().coerceAtLeast(2)

        val (basePremises, finalConclusion) = buildProof(generationSteps, allVars)
        val selectedPremises = selectPremises(basePremises, finalConclusion, difficulty)

        return Problem(
            id = "gen_${System.currentTimeMillis()}",
            name = "Generated Problem (Lvl $difficulty)",
            premises = selectedPremises.distinct().shuffled(),
            conclusion = finalConclusion,
            difficulty = difficulty
        )
    }
}
