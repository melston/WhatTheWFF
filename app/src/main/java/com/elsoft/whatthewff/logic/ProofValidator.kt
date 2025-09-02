// File: logic/ProofValidator.kt
// This file contains the logic for validating an entire proof.

package com.elsoft.whatthewff.logic

import com.elsoft.whatthewff.logic.AvailableTiles.and
import com.elsoft.whatthewff.logic.AvailableTiles.implies
import com.elsoft.whatthewff.logic.AvailableTiles.not
import com.elsoft.whatthewff.logic.AvailableTiles.or
import com.elsoft.whatthewff.logic.treeToFormula


/**
 * A data class to hold the result of a proof validation.
 * This is more informative than a simple Boolean.
 *
 * @property isValid Whether the proof is valid.
 * @property errorMessage An optional message explaining why the proof is invalid.
 */
data class ValidationResult(val isValid: Boolean, val errorMessage: String? = null, val errorLine: Int = -1)

/**
 * A singleton object to provide functionality for validating proofs.
 */
object ProofValidator {

    /**
     * Public entry point for proof validation.
     *
     * Validate each line in the proof based on the previous lines.  If valid,
     * add it to the list of proven lines.  If all lines are valid then the proof
     * is valid.
     */
    fun validate(proof: Proof): ValidationResult {
        // Optimization: Store parsed trees to avoid re-parsing on every reference.
        val provenTrees = mutableMapOf<Int, FormulaNode>()

        for (line in proof.lines) {
            // 1. Every line must be a parsable WFF.
            val currentTree = WffParser.parse(line.formula)
                ?: return ValidationResult(false, "Line ${line.lineNumber} is not a Well-Formed Formula.")

            // 2. Validate the justification for the line.
            val justificationResult = when (val just = line.justification) {
                is Justification.Premise -> ValidationResult(true) // Premises are assumed valid.
                is Justification.Assumption -> ValidationResult(true) // Assumptions are assumed valid.
                is Justification.Reiteration -> validateReiteration(currentTree, just, proof, line.lineNumber, line.depth)
                is Justification.Inference -> validateInference(currentTree, just, provenTrees, line.lineNumber)
                is Justification.Replacement -> validateReplacement(currentTree, just, provenTrees, line.lineNumber)
                is Justification.ImplicationIntroduction -> validateImplicationIntroduction(currentTree, just, proof, line.lineNumber)
                is Justification.ReductioAdAbsurdum -> validateReductioAdAbsurdum(currentTree, just, proof, line.lineNumber)
            }

            if (!justificationResult.isValid) {
                return justificationResult // Return the first error found.
            }

            // If the line is valid, add its parsed tree to our map.
            provenTrees[line.lineNumber] = currentTree
        }
        return ValidationResult(true, "Proof is valid!")
    }

    private fun validateReductioAdAbsurdum(
        conclusionTree: FormulaNode,
        justification: Justification.ReductioAdAbsurdum,
        proof: Proof,
        currentLineNumber: Int
    ): ValidationResult {
        // 1. Get the assumption and the contradiction lines from the sub-proof
        val assumptionLine = proof.lines.getOrNull(justification.subproofStart - 1)
        val contradictionLine = proof.lines.getOrNull(justification.contradictionLine - 1)

        if (assumptionLine == null || contradictionLine == null) {
            return ValidationResult(false, "RAA refers to invalid sub-proof lines.", currentLineNumber)
        }
        if (contradictionLine.lineNumber != justification.subproofEnd) {
            return ValidationResult(false, "Contradiction must be the last line of the sub-proof.", currentLineNumber)
        }


        // 2. Check that the contradiction line is actually a contradiction (P ∧ ¬P)
        val contradictionTree = WffParser.parse(contradictionLine.formula)
        if (contradictionTree !is FormulaNode.BinaryOpNode || contradictionTree.operator.symbol != "∧") {
            return ValidationResult(false, "Line ${justification.contradictionLine} is not a valid contradiction (not a conjunction).", currentLineNumber)
        }

        val left = contradictionTree.left
        val right = contradictionTree.right
        val negatedLeft = WffParser.parse(ForwardRuleGenerators.fNeg(treeToFormula(left)))

        if (negatedLeft != right) {
            return ValidationResult(false, "Line ${justification.contradictionLine} is not a valid contradiction (P and ¬P).", currentLineNumber)
        }

        // 3. Check that the final conclusion is the negation of the assumption
        val assumptionTree = WffParser.parse(assumptionLine.formula)
        val negatedAssumption = WffParser.parse(ForwardRuleGenerators.fNeg(assumptionLine.formula))

        if (conclusionTree != negatedAssumption) {
            return ValidationResult(false, "Conclusion must be the negation of the assumption on line ${justification.subproofStart}.", currentLineNumber)
        }

        return ValidationResult(true)
    }

