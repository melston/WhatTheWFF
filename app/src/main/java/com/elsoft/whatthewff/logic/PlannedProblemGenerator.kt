// File: /home/mark/AndroidStudioProjects/WhattheWFF/app/src/main/java/com/elsoft/whatthewff/logic/PlannedProblemGenerator.kt

package com.elsoft.whatthewff.logic


import com.elsoft.whatthewff.logic.AvailableTiles.and
import com.elsoft.whatthewff.logic.AvailableTiles.implies
import com.elsoft.whatthewff.logic.AvailableTiles.not
import com.elsoft.whatthewff.logic.AvailableTiles.or
import com.elsoft.whatthewff.logic.RuleGenerators.fAnd
import com.elsoft.whatthewff.logic.RuleGenerators.fImplies
import com.elsoft.whatthewff.logic.RuleGenerators.fNeg
import com.elsoft.whatthewff.logic.RuleGenerators.fOr
import com.elsoft.whatthewff.logic.RuleGenerators.treeToFormula
import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DirectedAcyclicGraph

private const val debug = false
private fun log(depth: Int, message: String) {
    if (debug) {
        val indent = " . ".repeat(depth)
        println("$indent$message")
    }
}

// --- Data Structures for the Plan ---

/**
 * A constraint that defines the required logical shape of a formula at a node.
 */
sealed class FormulaShape(val name: String) {
    object Any : FormulaShape("Any")
    object IsImplication : FormulaShape("IsImplication")
    object IsConjunction : FormulaShape("IsConjunction")
    object IsDisjunction : FormulaShape("IsDisjunction")
    object IsNegation : FormulaShape("IsNegation")
    object IsAtomic : FormulaShape("IsAtomic")
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
    val allNodes: Set<ProofNode>
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

/**
 * PlannedProblemGenerator:  A generator for Problems.
 * It has the following elements:
 * <ul>
 *   <li>Planner (generatePlan): Its only job is to create a structurally valid,
 *       but abstract, proof tree. It uses conclusionConstraint to ensure rules are
 *       chained together logically (e.g., Simplification can't feed into Absorption's
 *       premise). It doesn't know anything about specific formulas like p or q. </li>
 *   <li>Top-Down Solver (selectApplicationPath): This is the "brains" of the operation. </li>
 *     <ul>
 *         <li>It starts at the root of the plan with a general goal (e.g.,
 *             "prove a formula of any shape").
 *         <li>It works top-down. When it encounters a node for a rule like MODUS_PONENS,
 *             it doesn't try to solve the children first. Instead, it generates a
 *             potential set of concrete premises (e.g., p -> q, p). </li>
 *         <li>It then recursively calls itself on its children with a specific goal:
 *             "You must prove p -> q" and "You must prove p".</li>
 *         <li>This completely avoids the combinatorial explosion because it never tries
 *             to pre-calculate all possibilities. It performs a targeted search for
 *             just one valid proof.
 *     </ul>
 *   <li>No More Infinite Loop: The fatal mutual recursion between selectApplicationPath
 *       and findSolutionsForNode is gone. There is now only one primary recursive solver,
 *       which guarantees termination (either by finding a solution or by exhausting all
 *       valid, non-repeating variable assignments).
 * </ul>
 */
class PlannedProblemGenerator {

    private val maxGenerationAttempts = 50 // To prevent infinite loops
    private var nextId = 0
    private fun generateId(): String = "ID_${nextId++}"

    fun generate(difficulty: Int): Problem? {
        nextId = 0 // Reset for each new generation
        repeat(maxGenerationAttempts) { attempt: Int ->
            log(0, "\n*** Generating problem attempt $attempt")
            val plan = generatePlan(difficulty)
            if (debug) printTree(plan)

            val problem = generateProblemFromPlan(plan)
            if (problem == null) {
                log(0, "***!!! Generated null problem on attempt $attempt")
            } else {
                if (problem.premises.isNotEmpty()) {
                    val consistencyCheck =
                        problem.premises.map { it.getAtomicAssertions() }
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
                        log(0, "***!!! Found a problem with a premise equal to the conclusion on attempt $attempt")
                    } else {
                        log(0, "***!!! Found a contradiction while generating problem on attempt $attempt")
                    }
                } else {
                    log(0, "***!!! Found a problem with no premises on attempt $attempt")
                }
            }
        }
        log(0, "***!!! Failed to generate a valid problem after $maxGenerationAttempts attempts.")
        return null // Failed to generate a valid problem
    }

