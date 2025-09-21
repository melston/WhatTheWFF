// File: ui/features/proof/ProofScreen.kt
// This file contains the Jetpack Compose UI for the main proof-building screen,
// now with full support for sub-proofs, reiteration, and Reductio ad Absurdum (RAA).

package com.elsoft.whatthewff.ui.features.proof

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elsoft.whatthewff.data.ProofViewModel
import com.elsoft.whatthewff.logic.AvailableTiles
import com.elsoft.whatthewff.logic.Formula
import com.elsoft.whatthewff.logic.FormulaNode
import com.elsoft.whatthewff.logic.Justification
import com.elsoft.whatthewff.logic.Problem
import com.elsoft.whatthewff.logic.Proof
import com.elsoft.whatthewff.logic.ProofExporter
import com.elsoft.whatthewff.logic.ProofLine
import com.elsoft.whatthewff.logic.ProofValidator
import com.elsoft.whatthewff.logic.RuleGenerators
import com.elsoft.whatthewff.logic.SymbolType
import com.elsoft.whatthewff.logic.WffParser
import com.elsoft.whatthewff.ui.features.proof.components.AddLineDialog
import com.elsoft.whatthewff.ui.features.proof.components.DeleteConfirmationDialog
import com.elsoft.whatthewff.ui.features.proof.components.FabMenu
import com.elsoft.whatthewff.ui.features.proof.components.ProofLineView
import com.elsoft.whatthewff.ui.features.proof.components.SymbolPalette
import com.elsoft.whatthewff.ui.features.proof.components.ConstructionArea
import com.elsoft.whatthewff.ui.features.proof.components.DragAndDropState

private val LocalDragAndDropState = compositionLocalOf { DragAndDropState() }

// TODO: Find a way to move this into components/DragAndDropSupport.kt as well.
@Composable
fun DragAndDropContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = remember { DragAndDropState() }
    CompositionLocalProvider(LocalDragAndDropState provides state) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
            if (state.isDragging) {
                Box(modifier = Modifier.graphicsLayer {
                    // Center the dragged item under the user's finger
                    translationX =
                        state.dragPosition.x - state.draggableContentSize.width / 2
                    translationY =
                        state.dragPosition.y - state.draggableContentSize.height / 2
                }) {
                    state.draggableContent?.invoke()
                }
            }
        }
    }
}

