// File: logic/WffValidator.kt
// This file contains the core logic for validating Well-Formed Formulas.

package com.elsoft.whatthewff.logic

/**
 * A singleton object that provides functionality to validate WFFs.
 * Using an object makes it a singleton, so we don't need to create new instances.
 */
object WffValidator {

    /**
     * The public entry point for validation.
     * It checks if a given formula is a valid WFF.
     *
     * @param formula The formula to validate.
     * @return `true` if the formula is a valid WFF, `false` otherwise.
     */
    fun validate(formula: Formula): Boolean {
        if (formula.tiles.isEmpty()) {
            return false
        }
        val resultIndex = parseWff(formula.tiles, 0)
        // A successful parse must consume all tiles.
        // If the result index equals the list size, it means the whole formula is one valid WFF.
        return resultIndex == formula.tiles.size
    }

    /**
     * The core recursive parsing function. It tries to parse a WFF starting from a given index.
     * 
     * A WFF is either:
     * - a VARIABLE,
     * - a LEFT_PAREN followed by a WFF followed by a RIGHT_PAREN,
     * - a UNARY_OPERATOR followed by a WFF (surrounded by parens),
     * - a WFF followed by a BINARY_OPERATOR followed by a WFF (surrounded by parens)
     *
     * @param tiles The list of all tiles in the formula.
     * @param startIndex The index from which to start parsing.
     * @return The index of the token *after* the parsed WFF, or -1 if parsing fails.
     */
    private fun parseWff(tiles: List<LogicTile>, startIndex: Int): Int {
        // Cannot parse if the start index is out of bounds.
        if (startIndex >= tiles.size) return -1

        val currentTile = tiles[startIndex]

        // Rule 1: A single variable is a WFF.
        if (currentTile.type == SymbolType.VARIABLE) {
            return startIndex + 1 // Consumed one tile.
        }

        // Rules 2 & 3: Parenthesized formulas must start with '('.
        if (currentTile.type == SymbolType.LEFT_PAREN) {
            val innerStartIndex = startIndex + 1

            // Attempt to parse a negation: (¬WFF)
            val nextTile = tiles.getOrNull(innerStartIndex)
            if (nextTile?.type == SymbolType.UNARY_OPERATOR) {
                // Parse the expression that comes after the '¬'.
                val wffEndIndex = parseWff(tiles, innerStartIndex + 1)
                if (wffEndIndex == -1) return -1 // Inner part is not a WFF.

                // Check for the closing parenthesis.
                return if (tiles.getOrNull(wffEndIndex)?.type == SymbolType.RIGHT_PAREN) {
                    wffEndIndex + 1 // Success, consume the ')'.
                } else {
                    -1 // Failure, missing ')'.
                }
            }

            // Not a negation. Attempt to parse a binary operation: (WFF B WFF)
            // First, parse the first WFF (P).
            val firstWffEndIndex = parseWff(tiles, innerStartIndex)
            if (firstWffEndIndex == -1) return -1 // Left side is not a WFF.

            // Check for a binary operator after the first WFF.
            val operatorTile = tiles.getOrNull(firstWffEndIndex)
            if (operatorTile?.type == SymbolType.BINARY_OPERATOR) {
                // Parse the second WFF (Q).
                val secondWffEndIndex = parseWff(tiles, firstWffEndIndex + 1)
                if (secondWffEndIndex == -1) return -1 // Right side is not a WFF.

                // Check for the closing parenthesis.
                return if (tiles.getOrNull(secondWffEndIndex)?.type == SymbolType.RIGHT_PAREN) {
                    secondWffEndIndex + 1 // Success, consume the ')'.
                } else {
                    -1 // Failure, missing ')'.
                }
            }
        }

        // If it doesn't match any of the above rules, it's not a WFF.
        return -1
    }
}