    private fun validateReiteration(
        currentTree: FormulaNode,
        justification: Justification.Reiteration,
        fullProof: Proof,
        currentLineNumber: Int,
        currentDepth: Int
    ): ValidationResult {
        // Does the referenced line exist?
        val referencedLine = fullProof.lines.find { it.lineNumber == justification.lineReference }
            ?: return ValidationResult(false, "Line $currentLineNumber: Reiteration references non-existent line ${justification.lineReference}.")

        // Check 1: The formulas must match.
        val referencedTree = WffParser.parse(referencedLine.formula)
        if (currentTree != referencedTree) {
            return ValidationResult(false, "Line $currentLineNumber: Formula does not match the formula on line ${justification.lineReference}.")
        }

        // Check 2: The referenced line must be in an accessible scope.
        // Its depth must be less than or equal to the current line's depth.
        if (referencedLine.depth > currentDepth) {
            return ValidationResult(false, "Line $currentLineNumber: Cannot reiterate line ${justification.lineReference} from within a closed sub-proof.")
        }

        return ValidationResult(true)
    }

    /**
     * Validates a line derived by a rule of inference using syntax trees.
     */
    private fun validateInference(
        conclusionTree: FormulaNode,
        justification: Justification.Inference,
        provenTrees: Map<Int, FormulaNode>,
        currentLineNumber: Int
    ): ValidationResult {
        val refTrees = justification.lineReferences.map { refNum ->
            provenTrees[refNum] ?: return ValidationResult(false, "Line references non-existent line $refNum.")
        }

        return when (justification.rule) {
            InferenceRule.MODUS_PONENS -> validateModusPonens(conclusionTree, refTrees, currentLineNumber)
            InferenceRule.MODUS_TOLLENS -> validateModusTollens(conclusionTree, refTrees, currentLineNumber)
            InferenceRule.HYPOTHETICAL_SYLLOGISM -> validateHypotheticalSyllogism(conclusionTree, refTrees, currentLineNumber)
            InferenceRule.DISJUNCTIVE_SYLLOGISM -> validateDisjunctiveSyllogism(conclusionTree, refTrees, currentLineNumber)
            InferenceRule.CONSTRUCTIVE_DILEMMA -> validateConstructiveDilemma(conclusionTree, refTrees, currentLineNumber)
            InferenceRule.ABSORPTION -> validateAbsorption(conclusionTree, refTrees, currentLineNumber)
            InferenceRule.SIMPLIFICATION -> validateSimplification(conclusionTree, refTrees, currentLineNumber)
            InferenceRule.CONJUNCTION -> validateConjunction(conclusionTree, refTrees, currentLineNumber)
            InferenceRule.ADDITION -> validateAddition(conclusionTree, refTrees, currentLineNumber)
        }
    }

