package com.elsoft.whatthewff.logic

data class LineInfo(val formulaString: String, val justification: Justification)

open class LogicTestBase {
    // A helper function to easily create Formula objects from strings for testing.
    fun f(formulaString: String): Formula {
        return WffParser.parseFormulaFromString(formulaString)
    }

    fun createFormulas(vararg wffs: String): Set<Formula> {
        return wffs
                .map { wff -> WffParser.parseFormulaFromString(wff) }
                .toSet()
    }

    fun createProof(lines: List<LineInfo>): Proof {
        return Proof(lines.mapIndexed { index, line ->
            ProofLine(index + 1,
                      WffParser.parseFormulaFromString(line.formulaString),
                      line.justification)
        })

    }
}