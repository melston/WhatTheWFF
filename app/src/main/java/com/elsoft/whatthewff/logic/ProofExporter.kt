// File: logic/ProofExporter.kt
// This file contains the logic to format a completed proof into a
// shareable and printable HTML document.

package com.elsoft.whatthewff.logic

import com.elsoft.whatthewff.ui.features.proof.ProofScreen

/**
 * A utility object to handle exporting proofs to different formats.
 */
object ProofExporter {

    /**
     * Formats a given problem and its corresponding proof into a self-contained HTML string.
     * The output is styled to resemble a classic Fitch-style proof, suitable for
     * viewing in a browser, sharing, or printing.
     *
     * @param problem The original problem, used for the header information.
     * @param proof The completed proof object.
     * @return A String containing the full HTML document.
     */
    fun formatProofAsHtml(problem: Problem, proof: Proof): String {
        val css = """
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; margin: 20px; line-height: 1.6; }
                .proof-container { border: 1px solid #dfe1e5; border-radius: 8px; padding: 24px; max-width: 800px; margin: auto; background-color: #ffffff; }
                .header { margin-bottom: 24px; padding-bottom: 16px; border-bottom: 1px solid #dfe1e5;}
                .problem-title { font-size: 1.8em; font-weight: 600; margin-bottom: 12px; }
                .premises, .goal { margin-top: 8px; font-size: 1.1em; }
                .label { font-weight: bold; color: #5f6368; }
                table { width: 100%; border-collapse: collapse; margin-top: 20px;}
                td { padding: 10px 8px; vertical-align: top; border-bottom: 1px solid #f1f3f4; }
                .line-number { width: 40px; text-align: right; padding-right: 12px; color: #80868b; font-family: monospace; }
                .formula { font-family: "SFMono-Regular", Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace; font-size: 1.15em; }
                .justification { width: 150px; text-align: right; color: #5f6368; font-style: italic; }
                .subproof-indent { border-left: 2px solid #dadce0; }
            </style>
        """.trimIndent()

        val headerHtml = """
            <div class="header">
                <div class="problem-title">${problem.name}</div>
                <div class="premises"><span class="label">Premises:</span> ${problem.premises.joinToString { it.stringValue }}</div>
                <div class="goal"><span class="label">Goal:</span> ${problem.conclusion.stringValue}</div>
            </div>
        """.trimIndent()

        val proofRows = proof.lines.joinToString("\n") { line ->
            val indentClass = if (line.depth > 0) "subproof-indent" else ""
            val indentStyle = "padding-left: ${(line.depth * 24) + 8}px;"
            """
            <tr class='$indentClass'>
                <td class="line-number">${line.lineNumber}.</td>
                <td class="formula" style="$indentStyle">${line.formula.stringValue}</td>
                <td class="justification">${line.justification.displayText()}</td>
            </tr>
            """.trimIndent()
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Proof Solution: ${problem.name}</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                $css
            </head>
            <body>
                <div class="proof-container">
                    $headerHtml
                    <table>
                        <tbody>
                            $proofRows
                        </tbody>
                    </table>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
