package com.example.darkmusic.ui.settings

import androidx.lifecycle.ViewModel
import com.example.darkmusic.core.preferences.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settingsManager: SettingsManager
) : ViewModel()
