// File: /home/mark/AndroidStudioProjects/WhattheWFF/app/src/main/java/com/elsoft/whatthewff/logic/PlannedProblemGenerator.kt

package com.elsoft.whatthewff.logic

import com.elsoft.whatthewff.logic.RuleGenerators.fAnd
import com.elsoft.whatthewff.logic.RuleGenerators.fImplies
import com.elsoft.whatthewff.logic.RuleGenerators.fNeg
import com.elsoft.whatthewff.logic.RuleGenerators.fOr
import com.elsoft.whatthewff.logic.RuleGenerators.treeToFormula
import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DirectedAcyclicGraph
import java.util.Collections
import kotlin.collections.shuffle

private val debug = true
private fun log(depth: Int, message: String) {
    if (debug) {
        val indent = "  ".repeat(depth)
        println("$indent$message")
    }
}

// --- Data Structures for the Plan ---

/**
 * A constraint that defines the required logical shape of a formula at a node.
 */
sealed class FormulaShape {
    object Any : FormulaShape()
    object IsImplication : FormulaShape()
    object IsConjunction : FormulaShape()
    object IsDisjunction : FormulaShape()
    object IsNegation : FormulaShape()
    object IsAtomic : FormulaShape()
}

/**
 * Defines the premise shapes required for each rule. This is the blueprint
 * for generating the proof plan graph.
 */
private val rulePremiseShapes = mapOf(
    InferenceRule.ABSORPTION to listOf(FormulaShape.IsImplication),
    InferenceRule.ADDITION to listOf(FormulaShape.Any),
    InferenceRule.ASSUMPTION to listOf(),
    InferenceRule.CONJUNCTION to listOf(FormulaShape.Any, FormulaShape.Any),
    InferenceRule.CONSTRUCTIVE_DILEMMA to listOf(FormulaShape.IsConjunction, FormulaShape.IsDisjunction),
    InferenceRule.DISJUNCTIVE_SYLLOGISM to listOf(FormulaShape.IsDisjunction, FormulaShape.IsNegation),
    InferenceRule.HYPOTHETICAL_SYLLOGISM to listOf(FormulaShape.IsImplication, FormulaShape.IsImplication),
    InferenceRule.MODUS_PONENS to listOf(FormulaShape.IsImplication, FormulaShape.IsAtomic),
    InferenceRule.MODUS_TOLLENS to listOf(FormulaShape.IsImplication, FormulaShape.IsNegation),
    InferenceRule.SIMPLIFICATION to listOf(FormulaShape.IsConjunction),
)

/**
 * Defines the shape of the conclusion produced by each rule.
 */
private val ruleConclusionShapes = mapOf(
    InferenceRule.ASSUMPTION to FormulaShape.Any,
    InferenceRule.MODUS_PONENS to FormulaShape.Any,
    InferenceRule.MODUS_TOLLENS to FormulaShape.IsNegation,
    InferenceRule.HYPOTHETICAL_SYLLOGISM to FormulaShape.IsImplication,
    InferenceRule.DISJUNCTIVE_SYLLOGISM to FormulaShape.Any,
    InferenceRule.CONSTRUCTIVE_DILEMMA to FormulaShape.IsDisjunction,
    InferenceRule.ABSORPTION to FormulaShape.IsImplication,
    InferenceRule.SIMPLIFICATION to FormulaShape.Any,
    InferenceRule.CONJUNCTION to FormulaShape.IsConjunction,
    InferenceRule.ADDITION to FormulaShape.IsDisjunction
)

private data class ProofPlan(
    val graph: DirectedAcyclicGraph<ProofNode, DefaultEdge>,
    val finalConclusionNode: ProofNode,
    val initialPremiseNodes: Set<ProofNode>
)

/**
 * Represents a node in our proof graph. It's an inner class because it's tightly
 * coupled with the generator's state and logic.
 */
