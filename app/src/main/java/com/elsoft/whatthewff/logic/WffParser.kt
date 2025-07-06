// File: logic/WffParser.kt
// This file contains an advanced parser that understands operator precedence,
// allowing for more natural formula construction (e.g., ¬p ∨ q).

package com.elsoft.whatthewff.logic

/**
 * A stateful helper class for parsing. It keeps track of the current position
 * in the list of tiles, which simplifies the recursive parsing logic.
 */
private class ParserState(val tiles: List<LogicTile>) {
    var position = 0
    fun hasMore(): Boolean = position < tiles.size
    fun current(): LogicTile? = tiles.getOrNull(position)
    fun advance() { position++ }
}

/**
 * A singleton object responsible for parsing formulas into syntax trees.
 * This version uses a recursive descent method that respects operator precedence.
 */
object WffParser {

    /**
     * Public entry point for parsing.
     * @return The root FormulaNode if parsing is successful, otherwise null.
     */
    fun parse(formula: Formula): FormulaNode? {
        if (formula.tiles.isEmpty()) return null
        val state = ParserState(formula.tiles)
        val resultNode = parseBinaryExpression(state)
        // A successful parse must consume all tiles.
        return if (state.hasMore()) null else resultNode
    }

    /**
     * Parses binary expressions (∧, ∨, →, ↔).
     * This is the entry point for parsing expressions with the lowest precedence.
     */
    private fun parseBinaryExpression(state: ParserState, currentPrecedence: Int = 0): FormulaNode? {
        var left = parseUnary(state) ?: return null

        while (state.hasMore()) {
            val operator = state.current()
            if (operator?.type != SymbolType.BINARY_OPERATOR) break

            // For now, all binary operators have the same precedence.
            // This is where more complex precedence logic would go if needed.

            state.advance() // Consume the operator
            val right = parseUnary(state) ?: return null
            left = FormulaNode.BinaryOpNode(operator, left, right)
        }
        return left
    }

    /**
     * Parses unary expressions (¬).
     * This has higher precedence than binary operators.
     */
    private fun parseUnary(state: ParserState): FormulaNode? {
        val operator = state.current()
        return if (operator?.type == SymbolType.UNARY_OPERATOR) {
            state.advance() // Consume the '¬'
            // Recursively call parseUnary to handle multiple negations like ¬¬p
            val operand = parseUnary(state) ?: return null
            FormulaNode.UnaryOpNode(operator, operand)
        } else {
            // If not a unary operator, parse the next highest precedence level.
            parsePrimary(state)
        }
    }

    /**
     * Parses primary expressions: variables or parenthesized groups.
     * This is the highest level of precedence.
     */
    private fun parsePrimary(state: ParserState): FormulaNode? {
        val tile = state.current() ?: return null

        return when (tile.type) {
            SymbolType.VARIABLE -> {
                state.advance() // Consume variable
                FormulaNode.VariableNode(tile)
            }
            SymbolType.LEFT_PAREN -> {
                state.advance() // Consume '('
                // The expression inside the parentheses is parsed starting from the lowest precedence.
                val expression = parseBinaryExpression(state)
                if (state.current()?.type != SymbolType.RIGHT_PAREN) {
                    return null // Mismatched parentheses
                }
                state.advance() // Consume ')'
                expression
            }
            else -> null // Unexpected token
        }
    }
}
