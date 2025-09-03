package com.elsoft.whatthewff.ui.features.proof.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsoft.whatthewff.logic.AvailableTiles
import com.elsoft.whatthewff.ui.features.proof.DragData
import com.elsoft.whatthewff.ui.features.proof.DraggableItem

@Composable
fun SymbolPalette() {
    val variables = AvailableTiles.variables
    val operators = AvailableTiles.connectors
    LazyRow(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.Companion.CenterHorizontally)
    ) {
        items(variables) { symbol ->
            DraggableItem(dataToDrop = DragData.NewTile(symbol)) {
                Button(onClick = { /* Drag Only */ }) {
                    Text(
                        symbol.symbol,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Companion.Monospace
                    )
                }
            }
        }
    }
    LazyRow(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.Companion.CenterHorizontally)
    ) {
        items(operators) { operator ->
            DraggableItem(dataToDrop = DragData.NewTile(operator)) {
                Button(onClick = { /* Drag Only */ }) {
                    Text(
                        operator.symbol,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Companion.Monospace
                    )
                }
            }
        }
    }
}