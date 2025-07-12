// File: logic/WffParser.kt
// This file contains a robust parser that correctly handles top-level binary
// expressions and ensures consistent tree generation.

package com.elsoft.whatthewff.logic

/**
 * A singleton object responsible for parsing formulas into syntax trees.
 */
object WffParser {

    /**
     * Public entry point for parsing.
     */
    fun parse(formula: Formula): FormulaNode? {
        if (formula.tiles.isEmpty()) return null
        val result = parseExpression(formula.tiles)
        // A successful parse must consume all tiles.
        return if (result != null && result.second == formula.tiles.size) {
            result.first
        } else {
            null
        }
    }

    /**
     * The main recursive parsing function.
     * @return A Pair containing the parsed node and the index of the next token.
     */
    private fun parseExpression(tiles: List<LogicTile>, startIndex: Int = 0): Pair<FormulaNode, Int>? {
        // An expression is a term, possibly followed by a binary operator and another term.
        var (left, nextIndex) = parseTerm(tiles, startIndex) ?: return null

        // Check if there's a binary operator next
        val operator = tiles.getOrNull(nextIndex)
        if (operator?.type == SymbolType.BINARY_OPERATOR) {
            // If so, parse the right-hand side
            val (right, finalIndex) = parseExpression(tiles, nextIndex + 1) ?: return null
            val node = FormulaNode.BinaryOpNode(operator, left, right)
            return Pair(node, finalIndex)
        }

        // If no operator, it's just the term itself.
        return Pair(left, nextIndex)
    }

    /**
     * Parses a "term", which is either a single variable, a negated term,
     * or a parenthesized expression.
     */
    private fun parseTerm(tiles: List<LogicTile>, startIndex: Int): Pair<FormulaNode, Int>? {
        val currentTile = tiles.getOrNull(startIndex) ?: return null

        return when (currentTile.type) {
            SymbolType.VARIABLE -> {
                Pair(FormulaNode.VariableNode(currentTile), startIndex + 1)
            }
            SymbolType.UNARY_OPERATOR -> {
                // Parse the term that follows the negation.
                val (operand, nextIndex) = parseTerm(tiles, startIndex + 1) ?: return null
                val node = FormulaNode.UnaryOpNode(currentTile, operand)
                Pair(node, nextIndex)
            }
            SymbolType.LEFT_PAREN -> {
                // The content inside the parentheses is a full new expression.
                val (expression, nextIndex) = parseExpression(tiles, startIndex + 1) ?: return null
                // Check for the closing parenthesis.
                if (tiles.getOrNull(nextIndex)?.type == SymbolType.RIGHT_PAREN) {
                    Pair(expression, nextIndex + 1) // Consume ')'
                } else {
                    null // Mismatched parentheses
                }
            }
            else -> null // Unexpected token
        }
    }
}
