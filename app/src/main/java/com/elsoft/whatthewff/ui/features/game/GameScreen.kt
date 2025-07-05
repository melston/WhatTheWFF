// File: ui/features/game/GameScreen.kt
// This file defines the main interactive game screen.

package com.elsoft.whatthewff.ui.features.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsoft.whatthewff.logic.AvailableTiles
import com.elsoft.whatthewff.logic.Formula
import com.elsoft.whatthewff.logic.LogicTile
import com.elsoft.whatthewff.logic.WffValidator
import com.elsoft.whatthewff.ui.theme.WhatTheWFFTheme

/**
 * A composable that displays a single, tappable logic tile.
 *
 * @param tile The LogicTile to display.
 * @param modifier Modifier for custom styling.
 * @param onClick The action to perform when the tile is clicked.
 */
@Composable
fun LogicTileView(tile: LogicTile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(50.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tile.symbol,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Displays a single row of tappable tiles
 *
 * @param tiles A list of LogicTile objects to display.
 * @param onTileTapped A function that is called when a tile is tapped.
 */
@Composable
fun TileRow(title: String,
            tiles: List<LogicTile>,
            onTileTapped: (LogicTile) -> Unit,
            modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = title,
             style = MaterialTheme.typography.bodySmall.copy(
                 color = Color.Gray
             ))
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .wrapContentWidth(Alignment.Start),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(tiles) { tile ->
                LogicTileView(tile = tile) {
                    onTileTapped(tile)
                }
            }
        }
    }
}


/**
 * Displays the palette of available tiles that the user can select.
 *
 * @param onTileTapped A function that is called when a tile is tapped.
 */
@Composable
fun SymbolPalette(onTileTapped: (LogicTile) -> Unit) {
    data class TileSectionInfo(val title: String, val tiles: List<LogicTile>)

    val tileSections = listOf(
        TileSectionInfo("Variables", AvailableTiles.variables),
        TileSectionInfo("Operators", AvailableTiles.operators),
        TileSectionInfo("Grouping", AvailableTiles.grouping)
    )
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Symbol Palette",
             style = MaterialTheme.typography.titleMedium,
             modifier = Modifier.padding(horizontal = 8.dp))
        LazyColumn {
            items(tileSections) { section ->
                TileRow(
                    title = section.title,
                    tiles = section.tiles,
                    onTileTapped = onTileTapped
                )
            }
        }
    }
}

/**
 * Displays the area where the user constructs their formula.
 *
 * @param formula The current formula being constructed.
 */
@Composable
fun ConstructionArea(formula: Formula) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Construction Area", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (formula.tiles.isEmpty()) {
                Text("Tap a symbol to begin...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                formula.tiles.forEach { tile ->
                    // Use a non-clickable version for the construction area
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = tile.symbol, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

/**
 * The main screen for the WFF game.
 *
 * @param modifier The modifier to be applied to the layout.
 */
@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    // State for the formula the user is building
    var currentFormula by remember { mutableStateOf(Formula(emptyList())) }
    // State for the feedback message after checking the WFF
    var feedbackMessage by remember { mutableStateOf("Build a formula and check it.") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SymbolPalette { tile ->
            // When a tile is tapped, add it to the current formula
            currentFormula = Formula(currentFormula.tiles + tile)
        }

        Spacer(modifier = Modifier.height(16.dp))

        ConstructionArea(formula = currentFormula)

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = {
                // *** THIS IS THE UPDATED LOGIC ***
                // Call our new validator with the current formula
                val isValid = WffValidator.validate(currentFormula)
                feedbackMessage = if (isValid) {
                    "Congratulations! This is a Well-Formed Formula."
                } else {
                    "This is not a Well-Formed Formula. Check the rules and try again."
                }
            }) {
                Text("Check WFF")
            }

            Button(
                onClick = {
                    // Clear the formula and reset feedback
                    currentFormula = Formula(emptyList())
                    feedbackMessage = "Build a formula and check it."
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Clear")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Feedback display area
        Text(
            text = feedbackMessage,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}


@Preview(showBackground = true)
@Composable
fun GameScreenPreview() {
    WhatTheWFFTheme {
        GameScreen()
    }
}
