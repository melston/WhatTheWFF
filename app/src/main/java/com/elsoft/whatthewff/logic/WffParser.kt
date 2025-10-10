// File: logic/WffParser.kt
// This file contains a robust, multi-level recursive descent parser that
// correctly handles operator precedence and parenthesized expressions.

package com.elsoft.whatthewff.logic

import com.elsoft.whatthewff.logic.AvailableTiles.implies
import com.elsoft.whatthewff.logic.AvailableTiles.iff
import com.elsoft.whatthewff.logic.AvailableTiles.or
import com.elsoft.whatthewff.logic.AvailableTiles.and
import com.elsoft.whatthewff.logic.AvailableTiles.leftParen
import com.elsoft.whatthewff.logic.AvailableTiles.not
import com.elsoft.whatthewff.logic.AvailableTiles.rightParen
import com.elsoft.whatthewff.logic.RuleGenerators.treeToFormula

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
     * Public entry point for parsing.
     */
    fun parse(formula: Formula): FormulaNode? {
        if (formula.tiles.isEmpty()) return null
        val state = ParserState(formula.tiles)
        val resultNode = parseImplication(state) // Start with the lowest precedence operator
        // A successful parse must consume all tiles.
        return if (state.hasMore()) null else resultNode
    }

    /**
     * A robust public function to parse a string into a Formula object.
     * This version correctly handles multi-character operators and common ASCII symbols.
     */
    fun parseFormulaFromString(formulaString: String): Formula {
        val tokens = mutableListOf<LogicTile>()
        var i = 0
        while (i < formulaString.length) {
            val char = formulaString[i]
            when {
                char.isWhitespace() -> { i++; continue }
                char.isLetter() -> {
                    tokens.add(LogicTile(char.toString(), SymbolType.VARIABLE))
                    i++
                }
                char == '&' || char == '∧' -> { tokens.add(and); i++ }
                char == '|' || char == '∨' -> { tokens.add(or); i++ }
                char == '~' || char == '¬' -> { tokens.add(not); i++ }
                char == '(' -> { tokens.add(leftParen); i++ }
                char == ')' -> { tokens.add(rightParen); i++ }
                formulaString.substring(i).startsWith("->") ||
                formulaString.substring(i).startsWith("→") -> {
                    tokens.add(implies)
                    i += if (formulaString.substring(i).startsWith("->")) 2 else 1
                }
                formulaString.substring(i).startsWith("<->") ||
                formulaString.substring(i).startsWith("iff") ||
                formulaString.substring(i).startsWith("↔") -> {
                    tokens.add(iff)
                    i += when {
                        formulaString.substring(i).startsWith("<->") -> 3
                        formulaString.substring(i).startsWith("iff") -> 3
                        else -> 1
                    }
                }
                else -> i++
            }
        }
        return Formula(tokens)
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

/**
 * Normalizes a Formula by parsing it into a syntax tree and then regenerating it.
 * This removes cosmetic differences like extra parentheses, ensuring a canonical representation.
 */
fun Formula.normalize(): Formula {
    val node = WffParser.parse(this) ?: return Formula(emptyList())
    return treeToFormula(node)
}