// --- Main Proof Screen Composable ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofScreen(
    problem: Problem,
    problemSetTitle: String,
    initialProof: Proof?,
    onBackClicked: () -> Unit
) {
    // --- State Management ---
    val isViewOnly = initialProof != null
    var proof by remember {
        mutableStateOf(
            initialProof ?: Proof(lines = problem.premises.mapIndexed { i, p ->
                ProofLine(i + 1, p, Justification.Premise)
            })
        )
    }
    var currentFormula by remember { mutableStateOf(Formula(emptyList())) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    // Sub-proof state
    var currentDepth by remember { mutableIntStateOf(0) }
    var subproofStartLines by remember { mutableStateOf(listOf<Int>()) }

    // Selection and Dialog state
    var selectedLines by remember { mutableStateOf(setOf<Int>()) }
    var showAddLineDialog by remember { mutableStateOf(false) }
    var lineToDelete by remember { mutableStateOf<Int?>(null) }
    val showDeleteDialog = lineToDelete != null

    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val proofViewModel: ProofViewModel = viewModel(
        factory = ProofViewModel.Factory(
            context.applicationContext as Application,
            problemSetTitle,
            problem.id
        )
    )

    // Dynamically create the symbol palette based on the problem.  Only show the variables
    // that are used in the current proof.
    // TODO:  we may want to give users the ability to introduce new variables as needed.
    val paletteTiles = remember(problem) {
        val variables = (problem.premises.flatMap { it.tiles } + problem.conclusion.tiles)
            .filter { it.type == SymbolType.VARIABLE }
            .distinctBy { it.symbol }
            .sortedBy { it.symbol }

        variables
    }

    val fileSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html"),
        onResult = { uri: Uri? ->
            uri?.let { fileUri ->
                try {
                    val htmlContent = ProofExporter.formatProofAsHtml(problem, proof)
                    context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                        outputStream.write(htmlContent.toByteArray())
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Error Saving File: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    )

    // --- UI Structure ---
    DragAndDropContainer {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(problem.name) },
                    navigationIcon = {
                        IconButton(onClick = onBackClicked) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save Solution As...") }, // Changed label for clarity
                                onClick = {
                                    val defaultFileName =
                                        "Solution - ${problem.name.replace(" ", "_")}.html"
                                    fileSaverLauncher.launch(defaultFileName)
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Solution") },
                                onClick = {
                                    val htmlContent =
                                        ProofExporter.formatProofAsHtml(problem, proof)
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, htmlContent)
                                        putExtra(
                                            Intent.EXTRA_SUBJECT,
                                            "Proof Solution: ${problem.name}"
                                        )
                                        type = "text/html"
                                    }
                                    val shareIntent =
                                        Intent.createChooser(sendIntent, "Share Proof")
                                    context.startActivity(shareIntent)
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Print / Save as PDF") },
                                onClick = {
                                    printProof(context, problem, proof)
                                    showMenu = false
                                }
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                if (!isViewOnly) {
                    FabMenu(
                        isExpanded = isFabMenuExpanded,
                        onToggle = { isFabMenuExpanded = !isFabMenuExpanded },
                        onAddPremise = {
                            val newProofLine = ProofLine(
                                proof.lines.size + 1,
                                currentFormula,
                                Justification.Premise,
                                currentDepth
                            )
                            proof = Proof(proof.lines + newProofLine)
                            currentFormula = Formula(emptyList())
                            isFabMenuExpanded = false
                        },
                        onStartSubproof = {
                            val assumptionFormula = if (selectedLines.size == 1) {
                                proof.lines.getOrNull(selectedLines.first() - 1)?.formula
                                ?: currentFormula
                            } else {
                                currentFormula
                            }

                            val newProofLine = ProofLine(
                                proof.lines.size + 1,
                                assumptionFormula,
                                Justification.Assumption,
                                currentDepth + 1
                            )
                            proof = Proof(proof.lines + newProofLine)
                            subproofStartLines = subproofStartLines + (proof.lines.size)
                            currentDepth++
                            currentFormula =
                                Formula(emptyList()) // Always clear construction area
                            selectedLines = emptySet() // Clear selection after using it
                            isFabMenuExpanded = false
                        },
                        onEndSubproof = {
                            val startLine = subproofStartLines.last()
                            val endLine = proof.lines.size
                            val assumptionLine = proof.lines[startLine - 1]
                            val conclusionLine = proof.lines.last()

                            val finalFormula: Formula
                            val justification: Justification

                            // Check if the last line of the sub-proof is a contradiction
                            val lastLineNode = WffParser.parse(conclusionLine.formula)
                            val isContradiction =
                                if (lastLineNode is FormulaNode.BinaryOpNode &&
                                    lastLineNode.operator.symbol == "∧"
                                ) {
                                    val leftNegatedTree = WffParser.parse(
                                        RuleGenerators.fNeg(
                                            RuleGenerators.treeToFormula(
                                                lastLineNode.left
                                            )
                                        )
                                    )
                                    leftNegatedTree == lastLineNode.right
                                } else {
                                    false
                                }

                            if (isContradiction) {
                                // RAA: Conclude the negation of the assumption
                                finalFormula = RuleGenerators.fNeg(assumptionLine.formula)
                                justification =
                                    Justification.ReductioAdAbsurdum(
                                        startLine,
                                        endLine,
                                        endLine
                                    )
                            } else {
                                // II: Conclude the implication, ensuring it's correctly parenthesized.
                                val antecedent = assumptionLine.formula
                                val consequent = conclusionLine.formula

                                val antecedentTiles = WffParser.parse(antecedent)?.let {
                                    if (it is FormulaNode.BinaryOpNode) {
                                        listOf(AvailableTiles.leftParen) +
                                        antecedent.tiles +
                                        listOf(AvailableTiles.rightParen)
                                    } else {
                                        antecedent.tiles
                                    }
                                } ?: antecedent.tiles

                                val consequentTiles = WffParser.parse(consequent)?.let {
                                    if (it is FormulaNode.BinaryOpNode) {
                                        listOf(AvailableTiles.leftParen) +
                                        consequent.tiles +
                                        listOf(AvailableTiles.rightParen)
                                    } else {
                                        consequent.tiles
                                    }
                                } ?: consequent.tiles

                                finalFormula = Formula(
                                    antecedentTiles +
                                    listOf(AvailableTiles.implies) +
                                    consequentTiles
                                )
                                justification =
                                    Justification.ImplicationIntroduction(
                                        startLine,
                                        endLine
                                    )
                            }

                            val newProofLine = ProofLine(
                                proof.lines.size + 1,
                                finalFormula,
                                justification,
                                currentDepth - 1
                            )
                            proof = Proof(proof.lines + newProofLine)
                            subproofStartLines = subproofStartLines.dropLast(1)
                            currentDepth--
                            isFabMenuExpanded = false
                        },
                        onAddDerivedLine = {
                            showAddLineDialog = true
                            isFabMenuExpanded = false
                        },
                        onReiterate = {
                            val lineToReiterate = proof.lines[selectedLines.first() - 1]
                            val newProofLine = ProofLine(
                                proof.lines.size + 1,
                                lineToReiterate.formula,
                                Justification.Reiteration(lineToReiterate.lineNumber),
                                currentDepth
                            )
                            proof = Proof(proof.lines + newProofLine)
                            selectedLines = emptySet()
                            isFabMenuExpanded = false
                        },
                        isAddPremiseEnabled = currentDepth == 0 && currentFormula.tiles.isNotEmpty(),
                        isStartSubproofEnabled = currentFormula.tiles.isNotEmpty() || selectedLines.size == 1,
                        isEndSubproofEnabled = currentDepth > 0,
                        isReiterateEnabled = selectedLines.size == 1 && currentDepth > 0 && (proof.lines.getOrNull(
                            selectedLines.first() - 1
                        )?.depth ?: 0) < currentDepth,
                        isAddDerivedLineEnabled = currentFormula.tiles.isNotEmpty() || selectedLines.isNotEmpty()
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- Goal Display ---
                Text(
                    "Goal: ${problem.conclusion}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                // --- Proof Display Area ---
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(proof.lines) { line ->
                            ProofLineView(
                                line = line,
                                isSelected = selectedLines.contains(line.lineNumber),
                                onLineClicked = {
                                    selectedLines =
                                        if (selectedLines.contains(it)) selectedLines - it
                                        else selectedLines + it
                                },
                                onDeleteClicked = { lineToDelete = it }
                            )
                        }
                    }
                }

                // --- Feedback & Controls ---
                Spacer(Modifier.height(8.dp))
                if (feedbackMessage != null) {
                    Text(
                        feedbackMessage ?: "",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (!isViewOnly) {
                    ConstructionArea(
                        formula = currentFormula,
                        onFormulaChange = { newFormula ->
                            currentFormula = newFormula
                        },
                        state = LocalDragAndDropState.current
                    )

                    SymbolPalette(variables = paletteTiles, LocalDragAndDropState.current)

                    Button(
                        onClick = {
                            val result = ProofValidator.validate(proof)
                            // We do this next to allow for the case where the goal is expressed with
                            // surrounding parens but the user doesn't include them.  If the two formulas
                            // are otherwise equal, we consider the proof valid.
                            val finalLineMatchesGoal =
                                proof.lines.lastOrNull()?.let { lastLine ->
                                    WffParser.parse(lastLine.formula) == WffParser.parse(
                                        problem.conclusion
                                    )
                                } ?: false // If there's no last line, it can't match.

                            feedbackMessage = when {
                                !result.isValid -> "Error on line ${result.errorLine}: ${result.errorMessage}"
                                !finalLineMatchesGoal -> "Proof is valid, but does not reach the goal."
                                else -> {
                                    proofViewModel.saveProof(proof)
                                    Toast.makeText(
                                        context,
                                        "Solution Saved!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    "Congratulations! Proof is valid and complete."
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) { Text("Validate Proof") }
                }
            }

            // --- Dialogs ---
            if (showAddLineDialog) {
                AddLineDialog(
                    currentProof = proof,
                    selectedProofLines = proof.lines.filter { selectedLines.contains(it.lineNumber) },
                    currentFormula = currentFormula,
                    currentDepth = currentDepth,
                    onDismiss = { showAddLineDialog = false },
                    onConfirm = { newFormula, justification ->
                        val newProofLine = ProofLine(
                            proof.lines.size + 1,
                            newFormula,
                            justification,
                            currentDepth
                        )
                        proof = Proof(proof.lines + newProofLine)
                        currentFormula = Formula(emptyList())
                        selectedLines = emptySet()
                        showAddLineDialog = false
                    }
                )
            }
            if (showDeleteDialog) {
                DeleteConfirmationDialog(
                    onConfirm = {
                        lineToDelete?.let { lineNum ->
                            proof = Proof(proof.lines.take(lineNum - 1))
                            feedbackMessage = "Line $lineNum and subsequent lines deleted."
                            // Adjust sub-proof state if deletion happened inside one
                            if (proof.lines.size < (subproofStartLines.lastOrNull() ?: 0)) {
                                val lastStart = subproofStartLines.last()
                                subproofStartLines =
                                    subproofStartLines.filter { it < lastStart }
                                currentDepth = subproofStartLines.size
                            }
                        }
                        lineToDelete = null
                    },
                    onDismiss = { lineToDelete = null }
                )
            }
        }
    }
}

/**
 * Helper function to trigger the Android printing framework.
 * It loads the generated HTML into an invisible WebView and asks the PrintManager
 * to handle the printing or saving as a PDF.
 */
private fun printProof(context: Context, problem: Problem, proof: Proof) {
    val htmlContent = ProofExporter.formatProofAsHtml(problem, proof)

    // Create a WebView instance programmatically
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)

            // Once the page is loaded, create the print job
            val printManager =
                context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "Proof Solution - ${problem.name}"
            val printAdapter = view.createPrintDocumentAdapter(jobName)

            printManager.print(
                jobName,
                printAdapter,
                PrintAttributes.Builder().build()
            )
        }
    }
    // Load our generated HTML into the WebView
    webView.loadDataWithBaseURL(null, htmlContent, "text/HTML", "UTF-8", null)
}
