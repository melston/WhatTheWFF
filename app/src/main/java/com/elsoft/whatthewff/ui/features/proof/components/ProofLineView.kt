package com.elsoft.whatthewff.ui.features.proof.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsoft.whatthewff.logic.ProofLine

@Composable
fun ProofLineView(
    line: ProofLine,
    isSelected: Boolean,
    onLineClicked: (Int) -> Unit,
    onDeleteClicked: (Int) -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Companion.Transparent
    val indentSize = (line.depth * 24).dp

    Row(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .clickable { onLineClicked(line.lineNumber) }
            .padding(start = indentSize, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Companion.CenterVertically
    ) {
        Text(
            text = "${line.lineNumber}.",
            modifier = Modifier.Companion.width(32.dp),
            fontFamily = FontFamily.Companion.Monospace
        )
        Text(
            text = line.formula.stringValue,
            modifier = Modifier.Companion.weight(1f),
            fontFamily = FontFamily.Companion.Monospace,
            fontSize = 16.sp
        )
        Text(
            text = line.justification.displayText(),
            modifier = Modifier.Companion.padding(horizontal = 8.dp),
            fontFamily = FontFamily.Companion.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
        Box(
            modifier = Modifier.Companion
                .size(32.dp)
                .clip(CircleShape)
                .clickable { onDeleteClicked(line.lineNumber) },
            contentAlignment = Alignment.Companion.Center
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete line",
                modifier = Modifier.Companion.size(18.dp)
            )
        }
    }
}