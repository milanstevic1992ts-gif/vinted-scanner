package it.ge360.vintedscanner.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.ge360.vintedscanner.model.Listing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VintedScannerApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }

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
                    TextButton(onClick = viewModel::scanNow) { Text("SCANSIONA") }
                }
            )
        },
        floatingActionButton = {
            if (tab == 1) {
                FloatingActionButton(onClick = { creating = !creating }) {
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
                    label = { Text("Dashboard") }
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
                    0 -> Dashboard(state)
                    1 -> Searches(state, creating, viewModel::addSearch)
                    else -> Opportunities(state.opportunities)
                }
            }
        }
    }
}

@Composable
private fun Dashboard(state: ScannerUiState) {
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
            val best = state.opportunities.maxByOrNull { it.score }
            MetricCard(
                "Miglior punteggio",
                best?.score?.let { it.toString() + "/100" } ?: "—",
                Modifier.fillMaxWidth()
            )
        }
        item { Text("In evidenza", style = MaterialTheme.typography.titleLarge) }
        if (state.opportunities.isEmpty()) {
            item {
                EmptyCard("Nessun annuncio ancora. Crea una ricerca: il connettore annunci è il prossimo modulo.")
            }
        } else {
            items(state.opportunities.take(5), key = { it.id }) { ListingCard(it) }
        }
    }
}

@Composable
private fun Searches(
    state: ScannerUiState,
    creating: Boolean,
    onCreate: (String, Double?, String?, Double?) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var margin by remember { mutableStateOf("20") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        if (creating) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Nuova ricerca", style = MaterialTheme.typography.titleLarge)
                        TextField(query, { query = it }, label = { Text("Cosa cerchi") }, modifier = Modifier.fillMaxWidth())
                        TextField(price, { price = it }, label = { Text("Prezzo massimo €") }, modifier = Modifier.fillMaxWidth())
                        TextField(size, { size = it }, label = { Text("Taglia") }, modifier = Modifier.fillMaxWidth())
                        TextField(margin, { margin = it }, label = { Text("Margine minimo €") }, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                onCreate(
                                    query,
                                    price.replace(",", ".").toDoubleOrNull(),
                                    size,
                                    margin.replace(",", ".").toDoubleOrNull()
                                )
                                query = ""
                                price = ""
                                size = ""
                                margin = "20"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("SALVA RICERCA")
                        }
                    }
                }
            }
        }

        if (state.searches.isEmpty()) {
            item { EmptyCard("Premi + per creare la prima ricerca.") }
        } else {
            items(state.searches, key = { it.id }) { search ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(search.query, style = MaterialTheme.typography.titleMedium)
                        val details = mutableListOf<String>()
                        search.maxPrice?.let { details += "max €" + "%.0f".format(it) }
                        search.size?.let { details += "taglia " + it }
                        details += "margine €" + "%.0f".format(search.minMargin)
                        Text(details.joinToString(" · "))
                    }
                }
            }
        }
    }
}

@Composable
private fun Opportunities(listings: List<Listing>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        if (listings.isEmpty()) {
            item { EmptyCard("Le occasioni trovate compariranno qui ordinate per punteggio.") }
        } else {
            items(listings, key = { it.id }) { ListingCard(it) }
        }
    }
}

@Composable
private fun ListingCard(listing: Listing) {
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
            listing.estimatedMargin?.let {
                Text("Margine stimato: €" + "%.2f".format(it))
            }
            if (listing.riskFlags.isNotEmpty()) {
                Text("Attenzione: " + listing.riskFlags.joinToString())
            }
            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(listing.url)))
                }
            ) {
                Text("APRI ANNUNCIO")
            }
        }
    }
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
