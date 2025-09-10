package com.elsoft.whatthewff.logic

// File: Logic.kt
// This file will contain the core data models for our symbolic logic engine.

/**
 * Represents the different categories of symbols in our logic system.
 * Using an enum like this helps us write clear and robust validation rules later.
 * For example, we can check if an operator is followed by the correct number of formulas.
 */
enum class SymbolType {
    VARIABLE,        // Represents atomic propositions like p, q, r
    UNARY_OPERATOR,  // Represents operators that apply to one WFF (e.g., ¬)
    BINARY_OPERATOR, // Represents operators that connect two WFFs (e.g., ∧, ∨, →, ↔)
    LEFT_PAREN,      // The opening parenthesis '('
    RIGHT_PAREN      // The closing parenthesis ')'
}

/**
 * Represents a single "tile" in our game. Each tile has a visual symbol
 * that the user sees and a logical type that our engine uses for validation.
 *
 * Using a `data class` in Kotlin automatically gives us useful functions
 * for comparing and handling these objects.
 *
 * @property symbol The string representation of the tile (e.g., "p", "¬", "∧").
 * @property type The logical category of the tile, from the SymbolType enum.
 */
data class LogicTile(val symbol: String, val type: SymbolType)

/**
 * Represents a potential formula that the user has constructed on the game board.
 * It's essentially just an ordered list of the LogicTiles they've placed.
 * Our validation engine will take this object as input to determine if it's a WFF.
 *
 * Because this is a data class then two <code>Formula</code>s may be compared for equality.
 * This comparison results in a true if each of the tiles in the two <code>Formula</code>s are
 * in the same order and have the same symbols and SymbolTypes.
 *
 * @property tiles The ordered list of tiles making up the potential formula.
 */
data class Formula(val tiles: List<LogicTile>) {
    // Helper property to easily get the string representation of a formula
    val stringValue: String
        get() = tiles.joinToString(separator = "") { it.symbol }

    override fun toString(): String {
        return stringValue
    }
}

/**
 * An object to hold a pre-defined list of all the tiles available to the user.
 * This will be very useful when we build the UI, as we can just pull from this
 * list to create the symbol palette for the user to drag from.
 */
object AvailableTiles {
    // Variables

    // This val is the complete list of characters used as variables
    // This is used by the parser to parse WFFs from any source that may
    // use any single character as a variable.
    val allVariables = (('a'..'z').toList() + ('A'..'Z').toList())
        .map {
            LogicTile(it.toString(), SymbolType.VARIABLE)
        }

    // This next val is for the variables used in problem generation.
    val problemVariables = allVariables.filter { it.symbol in "p".."w" }

    // The following is used as a fallback in a couple of instances
    val p = allVariables.filter { it.symbol == "p" }.first()

    // Operators
    val not = LogicTile("¬", SymbolType.UNARY_OPERATOR)
    val and = LogicTile("∧", SymbolType.BINARY_OPERATOR)
    val or = LogicTile("∨", SymbolType.BINARY_OPERATOR)
    val implies = LogicTile("→", SymbolType.BINARY_OPERATOR)
    val iff = LogicTile("↔", SymbolType.BINARY_OPERATOR)

    // Parentheses
    val leftParen = LogicTile("(", SymbolType.LEFT_PAREN)
    val rightParen = LogicTile(")", SymbolType.RIGHT_PAREN)

    /**
     * A complete list of all defined tiles. We'll use this to populate the UI.
     */
    val operators = listOf(not, and, or, implies, iff)
    val grouping = listOf(leftParen, rightParen)
    val connectors = operators + grouping
    val allTiles = allVariables + operators + grouping
}

/**
 * Defines the standard rules of inference we will support.
 *
 * @property ruleName The user-friendly name of the rule.
 * @property abbreviation The standard abbreviation for the rule.
 */
enum class InferenceRule(val ruleName: String, val abbreviation: String) {
    MODUS_PONENS("Modus Ponens", "MP"),
    MODUS_TOLLENS("Modus Tollens", "MT"),
    HYPOTHETICAL_SYLLOGISM("Hypothetical Syllogism", "HS"),
    DISJUNCTIVE_SYLLOGISM("Disjunctive Syllogism", "DS"),
    CONSTRUCTIVE_DILEMMA("Constructive Dilemma", "CD"),
    ABSORPTION("Absorption", "Abs"),
    SIMPLIFICATION("Simplification", "Simp"),
    CONJUNCTION("Conjunction", "Conj"),
    ADDITION("Addition", "Add")
}

enum class ReplacementRule(val ruleName: String, val abbreviation: String) {
    DEMORGANS_THEOREM("De Morgan's Theorem", "DM"),
    COMMUTATION("Commutation", "Comm"),
    ASSOCIATION("Association", "Assoc"),
    DISTRIBUTION("Distribution", "Dist"),
    DOUBLE_NEGATION("Double Negation", "DN"),
    TRANSPOSITION("Transposition", "Trans"),
    MATERIAL_IMPLICATION("Material Implication", "MI"),
    MATERIAL_EQUIVALENCE("Material Equivalence", "ME"),
    EXPORTATION("Exporation", "Exp"),
    TAUTOLOGY("Tautology", "Tau")
}

/**
 * Represents the justification for a line in a proof.
 * A sealed class is perfect here, as a justification can only be one of a few specific types.
 */
sealed class Justification {
    // A property for displaying the justification in the UI.
    // A string representation for display in the UI.
    fun displayText(): String {
        return when (this) {
            is Premise     -> "Premise"
            is Assumption -> "Assumption"
            is Inference   -> "${lineReferences.joinToString(separator = ",")}: ${rule.abbreviation}"
            is Replacement -> "${lineReference}: ${rule.abbreviation}"
            is ImplicationIntroduction -> "${subproofStart}-${subproofEnd} II"
            is Reiteration -> "${lineReference}: Reit."
            is ReductioAdAbsurdum -> "${subproofStart}-${subproofEnd} RAA"
        }
    }

    // Represents a premise, which requires no further justification.
    object Premise : Justification()

    // A justification for a temporary assumption that starts a sub-proof.
    object Assumption : Justification()

    data class ReductioAdAbsurdum(
        val subproofStart: Int,
        val subproofEnd: Int,
        val contradictionLine: Int
    ) : Justification()

    // Represents a line derived from other lines using a rule of inference.
    data class Inference(
        val rule: InferenceRule,
        val lineReferences: List<Int> // The line numbers this rule applies to
    ) : Justification()

    data class Replacement(
        val rule: ReplacementRule,
        val lineReference: Int // The line numbers this rule applies to
    ) : Justification()

    data class Reiteration(val lineReference: Int) : Justification()

    // A justification for concluding an implication after a sub-proof is complete.
    data class ImplicationIntroduction(val subproofStart: Int, val subproofEnd: Int) : Justification()
}

/**
 * Represents a single numbered line in a proof.
 *
 * @property lineNumber The line number (starting from 1).
 * @property formula The WFF on this line.
 * @property justification How this line was derived.
 */
data class ProofLine(
    val lineNumber: Int,
    val formula: Formula,
    val justification: Justification,
    val depth: Int = 0 // Property to handle indentation and scope
)

/**
 * Represents a complete proof, which is a list of premises and derived lines.
 *
 * @property lines The ordered list of lines that make up the proof.
 */
data class Proof(val lines: List<ProofLine>)

