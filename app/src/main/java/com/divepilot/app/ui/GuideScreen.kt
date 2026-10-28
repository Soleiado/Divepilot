@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.divepilot.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.divepilot.app.R

@Composable
fun GuideScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guide_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Section(
                stringResource(R.string.guide_1_title),
                stringResource(R.string.guide_1_text),
            )
            Section(
                stringResource(R.string.guide_2_title),
                stringResource(R.string.guide_2_text),
            )
            Section(
                stringResource(R.string.guide_3_title),
                stringResource(R.string.guide_3_text),
            )
            Section(
                stringResource(R.string.guide_4_title),
                stringResource(R.string.guide_4_text),
            )
            Section(
                stringResource(R.string.guide_5_title),
                stringResource(R.string.guide_5_text),
            )
            Section(
                stringResource(R.string.guide_6_title),
                stringResource(R.string.guide_6_text),
            )
        }
    }
}

@Composable
private fun Section(title: String, text: String) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
