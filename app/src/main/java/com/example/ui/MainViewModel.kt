package com.example.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.analyzeBeachCleanup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

sealed class MainUiState {
    object Idle : MainUiState()
    object Loading : MainUiState()
    data class Success(val rawJson: String, val report: CleanupReport?) : MainUiState()
    data class Error(val message: String) : MainUiState()
}

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Idle)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _selectedImage = MutableStateFlow<Bitmap?>(null)
    val selectedImage: StateFlow<Bitmap?> = _selectedImage.asStateFlow()
    
    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun setImage(bitmap: Bitmap?) {
        _selectedImage.value = bitmap
    }

    fun setDescription(text: String) {
        _description.value = text
    }

    fun reset() {
        _selectedImage.value = null
        _description.value = ""
        _uiState.value = MainUiState.Idle
    }
    
    fun resetReport() {
        _uiState.value = MainUiState.Idle
    }

    fun analyze() {
        val image = _selectedImage.value
        val desc = _description.value
        if (image == null && desc.isBlank()) {
            _uiState.value = MainUiState.Error("Please provide an image or description.")
            return
        }

        _uiState.value = MainUiState.Loading

        viewModelScope.launch {
            try {
                val result = analyzeBeachCleanup(image, desc)
                if (result.startsWith("Error:")) {
                    _uiState.value = MainUiState.Error(result)
                } else {
                    val report = try {
                        json.decodeFromString<CleanupReport>(result)
                    } catch (e: Exception) {
                        null
                    }
                    _uiState.value = MainUiState.Success(result, report)
                }
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.localizedMessage ?: "Unknown Error")
            }
        }
    }
}
