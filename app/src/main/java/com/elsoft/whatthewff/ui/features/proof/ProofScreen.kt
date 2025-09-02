// File: ui/features/proof/ProofScreen.kt
// This file contains the Jetpack Compose UI for the main proof-building screen,
// now with full support for sub-proofs, reiteration, and RAA.

package com.elsoft.whatthewff.ui.features.proof

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsoft.whatthewff.logic.*
import com.elsoft.whatthewff.logic.ProofValidator

// --- Main Proof Screen Composable ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofScreen(
    problem: Problem,
    onBackClicked: () -> Unit
) {
    // --- State Management ---
    var proof by remember { mutableStateOf(Proof(lines = problem.premises.mapIndexed { i, p -> ProofLine(i + 1, p, Justification.Premise) })) }
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(problem.name) },
                navigationIcon = { IconButton(onClick = onBackClicked) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = {
            FabMenu(
                isExpanded = isFabMenuExpanded,
                onToggle = { isFabMenuExpanded = !isFabMenuExpanded },
                onAddPremise = {
                    val newProofLine = ProofLine(proof.lines.size + 1, currentFormula, Justification.Premise, currentDepth)
                    proof = Proof(proof.lines + newProofLine)
                    currentFormula = Formula(emptyList())
                    isFabMenuExpanded = false
                },
                onStartSubproof = {
                    val assumptionLineNumber = proof.lines.size + 1
                    val newProofLine = ProofLine(assumptionLineNumber, currentFormula, Justification.Assumption, currentDepth + 1)
                    proof = Proof(proof.lines + newProofLine)
                    subproofStartLines = subproofStartLines + assumptionLineNumber
                    currentDepth++
                    currentFormula = Formula(emptyList())
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
                                              lastLineNode.operator.symbol == "∧") {
                        val leftNegatedTree = WffParser.parse(
                            ForwardRuleGenerators.fNeg(treeToFormula(lastLineNode.left)))
                        leftNegatedTree == lastLineNode.right
                    } else {
                        false
                    }

                    if (isContradiction) {
                        // RAA: Conclude the negation of the assumption
                        finalFormula = ForwardRuleGenerators.fNeg(assumptionLine.formula)
                        justification = Justification.ReductioAdAbsurdum(startLine, endLine, endLine)
                    } else {
                        // II: Conclude the implication
                        finalFormula = ForwardRuleGenerators.fImplies(assumptionLine.formula, conclusionLine.formula)
                        justification = Justification.ImplicationIntroduction(startLine, endLine)
                    }

                    val newProofLine = ProofLine(proof.lines.size + 1, finalFormula, justification, currentDepth - 1)
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
                    val newProofLine = ProofLine(proof.lines.size + 1, lineToReiterate.formula, Justification.Reiteration(lineToReiterate.lineNumber), currentDepth)
                    proof = Proof(proof.lines + newProofLine)
                    selectedLines = emptySet()
                    isFabMenuExpanded = false
                },
                isAddPremiseEnabled = currentDepth == 0 && currentFormula.tiles.isNotEmpty(),
                isStartSubproofEnabled = currentFormula.tiles.isNotEmpty(),
                isEndSubproofEnabled = currentDepth > 0,
                isReiterateEnabled = selectedLines.size == 1 && currentDepth > 0 && (proof.lines.getOrNull(
                    selectedLines.first() - 1
                )?.depth ?: 0) < currentDepth
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
            Text("Goal: ${problem.conclusion}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                                selectedLines = if (selectedLines.contains(it)) selectedLines - it else selectedLines + it
                            },
                            onDeleteClicked = { lineToDelete = it }
                        )
                    }
                }
            }

            // --- Feedback & Controls ---
            Spacer(Modifier.height(8.dp))
            if (feedbackMessage != null) {
                Text(feedbackMessage ?: "", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            }

            ConstructionArea(
                formula = currentFormula,
                onDeleteLast = {
                    if (currentFormula.tiles.isNotEmpty()) {
                        currentFormula = Formula(currentFormula.tiles.dropLast(1))
                    }
                }
            )

            SymbolPalette(onSymbolClick = {
                currentFormula = Formula(currentFormula.tiles + it)
            })

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
                onConfirm = { justification ->
                    val newProofLine = ProofLine(proof.lines.size + 1, currentFormula, justification, currentDepth)
                    proof = Proof(proof.lines + newProofLine)
                    currentFormula = Formula(emptyList())
                    selectedLines = emptySet()
                    showAddLineDialog = false
                },
                initialLines = selectedLines.joinToString(",")
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


// --- UI Sub-components ---

@Composable
fun FabMenu(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onAddPremise: () -> Unit,
    onStartSubproof: () -> Unit,
    onEndSubproof: () -> Unit,
    onAddDerivedLine: () -> Unit,
    onReiterate: () -> Unit,
    isAddPremiseEnabled: Boolean,
    isStartSubproofEnabled: Boolean,
    isEndSubproofEnabled: Boolean,
    isReiterateEnabled: Boolean
) {
    Column(horizontalAlignment = Alignment.End,
           verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (isExpanded) {
            FabMenuItem(icon = Icons.Default.AddCircle,
                        label = "Add Premise", onClick = onAddPremise, enabled = isAddPremiseEnabled)
            FabMenuItem(icon = Icons.AutoMirrored.Filled.ArrowForward,
                        label = "Start Sub-proof", onClick = { onStartSubproof() }, enabled = isStartSubproofEnabled)
            FabMenuItem(icon = Icons.Default.KeyboardArrowUp,
                        label = "End Sub-proof", onClick = onEndSubproof, enabled = isEndSubproofEnabled)
            FabMenuItem(icon = Icons.Default.Replay,
                        label = "Reiterate", onClick = onReiterate, enabled = isReiterateEnabled)
            FabMenuItem(icon = Icons.Default.Create,
                        label = "Add Derived Line", onClick = onAddDerivedLine)
        }
        FloatingActionButton(onClick = onToggle) {
            Icon(
                if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (isExpanded) "Close Menu" else "Open Menu"
            )
        }
    }
}

@Composable
fun FabMenuItem(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Text(text = label,
                 modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                 style = MaterialTheme.typography.labelLarge)
        }
        FloatingActionButton(
            onClick = { if (enabled) onClick() },
            containerColor = if (enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.size(56.dp)
        ) {
            if (enabled) {
                Icon(icon, contentDescription = label)
            } else {
                Icon(icon, contentDescription = label, tint = Color.Gray)
            }
        }
    }
}

