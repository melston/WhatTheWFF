// File: logic/ProofValidator.kt
// This file contains the logic for validating an entire proof.

package com.elsoft.whatthewff.logic

import com.elsoft.whatthewff.logic.AvailableTiles.and
import com.elsoft.whatthewff.logic.AvailableTiles.implies
import com.elsoft.whatthewff.logic.AvailableTiles.not
import com.elsoft.whatthewff.logic.RuleGenerators.treeToFormula

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
        var currentDepth = 0

        for ((index, line) in proof.lines.withIndex()) {
            // --- Scope Validation ---
            if (line.depth > currentDepth + 1 || (line.depth < currentDepth && line.justification !is Justification.ImplicationIntroduction && line.justification !is Justification.ReductioAdAbsurdum)) {
                return ValidationResult(
                    false,
                    "Invalid indentation change.",
                    line.lineNumber
                )
            }
            currentDepth = line.depth

            val currentTree = WffParser.parse(line.formula)
            if (currentTree == null) {
                return ValidationResult(
                    false,
                    "Not a Well-Formed Formula.",
                    line.lineNumber
                )
            }

            // Determine all lines currently in scope
            val inScopeLines = proof.lines.take(index).filter { prevLine ->
                prevLine.depth <= line.depth ||
                // Allow referencing the assumption of the current sub-proof
                (line.depth > 0 &&
                 prevLine.lineNumber == proof.lines.lastOrNull {
                     it.depth < line.depth && it.justification is Justification.Assumption
                 }?.lineNumber)
            }.map { it.lineNumber }.toSet()


            val justificationResult = when (val justification = line.justification) {
                is Justification.Premise ->
                    if (line.depth == 0) ValidationResult(true)
                    else ValidationResult(
                        false,
                        "Premises must be at the main proof level."
                    )

                is Justification.Assumption ->
                    if (index > 0 && line.depth == proof.lines[index - 1].depth + 1)
                        ValidationResult(true)
                    else ValidationResult(false, "Assumptions must start a new sub-proof.")

                is Justification.Inference ->
                    validateInference(
                        currentTree,
                        justification,
                        provenTrees,
                        inScopeLines,
                        line.lineNumber
                    )

                is Justification.Replacement ->
                    validateReplacement(
                        currentTree,
                        justification,
                        provenTrees,
                        inScopeLines
                    )

                is Justification.ImplicationIntroduction ->
                    validateImplicationIntroduction(currentTree, justification, proof, line)

                is Justification.ReductioAdAbsurdum ->
                    validateReductioAdAbsurdum(currentTree, justification, proof, line)

                is Justification.Reiteration ->
                    validateReiteration(currentTree, justification, proof, inScopeLines)
            }

            if (!justificationResult.isValid) {
                return justificationResult.copy(errorLine = line.lineNumber)
            }
            provenTrees[line.lineNumber] = currentTree
        }

        return ValidationResult(true, "Proof is valid!")
    }

    /**
     * Validates a line derived by a rule of replacement.
     */
    private fun validateReplacement(
        conclusionTree: FormulaNode,
        justification: Justification.Replacement,
        provenTrees: Map<Int, FormulaNode>,
        inScopeLines: Set<Int>
    ): ValidationResult {
        // --- Scope Check ---
        if (justification.lineReference !in inScopeLines)
            return ValidationResult(
                false,
                "Reference line ${justification.lineReference} is out of scope."
            )

        val premiseNode = provenTrees[justification.lineReference]
                          ?: return ValidationResult(
                              false,
                              "Reference line ${justification.lineReference} not found."
                          )

        val possibleOutcomes = RuleReplacer.apply(justification.rule, premiseNode)

        return if (conclusionTree in possibleOutcomes) ValidationResult(true)
        else ValidationResult(
            false,
            "Conclusion does not follow by ${justification.rule.ruleName}."
        )
    }

    /**
     * Validate Implication Introduction:  This closes a sub-proof and introduces a new
     * implication with the antecedents being the assumptions of the sub-proof and the
     * consequent being the last line of the sub-proof.
     */
    private fun validateImplicationIntroduction(
        conclusionTree: FormulaNode,
        justification: Justification.ImplicationIntroduction,
        fullProof: Proof,
        currentLine: ProofLine
    ): ValidationResult {
        if (currentLine.depth != fullProof.lines.getOrNull(justification.subproofStart - 2)?.depth ?: 0) {
            val msg =
                "Implication Introduction must end the sub-proof at the correct indentation level."
            return ValidationResult(false, msg)
        }
        if (conclusionTree !is FormulaNode.BinaryOpNode || conclusionTree.operator != implies) {
            val msg = "Implication Introduction must result in an implication."
            return ValidationResult(false, msg)
        }

        val assumptionLine = fullProof.lines.getOrNull(justification.subproofStart - 1)
        val subproofConclusionLine =
            fullProof.lines.getOrNull(justification.subproofEnd - 1)

        if (assumptionLine == null || subproofConclusionLine == null) {
            return ValidationResult(
                false,
                "Sub-proof lines are invalid."
            )
        }
        if (assumptionLine.justification !is Justification.Assumption) {
            val msg = "Sub-proof for II must start with an Assumption."
            return ValidationResult(false, msg)
        }

        val assumptionTree = WffParser.parse(assumptionLine.formula)
        val subproofConclusionTree = WffParser.parse(subproofConclusionLine.formula)

        if (assumptionTree != conclusionTree.left) {
            val msg =
                "The antecedent does not match the assumption on line ${justification.subproofStart}."
            return ValidationResult(false, msg)
        }
        if (subproofConclusionTree != conclusionTree.right) {
            val msg =
                "The consequent does not match the conclusion of the sub-proof on line ${justification.subproofEnd}."
            return ValidationResult(false, msg)
        }

        return ValidationResult(true)
    }

    /**
     * Validates a sub-proof by RAA.
     */
    private fun validateReductioAdAbsurdum(
        conclusionTree: FormulaNode,
        justification: Justification.ReductioAdAbsurdum,
        fullProof: Proof,
        currentLine: ProofLine
    ): ValidationResult {
        if (currentLine.depth != fullProof.lines.getOrNull(justification.subproofStart - 2)?.depth ?: 0) {
            val msg = "RAA must end the sub-proof at the correct indentation level."
            return ValidationResult(false, msg)
        }
        val assumptionLine = fullProof.lines.getOrNull(justification.subproofStart - 1)
                             ?: return ValidationResult(
                                 false,
                                 "Sub-proof start line not found."
                             )
        val contradictionLine =
            fullProof.lines.getOrNull(justification.contradictionLine - 1)
            ?: return ValidationResult(false, "Contradiction line not found.")

        if (assumptionLine.justification !is Justification.Assumption) {
            val msg = "Sub-proof for RAA must start with an Assumption."
            return ValidationResult(false, msg)
        }

        val assumptionTree = WffParser.parse(assumptionLine.formula)
        val contradictionTree = WffParser.parse(contradictionLine.formula)

        val expectedConclusion = FormulaNode.UnaryOpNode(not, assumptionTree!!)

        if (conclusionTree != expectedConclusion) {
            val errorMessage = "Conclusion must be the negation of the assumption."
            return ValidationResult(false, errorMessage)
        }

        if (contradictionTree !is FormulaNode.BinaryOpNode || contradictionTree.operator != and ||
            WffParser.parse(RuleGenerators.fNeg(treeToFormula(contradictionTree.left))) != contradictionTree.right
        ) {

            val errorMessage =
                "Line ${justification.contradictionLine} is not a valid contradiction of the form (P & ~P)."
            return ValidationResult(false, errorMessage)
        }
        return ValidationResult(true)
    }

    /**
     * Validates a (sub-proof) line derived by reiteration
     */
    private fun validateReiteration(
        conclusionTree: FormulaNode,
        justification: Justification.Reiteration,
        fullProof: Proof,
        inScopeLines: Set<Int>
    ): ValidationResult {
        if (justification.lineReference !in inScopeLines) {
            val errorMessage =
                "Cannot reiterate line ${justification.lineReference} as it is out of scope."
            return ValidationResult(false, errorMessage)
        }

        val referencedLine = fullProof.lines.getOrNull(justification.lineReference - 1)
                             ?: return ValidationResult(
                                 false,
                                 "Referenced line for reiteration not found."
                             )

        val referencedTree = WffParser.parse(referencedLine.formula)

        return if (conclusionTree == referencedTree) {
            ValidationResult(true)
        } else {
            val errorMessage =
                "Reiterated formula does not match the formula on line ${justification.lineReference}."
            ValidationResult(false, errorMessage)
        }
    }

    /**
     * Validates a line derived by a rule of inference
     */
    private fun validateInference(
        conclusionTree: FormulaNode,
        justification: Justification.Inference,
        provenTrees: Map<Int, FormulaNode>,
        inScopeLines: Set<Int>,
        lineNumber: Int
    ): ValidationResult {
        // --- Scope Check (unchanged) ---
        justification.lineReferences.forEach {
            if (it !in inScopeLines) {
                val errorMessage = "Reference line $it is out of scope."
                return ValidationResult(false, errorMessage)
            }
        }

        // --- KEY CHANGE: All logic is now deferred to the central engine ---
        val refFormulas =
            justification.lineReferences
                .mapNotNull { provenTrees[it] }
                .map { treeToFormula(it) }
                .toSet()
        val conclusionFormula = treeToFormula(conclusionTree)

        return if (InferenceRuleEngine.isValidInference(
                justification.rule,
                refFormulas,
                conclusionFormula
            )
        ) {
            ValidationResult(true)
        } else {
            val errorMessage =
                "Line ${lineNumber} does not follow from the premises by ${justification.rule.ruleName}."
            ValidationResult(false, errorMessage)
        }
    }
}
