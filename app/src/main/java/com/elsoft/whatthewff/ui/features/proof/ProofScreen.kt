// File: ui/features/proof/ProofScreen.kt
// This file contains the Jetpack Compose UI for the main proof-building screen,
// now with full support for sub-proofs, reiteration, and RAA.

package com.elsoft.whatthewff.ui.features.proof

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsoft.whatthewff.logic.AvailableTiles
import com.elsoft.whatthewff.logic.Formula
import com.elsoft.whatthewff.logic.FormulaNode
import com.elsoft.whatthewff.logic.ForwardRuleGenerators
import com.elsoft.whatthewff.logic.Justification
import com.elsoft.whatthewff.logic.LogicTile
import com.elsoft.whatthewff.logic.Problem
import com.elsoft.whatthewff.logic.Proof
import com.elsoft.whatthewff.logic.ProofLine
import com.elsoft.whatthewff.logic.ProofValidator
import com.elsoft.whatthewff.logic.WffParser
import com.elsoft.whatthewff.ui.features.proof.components.AddLineDialog
import com.elsoft.whatthewff.ui.features.proof.components.DeleteConfirmationDialog
import com.elsoft.whatthewff.ui.features.proof.components.FabMenu
import com.elsoft.whatthewff.ui.features.proof.components.ProofLineView
import com.elsoft.whatthewff.ui.features.proof.components.SymbolPalette

// --- Drag and Drop State ---

sealed class DragData {
    data class NewTile(val tile: LogicTile) : DragData()
    data class ExistingTile(val tile: LogicTile, val index: Int) : DragData()
}

private class DragAndDropState {
    var isDragging: Boolean by mutableStateOf(false)
    var dragPosition by mutableStateOf(Offset.Zero)
    var draggableContent by mutableStateOf<(@Composable () -> Unit)?>(null)
    var draggableContentSize by mutableStateOf(IntSize.Zero)
    var dataToDrop by mutableStateOf<DragData?>(null)
}

private val LocalDragAndDropState = compositionLocalOf { DragAndDropState() }

// --- Drag and Drop Composables ---
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
                    translationX = state.dragPosition.x - state.draggableContentSize.width / 2
                    translationY = state.dragPosition.y - state.draggableContentSize.height / 2
                }) {
                    state.draggableContent?.invoke()
                }
            }
        }
    }
}

@Composable
fun DraggableItem(
    modifier: Modifier = Modifier,
    dataToDrop: DragData,
    content: @Composable () -> Unit
) {
    var currentSize by remember { mutableStateOf(IntSize.Zero) }
    var currentPosition by remember { mutableStateOf(Offset.Zero) } // keep track of global position
    val dragAndDropState = LocalDragAndDropState.current
    val isCurrentlyDragged = dragAndDropState.isDragging && dragAndDropState.dataToDrop == dataToDrop

    Box(
        modifier = modifier
            .onGloballyPositioned {
                currentSize = it.size
                currentPosition = it.localToWindow(Offset.Zero) // Store the global position
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragAndDropState.isDragging = true
                        dragAndDropState.dataToDrop = dataToDrop
                        dragAndDropState.draggableContentSize = currentSize
                        // Correctly calculate the initial drag position using the item's global position
                        dragAndDropState.dragPosition = currentPosition + offset
//                        dragAndDropState.dragPosition = currentSize.center.toOffset() + offset
                        dragAndDropState.draggableContent = content
                    },
                    onDragEnd = {
                        dragAndDropState.isDragging = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAndDropState.dragPosition += dragAmount
                    }
                )
            }
    ) {
        val alpha = if (isCurrentlyDragged && dataToDrop is DragData.ExistingTile) 0f else 1f
        Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
            content()
        }
    }
}

