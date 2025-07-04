package com.elsoft.whatthewff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.elsoft.whatthewff.ui.features.game.GameScreen
import com.elsoft.whatthewff.ui.features.proof.ProofScreen

import com.elsoft.whatthewff.ui.theme.WhatTheWFFTheme
import com.elsoft.whatthewff.ui.features.rulebook.RulebookScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhatTheWFFTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ProofScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
