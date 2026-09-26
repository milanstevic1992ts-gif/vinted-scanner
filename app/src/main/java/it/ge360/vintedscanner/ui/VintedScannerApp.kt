package it.ge360.vintedscanner.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.SavedSearch
import it.ge360.vintedscanner.model.SharedListingDraft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VintedScannerApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var createRequest by remember { mutableStateOf(false) }

    state.sharedDraft?.let { draft ->
        SharedImportDialog(
            draft = draft,
            onDismiss = viewModel::dismissSharedDraft,
            onImport = viewModel::importSharedListing
        )
    }

    if (state.historyTitle != null) {
        PriceHistoryDialog(
            title = state.historyTitle.orEmpty(),
            history = state.priceHistory,
            onDismiss = viewModel::closeHistory
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vinted Scanner")
                        Text("Caccia alle occasioni", style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::scanNow) { Text("AGGIORNA") }
                }
            )
        },
        floatingActionButton = {
            if (tab == 1) {
                FloatingActionButton(onClick = { createRequest = !createRequest }) {
                    Icon(Icons.Default.Add, contentDescription = "Nuova ricerca")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Search, null) },
                    label = { Text("Ricerche") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Star, null) },
                    label = { Text("Occasioni") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Favorite, null) },
                    label = { Text("Watchlist") }
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            } else {
                when (tab) {
                    0 -> Dashboard(state, viewModel::toggleFavorite, viewModel::showHistory, viewModel::notInterested)
                    1 -> Searches(
                        state = state,
                        createRequest = createRequest,
                        onSave = viewModel::saveSearch,
                        onDelete = viewModel::deleteSearch,
                        onToggle = viewModel::toggleSearch
                    )
                    2 -> Opportunities(
                        listings = state.opportunities,
                        emptyText = "Le occasioni salvate o trovate compariranno qui.",
                        onFavorite = viewModel::toggleFavorite,
                        onHistory = viewModel::showHistory,
                        onNotInterested = viewModel::notInterested
                    )
                    else -> Opportunities(
                        listings = state.favorites,
                        emptyText = "La watchlist è vuota. Tocca il cuore su un annuncio.",
                        onFavorite = viewModel::toggleFavorite,
                        onHistory = viewModel::showHistory,
                        onNotInterested = viewModel::notInterested
                    )
                }
            }
        }
    }
}

@Composable
private fun Dashboard(
    state: ScannerUiState,
    onFavorite: (Listing) -> Unit,
    onHistory: (Listing) -> Unit,
    onNotInterested: (Listing) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item { Text("Oggi", style = MaterialTheme.typography.headlineMedium) }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricCard("Ricerche", state.searches.count { it.active }.toString(), Modifier.weight(1f))
                MetricCard("Occasioni", state.opportunities.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricCard("Watchlist", state.favorites.size.toString(), Modifier.weight(1f))
                val best = state.opportunities.maxByOrNull { it.score }
                MetricCard(
                    "Migliore",
                    best?.score?.let { it.toString() + "/100" } ?: "—",
                    Modifier.weight(1f)
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Importa direttamente dal telefono", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Da Vinted usa Condividi → Vinted Scanner. L'annuncio viene deduplicato e lo storico prezzo resta sul telefono."
                    )
                }
            }
        }
        item {
            val learned = state.preferenceProfile.tokenWeights
                .entries
                .sortedByDescending { kotlin.math.abs(it.value) }
                .take(5)
            Card(Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Intelligence locale", style = MaterialTheme.typography.titleMedium)
                    if (learned.isEmpty()) {
                        Text("Ancora neutrale: usa il cuore o 'Non mi interessa' per far imparare le tue preferenze.")
                    } else {
                        Text(
                            learned.joinToString(" · ") { entry ->
                                entry.key + " " + if (entry.value >= 0) "+" + entry.value else entry.value.toString()
                            }
                        )
                    }
                }
            }
        }
        item { Text("In evidenza", style = MaterialTheme.typography.titleLarge) }
        if (state.opportunities.isEmpty()) {
            item {
                EmptyCard("Nessun annuncio ancora. Condividi un annuncio con Vinted Scanner oppure crea una ricerca.")
            }
        } else {
            items(state.opportunities.take(5), key = { it.id }) {
                ListingCard(it, onFavorite, onHistory, onNotInterested)
            }
        }
    }
}

