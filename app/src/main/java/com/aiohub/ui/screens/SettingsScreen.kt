package com.aiohub.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiohub.ui.viewmodel.SettingsViewModel

// The real settings UI lives in com.aiohub.ui.screens.settings.
// This file only keeps the old import path working.

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) = com.aiohub.ui.screens.settings.SettingsScreen(modifier, viewModel)
