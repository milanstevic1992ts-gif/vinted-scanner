package it.ge360.vintedscanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.ge360.vintedscanner.VintedScannerApplication
import it.ge360.vintedscanner.data.SharedListingParser
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.PreferenceProfile
import it.ge360.vintedscanner.model.PricePoint
import it.ge360.vintedscanner.model.SavedSearch
import it.ge360.vintedscanner.model.SharedListingDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScannerUiState(
    val searches: List<SavedSearch> = emptyList(),
    val opportunities: List<Listing> = emptyList(),
    val favorites: List<Listing> = emptyList(),
    val preferenceProfile: PreferenceProfile = PreferenceProfile(),
    val sharedDraft: SharedListingDraft? = null,
    val priceHistory: List<PricePoint> = emptyList(),
    val historyTitle: String? = null,
    val loading: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as VintedScannerApplication
    private val repository = app.repository
    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val searches = repository.searches()
            val opportunities = repository.opportunities()
            val favorites = repository.favorites()
            val preferenceProfile = repository.preferenceProfile()
            _state.value = _state.value.copy(
                searches = searches,
                opportunities = opportunities,
                favorites = favorites,
                preferenceProfile = preferenceProfile,
                loading = false
            )
        }
    }

    fun saveSearch(
        existing: SavedSearch?,
        query: String,
        maxPrice: Double?,
        size: String?,
        brand: String?,
        condition: String?,
        minMargin: Double?
    ) {
        if (query.isBlank()) return
        viewModelScope.launch {
            repository.saveSearch(
                SavedSearch(
                    id = existing?.id ?: 0,
                    query = query.trim(),
                    maxPrice = maxPrice,
                    size = size?.trim()?.takeIf(String::isNotBlank),
                    brand = brand?.trim()?.takeIf(String::isNotBlank),
                    condition = condition?.trim()?.takeIf(String::isNotBlank),
                    minMargin = minMargin ?: 20.0,
                    active = existing?.active ?: true,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
            )
            refresh()
        }
    }

    fun deleteSearch(id: Long) {
        viewModelScope.launch {
            repository.deleteSearch(id)
            refresh()
        }
    }

    fun toggleSearch(search: SavedSearch) {
        viewModelScope.launch {
            repository.setSearchActive(search.id, !search.active)
            refresh()
        }
    }

    fun toggleFavorite(listing: Listing) {
        viewModelScope.launch {
            repository.setFavorite(listing.id, !listing.favorite)
            refresh()
        }
    }

    fun notInterested(listing: Listing) {
        viewModelScope.launch {
            repository.setNotInterested(listing.id)
            refresh()
        }
    }

    fun showHistory(listing: Listing) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                priceHistory = repository.priceHistory(listing.id),
                historyTitle = listing.title
            )
        }
    }

    fun closeHistory() {
        _state.value = _state.value.copy(priceHistory = emptyList(), historyTitle = null)
    }

    fun onSharedText(text: String?) {
        if (text.isNullOrBlank()) return
        _state.value = _state.value.copy(sharedDraft = SharedListingParser.parse(text))
    }

    fun dismissSharedDraft() {
        _state.value = _state.value.copy(sharedDraft = null)
    }

    fun importSharedListing(
        title: String,
        price: Double?,
        marketMedian: Double?,
        url: String,
        condition: String?
    ) {
        if (price == null || price < 0 || url.isBlank()) return
        viewModelScope.launch {
            val (listing, isNew) = repository.importSharedListing(
                title = title,
                price = price,
                marketMedian = marketMedian,
                url = url,
                condition = condition
            )
            if (isNew && listing.score >= 75 && (listing.estimatedMargin ?: 0.0) > 0) {
                app.notificationHelper.notifyOpportunity(listing)
            }
            _state.value = _state.value.copy(sharedDraft = null)
            refresh()
        }
    }

    fun scanNow() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val outcome = repository.scanActive()
            outcome.newOpportunities.forEach(app.notificationHelper::notifyOpportunity)
            refresh()
        }
    }
}
