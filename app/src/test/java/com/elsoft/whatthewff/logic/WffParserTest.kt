// File: test/com/elsoft/whatthewff/logic/WffParserTest.kt
// This file contains unit tests for the WffParser object.

package com.elsoft.whatthewff.logic

import org.junit.Assert.*
import org.junit.Test

class WffParserTest {

    // A helper function to easily create Formula objects from strings for testing.
    private fun f(formulaString: String): Formula {
        val tileMap = AvailableTiles.allTiles.associateBy { it.symbol }
        val tiles = formulaString.mapNotNull { char -> tileMap[char.toString()] }
        return Formula(tiles)
    }

    @Test
    fun `test simple variable parses correctly`() {
        val formula = f("p")
        val result = WffParser.parse(formula)
        assertNotNull("Parser should not return null for a simple variable", result)
        assertTrue("Result should be a VariableNode", result is FormulaNode.VariableNode)
        assertEquals("p", (result as FormulaNode.VariableNode).tile.symbol)
    }

    @Test
    fun `test simple negation parses correctly`() {
        val formula = f("¬p")
        val result = WffParser.parse(formula)
        assertNotNull("Parser should not return null for a simple negation", result)
        assertTrue("Result should be a UnaryOpNode", result is FormulaNode.UnaryOpNode)
        val unaryNode = result as FormulaNode.UnaryOpNode
        assertEquals("¬", unaryNode.operator.symbol)
        assertTrue("Child of negation should be a VariableNode", unaryNode.child is FormulaNode.VariableNode)
    }

    @Test
    fun `test simple binary expression parses correctly`() {
        val formula = f("(p∧q)")
        val result = WffParser.parse(formula)
        assertNotNull("Parser should not return null for a simple binary expression", result)
        assertTrue("Result should be a BinaryOpNode", result is FormulaNode.BinaryOpNode)
        val binaryNode = result as FormulaNode.BinaryOpNode
        assertEquals("∧", binaryNode.operator.symbol)
        assertTrue("Left child should be a VariableNode", binaryNode.left is FormulaNode.VariableNode)
        assertTrue("Right child should be a VariableNode", binaryNode.right is FormulaNode.VariableNode)
    }

    @Test
    fun `test binary expression without outer parentheses parses correctly`() {
        val formula = f("p∨q")
        val result = WffParser.parse(formula)
        assertNotNull("Parser should not return null for a binary expression without outer parens", result)
        assertTrue("Result should be a BinaryOpNode", result is FormulaNode.BinaryOpNode)
        assertEquals("∨", (result as FormulaNode.BinaryOpNode).operator.symbol)
    }

    @Test
    fun `test complex nested formula parses correctly`() {
        val formula = f("((p→q)∧¬r)")
        val result = WffParser.parse(formula)
        assertNotNull("Parser should not return null for a complex formula", result)
        assertTrue("Root should be a BinaryOpNode", result is FormulaNode.BinaryOpNode)

        val root = result as FormulaNode.BinaryOpNode
        assertEquals("∧", root.operator.symbol)
        assertTrue("Left child should be a BinaryOpNode", root.left is FormulaNode.BinaryOpNode)
        assertTrue("Right child should be a UnaryOpNode", root.right is FormulaNode.UnaryOpNode)
    }

    @Test
    fun `test invalid formula with mismatched parentheses returns null`() {
        val formula = f("(p∧q") // Missing closing parenthesis
        val result = WffParser.parse(formula)
        assertNull("Parser should return null for mismatched parentheses", result)
    }

    @Test
    fun `test invalid formula with extra tokens returns null`() {
        val formula = f("(p∧q)r") // Extra token at the end
        val result = WffParser.parse(formula)
        assertNull("Parser should return null for formulas with extra tokens", result)
    }

    @Test
    fun `test invalid formula with operator in wrong place returns null`() {
        val formula = f("(p∧)") // Operator in wrong place
        val result = WffParser.parse(formula)
        assertNull("Parser should return null for misplaced operator", result)
    }

    @Test
    fun `test empty formula returns null`() {
        val formula = f("")
        val result = WffParser.parse(formula)
        assertNull("Parser should return null for an empty formula", result)
    }
}
