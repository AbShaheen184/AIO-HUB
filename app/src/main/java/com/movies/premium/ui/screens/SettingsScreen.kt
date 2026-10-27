package com.movies.premium.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.movies.premium.ui.viewmodel.SettingsViewModel

// The real settings UI lives in com.movies.premium.ui.screens.settings.
// This file only keeps the old import path working.

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) = com.movies.premium.ui.screens.settings.SettingsScreen(modifier, viewModel)