    /**
     * Validates a line derived by a rule of replacement.
     */
    private fun validateReplacement(
        conclusionTree: FormulaNode,
        justification: Justification.Replacement,
        provenTrees: Map<Int, FormulaNode>,
        currentLineNumber: Int
    ): ValidationResult {
        // 1. Get the premise tree from the referenced line.
        val premiseTree = provenTrees[justification.lineReference]
            ?: return ValidationResult(false, "Line ${justification.lineReference} referenced in replacement does not exist or is invalid.")

        // 2. Generate all possible valid transformations from the premise tree.
        val possibleResults = RuleReplacer.apply(justification.rule, premiseTree)

        // 3. Check if the conclusion tree is one of the valid possibilities.
        // Since FormulaNode is a data class, the '==' check compares the structure of the trees.
        return if (conclusionTree in possibleResults) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Line ${currentLineNumber} does not follow from line ${justification.lineReference} by ${justification.rule.ruleName}.")
        }
    }

    private fun validateImplicationIntroduction(
        conclusionTree: FormulaNode,
        justification: Justification.ImplicationIntroduction,
        fullProof: Proof, // We need the full proof to check the sub-proof lines
        currentLineNumber: Int
    ): ValidationResult {

        // 1. Check that the conclusion is actually an implication (P → Q)
        if (conclusionTree !is FormulaNode.BinaryOpNode || conclusionTree.operator != implies) {
            return ValidationResult(false, "Line $currentLineNumber: Implication Introduction must result in an implication.")
        }

        // 2. Get the assumption and the final line from the sub-proof
        val assumptionLine = fullProof.lines.getOrNull(justification.subproofStart - 1)
        val subproofConclusionLine = fullProof.lines.getOrNull(justification.subproofEnd - 1)

        if (assumptionLine == null || subproofConclusionLine == null) {
            return ValidationResult(false, "Line $currentLineNumber: Sub-proof lines ${justification.subproofStart}-${justification.subproofEnd} are invalid.")
        }

        // 3. Check if the assumption matches the antecedent (P) of the conclusion
        val assumptionTree = WffParser.parse(assumptionLine.formula)
        if (assumptionTree != conclusionTree.left) {
            return ValidationResult(false, "Line $currentLineNumber: The antecedent does not match the assumption on line ${justification.subproofStart}.")
        }

        // 4. Check if the sub-proof's result matches the consequent (Q) of the conclusion
        val subproofConclusionTree = WffParser.parse(subproofConclusionLine.formula)
        if (subproofConclusionTree != conclusionTree.right) {
            return ValidationResult(false, "Line $currentLineNumber: The consequent does not match the conclusion of the sub-proof on line ${justification.subproofEnd}.")
        }

        // (A more advanced validator would also re-validate the lines inside the sub-proof here,
        // ensuring they respect scoping rules, but for now, this checks the main structure.)

        return ValidationResult(true)
    }

    /**
     * Validates Modus Ponens: (P → Q), P |- Q
     */
    private fun validateModusPonens(conclusionTree: FormulaNode, refs: List<FormulaNode>, lineNum: Int): ValidationResult {
        // The ?.let {...} executes the lambda only if the value is not null.
        validateRefs(refs, lineNum, 2, "Modus Ponens") ?.let { return it }
        val p1 = checkMP(implication = refs[0], antecedent = refs[1], conclusion = conclusionTree)
        val p2 = checkMP(implication = refs[1], antecedent = refs[0], conclusion = conclusionTree)
        return if (p1 || p2) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Modus Ponens.")
    }

    private fun checkMP(implication: FormulaNode, antecedent: FormulaNode, conclusion: FormulaNode): Boolean {
        // The implication must be a BinaryOpNode with the '→' operator.
        if (implication !is FormulaNode.BinaryOpNode || implication.operator != implies) return false
        // The left side of the implication must match the antecedent.
        // The right side of the implication must match the conclusion.
        return implication.left == antecedent && implication.right == conclusion
    }

    /**
     * Validates Modus Tollens: (P → Q), ¬Q |- ¬P
     */
    private fun validateModusTollens(conclusionTree: FormulaNode, refs: List<FormulaNode>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 2, "Modus Tollens") ?.let { return it }
        val p1 = checkMT(implication = refs[0], negatedConsequent = refs[1], conclusion = conclusionTree)
        val p2 = checkMT(implication = refs[1], negatedConsequent = refs[0], conclusion = conclusionTree)
        return if (p1 || p2) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Modus Tollens.")
    }

    private fun checkMT(implication: FormulaNode, negatedConsequent: FormulaNode, conclusion: FormulaNode): Boolean {
        // The implication must be (P → Q).
        if (implication !is FormulaNode.BinaryOpNode || implication.operator != implies) return false
        // The negated consequent must be (¬Q).
        if (negatedConsequent !is FormulaNode.UnaryOpNode || negatedConsequent.operator != not) return false
        // The conclusion must be (¬P).
        if (conclusion !is FormulaNode.UnaryOpNode || conclusion.operator != not) return false

        // Check if the inner part of the negated consequent matches the right side of the implication (Q).
        val qMatches = negatedConsequent.child == implication.right
        // Check if the inner part of the conclusion matches the left side of the implication (P).
        val pMatches = conclusion.child == implication.left

        return qMatches && pMatches
    }

    /**
     * Validate Hypothetical Syllogism: (P → Q), (Q → R) |- P → R
     */
    private fun validateHypotheticalSyllogism(conclusionTree: FormulaNode, refs: List<FormulaNode>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 2, "Hypothetical Syllogism") ?.let { return it }
        val p1 = checkHS(f1 = refs[0], f2 = refs[1], conclusion = conclusionTree)
        val p2 = checkHS(f1 = refs[1], f2 = refs[0], conclusion = conclusionTree)
        return if (p1 || p2) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Hypothetical Syllogism.")
    }

    private fun checkHS(f1: FormulaNode, f2: FormulaNode, conclusion: FormulaNode): Boolean {
        if (f1 !is FormulaNode.BinaryOpNode || f1.operator != implies) return false
        if (f2 !is FormulaNode.BinaryOpNode || f2.operator != implies) return false
        if (conclusion !is FormulaNode.BinaryOpNode || conclusion.operator != implies) return false
        if (f1.right != f2.left) return false
        if (conclusion.left != f1.left) return false
        return conclusion.right == f2.right
    }

    /**
     * Validate Disjunctive Syllogism: (P ∨ Q), ¬P |- Q
     */
    private fun validateDisjunctiveSyllogism(conclusionTree: FormulaNode, refs: List<FormulaNode>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 2, "Disjunctive Syllogism") ?.let { return it }
        val p1 = checkDS(disjunction = refs[0], negation = refs[1], conclusion = conclusionTree)
        val p2 = checkDS(disjunction = refs[1], negation = refs[0], conclusion = conclusionTree)
        return if (p1 || p2) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Disjunctive Syllogism.")
    }

    private fun checkDS(disjunction: FormulaNode, negation: FormulaNode, conclusion: FormulaNode): Boolean {
        // 1. Make sure the premises have the right structure.
        if (disjunction !is FormulaNode.BinaryOpNode || disjunction.operator != or) return false
        if (negation !is FormulaNode.UnaryOpNode || negation.operator != not) return false

        // 2. Get the parts of the formulas.
        val p = disjunction.left
        val q = disjunction.right
        val negatedPart = negation.child

        // 3. Check both forms of the syllogism:
        // Form A: The negated part matches P, so the conclusion must be Q.
        val formA = (negatedPart == p && conclusion == q)

        // Form B: The negated part matches Q, so the conclusion must be P.
        val formB = (negatedPart == q && conclusion == p)

        // If either form is valid, the rule holds.
        return formA || formB
    }

    /**
     * Validate Constructive Dilemma: (P → Q) ∧ (R → S), P ∨ R |- (Q ∨ S)
     */
    private fun validateConstructiveDilemma(conclusionTree: FormulaNode, refs: List<FormulaNode>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 2, "Constructive Dilemma") ?.let { return it }
        val p1 = checkCD(conjOfImps = refs[0], disjOfAnts = refs[1], conclusion = conclusionTree)
        val p2 = checkCD(conjOfImps = refs[1], disjOfAnts = refs[0], conclusion = conclusionTree)
        return if (p1 || p2) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Constructive Dilemma.")
    }

    private fun checkCD(conjOfImps: FormulaNode, disjOfAnts: FormulaNode, conclusion: FormulaNode): Boolean {
        // 1. Deconstruct the main premise: (Imp1 ∧ Imp2)
        if (conjOfImps !is FormulaNode.BinaryOpNode || conjOfImps.operator != and) return false
        val imp1 = conjOfImps.left
        val imp2 = conjOfImps.right

        // 2. Deconstruct the two implications: (P → Q) and (R → S)
        if (imp1 !is FormulaNode.BinaryOpNode || imp1.operator != implies) return false
        if (imp2 !is FormulaNode.BinaryOpNode || imp2.operator != implies) return false
        val p = imp1.left
        val q = imp1.right
        val r = imp2.left
        val s = imp2.right

        // 3. Deconstruct the second premise: (P ∨ R)
        if (disjOfAnts !is FormulaNode.BinaryOpNode || disjOfAnts.operator != or) return false
        val ant1 = disjOfAnts.left  // Antecedent 1
        val ant2 = disjOfAnts.right // Antecedent 2

        // 4. Deconstruct the conclusion: (Q ∨ S)
        if (conclusion !is FormulaNode.BinaryOpNode || conclusion.operator != or) return false
        val con1 = conclusion.left  // Consequent 1
        val con2 = conclusion.right // Consequent 2

        // 5. Check for a match.
        // The set of antecedents {ant1, ant2} must match the set {p, r}.
        val antecedentsMatch = (ant1 == p && ant2 == r) || (ant1 == r && ant2 == p)
        // The set of consequents {con1, con2} must match the set {q, s}.
        val consequentsMatch = (con1 == q && con2 == s) || (con1 == s && con2 == q)

        return antecedentsMatch && consequentsMatch
    }
    /**
     * Validate Absorption: (P → Q) |- P → (P ∧ Q)
     */
    private fun validateAbsorption(conclusionTree: FormulaNode, refs: List<FormulaNode>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 1, "Absorption") ?.let { return it }
        val res = checkAbsorption(antecedent = refs[0], conclusion = conclusionTree)
        return if (res) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Absorption.")
    }

    private fun checkAbsorption(antecedent: FormulaNode, conclusion: FormulaNode): Boolean {
        if (antecedent !is FormulaNode.BinaryOpNode || antecedent.operator != implies) return false
        if (conclusion !is FormulaNode.BinaryOpNode || conclusion.operator != implies) return false
        if (conclusion.left != antecedent.left) return false
        if (conclusion.right !is FormulaNode.BinaryOpNode || conclusion.right.operator != and) return false
        val concConj = conclusion.right
        val conjLeft = concConj.left
        val conjRight = concConj.right
        if (conjLeft == conjRight) return false
        val psMatch = antecedent.left == conclusion.left && (conjLeft == antecedent.left || conjLeft == antecedent.right)
        return psMatch && (antecedent.right == conjRight || antecedent.right == conjLeft)
    }

    /**
     * Validate Simplification: (P ∧ Q) |- P
     */
    private fun validateSimplification(conclusionTree: FormulaNode, refs: List<FormulaNode>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 1, "Simplification") ?.let { return it }
        val res = checkSimp(antecedent = refs[0], conclusion = conclusionTree)
        return if (res) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Simplification.")
    }

    private fun checkSimp(antecedent: FormulaNode, conclusion: FormulaNode): Boolean {
        if (antecedent !is FormulaNode.BinaryOpNode || antecedent.operator != and) return false
        return antecedent.left == conclusion || antecedent.right == conclusion
    }

    /**
     * Validate Conjunction: P, Q |- P ∧ Q
     */
    private fun validateConjunction(conclusionTree: FormulaNode, refs: List<FormulaNode>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 2, "Conjunction") ?.let { return it }
        val p1 = checkConj(f1 = refs[0], f2 = refs[1], conclusion = conclusionTree)
        return if (p1) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Conjunction.")
    }

    private fun checkConj(f1: FormulaNode, f2: FormulaNode, conclusion: FormulaNode): Boolean {
        if (conclusion !is FormulaNode.BinaryOpNode || conclusion.operator != and) return false
        return (conclusion.left == f1 && conclusion.right == f2) ||
                (conclusion.left == f2 && conclusion.right == f1)
    }

    /**
     * Validate Addition: P |- P ∨ Q
     */
    private fun validateAddition(conclusionTree: FormulaNode, refs: List<FormulaNode>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 1, "Addition") ?.let { return it }
        val res = checkAdd(antecedent = refs[0], conclusion = conclusionTree)
        return if (res) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Addition.")
    }

    private fun checkAdd(antecedent: FormulaNode, conclusion: FormulaNode): Boolean {
        if (conclusion !is FormulaNode.BinaryOpNode || conclusion.operator != or) return false
        return antecedent == conclusion.left || antecedent == conclusion.right
    }

    private fun validateRefs(refs: List<FormulaNode>, lineNum: Int, refCount: Int, ruleName: String): ValidationResult? {
        if (refs.size != refCount)
            return ValidationResult(false,
                                    "Line $lineNum: $ruleName requires $refCount reference line(s).")
        return null
    }
}
