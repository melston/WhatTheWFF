// File: logic/ProofValidator.kt
// This file contains the logic for validating an entire proof.

package com.elsoft.whatthewff.logic

/**
 * A data class to hold the result of a proof validation.
 * This is more informative than a simple Boolean.
 *
 * @property isValid Whether the proof is valid.
 * @property errorMessage An optional message explaining why the proof is invalid.
 */
data class ValidationResult(val isValid: Boolean, val errorMessage: String? = null)

/**
 * A singleton object to provide functionality for validating proofs.
 */
object ProofValidator {

    /**
     * Public entry point for proof validation.
     */
    fun validate(proof: Proof): ValidationResult {
        val provenLines = mutableMapOf<Int, Formula>()
        for (line in proof.lines) {
            if (!WffValidator.validate(line.formula)) {
                return ValidationResult(false, "Line ${line.lineNumber}: Formula is not a WFF.")
            }
            val justificationResult = when (line.justification) {
                is Justification.Premise -> ValidationResult(true)
                is Justification.Inference -> validateInference(line, line.justification, provenLines)
            }
            if (!justificationResult.isValid) {
                return justificationResult
            }
            provenLines[line.lineNumber] = line.formula
        }
        return ValidationResult(true, "Proof is valid!")
    }

    private fun validateInference(
        currentLine: ProofLine,
        justification: Justification.Inference,
        provenLines: Map<Int, Formula>
    ): ValidationResult {
        val refFormulas = justification.lineReferences.map { refNum ->
            provenLines[refNum] ?: return ValidationResult(false, "Line ${currentLine.lineNumber} references non-existent line $refNum.")
        }
        return when (justification.rule) {
            InferenceRule.MODUS_PONENS -> validateModusPonens(currentLine.formula, refFormulas, currentLine.lineNumber)
            InferenceRule.MODUS_TOLLENS -> validateModusTollens(currentLine.formula, refFormulas, currentLine.lineNumber)
        }
    }

    /**
     * Validates Modus Ponens: (P → Q), P |- Q
     */
    private fun validateModusPonens(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        if (refs.size != 2) return ValidationResult(false, "Line $lineNum: Modus Ponens requires 2 reference lines.")
        val p1 = checkMP(implication = refs[0], antecedent = refs[1], result = resultFormula)
        val p2 = checkMP(implication = refs[1], antecedent = refs[0], result = resultFormula)
        return if (p1 || p2) ValidationResult(true) else ValidationResult(false, "Line $lineNum: Does not follow by Modus Ponens.")
    }

    private fun checkMP(implication: Formula, antecedent: Formula, result: Formula): Boolean {
        val (p, q) = deconstructImplication(implication) ?: return false
        return antecedent == p && result == q
    }

    /**
     * Validates Modus Tollens: (P → Q), ¬Q |- ¬P
     */
    private fun validateModusTollens(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        if (refs.size != 2) return ValidationResult(false, "Line $lineNum: Modus Tollens requires 2 reference lines.")
        val p1 = checkMT(implication = refs[0], negatedConsequent = refs[1], result = resultFormula)
        val p2 = checkMT(implication = refs[1], negatedConsequent = refs[0], result = resultFormula)
        return if (p1 || p2) ValidationResult(true) else ValidationResult(false, "Line $lineNum: Does not follow by Modus Tollens.")
    }

    private fun checkMT(implication: Formula, negatedConsequent: Formula, result: Formula): Boolean {
        val (p, q) = deconstructImplication(implication) ?: return false
        val innerQ = deconstructNegation(negatedConsequent) ?: return false
        val innerP = deconstructNegation(result) ?: return false
        return innerQ == q && innerP == p
    }

    /**
     * Deconstructs a formula of the form (P → Q) into P and Q.
     */
    private fun deconstructImplication(formula: Formula): Pair<Formula, Formula>? {
        val tiles = formula.tiles
        if (tiles.firstOrNull()?.type != SymbolType.LEFT_PAREN || tiles.lastOrNull()?.type != SymbolType.RIGHT_PAREN) return null
        var parenDepth = 0
        var mainOperatorIndex = -1
        for (i in 1 until tiles.size - 1) {
            when (tiles[i].type) {
                SymbolType.LEFT_PAREN -> parenDepth++
                SymbolType.RIGHT_PAREN -> parenDepth--
                SymbolType.BINARY_OPERATOR -> if (parenDepth == 0 && tiles[i].symbol == "→") {
                    mainOperatorIndex = i
                    break
                }
                else -> {}
            }
        }
        if (mainOperatorIndex == -1) return null
        val pTiles = tiles.subList(1, mainOperatorIndex)
        val qTiles = tiles.subList(mainOperatorIndex + 1, tiles.size - 1)
        if (pTiles.isEmpty() || qTiles.isEmpty()) return null
        return Pair(Formula(pTiles), Formula(qTiles))
    }

    /**
     * Deconstructs a formula of the form (¬P) into P.
     */
    private fun deconstructNegation(formula: Formula): Formula? {
        val tiles = formula.tiles
        if (tiles.firstOrNull()?.type != SymbolType.LEFT_PAREN || tiles.lastOrNull()?.type != SymbolType.RIGHT_PAREN) return null
        if (tiles.getOrNull(1)?.type != SymbolType.UNARY_OPERATOR) return null

        val innerTiles = tiles.subList(2, tiles.size - 1)
        val innerFormula = Formula(innerTiles)

        // The inner part must also be a valid WFF.
        return if (WffValidator.validate(innerFormula)) innerFormula else null
    }
}
