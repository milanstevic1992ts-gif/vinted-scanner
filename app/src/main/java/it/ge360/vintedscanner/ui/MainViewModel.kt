package it.ge360.vintedscanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.ge360.vintedscanner.VintedScannerApplication
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.SavedSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScannerUiState(
    val searches: List<SavedSearch> = emptyList(),
    val opportunities: List<Listing> = emptyList(),
    val loading: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as VintedScannerApplication).repository
    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            _state.value = ScannerUiState(
                searches = repository.searches(),
                opportunities = repository.opportunities()
            )
        }
    }

    fun addSearch(query: String, maxPrice: Double?, size: String?, minMargin: Double?) {
        if (query.isBlank()) return
        viewModelScope.launch {
            repository.addSearch(
                SavedSearch(
                    query = query.trim(),
                    maxPrice = maxPrice,
                    size = size?.trim()?.takeIf(String::isNotBlank),
                    minMargin = minMargin ?: 20.0
                )
            )
            refresh()
        }
    }

    fun scanNow() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            repository.scanActive()
            refresh()
        }
    }
}
