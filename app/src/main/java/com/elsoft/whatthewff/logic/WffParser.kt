// File: logic/WffParser.kt
// This file contains a robust, multi-level recursive descent parser that
// correctly handles operator precedence and parenthesized expressions.

package com.elsoft.whatthewff.logic

import com.elsoft.whatthewff.logic.AvailableTiles.implies
import com.elsoft.whatthewff.logic.AvailableTiles.iff
import com.elsoft.whatthewff.logic.AvailableTiles.or
import com.elsoft.whatthewff.logic.AvailableTiles.and
//import com.elsoft.whatthewff.logic.AvailableTiles.not

/**
 * A singleton object responsible for parsing formulas into syntax trees.
 */
object WffParser {

    // A stateful helper class for the parser.
    private class ParserState(val tiles: List<LogicTile>) {
        var position = 0
        fun hasMore(): Boolean = position < tiles.size
        fun current(): LogicTile? = tiles.getOrNull(position)
        fun advance() { position++ }
    }

    /**
     * A simple DSL/parser function that converts a readable string into a Formula object.
     * This allows for clear and concise problem definitions.
     * Example: f("(p→q)")
     */
    fun f(formulaString: String): Formula {
        // Create a quick lookup map for mapping characters to their LogicTile objects.
        val tileMap = AvailableTiles.allTiles.associateBy { it.symbol }

        // Map each character in the string to its corresponding tile.
        // If a character isn't a valid symbol (like whitespace), it's ignored.
        val tiles = formulaString.mapNotNull { char ->
            tileMap[char.toString()]
        }
        return Formula(tiles)
    }

    /**
     * Public entry point for parsing.
     */
    fun parse(formula: Formula): FormulaNode? {
        if (formula.tiles.isEmpty()) return null
        val state = ParserState(formula.tiles)
        val resultNode = parseImplication(state) // Start with the lowest precedence operator
        // A successful parse must consume all tiles.
        return if (state.hasMore()) null else resultNode
    }

    // The parser is broken into a hierarchy of functions, where each function
    // handles operators of a certain precedence level.

    // Handles '→' and '↔' (lowest precedence, right-associative)
    private fun parseImplication(state: ParserState): FormulaNode? {
        val left = parseDisjunction(state) ?: return null
        // Use a simple 'if' for right-associativity, not a 'while' loop.
        if (state.current()?.let { it == implies } == true || state.current()?.let { it == iff } == true) {
            val op = state.current()!!
            state.advance()
            // Recurse on parseImplication itself for the right-hand side.
            val right = parseImplication(state) ?: return null
            return FormulaNode.BinaryOpNode(op, left, right)
        }
        return left
    }

    // Handles '∨' (left-associative)
    private fun parseDisjunction(state: ParserState): FormulaNode? {
        var left = parseConjunction(state) ?: return null
        while (state.current()?.let { it == or } == true) {
            val op = state.current()!!
            state.advance()
            val right = parseConjunction(state) ?: return null
            left = FormulaNode.BinaryOpNode(op, left, right)
        }
        return left
    }

    // Handles '∧' (left-associative)
    private fun parseConjunction(state: ParserState): FormulaNode? {
        var left = parseFactor(state) ?: return null
        while (state.current()?.let { it == and } == true) {
            val op = state.current()!!
            state.advance()
            val right = parseFactor(state) ?: return null
            left = FormulaNode.BinaryOpNode(op, left, right)
        }
        return left
    }

    // Handles the highest precedence items: variables, negations, and parentheses.
    private fun parseFactor(state: ParserState): FormulaNode? {
        val tile = state.current() ?: return null
        return when (tile.type) {
            SymbolType.VARIABLE -> {
                state.advance()
                FormulaNode.VariableNode(tile)
            }
            SymbolType.UNARY_OPERATOR -> {
                state.advance() // Consume '¬'
                val operand = parseFactor(state) ?: return null // Negation applies to the next factor
                FormulaNode.UnaryOpNode(tile, operand)
            }
            SymbolType.LEFT_PAREN -> {
                state.advance() // Consume '('
                val expression = parseImplication(state) // Start parsing from the lowest precedence inside parens
                if (state.current()?.type != SymbolType.RIGHT_PAREN) return null
                state.advance() // Consume ')'
                expression
            }
            else -> null
        }
    }
}
