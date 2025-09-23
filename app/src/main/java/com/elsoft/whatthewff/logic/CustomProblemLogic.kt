// File: logic/CustomProblemLogic.kt
// This file contains the data structures and parser for handling
// custom, user-imported problem sets from text files.

package com.elsoft.whatthewff.logic

// --- Data Structures for Custom Problems ---

/**
 * Represents a group of problems, like a chapter in a book.
 * @param title The title of the set (e.g., "Chapter 8.4").
 * @param problems The list of individual problems in this set.
 */
data class ProblemSet(
    val title: String,
    val problems: List<CustomProblem>
)

/**
 * Represents a single custom problem defined by the user.
 * @param id The identifier for the problem (e.g., "1.a").
 * @param premises A list of formulas that are the premises.
 * @param conclusion The single formula that is the goal of the proof.
 * @param solvedProof The user's completed proof (null if unsolved).
 */
data class CustomProblem(
    val id: String,
    val premises: List<Formula>,
    val conclusion: Formula,
    var solvedProof: Proof? = null
)

// --- File Parser for Custom Problems ---

/**
 * An object responsible for parsing a text file into a list of ProblemSet objects.
 */
object ProblemFileParser {

    private enum class ParseMode { NONE, PREMISES, GOAL }

    /**
     * Parses the entire content of a text file.
     * @param fileContent The full string content of the user's file.
     * @return A list of ProblemSet objects parsed from the file.
     */
    fun parse(fileContent: String): List<ProblemSet> {
        val problemSets = mutableListOf<ProblemSet>()
        var currentSetTitle: String? = null
        var currentProblems = mutableListOf<CustomProblem>()

        var currentProblemId: String? = null
        var currentPremises = mutableListOf<Formula>()
        var currentGoal: Formula? = null
        var mode = ParseMode.NONE

        fun finalizeProblem() {
            if (currentProblemId != null && currentGoal != null) {
                currentProblems.add(CustomProblem(currentProblemId!!, currentPremises, currentGoal!!))
            }
            currentProblemId = null
            currentPremises = mutableListOf()
            currentGoal = null
            mode = ParseMode.NONE
        }

        fun finalizeSet() {
            finalizeProblem()
            if (currentSetTitle != null && currentProblems.isNotEmpty()) {
                problemSets.add(ProblemSet(currentSetTitle!!, currentProblems))
            }
            currentSetTitle = null
            currentProblems = mutableListOf()
        }

        fileContent.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("Problem Group:", ignoreCase = true)) {
                finalizeSet()
                currentSetTitle = trimmedLine.substringAfter(":").trim()
            } else if (trimmedLine.startsWith("Problem:", ignoreCase = true)) {
                finalizeProblem()
                currentProblemId = trimmedLine.substringAfter(":").trim()
            } else if (trimmedLine.equals("Premises:", ignoreCase = true)) {
                mode = ParseMode.PREMISES
            } else if (trimmedLine.equals("Goal:", ignoreCase = true)) {
                mode = ParseMode.GOAL
            } else if (trimmedLine.isNotEmpty()) {
                when (mode) {
                    ParseMode.PREMISES -> {
                        WffParser.parseFormulaFromString(trimmedLine)?.let {
                            currentPremises.add(it)
                        }
                    }
                    ParseMode.GOAL -> {
                        currentGoal = WffParser.parseFormulaFromString(trimmedLine)
                        mode = ParseMode.NONE // A goal is only one line
                    }
                    ParseMode.NONE -> { /* Ignore other text */ }
                }
            }
        }
        finalizeSet() // Finalize the last set in the file

        return problemSets
    }
}