@Composable
private fun Searches(
    state: ScannerUiState,
    createRequest: Boolean,
    onSave: (SavedSearch?, String, Double?, String?, String?, String?, Double?) -> Unit,
    onDelete: (Long) -> Unit,
    onToggle: (SavedSearch) -> Unit
) {
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavedSearch?>(null) }

    LaunchedEffect(createRequest) {
        if (createRequest) {
            editing = null
            editorOpen = true
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        if (editorOpen) {
            item {
                SearchEditor(
                    existing = editing,
                    onCancel = {
                        editorOpen = false
                        editing = null
                    },
                    onSave = { query, maxPrice, size, brand, condition, margin ->
                        onSave(editing, query, maxPrice, size, brand, condition, margin)
                        editorOpen = false
                        editing = null
                    }
                )
            }
        }

        if (state.searches.isEmpty()) {
            item { EmptyCard("Premi + per creare la prima ricerca.") }
        } else {
            items(state.searches, key = { it.id }) { search ->
                SearchCard(
                    search = search,
                    onEdit = {
                        editing = search
                        editorOpen = true
                    },
                    onDelete = { onDelete(search.id) },
                    onToggle = { onToggle(search) }
                )
            }
        }
    }
}

@Composable
private fun SearchEditor(
    existing: SavedSearch?,
    onCancel: () -> Unit,
    onSave: (String, Double?, String?, String?, String?, Double?) -> Unit
) {
    var query by remember(existing?.id) { mutableStateOf(existing?.query.orEmpty()) }
    var price by remember(existing?.id) { mutableStateOf(existing?.maxPrice?.toString().orEmpty()) }
    var size by remember(existing?.id) { mutableStateOf(existing?.size.orEmpty()) }
    var brand by remember(existing?.id) { mutableStateOf(existing?.brand.orEmpty()) }
    var condition by remember(existing?.id) { mutableStateOf(existing?.condition.orEmpty()) }
    var margin by remember(existing?.id) { mutableStateOf(existing?.minMargin?.toString() ?: "20") }

    Card(Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                if (existing == null) "Nuova ricerca" else "Modifica ricerca",
                style = MaterialTheme.typography.titleLarge
            )
            TextField(query, { query = it }, label = { Text("Cosa cerchi") }, modifier = Modifier.fillMaxWidth())
            TextField(brand, { brand = it }, label = { Text("Marca") }, modifier = Modifier.fillMaxWidth())
            TextField(size, { size = it }, label = { Text("Taglia") }, modifier = Modifier.fillMaxWidth())
            TextField(condition, { condition = it }, label = { Text("Condizione") }, modifier = Modifier.fillMaxWidth())
            TextField(price, { price = it }, label = { Text("Prezzo massimo €") }, modifier = Modifier.fillMaxWidth())
            TextField(margin, { margin = it }, label = { Text("Margine minimo €") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("ANNULLA")
                }
                Button(
                    onClick = {
                        onSave(
                            query,
                            price.toDecimalOrNull(),
                            size,
                            brand,
                            condition,
                            margin.toDecimalOrNull()
                        )
                    },
                    enabled = query.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("SALVA")
                }
            }
        }
    }
}

@Composable
private fun SearchCard(
    search: SavedSearch,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(search.query, style = MaterialTheme.typography.titleMedium)
                    Text(if (search.active) "Attiva" else "In pausa", style = MaterialTheme.typography.labelMedium)
                }
                Switch(checked = search.active, onCheckedChange = { onToggle() })
            }

            val details = mutableListOf<String>()
            search.brand?.let { details += it }
            search.size?.let { details += "taglia " + it }
            search.condition?.let { details += it }
            search.maxPrice?.let { details += "max €" + "%.0f".format(it) }
            details += "margine €" + "%.0f".format(search.minMargin)
            Text(details.joinToString(" · "))

            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        val terms = listOfNotNull(search.brand, search.query).joinToString(" ")
                        val uriBuilder = Uri.Builder()
                            .scheme("https")
                            .authority("www.vinted.it")
                            .appendPath("catalog")
                            .appendQueryParameter("search_text", terms)
                        search.maxPrice?.let {
                            uriBuilder.appendQueryParameter("price_to", "%.0f".format(Locale.US, it))
                        }
                        context.startActivity(Intent(Intent.ACTION_VIEW, uriBuilder.build()))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("APRI VINTED")
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (search.active) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (search.active) "Pausa" else "Attiva"
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifica")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina")
                }
            }
        }
    }
}

