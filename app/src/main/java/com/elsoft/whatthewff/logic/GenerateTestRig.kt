// File: /home/mark/AndroidStudioProjects/WhattheWFF/app/src/main/java/com/elsoft/whatthewff/logic/GeneratorTestRig.kt

package com.elsoft.whatthewff.logic

/**
 * A command-line test rig for running the PlannedProblemGenerator outside of the Android app.
 *
 * To run this, right-click anywhere in the file and select "Run 'GeneratorTestRigKt'".
 * The output will appear in your IDE's "Run" console.
 */
fun main() {
    println("--- Starting Problem Generation Test ---")

    val generator = PlannedProblemGenerator()
    val difficulty = 6 // Hard difficulty

    println("Attempting to generate a problem with difficulty: $difficulty\n")

    // Run the generator
    val problem = generator.generate(difficulty)

    // Print the results
    if (problem != null) {
        println("SUCCESS: Problem generated.")
        println("---------------------------------")
        println("PREMISES:")
        problem.premises.forEach { premise ->
            println("  - $premise")
        }
        println("\nCONCLUSION:")
        println("  - ${problem.conclusion}")
        println("---------------------------------")
    } else {
        println("FAILURE: The generator returned null. No valid problem was found within the attempt limit.")
    }

    println("\n--- Test Finished ---")
}