data class ProofNode(
    val id: String,
    var rule: InferenceRule? = null,
    var conclusionConstraint: FormulaShape = FormulaShape.Any,
    var selectedApplication: Application? = null,
    val possibleApplications: MutableList<Application> = mutableListOf(),
    var arePossibleApplicationsGenerated: Boolean = false
)
{
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProofNode
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

class PlannedProblemGenerator {

    private val maxGenerationAttempts = 50 // To prevent infinite loops
    private var nextId = 0
    private fun generateId(): String = "ID_${nextId++}"

    fun generate(difficulty: Int): Problem? {
        nextId = 0 // Reset for each new generation
        repeat(maxGenerationAttempts) {
            val attempt = it
            log(0, "*** Generating problem attempt $it")
            val plan = generatePlan(difficulty)

            if (debug) printTree(plan)

            val problem = generateProblemFromPlan(plan)

            if (problem == null) {
                log(0, "*** Generated null problem on attempt $it")
            } else {
                if (problem.premises.isNotEmpty()) {
                    val consistencyCheck = problem.premises.map {
                        it.getAtomicAssertions()
                    }
                        .flatten()
                        .distinct()
                    val contradiction = consistencyCheck.any { p1 ->
                        val negP1 = fNeg(p1)
                        consistencyCheck.any { it.normalize() == negP1.normalize() }
                    }
                    if (!contradiction) {
                        // Final check to make sure conclusion isn't in the premises
                        if (!problem.premises.any {
                                compareFormulas(it, problem.conclusion)
                            }) {
                            log(0, "*** Found a valid problem on attempt $attempt")
                            return problem
                        }
                        log(0, "*** Found a problem with a premise equal to the conclusion on attempt $attempt")
                    }
                    log(0, "*** Found a contradiction while generating problem on attempt $attempt")
                }
                log(0, "*** Found a problem with no premises on attempt $attempt")
            }
        }
        log(0, "*** Failed to generate a valid problem after $maxGenerationAttempts attempts.")
        return null // Failed to generate a valid problem
    }

    private fun generatePlan(difficulty: Int): ProofPlan {
        val graph = DirectedAcyclicGraph<ProofNode, DefaultEdge>(DefaultEdge::class.java)
        val premiseNodes = mutableSetOf<ProofNode>()

        val finalConclusionNode = ProofNode(id = generateId())
        graph.addVertex(finalConclusionNode)

        val goalsToSolve = mutableListOf(finalConclusionNode)
        var stepsBudget = difficulty.coerceAtLeast(1)
        val branchingChance = 0.3

        while (goalsToSolve.isNotEmpty()) {
            if (stepsBudget <= 0) {
                // If we are out of budget, this MUST be a leaf node (an assumption)
                goalsToSolve.forEach { it.rule = InferenceRule.ASSUMPTION }
                premiseNodes.addAll(goalsToSolve)
                goalsToSolve.clear() // Ensure the loop terminates
                continue
            }

            val currentNode = goalsToSolve.removeAt(0)
            stepsBudget-- // Decrement budget for processing this node.

            val applicableRules = InferenceRule.entries.filter { rule ->
                // ***NEVER RANDOMLY CHOOSE ASSUMPTION***
                // It is only a fallback if this list ends up empty.
                if (rule == InferenceRule.ASSUMPTION) return@filter false

                val conclusionShape = ruleConclusionShapes[rule]!!
                val constraint = currentNode.conclusionConstraint
                // A rule is applicable if its conclusion shape matches the constraint,
                // or if the constraint is 'Any'.
                val shapeOk = when (constraint) {
                    FormulaShape.Any -> true
                    else -> conclusionShape == constraint ||
                            conclusionShape == FormulaShape.Any
                }
                val budgetOk = (rulePremiseShapes[rule]?.size ?: 0) <= stepsBudget
                shapeOk && budgetOk
            }

            val rule = applicableRules.ifEmpty { null }?.random()

            if (rule == null) {
                currentNode.rule = InferenceRule.ASSUMPTION
                premiseNodes.add(currentNode)
                continue
            }

            currentNode.rule = rule

            val premiseShapes = rulePremiseShapes[rule]!!
            val newChildBlueprints = premiseShapes.map { shape ->
                // The child's conclusion MUST satisfy the parent's premise requirement.
                ProofNode(id = generateId(), conclusionConstraint = shape)
            }

            newChildBlueprints.forEach { childBlueprint ->
                graph.addVertex(childBlueprint)
                graph.addEdge(childBlueprint, currentNode)
            }

            if (newChildBlueprints.isNotEmpty()) {
                val premisesToShuffle = newChildBlueprints.toMutableList()
                premisesToShuffle.shuffle()

                if (Math.random() < branchingChance && newChildBlueprints.size > 1) {
                    goalsToSolve.addAll(premisesToShuffle)
                } else {
                    goalsToSolve.add(0, premisesToShuffle.first())
                    val remainingChildrenAsPremises = premisesToShuffle.drop(1)
                    premiseNodes.addAll(remainingChildrenAsPremises)
                }
            }
        }

        premiseNodes.addAll(goalsToSolve)
        val finalPremises = premiseNodes.filter { node ->
            graph.containsVertex(node) && graph.inDegreeOf(node) == 0
        }.toSet()

        return ProofPlan(graph, finalConclusionNode, finalPremises)
    }

    private fun generateProblemFromPlan(plan: ProofPlan): Problem? {
        val vars = VarLists.create()

        // *** Use a reversed (post-order) traversal ***
        val traversal = plan.graph.reversed().iterator()
        traversal.forEach { node ->
            getPossibleApplications(node, plan.graph, vars)
        }

        val finalConclusionNode = plan.finalConclusionNode
        val possibleFinalApplications = finalConclusionNode.possibleApplications.shuffled()

        if (possibleFinalApplications.isEmpty()) {
            log(1, "*** No possible final applications found.")
            return null
        }

        for (finalApp in possibleFinalApplications) {
            val selectionSuccessful = selectApplicationPath(
                finalConclusionNode,
                finalApp.conclusion,
                plan.graph,
                vars.copy(),
                1
            )
            if (selectionSuccessful) {
                val finalConclusionFormula =
                    finalConclusionNode.selectedApplication?.conclusion
                        ?: return null
                val premises = plan.initialPremiseNodes
                    .mapNotNull { it.selectedApplication?.conclusion }
                    .distinct()
                    .sortedBy { it.toString() }

                if (premises.isEmpty() && finalConclusionFormula.isAtomicOrNegatedAtomic()) {
                    return Problem(
                        id = "gen_${System.currentTimeMillis()}",
                        name = "Generated Problem",
                        difficulty = plan.graph.vertexSet().size,
                        premises = listOf(finalConclusionFormula),
                        conclusion = finalConclusionFormula
                    )
                }

                return Problem(
                    id = "gen_${System.currentTimeMillis()}",
                    name = "Generated Problem",
                    difficulty = plan.graph.vertexSet().size,
                    premises = premises,
                    conclusion = finalConclusionFormula
                )
            }
        }
        return null
    }

    private fun getPossibleApplications(
        node: ProofNode,
        graph: DirectedAcyclicGraph<ProofNode, DefaultEdge>,
        vars: VarLists
    ): List<Application> {
        if (node.arePossibleApplicationsGenerated) return node.possibleApplications

        node.possibleApplications.clear()
        val predecessorProofNodes = Graphs.predecessorListOf(graph, node)

        if (predecessorProofNodes.isEmpty()) {
            addLeafApplications(node)
        } else {
            val ruleToApply = node.rule!!

            if (ruleToApply == InferenceRule.ADDITION) {
                // Generate a richer set of possibilities for Addition
                val child = predecessorProofNodes.first()
                val childConclusions = getPossibleApplications(child, graph, vars).map {
                    it.conclusion
                }
                childConclusions.forEach { p ->
                    // Create disjunctions with a shuffled subset of all possible variables
                    // to give the solver more options without being overwhelming.
                    VarLists.allVars.shuffled().take(5).forEach { q ->
                        if (p.normalize() != q.normalize()) {
                            node.possibleApplications.add(
                                Application(
                                    conclusion = fOr(p, q),
                                    rule = InferenceRule.ADDITION,
                                    premises = listOf(p)
                                )
                            )
                        }
                    }
                }
            } else {
                // Original logic for all other rules
                val premiseConclusionOptionsPerNode: List<List<Formula>> =
                    predecessorProofNodes.map { predNode ->
                        getPossibleApplications(predNode, graph, vars).map { app -> app.conclusion }.distinct()
                    }

                if (premiseConclusionOptionsPerNode.any { it.isEmpty() }) {
                    node.arePossibleApplicationsGenerated = true
                    return emptyList()
                }

                // Use a lazy sequence for the Cartesian product to avoid memory errors.
                val productSequence = cartesianProductSequence(premiseConclusionOptionsPerNode)

                productSequence.forEach { specificPremiseCombination ->
                    val expectedPremiseCount = rulePremiseShapes[ruleToApply]?.size
                    if (specificPremiseCombination.size == expectedPremiseCount) {
                        val conclusionsFromEngine = InferenceRuleEngine.getPossibleApplications(
                            ruleToApply,
                            specificPremiseCombination
                        )

                        val validApplications = conclusionsFromEngine.filter { app ->
                            val appPremisesNormalized = app.premises.mapNotNull {
                                it.normalize()
                            }.toSet()
                            val specificCombinationNormalized =
                                specificPremiseCombination.mapNotNull {
                                    it.normalize()
                                }.toSet()
                            appPremisesNormalized == specificCombinationNormalized
                        }
                        node.possibleApplications.addAll(validApplications)
                    }
                }
            }
        }

        node.arePossibleApplicationsGenerated = true
        return node.possibleApplications.distinct()
    }

    private fun addLeafApplications(node: ProofNode) {
        val formulas = mutableListOf<Formula>()
        when (node.conclusionConstraint) {
            is FormulaShape.IsImplication -> {
                VarLists.allVars.shuffled().take(2).let {
                    if (it.size == 2) formulas.add(fImplies(it[0], it[1]))
                }
            }
            is FormulaShape.IsConjunction -> {
                VarLists.allVars.shuffled().take(2).let {
                    if (it.size == 2) formulas.add(fAnd(it[0], it[1]))
                }
            }
            is FormulaShape.IsDisjunction -> {
                VarLists.allVars.shuffled().take(2).let {
                    if (it.size == 2) formulas.add(fOr(it[0], it[1]))
                }
            }
            is FormulaShape.IsNegation -> {
                VarLists.allVars.shuffled().firstOrNull()?.let {
                    formulas.add(fNeg(it))
                }
            }
            else -> { // Any or IsAtomic
                formulas.addAll(VarLists.allVars)
                formulas.addAll(VarLists.allVars.map { fNeg(it) })
            }
        }

        formulas.forEach { formula ->
            node.possibleApplications.add(
                Application(formula, InferenceRule.ASSUMPTION, emptyList())
            )
        }
    }

    private fun selectApplicationPath(
        node: ProofNode,
        requiredConclusion: Formula,
        graph: DirectedAcyclicGraph<ProofNode, DefaultEdge>,
        vars: VarLists,
        depth: Int = 0
    ): Boolean {
        log(depth, "-> selectApplicationPath for [${node.id}] seeking '${requiredConclusion}'")
        val suitableApplications = node.possibleApplications
            .filter { compareFormulas(it.conclusion, requiredConclusion) }
            .shuffled()

        if (suitableApplications.isEmpty()) {
            log(depth, "<- FAIL: No suitable applications found for '${requiredConclusion}'")
            return false
        }

        log(depth, "Found ${suitableApplications.size} suitable applications for '${requiredConclusion}'")

        for (potentialAppToSelect in suitableApplications) {
            log(depth, "Attempting App: ${potentialAppToSelect.rule} -> '${potentialAppToSelect.conclusion}' with premises ${potentialAppToSelect.premises}")
            node.selectedApplication = potentialAppToSelect
            val tempVars = vars.copy()

            if (potentialAppToSelect.rule == InferenceRule.ASSUMPTION) {
                // This is a leaf node. We just need to check for variable consistency.
                val atoms = potentialAppToSelect.conclusion.getAtomicAssertions()
                var consistent = true
                for (atom in atoms) {
                    if (tempVars.useAtomicAssertion(atom) == null) {
                        consistent = false
                        break
                    }
                }

                if (consistent) {
                    // Commit var changes
                    vars.availableVars = tempVars.availableVars
                    vars.usedVars = tempVars.usedVars
                    log(depth, "   Leaf node success. Using '${potentialAppToSelect.conclusion}'.")
                    log(depth, "<- SUCCESS from leaf node [${node.id}]")
                    return true
                } else {
                    log(depth, "   Leaf node FAILED (variable conflict) for '${potentialAppToSelect.conclusion}'.")
                    node.selectedApplication = null
                    continue // Try next suitable application
                }
            }

            val predecessorProofNodes = Graphs.predecessorListOf(graph, node)
            if (potentialAppToSelect.premises.size != predecessorProofNodes.size) {
                node.selectedApplication = null
                continue
            }

            // We must try every permutation of children against every permutation of premises.
            val childPermutations = predecessorProofNodes.permutations()
            val premisePermutations = potentialAppToSelect.premises.permutations()

            for (childPermutation in childPermutations) {
                for (premisePermutation in premisePermutations) {
                    val permutationVars = tempVars.copy()
                    var allChildrenSolved = true

                    for (i in childPermutation.indices) {
                        val child = childPermutation[i]
                        val premise = premisePermutation[i]

                        if (!selectApplicationPath(child, premise, graph, permutationVars, depth + 1)) {
                            allChildrenSolved = false
                            break // This permutation failed, try the next one
                        }
                    }

                    if (allChildrenSolved) {
                        vars.availableVars = permutationVars.availableVars
                        vars.usedVars = permutationVars.usedVars
                        log(depth, "<- SUCCESS: Valid assignment found for [${node.id}]")
                        return true
                    }
                }
            }

            // If backtracking failed, clear the selection and try the next suitable application.
            log(depth, "   Backtracking from App: ${potentialAppToSelect.rule} -> '${potentialAppToSelect.conclusion}'")
            node.selectedApplication = null
        }

        log(depth, "<- FAIL: All suitable applications for [${node.id}] failed.")
        return false
    }

    // This helper is needed again for the simplified findValidAssignment
    private fun <T> List<T>.permutations(): List<List<T>> {
        if (this.isEmpty()) return listOf(emptyList())
        val result: MutableList<List<T>> = mutableListOf()
        for (i in this.indices) {
            val element = this[i]
            val remaining = this.toMutableList()
            remaining.removeAt(i)
            val permsOfRest = remaining.permutations()
            for (p in permsOfRest) {
                result.add(listOf(element) + p)
            }
        }
        return result
    }

    // New function to print the tree
    private fun printTree(plan: ProofPlan) {
        println("\n--- Proof Plan Tree ---")
        printTreeNode(plan.graph, plan.finalConclusionNode, "", true)
        println("-----------------------\n")
    }

    private fun printTreeNode(graph: DirectedAcyclicGraph<ProofNode, DefaultEdge>, node: ProofNode, prefix: String, isTail: Boolean) {
        println(prefix + (if (isTail) "└── " else "├── ") + "${node.id} [${node.rule?.name ?: "GOAL"}]")
        val children = Graphs.predecessorListOf(graph, node)
        for (i in 0 until children.size - 1) {
            printTreeNode(graph, children[i], prefix + if (isTail) "    " else "│   ", false)
        }
        if (children.isNotEmpty()) {
            printTreeNode(graph, children.last(), prefix + if (isTail) "    " else "│   ", true)
        }
    }
}

// --- Helper Classes and Functions (Moved to top level for clarity) ---

data class VarLists(var availableVars: MutableList<Formula>, var usedVars: MutableList<Formula>) {
    companion object {
        val allVars = ('p'..'w').toMutableList()
            .map { Formula(listOf(LogicTile(it.toString(), SymbolType.VARIABLE))) }
            .toList()

        fun create(): VarLists {
            val available = allVars
                .shuffled()
                .toMutableList()
            val used = mutableListOf<Formula>()
            return VarLists(available, used)
        }
    }

    fun copy(): VarLists { // Ensure deep copy
        return VarLists(availableVars.toMutableList(), usedVars.toMutableList())
    }

    fun useAtomicAssertion(atomicAssertion: Formula): Formula? {
        val baseVar = atomicAssertion.getBaseVariable()
        val baseVarNode = WffParser.parse(baseVar)

        // Check for direct contradictions: e.g., trying to use 'p' when '~p' is already used.
        val contradiction = usedVars.find { used ->
            WffParser.parse(used.getBaseVariable()) == baseVarNode && used.normalize() != atomicAssertion.normalize()
        }
        if (contradiction != null) return null // CONTRADICTION

        // Check if this exact assertion is already used. If so, it's fine.
        val alreadyUsed = usedVars.any { it.normalize() == atomicAssertion.normalize() }
        if (alreadyUsed) return atomicAssertion

        // If the base variable is available, use it and record the specific assertion.
        if (availableVars.contains(baseVar)) {
            availableVars.remove(baseVar)
            usedVars.add(atomicAssertion)
            return atomicAssertion
        }

        // If the base variable is not available, but the assertion is not a contradiction,
        // it means another branch used this variable in a compatible way (e.g. 'p' is used, and we need 'p').
        // This is valid.
        val compatibleUse = usedVars.any { used -> WffParser.parse(used.getBaseVariable()) == baseVarNode }
        if(compatibleUse) return atomicAssertion

        return null // Variable not available and not used in a compatible way.
    }
}

// --- Top-level helper functions and data classes ---

private fun compareFormulas(f1: Formula, f2: Formula): Boolean {
    return f1.normalize() == f2.normalize()
}

/**
 * e.g. from "(~p & q) | r" -> ["~p", "q", "r"]
 * or   from "~(p & q) | r" -> ["p", "q", "r"]
 */
fun Formula.getAtomicAssertions(): List<Formula> {
    if (tiles.isEmpty()) return emptyList()
    val node = WffParser.parse(this) ?: return emptyList()

    fun findVarNodes(currentNode: FormulaNode): List<FormulaNode.VariableNode> {
        return when (currentNode) {
            is FormulaNode.BinaryOpNode -> findVarNodes(currentNode.left) + findVarNodes(currentNode.right)
            is FormulaNode.UnaryOpNode -> findVarNodes(currentNode.child)
            is FormulaNode.VariableNode -> listOf(currentNode)
        }
    }

    fun findParent(targetNode: FormulaNode, currentNode: FormulaNode?): FormulaNode? {
        if (currentNode == null) return null
        when (currentNode) {
            is FormulaNode.BinaryOpNode -> {
                if (currentNode.left == targetNode || currentNode.right == targetNode) {
                    return currentNode
                }
                return findParent(targetNode, currentNode.left)
                       ?: findParent(targetNode, currentNode.right)
            }
            is FormulaNode.UnaryOpNode -> {
                if (currentNode.child == targetNode) {
                    return currentNode
                }
                return findParent(targetNode, currentNode.child)
            }
            is FormulaNode.VariableNode -> return null
        }
    }

    val allVarNodes = findVarNodes(node)
    val assertions = allVarNodes.map { varNode ->
        val parent = findParent(varNode, node)
        if (parent is FormulaNode.UnaryOpNode && parent.operator == AvailableTiles.not) {
            treeToFormula(parent)
        } else {
            treeToFormula(varNode)
        }
    }
    return assertions.distinct()
}

fun Formula.getBaseVariable(): Formula {
    val node = WffParser.parse(this) ?: return Formula(emptyList())
    return when (node) {
        is FormulaNode.BinaryOpNode -> Formula(emptyList()) // Should not happen
        is FormulaNode.UnaryOpNode -> treeToFormula(node.child)
        is FormulaNode.VariableNode -> this
    }
}

private fun Formula.isAtomicOrNegatedAtomic(): Boolean {
    if (tiles.isEmpty()) return false
    if (tiles.size == 1) return tiles[0].type == SymbolType.VARIABLE
    return tiles.size == 2 && tiles[0].type == SymbolType.UNARY_OPERATOR && tiles[1].type == SymbolType.VARIABLE
}

/**
 * Returns a lazy sequence representing the Cartesian product of several lists,
 * avoiding high memory usage for large inputs.
 */
fun <T> cartesianProductSequence(lists: List<List<T>>): Sequence<List<T>> {
    if (lists.isEmpty()) {
        return sequenceOf(emptyList())
    }

    return sequence {
        val head = lists.first()
        val tail = lists.drop(1)
        val tailCartesian = cartesianProductSequence(tail)

        for (item in head) {
            for (p in tailCartesian) {
                yield(listOf(item) + p)
            }
        }
    }
}

