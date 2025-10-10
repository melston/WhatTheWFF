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
import kotlin.random.Random

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
    object IsConjunctionOfImplications : FormulaShape("IsConjunctionOfImplications")
}

/**
 * Defines the premise shapes required for each rule. This is the blueprint
 * for generating the proof plan graph.
 */
private val rulePremiseShapes = mapOf(
    InferenceRule.ABSORPTION to listOf(FormulaShape.IsImplication),
    InferenceRule.ADDITION to listOf(FormulaShape.Any),
    InferenceRule.ASSUMPTION to listOf(),
    InferenceRule.CONJUNCTION to listOf(FormulaShape.Any,
                                        FormulaShape.Any),
    InferenceRule.CONSTRUCTIVE_DILEMMA to listOf(FormulaShape.IsConjunctionOfImplications,
                                                 FormulaShape.IsDisjunction),
    InferenceRule.DISJUNCTIVE_SYLLOGISM to listOf(FormulaShape.IsDisjunction,
                                                  FormulaShape.IsNegation),
    InferenceRule.HYPOTHETICAL_SYLLOGISM to listOf(FormulaShape.IsImplication,
                                                   FormulaShape.IsImplication),
    InferenceRule.MODUS_PONENS to listOf(FormulaShape.IsImplication,
                                         FormulaShape.IsAtomic),
    InferenceRule.MODUS_TOLLENS to listOf(FormulaShape.IsImplication,
                                          FormulaShape.IsNegation),
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

// Weights to encourage variety in rule selection during planning
private val ruleWeights: Map<InferenceRule, Double> = mapOf(
    InferenceRule.CONJUNCTION to 0.8,
    InferenceRule.SIMPLIFICATION to 1.0,
    InferenceRule.ADDITION to 1.0,
    InferenceRule.MODUS_PONENS to 1.5,
    InferenceRule.HYPOTHETICAL_SYLLOGISM to 2.0,
    InferenceRule.MODUS_TOLLENS to 2.4,
    InferenceRule.DISJUNCTIVE_SYLLOGISM to 2.4,
    InferenceRule.ABSORPTION to 2.0,
    InferenceRule.CONSTRUCTIVE_DILEMMA to 2.5
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
    // Memoization cache
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
        nextId = 0
        repeat(maxGenerationAttempts) repeat@{ attempt -> // <- 1. Add the label here
            log(0, "\n*** Generating problem attempt $attempt")
            val plan = generatePlan(difficulty)
            if (debug) printTree(plan)

            // *** NEW: Call the new single-pass, top-down solver ***
            val solution = selectApplicationPath(plan.finalConclusionNode, plan.graph,
                                                 VarLists.create(), 0)

            if (solution != null) {
                val premises = solution.premises.distinctBy {
                    it.normalize()
                }.sortedBy {
                    it.toString()
                }

                // Final validation step to prevent contradictions in the premise set.
                val normalizedPremises = premises.map { it.normalize() }.toSet()
                var isContradictory = false
                for (p1 in normalizedPremises) {
                    // Check 1: Does the negation of the whole formula exist? (e.g., p vs ¬p)
                    val negationOfP1 = fNeg(p1).normalize()
                    if (normalizedPremises.contains(negationOfP1)) {
                        log(0, "!! Contradiction Found: $p1 and $negationOfP1. Rejecting problem.")
                        isContradictory = true
                        break
                    }

                    // Check 2: Does any part of this formula contradict another whole premise?
                    // (e.g., (p & q) vs ¬p)
                    val atomsOfP1 = p1.getAtomicAssertions()
                    for (atom in atomsOfP1) {
                        val negationOfAtom = fNeg(atom).normalize()
                        if (normalizedPremises.contains(negationOfAtom)) {
                            log(0, "!! Embedded Contradiction Found: Part '$atom' in '$p1' contradicts '$negationOfAtom'. Rejecting problem.")
                            isContradictory = true
                            break
                        }
                    }
                    if (isContradictory) break
                }

                if (isContradictory) {
                    return@repeat // <- 2. Replace continue with the labeled return
                }

                val problem = Problem(
                    "gen_${System.currentTimeMillis()}",
                    "Generated Problem",
                    premises,
                    solution.conclusion,
                    plan.graph.vertexSet().size,
                    solution
                )

                val prunedProblem = pruneUnusedPremises(problem)
                if (prunedProblem.premises.isNotEmpty() && !prunedProblem.premises.any {
                        compareFormulas(it, prunedProblem.conclusion)
                    }) {
                    log(0, "*** Found a valid problem on attempt $attempt")
                    return prunedProblem
                }
            }
        }
        log(0, "***!!! Failed to generate a valid problem after $maxGenerationAttempts attempts.")
        return null
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
        var isRootNode = true

        while (goalsToSolve.isNotEmpty()) {
            if (stepsBudget <= 0) {
                premiseNodes.addAll(goalsToSolve)
                goalsToSolve.clear()
                continue
            }

            val currentNode = goalsToSolve.removeAt(0)
            stepsBudget--

            var applicableRules = InferenceRule.entries.filter { rule ->
                if (rule == InferenceRule.ASSUMPTION) return@filter false

                val conclusionShape = ruleConclusionShapes[rule]!!
                val constraint = currentNode.conclusionConstraint
                val shapeOk = when (constraint) {
                    FormulaShape.Any -> true
                    else -> conclusionShape == constraint || conclusionShape == FormulaShape.Any
                }
                val budgetOk = (rulePremiseShapes[rule]?.size ?: 0) <= stepsBudget
                shapeOk && budgetOk
            }

            if (isRootNode) {
                applicableRules = applicableRules.filter {
                    it != InferenceRule.CONJUNCTION && it != InferenceRule.ADDITION
                }
                isRootNode = false
            }

            val rule = weightedRandom(applicableRules)

            if (rule == null) {
                premiseNodes.add(currentNode)
                continue
            }

            currentNode.rule = rule
            val premiseShapes = rulePremiseShapes[rule]!!
            val newChildBlueprints = premiseShapes.map { shape ->
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
        return ProofPlan(graph, finalConclusionNode, allNodes)
    }

    private fun weightedRandom(rules: List<InferenceRule>): InferenceRule? {
        if (rules.isEmpty()) return null

        // Create a list of rules sorted by weight to ensure correct selection
        val sortedRules = rules.sortedBy { ruleWeights[it] ?: 1.0 }
        val totalRuleWeights = sortedRules.sumOf { ruleWeights[it] ?: 1.0 }

        // Randomly select a rule based on the weights
        val randomPoint = Random.nextDouble() * totalRuleWeights

        var cumWeight = 0.0
        for (rule in sortedRules) {
            cumWeight += (ruleWeights[rule] ?: 1.0)
            if (randomPoint < cumWeight) {
                return rule
            }
        }

        return sortedRules.lastOrNull() // Fallback
    }

    // This is the new top-down solver, replacing generateProblemFromPlan and findAllSolutions
    private fun selectApplicationPath(node: ProofNode,
                                      graph: DirectedAcyclicGraph<ProofNode, DefaultEdge>,
                                      vars: VarLists, depth: Int): Application? {
        log(depth, "-> selectApplicationPath for [${node.id}] with constraint ${node.conclusionConstraint.name}")

        val predecessors = Graphs.predecessorListOf(graph, node)

        // Base case: This is a leaf node, must be an assumption
        if (predecessors.isEmpty()) {
            val formulas = generateFormulasForShape(node.conclusionConstraint, vars, 20).shuffled()
            for (formula in formulas) {
                if (isConsistent(formula, vars.copy())) {
                    log(depth, "<- Solved [${node.id}] with ASSUMPTION: $formula")
                    return Application(formula, InferenceRule.ASSUMPTION, listOf(formula), emptyList())
                }
            }
            return null
        }

        // Recursive step: This is an intermediate node with a rule
        val rule = node.rule ?: return null
        val childNodes = predecessors.shuffled()

        // Attempt to find a valid application for this rule up to N times for variety
        repeat(30) {
            val potentialApp = InferenceRuleEngine.getPossiblePremises(rule, vars).firstOrNull() ?: return@repeat

            if (potentialApp.premises.size != childNodes.size) return@repeat

            val childSolutions = mutableListOf<Application>()
            var possible = true
            val tempVars = vars.copy() // Create a scope for this attempt

            for (i in childNodes.indices) {
                val childNode = childNodes[i]
                val requiredPremise = potentialApp.premises[i]

                // Solve the child with the SPECIFIC goal of proving the required premise
                val childSolution = findProofForFormula(childNode, requiredPremise,
                                                        graph, tempVars, depth + 1)
                if (childSolution != null) {
                    childSolutions.add(childSolution)
                } else {
                    possible = false
                    break
                }
            }

            if (possible) {
                vars.commit(tempVars) // Commit the vars used by the successful proof tree
                val allPremises = childSolutions.flatMap { it.premises }
                log(depth, "<- Solved [${node.id}] with RULE: $rule -> ${potentialApp.conclusion}")
                return Application(potentialApp.conclusion, rule, allPremises, childSolutions)
            }
        }

        log(depth, "<- FAILED to solve [${node.id}]")
        return null
    }

    private fun findProofForFormula(node: ProofNode, target: Formula,
                                    graph: DirectedAcyclicGraph<ProofNode, DefaultEdge>,
                                    vars: VarLists, depth: Int): Application? {
        log(depth, "  - findProofForFormula for [${node.id}] targeting '$target'")

        val predecessors = Graphs.predecessorListOf(graph, node)

        // Base case: This node is a leaf in the proof plan. It MUST be an assumption.
        if (predecessors.isEmpty()) {
            if (isConsistent(target, vars)) {
                log(depth+1, "<- Solved leaf node [${node.id}] with ASSUMPTION: $target")
                return Application(target, InferenceRule.ASSUMPTION, listOf(target), emptyList())
            }
            log(depth+1, "<- FAILED leaf node [${node.id}] due to inconsistency with target: $target")
            return null
        }

        // Recursive step: This is an intermediate node. It MUST be solved by a rule.
        // It CANNOT be solved by turning its target into an assumption.
        val rule = node.rule ?: return null
        val childNodes = predecessors.shuffled()

        // Get all possible premise combinations that could result in our target formula
        val premiseSets = InferenceRuleEngine.getPremiseSetsForConclusion(rule, target, vars)

        for (premiseSet in premiseSets.shuffled()) {
            if (premiseSet.size != childNodes.size) continue

            val childSolutions = mutableListOf<Application>()
            var possible = true
            val tempVars = vars.copy()

            for (i in childNodes.indices) {
                val childNode = childNodes[i]
                val requiredPremise = premiseSet[i]

                val childSolution = findProofForFormula(childNode, requiredPremise, graph, tempVars, depth + 1)
                if (childSolution != null) {
                    childSolutions.add(childSolution)
                } else {
                    possible = false
                    break
                }
            }

            if (possible) {
                vars.commit(tempVars)
                val allPremises = childSolutions.flatMap { it.premises }
                log(depth, "<- Solved intermediate node [${node.id}] with RULE: $rule -> $target")
                return Application(target, rule, allPremises, childSolutions)
            }
        }

        log(depth, "<- FAILED to solve intermediate node [${node.id}] for target: $target")
        return null
    }

    private fun findAllSolutions(
        node: ProofNode,
        graph: DirectedAcyclicGraph<ProofNode, DefaultEdge>,
        vars: VarLists,
        depth: Int
    ): List<Application> {
        log(depth, "-> findAllSolutions for [${node.id}]")

        if (node.arePossibleApplicationsGenerated) {
            log(depth, "<- findAllSolutions for [${node.id}] (cached)")
            return node.possibleApplications
        }

        val predecessorNodes = Graphs.predecessorListOf(graph, node)
        val solutions = mutableListOf<Application>()

        if (predecessorNodes.isEmpty()) {
            val potentialFormulas = generateFormulasForShape(node.conclusionConstraint, vars, 10)
            potentialFormulas.forEach { formula ->
                val tempVars = vars.copy()
                if (isConsistent(formula, tempVars)) {
                    solutions.add(Application(formula, InferenceRule.ASSUMPTION, emptyList(), emptyList()))
                }
            }
        } else {
            val ruleToApply = node.rule ?: return emptyList()

            val childSolutionSets: List<List<Application>> = predecessorNodes.map { childNode ->
                findAllSolutions(childNode, graph, vars, depth + 1)
            }

            if (childSolutionSets.any { it.isEmpty() }) return emptyList()

            val combinations = cartesianProduct(childSolutionSets)
            for (combination in combinations) {
                // *** THE FINAL, DEFINITIVE FIX IS HERE ***
                if (isCombinationRedundant(combination.map { it.conclusion })) {
                    continue // Skip this redundant combination
                }

                val combinationVars = vars.copy()
                var combinationConsistent = true
                val premisesForParent = mutableListOf<Formula>()

                for (childSolution in combination) {
                    if (!isConsistent(childSolution.conclusion, combinationVars)) {
                        combinationConsistent = false
                        break
                    }
                    premisesForParent.add(childSolution.conclusion)
                }

                if (combinationConsistent) {
                    val possibleConclusions =
                        InferenceRuleEngine.getPossibleConclusions(ruleToApply, premisesForParent)
                    for (conclusion in possibleConclusions) {
                        if (formulaMatchesShape(conclusion, node.conclusionConstraint)) {
                            solutions.add(Application(conclusion, ruleToApply, premisesForParent, combination))
                        }
                    }
                }
            }
        }

        node.possibleApplications.addAll(solutions.distinctBy { it.conclusion.normalize() })
        node.arePossibleApplicationsGenerated = true
        log(depth, "<- findAllSolutions for [${node.id}] (found ${node.possibleApplications.size} solutions)")
        return node.possibleApplications
    }

    /**
     * New validation function to check for logical redundancy within a set of premises.
     * Returns true if any premise is a sub-formula of another.
     */
    private fun isCombinationRedundant(premises: List<Formula>): Boolean {
        for (i in premises.indices) {
            for (j in premises.indices) {
                if (i == j) continue

                val p1 = premises[i]
                val p2 = premises[j]

                // Check if p1 is a sub-formula of p2.
                // toString() is a reliable way to check for structural containment.
                // We also check that they are not identical, which is a form of redundancy.
                if (p2.toString().contains(p1.toString())) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Reverting to the simpler, cartesian-product based solver.
     * The redundancy is now handled by a separate validation step.
     */
    private fun findSolutionsWithCartesianProduct(
        node: ProofNode,
        graph: DirectedAcyclicGraph<ProofNode, DefaultEdge>,
        vars: VarLists,
        depth: Int
    ): List<Application> {
        log(depth, "-> findSolutions for [${node.id}]")

        if (node.arePossibleApplicationsGenerated) {
            log(depth, "<- findSolutions for [${node.id}] (cached)")
            return node.possibleApplications
        }

        val predecessorNodes = Graphs.predecessorListOf(graph, node)
        val solutions = mutableListOf<Application>()

        if (predecessorNodes.isEmpty()) {
            val potentialFormulas = generateFormulasForShape(node.conclusionConstraint, vars, 10)
            potentialFormulas.forEach { formula ->
                val tempVars = vars.copy()
                if (isConsistent(formula, tempVars)) {
                    solutions.add(Application(formula, InferenceRule.ASSUMPTION, emptyList(), emptyList()))
                }
            }
        } else {
            val ruleToApply = node.rule ?: return emptyList()

            val childSolutionSets: List<List<Application>> = predecessorNodes.map { childNode ->
                findSolutionsWithCartesianProduct(childNode, graph, vars, depth + 1)
            }

            if (childSolutionSets.any { it.isEmpty() }) return emptyList()

            val combinations = cartesianProduct(childSolutionSets)
            for (combination in combinations) {
                val combinationVars = vars.copy()
                var combinationConsistent = true
                val premisesForParent = mutableListOf<Formula>()

                for (childSolution in combination) {
                    if (!isConsistent(childSolution.conclusion, combinationVars)) {
                        combinationConsistent = false
                        break
                    }
                    premisesForParent.add(childSolution.conclusion)
                }

                if (combinationConsistent) {
                    val possibleConclusions =
                        InferenceRuleEngine.getPossibleConclusions(ruleToApply, premisesForParent)
                    for (conclusion in possibleConclusions) {
                        if (formulaMatchesShape(conclusion, node.conclusionConstraint)) {
                            solutions.add(Application(conclusion, ruleToApply, premisesForParent, combination))
                        }
                    }
                }
            }
        }

        node.possibleApplications.addAll(solutions.distinctBy { it.conclusion.normalize() })
        node.arePossibleApplicationsGenerated = true
        log(depth, "<- findSolutions for [${node.id}] (found ${node.possibleApplications.size} solutions)")
        return node.possibleApplications
    }

    private fun pruneUnusedPremises(problem: Problem): Problem {
        val usedPremises = mutableSetOf<Formula>()
        val stack = ArrayDeque<Application>()
        problem.rootApplication?.let { stack.add(it) }

        while(stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.rule == InferenceRule.ASSUMPTION) {
                usedPremises.add(current.conclusion)
            } else {
                current.childApplications.forEach { stack.add(it) }
            }
        }
        return problem.copy(premises = usedPremises.toList().sortedBy { it.toString() })
    }

    private fun isConsistent(formula: Formula, vars: VarLists): Boolean {
        val atoms = formula.getAtomicAssertions()

        // *** THE FIX IS HERE ***
        // 1. Check for immediate internal contradictions like (p & ~p).
        for (atom in atoms) {
            val negation = fNeg(atom)
            if (atoms.any { other -> compareFormulas(other, negation) }) {
                log(0, "!! Internal contradiction found in formula: $formula. Rejecting.")
                return false
            }
        }

        // 2. Proceed with the original variable consistency check.
        val tempVars = vars.copy()
        for (atom in atoms) {
            if (tempVars.usedVars.contains(fNeg(atom))) {
                return false // Contradicts an already used variable
            }
            if (atom !in tempVars.usedVars) {
                tempVars.availableVars.remove(atom)
                tempVars.usedVars.add(atom)
            }
        }
        vars.commit(tempVars)
        return true
    }

    private fun generateFormulasForShape(shape: FormulaShape, vars: VarLists, count: Int): List<Formula> {
        val formulas = mutableListOf<Formula>()
        val atoms = (vars.availableVars + vars.usedVars).distinct().shuffled()
        if (atoms.size < 3) return emptyList() // Need at least 3 atoms for good variety

        repeat(count * 2) {
            // *** THE DEFINITIVE FIX IS HERE ***
            // Ensure p, q, r, and s are always distinct by taking from a shuffled list.
            val distinctAtoms = atoms.shuffled().take(4)
            val p = distinctAtoms[0]
            val q = distinctAtoms[1]
            val r = distinctAtoms[2]
            val s = distinctAtoms[3]

            // When asked for an atomic formula, occasionally return a more complex one to increase variety
            val actualShape = if (shape == FormulaShape.IsAtomic && Random.nextDouble() < 0.2) {
                listOf(FormulaShape.IsDisjunction, FormulaShape.IsConjunction, FormulaShape.IsImplication).random()
            } else {
                shape
            }

            val formula = when (actualShape) {
                FormulaShape.IsImplication -> fImplies(p, q)
                FormulaShape.IsConjunction -> fAnd(q, r)
                // Generate a valid, solvable set for Constructive Dilemma
                FormulaShape.IsConjunctionOfImplications -> {
                    // Create the required conjunction: (p->q) & (r->s)
                    fAnd(fImplies(p, q),
                         fImplies(r, s))
                }
                FormulaShape.IsDisjunction -> fOr(r, p)
                FormulaShape.IsNegation -> {
                    // Only add negation if the atom is not already negated to avoid ~~p
                    if (WffParser.parse(p) is FormulaNode.VariableNode) fNeg(p) else p
                }
                FormulaShape.IsAtomic -> p
                FormulaShape.Any -> when(Random.nextInt(5)) {
                    0 -> p
                    1 -> if (WffParser.parse(p) is FormulaNode.VariableNode) fNeg(p) else p
                    2 -> fAnd(p, q)
                    3 -> fOr(p, q)
                    else -> fImplies(p, r)
                }
            }
            formulas.add(formula)
        }
        return formulas.distinct().take(count)
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
            is FormulaShape.IsConjunctionOfImplications ->
                node is FormulaNode.BinaryOpNode && node.operator == and &&
                (WffParser.parse(treeToFormula(node.left)) as? FormulaNode.BinaryOpNode)?.operator == implies &&
                (WffParser.parse(treeToFormula(node.right)) as? FormulaNode.BinaryOpNode)?.operator == implies
        }
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

    fun commit(vars: VarLists) {
        availableVars = vars.availableVars
        usedVars = vars.usedVars
    }

    fun copy() = VarLists(availableVars.toMutableList(), usedVars.toMutableList())

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

fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
    if (lists.isEmpty()) return listOf(emptyList())
    var result = listOf(emptyList<T>())
    for (list in lists) {
        result = result.flatMap { res -> list.map { element -> res + element } }
    }
    return result
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
        return when (currentNode) {
            is FormulaNode.BinaryOpNode -> {
                if (currentNode.left == targetNode || currentNode.right == targetNode) currentNode
                else findParent(targetNode, currentNode.left) ?: findParent(targetNode, currentNode.right)
            }
            is FormulaNode.UnaryOpNode -> {
                if (currentNode.child == targetNode) currentNode
                else findParent(targetNode, currentNode.child)
            }
            is FormulaNode.VariableNode -> null
        }
    }

    val allVarNodes = findVarNodes(node)
    val assertions = allVarNodes.map { varNode ->
        val parent = findParent(varNode, node)
        if (parent is FormulaNode.UnaryOpNode && parent.operator == not) {
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