// --- Main Proof Screen Composable ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofScreen(
    problem: Problem,
    onBackClicked: () -> Unit
) {
    // --- State Management ---
    var proof by remember {
        mutableStateOf(Proof(lines = problem.premises.mapIndexed { i, p ->
            ProofLine(i + 1, p, Justification.Premise) }
        )) }
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
                    }
                )
            },
            floatingActionButton = {
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
                        currentFormula = Formula(emptyList()) // Always clear construction area
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
                        val isContradiction = if (lastLineNode is FormulaNode.BinaryOpNode &&
                            lastLineNode.operator.symbol == "∧"
                        ) {
                            val leftNegatedTree = WffParser.parse(
                                ForwardRuleGenerators.fNeg(
                                    ForwardRuleGenerators.treeToFormula(
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
                            finalFormula = ForwardRuleGenerators.fNeg(assumptionLine.formula)
                            justification =
                                Justification.ReductioAdAbsurdum(startLine, endLine, endLine)
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
                                Justification.ImplicationIntroduction(startLine, endLine)
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
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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
                                        if (selectedLines.contains(it)) selectedLines - it else selectedLines + it
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

                ConstructionArea(
                    formula = currentFormula,
                    onFormulaChange = { newFormula ->
                        currentFormula = newFormula
                    }
                )

                SymbolPalette()

                Button(
                    onClick = {
                        val result = ProofValidator.validate(proof)
                        // We do this next to allow for the case where the goal is expressed with
                        // surrounding parens but the user doesn't include them.  If the two formulas
                        // are otherwise equal, we consider the proof valid.
                        val finalLineMatchesGoal = proof.lines.lastOrNull()?.let { lastLine ->
                            WffParser.parse(lastLine.formula) == WffParser.parse(problem.conclusion)
                        } ?: false // If there's no last line, it can't match.
                        feedbackMessage = when {
                            !result.isValid -> "Error on line ${result.errorLine}: ${result.errorMessage}"
                            !finalLineMatchesGoal -> "Proof is valid, but does not reach the goal."
                            else -> "Congratulations! Proof is valid and complete."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Validate Proof") }
            }

            // --- Dialogs ---
            if (showAddLineDialog) {
                AddLineDialog(
                    onDismiss = { showAddLineDialog = false },
                    onConfirm = { justification, formulaToAdd ->
                        val newProofLine = ProofLine(
                            proof.lines.size + 1,
                            formulaToAdd,
                            justification,
                            currentDepth
                        )
                        proof = Proof(proof.lines + newProofLine)
                        currentFormula = Formula(emptyList())
                        selectedLines = emptySet()
                        showAddLineDialog = false
                    },
                    initialLines = selectedLines,
                    currentFormula = currentFormula,
                    fullProof = proof
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
                                subproofStartLines = subproofStartLines.filter { it < lastStart }
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

// --- UI Sub-components ---

@Composable
fun ConstructionArea(
    formula: Formula,
    onFormulaChange: (Formula) -> Unit
) {
    val dragAndDropState = LocalDragAndDropState.current
    var dropTargetBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var insertionIndex by remember { mutableIntStateOf(-1) }
    val itemPositions = remember { mutableStateMapOf<Int, Offset>() }
    val itemWidths = remember { mutableStateMapOf<Int, Int>() }

    // This is a unified effect to handle both hover detection during a drag
    // and the drop action when the drag ends. It avoids race conditions.
    LaunchedEffect(dragAndDropState.isDragging, dragAndDropState.dragPosition) {
        if (dragAndDropState.isDragging) {
            // While dragging, continuously calculate the hover state and insertion index.
            val isHovering = dropTargetBounds?.contains(dragAndDropState.dragPosition) ?: false
            if (isHovering) {
                var newIndex = formula.tiles.size
                for (i in formula.tiles.indices) {
                    val pos = itemPositions[i]
                    val width = itemWidths[i]
                    if (pos != null && width != null) {
                        if (dragAndDropState.dragPosition.x < pos.x + width / 2) {
                            newIndex = i
                            break
                        }
                    }
                }
                insertionIndex = newIndex
            } else {
                insertionIndex = -1
            }
        } else {
            // When not dragging (i.e., the drag just ended), handle the drop.
            if (dragAndDropState.dataToDrop != null) { // Check for pending data
                val data = dragAndDropState.dataToDrop
                if (insertionIndex != -1) { // Dropped inside the construction area
                    val currentTiles = formula.tiles.toMutableList()
                    when (data) {
                        is DragData.NewTile -> {
                            currentTiles.add(insertionIndex, data.tile)
                        }
                        is DragData.ExistingTile -> {
                            val draggedTile = currentTiles.removeAt(data.index)
                            val finalIndex = if (data.index < insertionIndex) insertionIndex - 1 else insertionIndex
                            currentTiles.add(finalIndex, draggedTile)
                        }
                        null -> {} // Don't do anything if no data.
                    }
                    onFormulaChange(Formula(currentTiles))
                } else { // Dropped outside (delete)
                    if (data is DragData.ExistingTile) {
                        val currentTiles = formula.tiles.toMutableList()
                        currentTiles.removeAt(data.index)
                        onFormulaChange(Formula(currentTiles))
                    }
                }
                // Reset state after handling the drop.
                dragAndDropState.dataToDrop = null
            }
        }
    }

    Box(
        modifier = Modifier.onGloballyPositioned {
            dropTargetBounds = it.boundsInWindow()
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(
                containerColor = if (insertionIndex != -1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                formula.tiles.forEachIndexed { index, tile ->
                    if (insertionIndex == index) {
                        Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    }
                    DraggableItem(dataToDrop = DragData.ExistingTile(tile, index)) {
                        Text(
                            text = tile.symbol,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .onGloballyPositioned {
                                    itemPositions[index] = it.localToWindow(Offset.Zero)
                                    itemWidths[index] = it.size.width
                                }
                        )
                    }
                }
                if (insertionIndex == formula.tiles.size) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

