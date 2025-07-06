// File: logic/WffParser.kt
// This file contains the logic for parsing a flat list of LogicTiles (a Formula)
// into a hierarchical FormulaNode syntax tree.

package com.elsoft.whatthewff.logic

/**
 * A singleton object responsible for parsing formulas.
 * This effectively replaces the old WffValidator by checking for well-formedness
 * and producing a useful syntax tree at the same time.
 */
object WffParser {

    /**
     * Public entry point for parsing. Takes a Formula and returns a FormulaNode tree.
     *
     * @param formula The formula to parse.
     * @return The root FormulaNode of the syntax tree if parsing is successful and
     * consumes the entire formula, otherwise null.
     */
    fun parse(formula: Formula): FormulaNode? {
        if (formula.tiles.isEmpty()) {
            return null
        }
        // Start the recursive parsing from the beginning of the tile list.
        val result = parseNode(formula.tiles, 0)

        // A successful parse must do two things:
        // 1. Return a non-null node (result != null).
        // 2. Consume all tiles in the formula (result.second == formula.tiles.size).
        return if (result != null && result.second == formula.tiles.size) {
            result.first // Return just the parsed node
        } else {
            null // The formula was not a single, valid WFF.
        }
    }

    /**
     * The core recursive parsing function. It attempts to parse a single FormulaNode
     * starting at a given index.
     *
     * @param tiles The full list of tiles.
     * @param startIndex The index to begin parsing from.
     * @return A Pair containing the parsed FormulaNode and the index of the next tile
     * *after* the parsed node. Returns null if parsing fails.
     */
    private fun parseNode(tiles: List<LogicTile>, startIndex: Int): Pair<FormulaNode, Int>? {
        if (startIndex >= tiles.size) return null

        val currentTile = tiles[startIndex]

        // Rule 1: A single variable is a WFF.
        if (currentTile.type == SymbolType.VARIABLE) {
            val node = FormulaNode.VariableNode(currentTile)
            return Pair(node, startIndex + 1) // Consume one tile.
        }

        // Rules 2 & 3: Parenthesized formulas must start with '('.
        if (currentTile.type == SymbolType.LEFT_PAREN) {
            val innerStartIndex = startIndex + 1
            val firstInnerTile = tiles.getOrNull(innerStartIndex)

            // Case 1: Check for a Unary Operation, e.g., (¬ WFF)
            if (firstInnerTile?.type == SymbolType.UNARY_OPERATOR) {
                val operator = firstInnerTile
                // The part after the operator must be a valid WFF.
                val innerWffResult = parseNode(tiles, innerStartIndex + 1) ?: return null
                val innerNode = innerWffResult.first
                val nextIndexAfterWff = innerWffResult.second

                // Check for the closing parenthesis.
                if (tiles.getOrNull(nextIndexAfterWff)?.type == SymbolType.RIGHT_PAREN) {
                    val node = FormulaNode.UnaryOpNode(operator, innerNode)
                    return Pair(node, nextIndexAfterWff + 1) // Consume the ')'
                }
            }

            // Case 2: Check for a Binary Operation, e.g., (WFF op WFF)
            // Start by parsing the left-hand side WFF.
            val leftNodeResult = parseNode(tiles, innerStartIndex) ?: return null
            val leftNode = leftNodeResult.first
            val nextIndexAfterLeft = leftNodeResult.second

            val operatorTile = tiles.getOrNull(nextIndexAfterLeft)
            if (operatorTile?.type == SymbolType.BINARY_OPERATOR) {
                // Now, parse the right-hand side.
                val rightNodeResult = parseNode(tiles, nextIndexAfterLeft + 1) ?: return null
                val rightNode = rightNodeResult.first
                val nextIndexAfterRight = rightNodeResult.second

                // Finally, check for the closing parenthesis.
                if (tiles.getOrNull(nextIndexAfterRight)?.type == SymbolType.RIGHT_PAREN) {
                    val node = FormulaNode.BinaryOpNode(operatorTile, leftNode, rightNode)
                    return Pair(node, nextIndexAfterRight + 1) // Consume the ')'
                }
            }
        }
        // If it doesn't match any valid structure, fail the parse.
        return null
    }
}
