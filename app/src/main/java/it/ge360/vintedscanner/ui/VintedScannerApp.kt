package it.ge360.vintedscanner.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.ge360.vintedscanner.domain.DealCandidate
import it.ge360.vintedscanner.domain.DealRanker
import it.ge360.vintedscanner.model.FeedbackReason
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.PreferenceProfile
import it.ge360.vintedscanner.model.PricePoint
import it.ge360.vintedscanner.model.SavedSearch
import it.ge360.vintedscanner.model.SharedListingDraft
import it.ge360.vintedscanner.model.SourceDiagnostic
import it.ge360.vintedscanner.model.SourceKind
import it.ge360.vintedscanner.model.SourceStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VintedScannerApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var createRequest by remember { mutableStateOf(false) }
    var sourceDialogOpen by remember { mutableStateOf(false) }
    var feedbackTarget by remember { mutableStateOf<Listing?>(null) }

    val backupPayload = state.backupPayload
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && backupPayload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(backupPayload)
                }
            }
        }
        viewModel.backupConsumed()
    }

    LaunchedEffect(backupPayload) {
        if (backupPayload != null) {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            backupLauncher.launch("vinted-scanner-backup-$stamp.json")
        }
    }

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

    feedbackTarget?.let { listing ->
        FeedbackReasonDialog(
            listing = listing,
            onDismiss = { feedbackTarget = null },
            onSelect = { reason ->
                viewModel.setFeedback(listing, reason)
                feedbackTarget = null
            }
        )
    }

    if (sourceDialogOpen) {
        SourceDiagnosticsDialog(
            diagnostics = state.sourceDiagnostics,
            onDismiss = { sourceDialogOpen = false },
            onRefresh = viewModel::refreshSources,
            onEnabledChange = viewModel::setSourceEnabled
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vinted Scanner", fontWeight = FontWeight.Bold)
                        Text(
                            if (state.liveMode) "LIVE ATTIVO" else "Scanner locale",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.liveMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.refreshSources()
                            sourceDialogOpen = true
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Diagnostica sorgenti")
                    }
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
                    label = { Text("Affari") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Archive, null) },
                    label = { Text("Archivio") }
                )
                NavigationBarItem(
                    selected = tab == 4,
                    onClick = { tab = 4 },
                    icon = { Icon(Icons.Default.Favorite, null) },
                    label = { Text("Watch") }
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            } else {
                when (tab) {
                    0 -> Dashboard(
                        state = state,
                        onFavorite = viewModel::toggleFavorite,
                        onHistory = viewModel::showHistory,
                        onNotInterested = { feedbackTarget = it },
                        onLiveChange = viewModel::setLiveMode,
                        onBackup = viewModel::prepareBackup,
                        onSources = {
                            viewModel.refreshSources()
                            sourceDialogOpen = true
                        }
                    )
                    1 -> Searches(
                        state = state,
                        createRequest = createRequest,
                        onSave = viewModel::saveSearch,
                        onDelete = viewModel::deleteSearch,
                        onToggle = viewModel::toggleSearch
                    )
                    2 -> DealCenterScreen(
                        listings = state.opportunities,
                        profile = state.preferenceProfile,
                        onFavorite = viewModel::toggleFavorite,
                        onHistory = viewModel::showHistory,
                        onNotInterested = { feedbackTarget = it }
                    )
                    3 -> ArchiveScreen(
                        listings = state.archive,
                        onFavorite = viewModel::toggleFavorite,
                        onHistory = viewModel::showHistory,
                        onNotInterested = { feedbackTarget = it }
                    )
                    else -> Opportunities(
                        listings = state.favorites,
                        emptyText = "La watchlist è vuota. Tocca il cuore su un annuncio.",
                        onFavorite = viewModel::toggleFavorite,
                        onHistory = viewModel::showHistory,
                        onNotInterested = { feedbackTarget = it }
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
    onNotInterested: (Listing) -> Unit,
    onLiveChange: (Boolean) -> Unit,
    onBackup: () -> Unit,
    onSources: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        "Scanner intelligente",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (state.liveMode)
                            "Modalità Live attiva: il telefono mantiene il monitoraggio con notifica persistente."
                        else
                            "Attiva Live quando vuoi controlli frequenti anche lasciando l'app in background."
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (state.liveMode) "LIVE ATTIVO" else "LIVE SPENTO",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                if (state.liveMode) "Controllo ogni 60 s" else "Modalità standard",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = state.liveMode,
                            onCheckedChange = onLiveChange
                        )
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricCard(
                    "Ricerche",
                    state.searches.count { it.active }.toString(),
                    Modifier.weight(1f)
                )
                MetricCard(
                    "Affari",
                    state.opportunities.count { it.score >= 75 }.toString(),
                    Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricCard("Archivio", state.archive.size.toString(), Modifier.weight(1f))
                MetricCard("Watchlist", state.favorites.size.toString(), Modifier.weight(1f))
            }
        }

        item {
            val best = state.opportunities.maxByOrNull { it.score }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Migliore occasione", style = MaterialTheme.typography.labelLarge)
                    if (best == null) {
                        Text("Ancora nessun dato sufficiente.")
                    } else {
                        Text(
                            best.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            best.score.toString() + "/100" +
                                (best.estimatedMargin?.let { " · +" + "%.2f".format(it) + " €" } ?: "")
                        )
                        LinearProgressIndicator(
                            progress = { best.score / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            val learned = state.preferenceProfile.tokenWeights
                .entries
                .sortedByDescending { kotlin.math.abs(it.value) }
                .take(6)

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Intelligence locale",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (learned.isEmpty() &&
                        state.preferenceProfile.purchasedCount == 0 &&
                        state.preferenceProfile.discardedCount == 0
                    ) {
                        Text("Ancora neutrale. Usa i feedback motivati per far imparare l'app.")
                    } else {
                        if (learned.isNotEmpty()) {
                            Text(
                                learned.joinToString(" · ") { entry ->
                                    entry.key + " " +
                                        if (entry.value >= 0) "+" + entry.value else entry.value.toString()
                                }
                            )
                        }
                        Text(
                            "Comprati " + state.preferenceProfile.purchasedCount +
                                " · Scartati " + state.preferenceProfile.discardedCount +
                                " · Troppo cari " + state.preferenceProfile.tooExpensiveCount,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Condizioni pessime " + state.preferenceProfile.badConditionCount +
                                " · Modello sbagliato " + state.preferenceProfile.wrongModelCount,
                            style = MaterialTheme.typography.bodySmall
                        )
                        state.preferenceProfile.averageTooExpensiveRatio?.let { ratio ->
                            Text(
                                "Soglia appresa “troppo caro”: circa " +
                                    "%.0f".format(ratio * 100) + "% del valore osservato",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Feedback recenti",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.feedbackEvents.isEmpty()) {
                        Text("Nessun feedback motivato ancora.")
                    } else {
                        state.feedbackEvents.take(3).forEach { event ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    event.title.take(30),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    feedbackReasonLabel(event.reason),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            val readyCount = state.sourceDiagnostics.count { it.status == SourceStatus.READY }
            val errorCount = state.sourceDiagnostics.count { it.status == SourceStatus.ERROR }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Sorgenti annunci",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        readyCount.toString() + " pronte · " +
                            state.sourceDiagnostics.size + " configurate" +
                            if (errorCount > 0) " · $errorCount errori" else ""
                    )
                    state.sourceDiagnostics.take(2).forEach { source ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(source.descriptor.name, modifier = Modifier.weight(1f))
                            Text(
                                sourceStatusLabel(source.status),
                                style = MaterialTheme.typography.labelMedium,
                                color = sourceStatusColor(source.status)
                            )
                        }
                    }
                    OutlinedButton(onClick = onSources, modifier = Modifier.fillMaxWidth()) {
                        Text("DIAGNOSTICA SORGENTI")
                    }
                }
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Backup locale",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Esporta ricerche, archivio e preferenze in un JSON versionato.")
                    Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("ESPORTA BACKUP")
                    }
                }
            }
        }

        item { Text("In evidenza", style = MaterialTheme.typography.titleLarge) }

        if (state.opportunities.isEmpty()) {
            item {
                EmptyCard("Condividi un annuncio con Vinted Scanner oppure crea una ricerca.")
            }
        } else {
            items(state.opportunities.take(4), key = { it.id }) {
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

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                if (existing == null) "Nuova ricerca" else "Modifica ricerca",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
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
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(search.query, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (search.active) "Attiva" else "In pausa",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (search.active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = search.active, onCheckedChange = { onToggle() })
            }

            val details = mutableListOf<String>()
            search.brand?.let { details += it }
            search.size?.let { details += "taglia $it" }
            search.condition?.let { details += it }
            search.maxPrice?.let { details += "max €" + "%.0f".format(it) }
            details += "margine €" + "%.0f".format(search.minMargin)
            Text(details.joinToString(" · "))

            HorizontalDivider()

            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        val terms = listOfNotNull(search.brand, search.query).joinToString(" ")
                        val builder = Uri.Builder()
                            .scheme("https")
                            .authority("www.vinted.it")
                            .appendPath("catalog")
                            .appendQueryParameter("search_text", terms)
                        search.maxPrice?.let {
                            builder.appendQueryParameter("price_to", "%.0f".format(Locale.US, it))
                        }
                        context.startActivity(Intent(Intent.ACTION_VIEW, builder.build()))
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
private fun DealCenterScreen(
    listings: List<Listing>,
    profile: PreferenceProfile,
    onFavorite: (Listing) -> Unit,
    onHistory: (Listing) -> Unit,
    onNotInterested: (Listing) -> Unit
) {
    var filter by remember { mutableIntStateOf(0) }
    var sort by remember { mutableIntStateOf(0) }

    val ranked = remember(listings, profile) {
        DealRanker.rank(listings = listings, profile = profile)
    }

    val filtered = when (filter) {
        1 -> ranked.filter { it.dealIndex >= 80 }
        2 -> ranked.filter { (it.listing.estimatedMargin ?: Double.NEGATIVE_INFINITY) >= 30.0 }
        3 -> ranked.filter {
            it.listing.marketConfidence >= 70 && it.listing.marketSimilarity >= 65
        }
        4 -> ranked.filter { it.freshnessScore >= 65 }
        else -> ranked
    }

    val visible = when (sort) {
        1 -> filtered.sortedByDescending { it.listing.estimatedMargin ?: Double.NEGATIVE_INFINITY }
        2 -> filtered.sortedByDescending { it.listing.marketConfidence }
        3 -> filtered.sortedByDescending { it.listing.firstSeenAt }
        else -> filtered
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Text(
                "Centro Affari",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Ranking locale basato su margine, qualità comparabili, confidenza, freschezza e rischi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            val best = ranked.firstOrNull()
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Radar occasioni", fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MetricCard(
                            "Indice 80+",
                            ranked.count { it.dealIndex >= 80 }.toString(),
                            Modifier.weight(1f)
                        )
                        MetricCard(
                            "Margine 30+",
                            ranked.count { (it.listing.estimatedMargin ?: -1.0) >= 30.0 }.toString(),
                            Modifier.weight(1f)
                        )
                    }
                    if (best != null) {
                        Text(
                            "Top: " + best.listing.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Indice Affare " + best.dealIndex + "/100" +
                                (best.listing.estimatedMargin?.let {
                                    " · margine " + (if (it >= 0) "+" else "") + "%.2f".format(it) + " €"
                                } ?: "")
                        )
                    }
                }
            }
        }

        item {
            Text("Filtra", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text("Tutti") })
                FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text("Indice 80+") })
                FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text("Margine 30+") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filter == 3, onClick = { filter = 3 }, label = { Text("Alta conf.") })
                FilterChip(selected = filter == 4, onClick = { filter = 4 }, label = { Text("Recenti") })
            }
        }

        item {
            Text("Ordina", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = sort == 0, onClick = { sort = 0 }, label = { Text("Indice") })
                FilterChip(selected = sort == 1, onClick = { sort = 1 }, label = { Text("Margine") })
                FilterChip(selected = sort == 2, onClick = { sort = 2 }, label = { Text("Confidenza") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = sort == 3, onClick = { sort = 3 }, label = { Text("Più recenti") })
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyCard("Nessun annuncio soddisfa questo filtro.")
            }
        } else {
            items(visible, key = { it.listing.id }) { candidate ->
                ListingCard(
                    listing = candidate.listing,
                    onFavorite = onFavorite,
                    onHistory = onHistory,
                    onNotInterested = onNotInterested,
                    dealCandidate = candidate
                )
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
private fun ArchiveScreen(
    listings: List<Listing>,
    onFavorite: (Listing) -> Unit,
    onHistory: (Listing) -> Unit,
    onNotInterested: (Listing) -> Unit
) {
    var filter by remember { mutableIntStateOf(0) }

    val filtered = when (filter) {
        1 -> listings.filter { it.favorite }
        2 -> listings.filter { it.feedback < 0 }
        3 -> listings.filter { it.feedbackReason == FeedbackReason.PURCHASED }
        4 -> listings.filter { it.feedbackReason == FeedbackReason.TOO_EXPENSIVE }
        else -> listings
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Text(
                "Archivio",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter == 0,
                    onClick = { filter = 0 },
                    label = { Text("Tutti") }
                )
                FilterChip(
                    selected = filter == 1,
                    onClick = { filter = 1 },
                    label = { Text("Watch") }
                )
                FilterChip(
                    selected = filter == 2,
                    onClick = { filter = 2 },
                    label = { Text("Scartati") }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter == 3,
                    onClick = { filter = 3 },
                    label = { Text("Comprati") }
                )
                FilterChip(
                    selected = filter == 4,
                    onClick = { filter = 4 },
                    label = { Text("Troppo cari") }
                )
            }
        }

        if (filtered.isEmpty()) {
            item { EmptyCard("Nessun annuncio in questa sezione.") }
        } else {
            items(filtered, key = { it.id }) {
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
    onNotInterested: (Listing) -> Unit,
    dealCandidate: DealCandidate? = null
) {
    val context = LocalContext.current

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            dealCandidate?.let { deal ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Indice Affare " + deal.dealIndex + "/100",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Freschezza " + deal.freshnessScore + "/100" +
                                " · margine relativo " + deal.marginRatio + "%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(99.dp)
                    ) {
                        Text(
                            deal.dealIndex.toString(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                deal.reasons.forEach { reason ->
                    Text(
                        "• $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
            }

            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listing.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatDate(listing.lastSeenAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = when {
                        listing.score >= 80 -> MaterialTheme.colorScheme.primaryContainer
                        listing.score >= 65 -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Text(
                        listing.score.toString() + "/100",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                "€" + "%.2f".format(listing.price),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            listing.comparableLabel?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "Firma comparabile: $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            listing.marketMedian?.let {
                Text("Valore osservato: €" + "%.2f".format(it))
                Text(
                    "Confronti " + listing.marketSampleCount +
                        " · similarità " + listing.marketSimilarity + "%" +
                        " · confidenza " + listing.marketConfidence + "%",
                    style = MaterialTheme.typography.labelMedium
                )
                if (listing.marketOutliersRemoved > 0) {
                    Text(
                        "Prezzi anomali esclusi: " + listing.marketOutliersRemoved,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { listing.marketConfidence / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            listing.estimatedMargin?.let {
                Text(
                    "Margine netto stimato: " +
                        (if (it >= 0) "+" else "") +
                        "%.2f".format(it) + " €",
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (listing.shipping > 0) {
                Text(
                    "Spese considerate: €" + "%.2f".format(listing.shipping),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (listing.preferenceBoost != 0) {
                Text(
                    "Preferenze: " +
                        if (listing.preferenceBoost > 0) "+" + listing.preferenceBoost
                        else listing.preferenceBoost.toString(),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            listing.condition?.let { Text("Condizione: $it") }

            if (listing.feedbackReason != FeedbackReason.NONE) {
                Text(
                    "Feedback: " + feedbackReasonLabel(listing.feedbackReason),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (listing.feedbackReason.polarity) {
                        1 -> MaterialTheme.colorScheme.primary
                        -1 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (listing.riskFlags.isNotEmpty()) {
                Text(
                    "Attenzione: " + listing.riskFlags.joinToString(),
                    color = MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider()

            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(listing.url)))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("APRI")
                }
                IconButton(onClick = { onHistory(listing) }) {
                    Icon(Icons.Default.History, contentDescription = "Storico prezzi")
                }
                IconButton(onClick = { onNotInterested(listing) }) {
                    Icon(
                        Icons.Default.ThumbDown,
                        contentDescription = "Non mi interessa",
                        tint = if (listing.feedback < 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onFavorite(listing) }) {
                    Icon(
                        if (listing.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (listing.favorite) "Rimuovi dalla watchlist" else "Aggiungi alla watchlist",
                        tint = if (listing.favorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackReasonDialog(
    listing: Listing,
    onDismiss: () -> Unit,
    onSelect: (FeedbackReason) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Perché?") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    listing.title,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Il motivo cambia ciò che Vinted Scanner impara. Prezzo e condizioni non penalizzano automaticamente marca o modello.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { onSelect(FeedbackReason.PURCHASED) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("COMPRATO") }

                OutlinedButton(
                    onClick = { onSelect(FeedbackReason.TOO_EXPENSIVE) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("TROPPO CARO") }

                OutlinedButton(
                    onClick = { onSelect(FeedbackReason.BAD_CONDITION) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("CONDIZIONI PESSIME") }

                OutlinedButton(
                    onClick = { onSelect(FeedbackReason.WRONG_MODEL) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("MODELLO SBAGLIATO") }

                OutlinedButton(
                    onClick = { onSelect(FeedbackReason.DISCARDED) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("SCARTATO") }

                if (listing.feedbackReason != FeedbackReason.NONE) {
                    TextButton(
                        onClick = { onSelect(FeedbackReason.NONE) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("AZZERA FEEDBACK") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ANNULLA") }
        }
    )
}

@Composable
private fun SourceDiagnosticsDialog(
    diagnostics: List<SourceDiagnostic>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onEnabledChange: (String, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Diagnostica sorgenti") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (diagnostics.isEmpty()) {
                    Text("Nessuna sorgente registrata.")
                } else {
                    diagnostics.forEach { source ->
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Row(Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            source.descriptor.name,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            sourceKindLabel(source.descriptor.kind),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    if (source.descriptor.supportsAutomaticScan) {
                                        Switch(
                                            checked = source.enabled,
                                            onCheckedChange = {
                                                onEnabledChange(source.descriptor.id, it)
                                            }
                                        )
                                    }
                                }

                                Text(
                                    sourceStatusLabel(source.status),
                                    color = sourceStatusColor(source.status),
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (source.descriptor.requiresConfiguration &&
                                    source.status == SourceStatus.NOT_CONFIGURED
                                ) {
                                    Text(
                                        "Richiede un connettore autorizzato prima di poter scaricare annunci automaticamente.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Text(
                                    "Ultimo evento: " + formatOptionalDate(source.lastEventAt),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "Ultimo scan: " + formatOptionalDate(source.lastScanAt),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "Ultimo successo: " + formatOptionalDate(source.lastSuccessAt),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "Ultimo giro: " + source.lastReceivedCount +
                                        " · Totale ricevuti: " + source.totalReceived,
                                    style = MaterialTheme.typography.bodySmall
                                )

                                source.lastError?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        "Errore: $it",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("AGGIORNA DIAGNOSTICA")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CHIUDI") }
        }
    )
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
                Text("Controlla i dati. Il valore di riferimento può essere lasciato vuoto.")
                TextField(title, { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth())
                TextField(price, { price = it }, label = { Text("Prezzo €") }, modifier = Modifier.fillMaxWidth())
                TextField(
                    extraCosts,
                    { extraCosts = it },
                    label = { Text("Spese extra € (opzionale)") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    median,
                    { median = it },
                    label = { Text("Valore di riferimento € (opzionale)") },
                    modifier = Modifier.fillMaxWidth()
                )
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
    history: List<PricePoint>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storico · $title") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 480.dp)
            ) {
                if (history.isEmpty()) {
                    Text("Nessuno storico disponibile.")
                } else {
                    PriceHistoryChart(history)
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 240.dp)
                    ) {
                        items(history) { point ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(formatDate(point.seenAt), modifier = Modifier.weight(1f))
                                Text(
                                    "€" + "%.2f".format(point.price),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
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
private fun PriceHistoryChart(history: List<PricePoint>) {
    val ordered = history.sortedBy { it.seenAt }
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceVariant

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(14.dp)
        ) {
            if (ordered.isEmpty()) return@Canvas

            val prices = ordered.map { it.price }
            val minPrice = prices.minOrNull() ?: 0.0
            val maxPrice = prices.maxOrNull() ?: minPrice
            val range = max(1.0, maxPrice - minPrice)

            drawLine(
                color = grid,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f)
            )

            if (ordered.size == 1) {
                drawCircle(
                    color = primary,
                    radius = 7f,
                    center = Offset(size.width / 2f, size.height / 2f)
                )
                return@Canvas
            }

            val path = Path()
            ordered.forEachIndexed { index, point ->
                val x = (index.toFloat() / (ordered.lastIndex).toFloat()) * size.width
                val normalized = ((point.price - minPrice) / range).toFloat()
                val y = size.height - normalized * size.height

                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(primary, radius = 5f, center = Offset(x, y))
            }

            drawPath(path = path, color = primary)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(text, Modifier.padding(18.dp))
    }
}

private fun String.toDecimalOrNull(): Double? =
    replace(",", ".").trim().toDoubleOrNull()

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun sourceStatusColor(status: SourceStatus) =
    when (status) {
        SourceStatus.READY -> MaterialTheme.colorScheme.primary
        SourceStatus.ERROR -> MaterialTheme.colorScheme.error
        SourceStatus.SCANNING -> MaterialTheme.colorScheme.secondary
        SourceStatus.NOT_CONFIGURED,
        SourceStatus.DISABLED,
        SourceStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun sourceStatusLabel(status: SourceStatus): String =
    when (status) {
        SourceStatus.READY -> "Pronta"
        SourceStatus.IDLE -> "In attesa"
        SourceStatus.SCANNING -> "Scansione in corso"
        SourceStatus.NOT_CONFIGURED -> "Non configurata"
        SourceStatus.ERROR -> "Errore"
        SourceStatus.DISABLED -> "Disattivata"
    }

private fun feedbackReasonLabel(reason: FeedbackReason): String =
    when (reason) {
        FeedbackReason.NONE -> "Nessuno"
        FeedbackReason.FAVORITE -> "Interessante"
        FeedbackReason.PURCHASED -> "Comprato"
        FeedbackReason.DISCARDED -> "Scartato"
        FeedbackReason.TOO_EXPENSIVE -> "Troppo caro"
        FeedbackReason.BAD_CONDITION -> "Condizioni pessime"
        FeedbackReason.WRONG_MODEL -> "Modello sbagliato"
    }

private fun sourceKindLabel(kind: SourceKind): String =
    when (kind) {
        SourceKind.MANUAL_SHARE -> "Ingresso manuale Android"
        SourceKind.AUTHORIZED_REMOTE -> "Connettore remoto autorizzato"
    }

private fun formatOptionalDate(timestamp: Long?): String =
    timestamp?.let(::formatDate) ?: "—"