@Composable
private fun Opportunities(
    listings: List<Listing>,
    emptyText: String,
    onFavorite: (Listing) -> Unit,
    onHistory: (Listing) -> Unit,
    onNotInterested: (Listing) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        if (listings.isEmpty()) {
            item { EmptyCard(emptyText) }
        } else {
            items(listings, key = { it.id }) {
                ListingCard(it, onFavorite, onHistory, onNotInterested)
            }
        }
    }
}

@Composable
private fun ListingCard(
    listing: Listing,
    onFavorite: (Listing) -> Unit,
    onHistory: (Listing) -> Unit,
    onNotInterested: (Listing) -> Unit
) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    listing.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(listing.score.toString() + "/100", style = MaterialTheme.typography.titleMedium)
            }
            Text("€" + "%.2f".format(listing.price))
            listing.marketMedian?.let {
                Text("Valore osservato: €" + "%.2f".format(it))
                Text(
                    "Confronti: " + listing.marketSampleCount +
                        " · confidenza " + listing.marketConfidence + "%",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            listing.estimatedMargin?.let { Text("Margine netto stimato: €" + "%.2f".format(it)) }
            if (listing.shipping > 0) {
                Text("Spese extra considerate: €" + "%.2f".format(listing.shipping), style = MaterialTheme.typography.labelMedium)
            }
            if (listing.preferenceBoost != 0) {
                Text(
                    "Preferenze personali: " +
                        if (listing.preferenceBoost > 0) "+" + listing.preferenceBoost else listing.preferenceBoost.toString(),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            listing.condition?.let { Text("Condizione: " + it) }
            if (listing.riskFlags.isNotEmpty()) {
                Text("Attenzione: " + listing.riskFlags.joinToString())
            }
            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(listing.url))) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("APRI ANNUNCIO")
                }
                IconButton(onClick = { onHistory(listing) }) {
                    Icon(Icons.Default.History, contentDescription = "Storico prezzi")
                }
                IconButton(onClick = { onNotInterested(listing) }) {
                    Icon(
                        Icons.Default.ThumbDown,
                        contentDescription = "Non mi interessa"
                    )
                }
                IconButton(onClick = { onFavorite(listing) }) {
                    Icon(
                        if (listing.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (listing.favorite) "Rimuovi dalla watchlist" else "Aggiungi alla watchlist"
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedImportDialog(
    draft: SharedListingDraft,
    onDismiss: () -> Unit,
    onImport: (String, Double?, Double?, Double?, String, String?) -> Unit
) {
    var title by remember(draft.rawText) { mutableStateOf(draft.titleGuess) }
    var price by remember(draft.rawText) { mutableStateOf(draft.priceGuess?.toString().orEmpty()) }
    var extraCosts by remember(draft.rawText) { mutableStateOf("") }
    var median by remember(draft.rawText) { mutableStateOf("") }
    var url by remember(draft.rawText) { mutableStateOf(draft.url.orEmpty()) }
    var condition by remember(draft.rawText) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importa annuncio") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Controlla i dati ricevuti. Se lasci vuoto il valore di riferimento, Vinted Scanner proverà a stimarlo dai confronti locali.")
                TextField(title, { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth())
                TextField(price, { price = it }, label = { Text("Prezzo €") }, modifier = Modifier.fillMaxWidth())
                TextField(extraCosts, { extraCosts = it }, label = { Text("Spese extra € (opzionale)") }, modifier = Modifier.fillMaxWidth())
                TextField(median, { median = it }, label = { Text("Valore di riferimento € (opzionale)") }, modifier = Modifier.fillMaxWidth())
                TextField(condition, { condition = it }, label = { Text("Condizione / note") }, modifier = Modifier.fillMaxWidth())
                TextField(url, { url = it }, label = { Text("Link annuncio") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onImport(
                        title,
                        price.toDecimalOrNull(),
                        extraCosts.toDecimalOrNull(),
                        median.toDecimalOrNull(),
                        url,
                        condition
                    )
                },
                enabled = price.toDecimalOrNull() != null && url.isNotBlank()
            ) {
                Text("IMPORTA")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ANNULLA") }
        }
    )
}

@Composable
private fun PriceHistoryDialog(
    title: String,
    history: List<it.ge360.vintedscanner.model.PricePoint>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storico · " + title) },
        text = {
            if (history.isEmpty()) {
                Text("Nessuno storico disponibile.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    items(history) { point ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                formatDate(point.seenAt),
                                modifier = Modifier.weight(1f)
                            )
                            Text("€" + "%.2f".format(point.price))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CHIUDI") }
        }
    )
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label)
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(16.dp))
    }
}

private fun String.toDecimalOrNull(): Double? =
    replace(",", ".").trim().toDoubleOrNull()

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
