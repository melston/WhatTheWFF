package com.elsoft.whatthewff.ui.features.proof.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import com.elsoft.whatthewff.logic.LogicTile

sealed class DragData {
    data class NewTile(val tile: LogicTile) : DragData()
    data class ExistingTile(val tile: LogicTile, val index: Int) : DragData()
}

class DragAndDropState {
    var isDragging: Boolean by mutableStateOf(false)
    var dragPosition by mutableStateOf(Offset.Companion.Zero)
    var draggableContent by mutableStateOf<(@Composable () -> Unit)?>(null)
    var draggableContentSize by mutableStateOf(IntSize.Companion.Zero)
    var dataToDrop by mutableStateOf<DragData?>(null)
}

@Composable
fun DraggableItem(
    modifier: Modifier = Modifier,
    dataToDrop: DragData,
    content: @Composable () -> Unit,
    state: DragAndDropState
) {
    var currentSize by remember { mutableStateOf(IntSize.Zero) }
    var currentPosition by remember { mutableStateOf(Offset.Zero) } // keep track of global position
    val dragAndDropState = state
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
        Box(modifier = Modifier.Companion.graphicsLayer { this.alpha = alpha }) {
            content()
        }
    }
}