@Composable
fun ProofLineView(
    line: ProofLine,
    isSelected: Boolean,
    onLineClicked: (Int) -> Unit,
    onDeleteClicked: (Int) -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val indentSize = (line.depth * 24).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .clickable { onLineClicked(line.lineNumber) }
            .padding(start = indentSize, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${line.lineNumber}.",
            modifier = Modifier.width(32.dp),
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = line.formula.stringValue,
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp
        )
        Text(
            text = line.justification.displayText(),
            modifier = Modifier.padding(horizontal = 8.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable { onDeleteClicked(line.lineNumber) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete line", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ConstructionArea(formula: Formula, onDeleteLast: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(modifier = Modifier.weight(1f)) {
                items(formula.tiles) { tile ->
                    Text(
                        text = tile.symbol,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
            IconButton(onClick = onDeleteLast) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Delete last symbol")
            }
        }
    }
}

@Composable
fun SymbolPalette(onSymbolClick: (LogicTile) -> Unit) {
    val variables = AvailableTiles.variables
    val operators = AvailableTiles.connectors
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)
    ) {
        items(variables) { symbol ->
            Button(onClick = { onSymbolClick(symbol) }) {
                Text(symbol.symbol, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)
    ) {
        items(operators) { symbol ->
            Button(onClick = { onSymbolClick(symbol) }) {
                Text(symbol.symbol, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLineDialog(
    onDismiss: () -> Unit,
    onConfirm: (Justification) -> Unit,
    initialLines: String
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var lineRefs by remember { mutableStateOf(initialLines) }
    var selectedInferenceRule by remember { mutableStateOf(InferenceRule.MODUS_PONENS) }
    var selectedReplacementRule by remember { mutableStateOf(ReplacementRule.DOUBLE_NEGATION) }
    var isRuleDropdownExpanded by remember { mutableStateOf(false) }

    val lineNumbers = remember(lineRefs) {
        lineRefs.split(',').mapNotNull { it.trim().toIntOrNull() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Derived Line") },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Inference") }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Replacement") }
                    )
                }
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = lineRefs,
                    onValueChange = { lineRefs = it },
                    label = { Text("Reference Lines (e.g., 1,2)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = isRuleDropdownExpanded,
                    onExpandedChange = { isRuleDropdownExpanded = !isRuleDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = if (selectedTabIndex == 0) selectedInferenceRule.ruleName else selectedReplacementRule.ruleName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rule") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isRuleDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isRuleDropdownExpanded,
                        onDismissRequest = { isRuleDropdownExpanded = false }
                    ) {
                        if (selectedTabIndex == 0) {
                            InferenceRule.entries.forEach { rule ->
                                DropdownMenuItem(
                                    text = { Text(rule.ruleName) },
                                    onClick = {
                                        selectedInferenceRule = rule
                                        isRuleDropdownExpanded = false
                                    }
                                )
                            }
                        } else {
                            ReplacementRule.entries.forEach { rule ->
                                DropdownMenuItem(
                                    text = { Text(rule.ruleName) },
                                    onClick = {
                                        selectedReplacementRule = rule
                                        isRuleDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val justification = if (selectedTabIndex == 0) {
                        Justification.Inference(selectedInferenceRule, lineNumbers)
                    } else {
                        Justification.Replacement(selectedReplacementRule, lineNumbers.firstOrNull() ?: 0)
                    }
                    onConfirm(justification)
                }
            ) { Text("Confirm") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun DeleteConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Deletion") },
        text = { Text("Are you sure you want to delete this line and all subsequent lines? This action cannot be undone.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Confirm") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

