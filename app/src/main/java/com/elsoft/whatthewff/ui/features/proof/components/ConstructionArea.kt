package com.elsoft.whatthewff.ui.features.proof.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsoft.whatthewff.logic.Formula

@Composable
fun ConstructionArea(
    formula: Formula,
    onFormulaChange: (Formula) -> Unit,
    state: DragAndDropState
) {
    val dragAndDropState = state
    var dropTargetBounds by remember { mutableStateOf<Rect?>(null) }
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
                            val finalIndex =
                                if (data.index < insertionIndex) insertionIndex - 1 else insertionIndex
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
        modifier = Modifier.Companion.onGloballyPositioned {
            dropTargetBounds = it.boundsInWindow()
        }
    ) {
        Card(
            modifier = Modifier.Companion.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(
                containerColor = if (insertionIndex != -1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier.Companion
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.Companion.CenterVertically
            ) {
                formula.tiles.forEachIndexed { index, tile ->
                    if (insertionIndex == index) {
                        Box(
                            Modifier.Companion.width(2.dp).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    DraggableItem(dataToDrop = DragData.ExistingTile(tile, index), content = {
                        Text(
                            text = tile.symbol,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Companion.Monospace,
                            modifier = Modifier.Companion
                                .padding(horizontal = 2.dp)
                                .onGloballyPositioned {
                                    itemPositions[index] = it.localToWindow(Offset.Companion.Zero)
                                    itemWidths[index] = it.size.width
                                }
                        )
                    }, state = state)
                }
                if (insertionIndex == formula.tiles.size) {
                    Box(
                        Modifier.Companion.width(2.dp).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}