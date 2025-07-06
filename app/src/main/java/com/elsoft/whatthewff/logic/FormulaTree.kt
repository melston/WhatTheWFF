// File: logic/FormulaTree.kt
// This file defines the data structures for representing a formula as a syntax tree.
// This is essential for correctly implementing replacement rules.

package com.elsoft.whatthewff.logic

/**
 * Represents a node in the formula's syntax tree.
 * A sealed class is used because a node can only be one of the defined types below.
 */
sealed class FormulaNode {
    /**
     * A leaf node representing a single propositional variable.
     * Example: 'p'
     */
    data class VariableNode(val tile: LogicTile) : FormulaNode()

    /**
     * A node representing a unary operation (e.g., negation).
     * It has exactly one child node.
     * Example: (¬p)
     */
    data class UnaryOpNode(val operator: LogicTile, val child: FormulaNode) : FormulaNode()

    /**
     * A node representing a binary operation (e.g., and, or, implies).
     * It has a left and a right child node.
     * Example: (p ∧ q)
     */
    data class BinaryOpNode(val operator: LogicTile, val left: FormulaNode, val right: FormulaNode) : FormulaNode()
}
