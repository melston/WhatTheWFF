package com.elsoft.whatthewff.ui.features.proof.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

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
    isReiterateEnabled: Boolean,
    isAddDerivedLineEnabled: Boolean
) {
    Column(
        horizontalAlignment = Alignment.Companion.End,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isExpanded) {
            FabMenuItem(
                icon = Icons.Default.AddCircle,
                label = "Add Premise", onClick = onAddPremise, enabled = isAddPremiseEnabled
            )
            FabMenuItem(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                label = "Start Sub-proof",
                onClick = { onStartSubproof() },
                enabled = isStartSubproofEnabled
            )
            FabMenuItem(
                icon = Icons.Default.KeyboardArrowUp,
                label = "End Sub-proof", onClick = onEndSubproof, enabled = isEndSubproofEnabled
            )
            FabMenuItem(
                icon = Icons.Default.Replay,
                label = "Reiterate", onClick = onReiterate, enabled = isReiterateEnabled
            )
            FabMenuItem(
                icon = Icons.Default.Create,
                label = "Add Derived Line", onClick = onAddDerivedLine
            )
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
    Row(
        verticalAlignment = Alignment.Companion.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Text(
                text = label,
                modifier = Modifier.Companion.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
        FloatingActionButton(
            onClick = { if (enabled) onClick() },
            containerColor = if (enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.Companion.size(56.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Companion.Gray
            )
        }
    }
}