package com.elsoft.whatthewff.logic

data class LineInfo(val formulaString: String, val justification: Justification)

open class LogicTestBase {
    // A helper function to easily create Formula objects from strings for testing.
    fun createFormula(formulaString: String): Formula {
        return WffParser.parseFormulaFromString(formulaString)
    }

    fun createFormulas(vararg wffs: String): List<Formula> {
        return wffs
                .map { wff -> WffParser.parseFormulaFromString(wff) }
                .toList()
    }

    fun createProof(lines: List<LineInfo>): Proof {
        return Proof(lines.mapIndexed { index, line ->
            ProofLine(index + 1,
                      WffParser.parseFormulaFromString(line.formulaString),
                      line.justification)
        })

    }

    fun compareFormulas(f1: Formula, f2: Formula): Boolean {
        return WffParser.parse(f1) == WffParser.parse(f2)
    }

    fun Set<Formula>.doesContain(f: Formula): Boolean {
        return this.any { compareFormulas(it, f) }
    }
}