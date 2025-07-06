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
     *
     * Validate each line in the proof based on the previous lines.  If valid,
     * add it to the list of proven lines.  If all lines are valid then the proof
     * is valid.
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

    private val validationStrategies = mapOf(
        InferenceRule.MODUS_PONENS to ::validateModusPonens,
        InferenceRule.MODUS_TOLLENS to ::validateModusTollens,
        InferenceRule.HYPOTHETICAL_SYLLOGISM to ::validateHypotheticalSyllogism,
        InferenceRule.DISJUNCTIVE_SYLLOGISM to ::validateDisjunctiveSyllogism,
        InferenceRule.CONSTRUCTIVE_DILEMMA to ::validateConstructiveDilemma,
        InferenceRule.ABSORPTION to ::validateAbsorption,
        InferenceRule.SIMPLIFICATION to ::validateSimplification,
        InferenceRule.CONJUNCTION to ::validateConjunction,
        InferenceRule.ADDITION to ::validateAddition,
        InferenceRule.DEMORGANS_THEOREM to ::validateDeMorgansTheorem,
        InferenceRule.COMMUTATION to ::validateCommutation,
        InferenceRule.ASSOCIATION to ::validateAssociation,
        InferenceRule.DISTRIBUTION to ::validateDistribution,
        InferenceRule.DOUBLE_NEGATION to ::validateDoubleNegation,
        InferenceRule.TRANSPOSITON to ::validateTransposition,
        InferenceRule.MATERIAL_IMPLICATION to ::validateMaterialImplication,
        InferenceRule.MATERIAL_EQUIVALENCE to ::validateMaterialEquivalence,
        InferenceRule.EXPORTATION to ::validateExportation,
        InferenceRule.TAUTOLOGY to ::validateTautology
        // Add any other rules from your InferenceRule enum here
    )

    private fun validateInference(
        currentLine: ProofLine,
        justification: Justification.Inference,
        provenLines: Map<Int, Formula>
    ): ValidationResult {
        val refFormulas = justification.lineReferences.map { refNum ->
            provenLines[refNum] ?: return ValidationResult(false,
                                                "Line ${currentLine.lineNumber} references non-existent line $refNum.")
        }
        val strategy = validationStrategies[justification.rule]
        return if (strategy != null) {
            strategy(currentLine.formula, refFormulas, currentLine.lineNumber)
        } else {
            ValidationResult(false, "Validation for ${justification.rule.ruleName} is not implemented.")
        }
    }

    /**
     * Validates Modus Ponens: (P → Q), P |- Q
     */
    private fun validateModusPonens(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        // The ?.let {...} executes the lambda only if the value is not null.
        validateRefs(refs, lineNum, 2, "Modus Ponens") ?.let { return it }
        val p1 = checkMP(implication = refs[0], antecedent = refs[1], result = resultFormula)
        val p2 = checkMP(implication = refs[1], antecedent = refs[0], result = resultFormula)
        return if (p1 || p2) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Modus Ponens.")
    }

    private fun checkMP(implication: Formula, antecedent: Formula, result: Formula): Boolean {
        val (p, q) = deconstructImplication(implication) ?: return false
        return antecedent == p && result == q
    }

    /**
     * Validates Modus Tollens: (P → Q), ¬Q |- ¬P
     */
    private fun validateModusTollens(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 2, "Modus Tollens") ?.let { return it }
        val p1 = checkMT(implication = refs[0], negatedConsequent = refs[1], result = resultFormula)
        val p2 = checkMT(implication = refs[1], negatedConsequent = refs[0], result = resultFormula)
        return if (p1 || p2) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Modus Tollens.")
    }

    private fun checkMT(implication: Formula, negatedConsequent: Formula, result: Formula): Boolean {
        val (p, q) = deconstructImplication(implication) ?: return false
        val innerQ = deconstructNegation(negatedConsequent) ?: return false
        val innerP = deconstructNegation(result) ?: return false
        return innerQ == q && innerP == p
    }

    /**
     * Validate Hypothetical Syllogism: (P → Q), (Q → R) |- P → R
     */
    private fun validateHypotheticalSyllogism(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 2, "Hypothetical Syllogism") ?.let { return it }
        val p1 = checkHS(f1 = refs[0], f2 = refs[1], result = resultFormula)
        val p2 = checkHS(f1 = refs[1], f2 = refs[0], result = resultFormula)
        return if (p1 || p2) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Hypothetical Syllogism.")
    }

    private fun checkHS(f1: Formula, f2: Formula, result: Formula): Boolean {
        val (_, q1) = deconstructImplication(f1) ?: return false
        val (q2, r) = deconstructImplication(f2) ?: return false
        return q1 == q2 && result == r
    }

    /**
     * Validate Disjunctive Syllogism: (P ∨ Q), ¬P |- Q
     */
    private fun validateDisjunctiveSyllogism(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 2, "Disjunctive Syllogism") ?.let { return it }
        val p1 = checkDS(f1 = refs[0], negatedP = refs[1], result = resultFormula)
        val p2 = checkDS(f1 = refs[1], negatedP = refs[0], result = resultFormula)
        return if (p1 || p2) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Disjunctive Syllogism.")
    }

    private fun checkDS(f1: Formula, negatedP: Formula, result: Formula): Boolean {
        val (p, q) = deconstructOr(f1) ?: return false
        val innerP = deconstructNegation(negatedP) ?: return false
        return innerP == p && result == q
    }

    /**
     * Validate Constructive Dilemma: (P → Q) ∧ (R → S) |- (Q → S)
     */
    private fun validateConstructiveDilemma(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 1, "Constructive Dilemma") ?.let { return it }
        val res = checkCD(antecedent = refs[0], result = resultFormula)
        return if (res) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Constructive Dilemma.")
    }

    private fun checkCD(antecedent: Formula, result: Formula): Boolean {
        val (f1, f2) = deconstructAnd(antecedent) ?: return false
        val (p, q) = deconstructImplication(f1) ?: return false
        val (r, s) = deconstructImplication(f2) ?: return false
        val (r1, r2) = deconstructImplication(result) ?: return false
        return p == r && q == s && ( (r1 == q && r2 == s) || (r1 == s && r2 == q) )
    }

    /**
     * Validate Absorption: (P → Q) |- P ∧ (P ∧ Q)
     */
    private fun validateAbsorption(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 1, "Absorption") ?.let { return it }
        val res = checkAbsorption(antecedent = refs[0], result = resultFormula)
        return if (res) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Absorption.")
    }

    private fun checkAbsorption(antecedent: Formula, result: Formula): Boolean {
        val (p, q) = deconstructImplication(antecedent) ?: return false
        val (r1, r2) = deconstructAnd(result) ?: return false
        val (r2a, r2b) = deconstructAnd(r2) ?: return false
        return p == r1 && p == r2a && q == r2b
    }

    /**
     * Validate Simplification: (P ∧ Q) |- P
     */
    private fun validateSimplification(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 1, "Simplification") ?.let { return it }
        val res = checkSimp(antecedent = refs[0], result = resultFormula)
        return if (res) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Simplification.")
    }

    private fun checkSimp(antecedent: Formula, result: Formula): Boolean {
        val (p, q) = deconstructAnd(antecedent) ?: return false
        return p == result || q == result
    }

    /**
     * Validate Conjunction: P, Q |- P ∧ Q
     */
    private fun validateConjunction(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 2, "Conjunction") ?.let { return it }
        val p1 = checkConj(f1 = refs[0], f2 = refs[1], result = resultFormula)
        return if (p1) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Conjunction.")
    }

    private fun checkConj(f1: Formula, f2: Formula, result: Formula): Boolean {
        val (p, q) = deconstructAnd(result) ?: return false
        return (p == f1 && q == f2) || (p == f2 && q == f1)
    }

    /**
     * Validate Addition: P |- P ∨ Q
     */
    private fun validateAddition(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        validateRefs(refs, lineNum, 1, "Addition") ?.let { return it }
        val res = checkAdd(antecedent = refs[0], result = resultFormula)
        return if (res) ValidationResult(true)
               else ValidationResult(false, "Line $lineNum: Does not follow by Addition.")
    }

    private fun checkAdd(antecedent: Formula, result: Formula): Boolean {
        val (p, q) = deconstructOr(result) ?: return false
        return p == antecedent || q == antecedent
    }

    //  Replacements.  Any of hte following logically equivalent expressions can replace
    //  each other wherever they occur

    /**
     * Validate DeMorgan's Theorem: ¬(P ∧ Q) == ¬P ∨ ¬Q
     *                              ¬(P ∨ Q) == ¬P ∧ ¬Q
     */
    private fun validateDeMorgansTheorem(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        return ValidationResult(false, "Validation for DeMorgan's Theorem is not implemented.")
    }

    /**
     * Validate Commutation: (P ∧ Q) == (Q ∧ P)
     *                       (P ∨ Q) == (Q ∨ P)
     */
    private fun validateCommutation(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        return ValidationResult(false, "Validation for Commutation is not implemented.")
    }

    /**
     * Validate Association: (P ∧ (Q ∧ R)) == ((P ∧ Q) ∧ R)
     *                       (P ∨ (Q ∨ R)) == ((P ∨ Q) ∨ R)
     */
    private fun validateAssociation(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        return ValidationResult(false, "Validation for Association is not implemented.")
    }

    /**
     * Validate Distribution: (P ∨ (Q ∧ R)) == (P ∨ Q) ∧ (P ∨ R)
     *                        (P ∧ (Q ∨ R)) == (P ∧ Q) ∨ (P ∧ R)
     */
    private fun validateDistribution(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        return ValidationResult(false, "Validation for Distribution is not implemented.")
    }

    /**
     * Validate Double Negation: ¬¬P == P
     *                             P == ¬¬P
     */
    private fun validateDoubleNegation(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        return ValidationResult(false, "Validation for Double Negation is not implemented.")
    }

    /**
     * Validate Transposition: (P → Q) == (¬Q → ¬P)
     */
    private fun validateTransposition(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        return ValidationResult(false, "Validation for Transposition is not implemented.")
    }

    /**
     * Validate Material Implication: (P → Q) == (¬P ∨ Q)
     */
    private fun validateMaterialImplication(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        return ValidationResult(false, "Validation for Material Implication is not implemented.")
    }

    /**
     * Validate Material Equivalence: (P == Q) == [(P → Q) ∧ (Q → P)]
     *                                (P == Q) == [(P ∧ Q) ∨ (¬P ∧ ¬Q)]
     */
    private fun validateMaterialEquivalence(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        return ValidationResult(false, "Validation for Material Equivalence is not implemented.")
    }

    /**
     * Validate Exportation: (P ∧ Q) → R == (P → (Q → R))
     */
    private fun validateExportation(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        return ValidationResult(false, "Validation for Exportation is not implemented.")
    }

    /**
     * Validate Tautology: P == (P ∨ P)
     *                     P == (P ∧ P)
     */
    private fun validateTautology(resultFormula: Formula, refs: List<Formula>, lineNum: Int): ValidationResult {
        return ValidationResult(false, "Validation for Tautology is not implemented.")
    }

    /**
     * Deconstructs a formula of the form (P → Q) into P and Q.
     */
    private fun deconstructImplication(formula: Formula): Pair<Formula, Formula>? {
        return deconstructBinaryOperator(formula, "→")
    }

    /**
     * Deconstructs a formula of the form (P ∧ Q) into P and Q.
     */
    private fun deconstructAnd(formula: Formula): Pair<Formula, Formula>? {
        return deconstructBinaryOperator(formula, "∧")
    }

    /**
     * Deconstructs a formula of the form (P ∨ Q) into P and Q.
     */
    private fun deconstructOr(formula: Formula): Pair<Formula, Formula>? {
        return deconstructBinaryOperator(formula, "∨")
    }

    /**
     * Deconstructs a formula of the form (P ↔ Q) into P and Q.
     */
    private fun deconstructIFF(formula: Formula): Pair<Formula, Formula>? {
        return deconstructBinaryOperator(formula, "↔")
    }

    /**
     * Deconstructs a formula of the form (P &otimes; Q) into P and Q, where
     * &otimes; is one of the logical binary operators (∧, ∨, →, ↔).
     */
    private fun deconstructBinaryOperator(formula: Formula, operator: String): Pair<Formula, Formula>? {
        val tiles = formula.tiles
        if (tiles.firstOrNull()?.type != SymbolType.LEFT_PAREN || tiles.lastOrNull()?.type != SymbolType.RIGHT_PAREN) return null
        var parenDepth = 0
        var mainOperatorIndex = -1
        // Don't look at the opening/closing parens.
        for (i in 1 until tiles.size - 1) {
            when (tiles[i].type) {
                SymbolType.LEFT_PAREN -> parenDepth++
                SymbolType.RIGHT_PAREN -> parenDepth--
//                SymbolType.BINARY_OPERATOR -> if (parenDepth == 0 && tiles[i].symbol == "→") {
                SymbolType.BINARY_OPERATOR -> if (parenDepth == 0 && tiles[i].symbol == operator) {
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

    private fun validateRefs(refs: List<Formula>, lineNum: Int, refCount: Int, ruleName: String): ValidationResult? {
        if (refs.size != refCount)
            return ValidationResult(false,
                                    "Line $lineNum: $ruleName requires $refCount reference line(s).")
        return null
    }
}