    private fun generatePlan(difficulty: Int): ProofPlan {
        val graph = DirectedAcyclicGraph<ProofNode, DefaultEdge>(DefaultEdge::class.java)
        val allNodes = mutableSetOf<ProofNode>()
        val premiseNodes = mutableSetOf<ProofNode>()

        val finalConclusionNode = ProofNode(id = generateId())
        graph.addVertex(finalConclusionNode)
        allNodes.add(finalConclusionNode)

        val goalsToSolve = mutableListOf(finalConclusionNode)
        var stepsBudget = difficulty.coerceAtLeast(1)
        val branchingChance = 0.3

        while (goalsToSolve.isNotEmpty()) {
            if (stepsBudget <= 0) {
                // If we are out of budget, this MUST be a leaf node (an assumption)
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
                // A rule is applicable if its conclusion shape matches the
                // constraint, or if the constraint is 'Any'.
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
                premiseNodes.add(currentNode)
                continue
            }

            currentNode.rule = rule
            val premiseShapes = rulePremiseShapes[rule]!!
            val newChildBlueprints = premiseShapes.map { shape ->
                // The child's conclusion MUST satisfy the parent's
                // premise requirement.
                ProofNode(id = generateId(), conclusionConstraint = shape)
            }

            newChildBlueprints.forEach { childBlueprint ->
                graph.addVertex(childBlueprint)
                allNodes.add(childBlueprint)
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
            // inDegreeOf finds the nodes that have no source nodes
            // attached to them.
            graph.containsVertex(node) && graph.inDegreeOf(node) == 0
        }.toSet()

        return ProofPlan(graph, finalConclusionNode, allNodes)
    }

    private fun generateProblemFromPlan(plan: ProofPlan): Problem? {
        val vars = VarLists.create()
        if (selectApplicationPath(plan.finalConclusionNode,
                                  FormulaShape.Any,
                                  plan.graph,
                                  vars,
                                  0)) {
            val finalConclusionFormula =
                plan.finalConclusionNode.selectedApplication?.conclusion
                    ?: return null
            val premises = plan.allNodes.filter { node ->
                    // A node is a premise if it was solved as an ASSUMPTION
                    node.selectedApplication?.rule == InferenceRule.ASSUMPTION
                }
                .mapNotNull { it.selectedApplication?.conclusion }
                .distinct()
                .sortedBy { it.toString() }

            return Problem("gen_${System.currentTimeMillis()}",
                           "Generated Problem",
                           premises,
                           finalConclusionFormula,
                           plan.graph.vertexSet().size)
        }
        return null
    }

    private fun selectApplicationPath(
        node: ProofNode,
        requiredShape: FormulaShape,
        graph: DirectedAcyclicGraph<ProofNode, DefaultEdge>,
        vars: VarLists,
        depth: Int
    ): Boolean {
        log(depth, "-> selectApplicationPath for [${node.id}] seeking ${requiredShape.name}")
        val predecessorNodes = Graphs.predecessorListOf(graph, node)

        // BASE CASE: LEAF NODE
        if (predecessorNodes.isEmpty()) {
            val potentialFormulas = generateLeafFormulas(requiredShape, vars)
            for (formula in potentialFormulas.shuffled()) {
                val tempVars = vars.copy()
                if (isConsistent(formula, tempVars)) {
                    vars.commit(tempVars)
                    node.selectedApplication = Application(formula, InferenceRule.ASSUMPTION, emptyList())
                    log(depth, "   Leaf node [${node.id}] success with '${formula}'")
                    return true
                }
            }
            log(depth, "   Leaf node [${node.id}] FAILED to find consistent formula for ${requiredShape.name}")
            return false
        }

        // RECURSIVE STEP: INTERMEDIATE NODE
        val ruleToApply = node.rule ?: return false // Should not happen in a valid plan

        // Generate a set of possible premise combinations this rule could use.
        val premiseSets = generatePremiseCombinations(ruleToApply, requiredShape, vars)

        for (premiseSet in premiseSets.shuffled()) {
            val childPermutations = predecessorNodes.permutations()
            for (childPermutation in childPermutations) {
                val tempVars = vars.copy()
                var allChildrenSolved = true
                val childSolutions = mutableMapOf<ProofNode, Application>()

                for (i in childPermutation.indices) {
                    val childNode = childPermutation[i]
                    val requiredPremise = premiseSet[i]

                    // Pass the main tempVars to be mutated by the child solver.
                    if (selectApplicationPath(childNode, requiredPremise, tempVars, depth + 1)) {
                        childSolutions[childNode] = childNode.selectedApplication!!
                        // State is now automatically carried over because `tempVars`
                        // was modified in place.
                    } else {
                        allChildrenSolved = false
                        break
                    }
                }

                if (allChildrenSolved) {
                    // All children were solved with a consistent set of premises.
                    val finalPremises = predecessorNodes.map { childSolutions[it]!!.conclusion }
                    val finalConclusion = InferenceRuleEngine.getPossibleConclusions(ruleToApply, finalPremises).firstOrNull()

                    if (finalConclusion != null && formulaMatchesShape(finalConclusion, requiredShape)) {
                        // Success! Commit selections and state.
                        vars.commit(tempVars)
                        node.selectedApplication = Application(finalConclusion, ruleToApply, finalPremises)
                        childSolutions.forEach { (childNode, app) -> childNode.selectedApplication = app }
                        log(depth, "<- SUCCESS for [${node.id}] with conclusion '${finalConclusion}'")
                        return true
                    }
                }
            }
        }

        log(depth, "<- FAILED for [${node.id}]")
        return false
    }

    // Overload for the recursive call that requires a specific formula
    private fun selectApplicationPath(
        node: ProofNode,
        requiredConclusion: Formula,
        vars: VarLists,
        depth: Int
    ): Boolean {
        // This is a much simpler case: we just need to solve this one node to be exactly this formula.
        // This is always a leaf in our new design.
        log(depth, "-> selectApplicationPath for [${node.id}] seeking specific formula '${requiredConclusion}'")
        val tempVars = vars.copy()
        if (isConsistent(requiredConclusion, tempVars)) {
            vars.commit(tempVars)
            node.selectedApplication = Application(requiredConclusion,
                                                   InferenceRule.ASSUMPTION,
                                                   emptyList())
            log(depth, "   Leaf node [${node.id}] success with specific formula '${requiredConclusion}'")
            return true
        }
        log(depth, "   Leaf node [${node.id}] FAILED, formula '${requiredConclusion}' is inconsistent")
        return false
    }

    private fun isConsistent(formula: Formula, vars: VarLists): Boolean {
        val atoms = formula.getAtomicAssertions()
        return atoms.all { vars.useAtomicAssertion(it) != null }
    }

    private fun generateLeafFormulas(shape: FormulaShape, vars: VarLists): List<Formula> {
        val formulas = mutableListOf<Formula>()
        val baseAvailable = vars.availableVars
        val specificUsed = vars.usedVars
        val allPossibleAtoms = (baseAvailable + specificUsed).distinctBy {
            it.getBaseVariable().toString()
        }.shuffled()

        when (shape) {
            is FormulaShape.IsImplication -> allPossibleAtoms.take(2).let {
                if (it.size == 2) formulas.add(fImplies(it[0], it[1]))
            }
            is FormulaShape.IsConjunction -> allPossibleAtoms.take(2).let {
                if (it.size == 2) formulas.add(fAnd(it[0], it[1]))
            }
            is FormulaShape.IsDisjunction -> allPossibleAtoms.take(2).let {
                if (it.size == 2) formulas.add(fOr(it[0], it[1]))
            }
            is FormulaShape.IsNegation -> allPossibleAtoms.firstOrNull()?.let {
                if (WffParser.parse(it) is FormulaNode.VariableNode) formulas.add(fNeg(it))
            }
            else -> { // Any or IsAtomic
                formulas.addAll(allPossibleAtoms)
                formulas.addAll(allPossibleAtoms.mapNotNull {
                    if (WffParser.parse(it) is FormulaNode.VariableNode) {
                        fNeg(it)
                    } else null
                })
            }
        }
        if (formulas.isEmpty()) { // Fallback
            VarLists.allVars.shuffled().take(2).let {
                if (it.size == 2) formulas.add(fImplies(it[0], it[1]))
            }
        }
        return formulas
    }

    private fun generatePremiseCombinations(rule: InferenceRule, shape: FormulaShape,
                                            vars: VarLists): List<List<Formula>> {
        // TODO: This is a simplified stub. A real implementation would be more intelligent.
        val atoms = vars.availableVars.shuffled().take(4)
        if (atoms.size < 2) return emptyList()

        val p = atoms[0]
        val q = atoms[1]
        val r = if (atoms.size > 2) atoms[2] else p
        val s = if (atoms.size > 3) atoms[3] else q

        return when (rule) {
            InferenceRule.MODUS_PONENS -> listOf(listOf(fImplies(p, q),
                                                        p))
            InferenceRule.MODUS_TOLLENS -> listOf(listOf(fImplies(p, q),
                                                         fNeg(q)))
            InferenceRule.HYPOTHETICAL_SYLLOGISM -> listOf(listOf(fImplies(p, q),
                                                                  fImplies(q, r)))
            InferenceRule.DISJUNCTIVE_SYLLOGISM -> listOf(listOf(fOr(p, q),
                                                                 fNeg(p)))
            InferenceRule.CONJUNCTION -> listOf(listOf(p, q))
            InferenceRule.SIMPLIFICATION -> listOf(listOf(fAnd(p, q)))
            InferenceRule.ADDITION -> listOf(listOf(p))
            InferenceRule.ABSORPTION -> listOf(listOf(fImplies(p, q)))
            InferenceRule.CONSTRUCTIVE_DILEMMA -> listOf(listOf(fAnd(fImplies(p, q),
                                                                     fImplies(r, s)),
                                                                fOr(p, r)))
            else -> emptyList()
        }
    }

    private fun formulaMatchesShape(formula: Formula, shape: FormulaShape): Boolean {
        val node = WffParser.parse(formula) ?: return false
        return when (shape) {
            is FormulaShape.Any -> true
            is FormulaShape.IsAtomic -> node is FormulaNode.VariableNode
            is FormulaShape.IsNegation ->
                node is FormulaNode.UnaryOpNode && node.operator == not
            is FormulaShape.IsConjunction ->
                node is FormulaNode.BinaryOpNode && node.operator == and
            is FormulaShape.IsDisjunction ->
                node is FormulaNode.BinaryOpNode && node.operator == or
            is FormulaShape.IsImplication ->
                node is FormulaNode.BinaryOpNode && node.operator == implies
        }
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
        val allVars by lazy {
            ('p'..'w').map {
                Formula(listOf(LogicTile(it.toString(), SymbolType.VARIABLE)))
            }
        }
        fun create(): VarLists {
            return VarLists(allVars.shuffled().toMutableList(), mutableListOf())
        }
    }

    fun copy() = VarLists(availableVars.toMutableList(), usedVars.toMutableList())

    fun commit(other: VarLists) {
        this.availableVars = other.availableVars
        this.usedVars = other.usedVars
    }

    fun useAtomicAssertion(atomicAssertion: Formula): Formula? {
        val baseVar = atomicAssertion.getBaseVariable()
        val baseVarNode = WffParser.parse(baseVar)
        val contradiction = usedVars.find { used ->
            WffParser.parse(used.getBaseVariable()) == baseVarNode
            && used.normalize() != atomicAssertion.normalize()
        }
        if (contradiction != null) return null
        val alreadyUsed = usedVars.any { it.normalize() == atomicAssertion.normalize() }
        if (alreadyUsed) return atomicAssertion
        if (availableVars.contains(baseVar)) {
            availableVars.remove(baseVar)
            usedVars.add(atomicAssertion)
            return atomicAssertion
        }
        val compatibleUse = usedVars.any { used -> WffParser.parse(used.getBaseVariable()) == baseVarNode }
        if(compatibleUse) return atomicAssertion
        return null
    }
}

// --- Top-level helper functions and data classes ---

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

private fun compareFormulas(f1: Formula, f2: Formula): Boolean {
    return f1.normalize() == f2.normalize()
}

