package eu.espcaa.boardingpassscanner.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AirplaneTicket
import androidx.compose.material.icons.automirrored.outlined.AirplaneTicket
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import eu.espcaa.boardingpassscanner.R
import eu.espcaa.boardingpassscanner.data.BoardingPassDao
import eu.espcaa.boardingpassscanner.data.BoardingPassWithLegs
import eu.espcaa.boardingpassscanner.utils.AirlineColorCache
import eu.espcaa.boardingpassscanner.utils.AirlineManager
import eu.espcaa.boardingpassscanner.utils.AirportManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File

data class Screen(
    val name: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val archived: Boolean = false,
    val content: @Composable (innerPadding: PaddingValues) -> Unit
)

private val BoardingPassExportJson = Json {
    prettyPrint = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    onScanClick: () -> Unit = {},
    onPassClick: (String, Int) -> Unit,
) {

    var activeQuery by rememberSaveable { mutableStateOf("") }

    var selectedPassesIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var isSelectionMode = selectedPassesIds.isNotEmpty()
    BackHandler(enabled = isSelectionMode) {
        selectedPassesIds = emptySet()
    }

    val toggleSelection: (Long) -> Unit = { id ->
        if (selectedPassesIds.contains(id)) {
            selectedPassesIds = selectedPassesIds - id
        } else {
            selectedPassesIds = selectedPassesIds + id
        }
    }

    var showArchived by rememberSaveable { mutableStateOf(false) }

    val screens = listOf(
        Screen(
            "Library",
            Icons.AutoMirrored.Filled.AirplaneTicket,
            Icons.AutoMirrored.Outlined.AirplaneTicket
        ) {
            HomeContent(
                it,
                searchQuery = activeQuery,
                onPassClick =
                    onPassClick,
                toggleSelection = {
                    toggleSelection(it)
                },
                selectedPassesIds = selectedPassesIds,
                isSelectionMode = isSelectionMode,
                archived = showArchived,
                onArchiveFooterClick = {
                    selectedPassesIds = emptySet()
                    showArchived = !showArchived
                },
            )
        },
    )

    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var searchHistory by remember { mutableStateOf(listOf<String>()) }

    val dao: BoardingPassDao = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    val currentScreen = screens[selectedIndex]
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }



    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            floatingActionButton = {
                if (!showArchived && !isSearchExpanded) {
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = onScanClick,
                            modifier = Modifier.size(80.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Add",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        ) { scaffoldPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                currentScreen.content(scaffoldPadding)
            }
        }

        if (isSelectionMode == true) {
            TopAppBar(
                title = { Text("${selectedPassesIds.size} selected") },
                navigationIcon = {
                    IconButton(onClick = { selectedPassesIds = emptySet() }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val selectedPasses = dao.getBoardingPassesByIds(selectedPassesIds.toList())
                            if (selectedPasses.isEmpty()) {
                                Toast.makeText(context, "No passes selected", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            shareBoardingPassesJson(context, selectedPasses)
                        }
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_download),
                            contentDescription = "Export JSON"
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            selectedPassesIds.forEach { id ->
                                dao.setArchived(id, archived = !showArchived)
                            }
                            selectedPassesIds = emptySet()
                        }

                    }) {
                        Icon(
                            if (showArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = if (showArchived) "Restore" else "Archive"
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            selectedPassesIds.forEach { id -> dao.deleteBoardingPass(id) }
                            selectedPassesIds = emptySet()
                        }

                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        } else {
            SearchBarOverlay(
                query = query,
                onQueryChange = {
                    query = it
                    activeQuery = it
                },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                searchHistory = searchHistory,
                onSearch = { searchText ->
                    activeQuery = searchText
                    expanded = false
                    if (searchText.isNotBlank() && !searchHistory.contains(searchText)) {
                        searchHistory = (listOf(searchText) + searchHistory).take(10)
                    }
                },
                onClear = {
                    query = ""
                    activeQuery = ""
                }
            )
        }

    }
}

fun shareBoardingPassesJson(
    context: Context,
    passes: List<BoardingPassWithLegs>
) {
    val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
    val exportFile = File(exportDir, "boarding-passes-${System.currentTimeMillis()}.json")
    exportFile.writeText(boardingPassesToJson(passes))

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        exportFile
    )

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Export boarding passes"))
}

fun boardingPassesToJson(passes: List<BoardingPassWithLegs>): String {
    val root = buildJsonObject {
        put("schemaVersion", JsonPrimitive(1))
        put("exportedAt", JsonPrimitive(System.currentTimeMillis()))
        put(
            "boardingPasses",
            JsonArray(passes.map { pass ->
                buildJsonObject {
                    put("id", JsonPrimitive(pass.boardingPass.id))
                    put("passengerName", JsonPrimitive(pass.boardingPass.passengerName))
                    put("pnrCode", JsonPrimitive(pass.boardingPass.pnrCode))
                    put("numberOfLegs", JsonPrimitive(pass.boardingPass.numberOfLegs))
                    put("isEticket", JsonPrimitive(pass.boardingPass.isEticket))
                    put("year", JsonPrimitive(pass.boardingPass.year))
                    put("rawBarcode", JsonPrimitive(pass.boardingPass.rawBarcode))
                    put("scannedAt", JsonPrimitive(pass.boardingPass.scannedAt))
                    put("archived", JsonPrimitive(pass.boardingPass.archived))
                    put(
                        "legs",
                        JsonArray(pass.legs.map { leg ->
                            JsonObject(
                                mapOf(
                                    "from" to JsonPrimitive(leg.from),
                                    "to" to JsonPrimitive(leg.to),
                                    "carrier" to JsonPrimitive(leg.carrier),
                                    "flightNumber" to JsonPrimitive(leg.flightNumber),
                                    "flightJulian" to JsonPrimitive(leg.flightJulian),
                                    "seat" to JsonPrimitive(leg.seat),
                                    "sequenceNumber" to JsonPrimitive(leg.sequenceNumber),
                                    "compartmentCode" to JsonPrimitive(leg.compartmentCode)
                                )
                            )
                        })
                    )
                }
            })
        )
    }

    return BoardingPassExportJson.encodeToString(JsonObject.serializer(), root)
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeContent(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    searchQuery: String = "",
    onPassClick: (String, Int) -> Unit = { _, _ -> },
    toggleSelection: (Long) -> Unit = { _ -> },
    selectedPassesIds: Set<Long> = emptySet(),
    isSelectionMode: Boolean = false,
    archived: Boolean = false,
    onArchiveFooterClick: () -> Unit = {}
) {

    val dao: BoardingPassDao = koinInject()
    val airlineManager: AirlineManager = koinInject()
    val colorCache: AirlineColorCache = koinInject()
    val isDarkTheme = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val allPasses by dao.getBoardingPasses(archived).collectAsState(initial = emptyList())
    val airlineColors by colorCache.colors.collectAsState()

    LaunchedEffect(isDarkTheme) {
        colorCache.loadFromDb(isDarkTheme)
    }

    val passes = remember(allPasses, searchQuery) {
        if (searchQuery.isBlank()) {
            allPasses
        } else {
            val q = searchQuery.trim().lowercase()
            allPasses.filter { pass ->
                pass.boardingPass.passengerName.lowercase().contains(q) ||
                        pass.boardingPass.pnrCode.lowercase().contains(q) ||
                        pass.legs.any { leg ->
                            leg.from.lowercase().contains(q) ||
                                    leg.to.lowercase().contains(q) ||
                                    leg.carrier.lowercase().contains(q) ||
                                    leg.flightNumber.lowercase().contains(q)
                        }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(top = 80.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            if (allPasses.isEmpty()) {
                Column {
                    BoardingPassesPlaceholder(
                        title = if (archived) "Archive is empty" else "No boarding passes yet",
                        subtitle = if (archived) "Archived passes will show up here." else "Scan your first one to get started!"
                    )
                    ArchiveFooterButton(
                        archived = archived,
                        onClick = onArchiveFooterClick
                    )
                }
            } else if (passes.isEmpty()) {
                Text("No results for \"$searchQuery\"")
            } else {
                val haptic = LocalHapticFeedback.current
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(passes, key = { it.boardingPass.id }) { pass ->
                        val carrier = pass.legs.first().carrier
                        val passId = pass.boardingPass.id
                        BoardingPassCard(
                            pass = pass,
                            airlineManager = airlineManager,
                            cachedScheme = airlineColors[carrier],
                            onSchemeReady = { seedColor, scheme ->
                                scope.launch {
                                    colorCache.cacheColorScheme(
                                        carrier,
                                        seedColor,
                                        scheme
                                    )
                                }
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    toggleSelection(passId)
                                } else {
                                    onPassClick(
                                        pass.boardingPass.rawBarcode,
                                        pass.boardingPass.year
                                    )
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                toggleSelection(passId)
                            },
                            selected = selectedPassesIds.contains(passId)
                        )
                    }
                    item {
                        ArchiveFooterButton(
                            archived = archived,
                            onClick = onArchiveFooterClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveFooterButton(
    archived: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        TextButton(onClick = onClick) {
            Icon(
                if (archived) Icons.AutoMirrored.Outlined.AirplaneTicket else Icons.Outlined.Archive,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (archived) "Back to library" else "Archived passes",
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoardingPassCard(
    pass: BoardingPassWithLegs,
    airlineManager: AirlineManager = koinInject(),
    airportManager: AirportManager = koinInject(),
    cachedScheme: ColorScheme?,
    onSchemeReady: (Color, ColorScheme) -> Unit,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    selected: Boolean = false
) {
    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    var localScheme by remember(pass.legs.first().carrier) { mutableStateOf(cachedScheme) }

    val imageRequest = ImageRequest.Builder(context)
        .data(getAirlineLogoURL(pass.legs.first().carrier, airlineManager))
        .allowHardware(false)
        .build()


    MaterialTheme(colorScheme = localScheme ?: MaterialTheme.colorScheme) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // box for selected things
                Box() {
                    // unselected one
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = MaterialShapes.SoftBurst.toShape(),
                        color = Color.White
                    ) {
                        AsyncImage(
                            contentDescription = "Airline Logo",
                            modifier = Modifier
                                .size(48.dp)
                                .padding(16.dp),
                            model = imageRequest,
                            onSuccess = { result ->
                                if (cachedScheme != null) return@AsyncImage
                                val bitmap = result.result.image.toBitmap()
                                Palette.from(bitmap).generate { palette ->
                                    val swatch = palette?.vibrantSwatch ?: palette?.dominantSwatch
                                    swatch?.let {
                                        val hsl = FloatArray(3)
                                        androidx.core.graphics.ColorUtils.colorToHSL(it.rgb, hsl)
                                        hsl[1] = (hsl[1] * 1.5f).coerceAtMost(1f)
                                        hsl[2] = maxOf(hsl[2], 0.5f)
                                        val seedColor =
                                            Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
                                        val scheme = dynamicColorScheme(
                                            seedColor = seedColor,
                                            isDark = isDarkTheme,
                                            style = PaletteStyle.Vibrant
                                        )
                                        onSchemeReady(seedColor, scheme)
                                    }
                                }
                            }
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = selected,
                        enter = fadeIn() + scaleIn(initialScale = 0.6f),
                        exit = fadeOut() + scaleOut(targetScale = 0.6f)
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = MaterialShapes.SoftBurst.toShape(),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(16.dp)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = "${airportManager.getCity(pass.legs.first().from)} → ${
                            airportManager.getCity(
                                pass.legs.first().to
                            )
                        }",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = "${pass.legs.first().carrier} ${
                                    pass.legs.first().flightNumber.trimStart(
                                        '0'
                                    )
                                }",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    searchHistory: List<String> = emptyList(),
    onSearch: (String) -> Unit = {},
    onClear: () -> Unit = {}
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (expanded) 0.dp else 16.dp)
    ) {
        SearchBar(
            modifier = Modifier.align(Alignment.TopCenter),
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = { onSearch(it) },
                    expanded = expanded,
                    onExpandedChange = onExpandedChange,
                    placeholder = { Text("Search in your flights...") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search Icon"
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear Search",
                                modifier = Modifier.clickable { onClear() }
                            )
                        }
                    }
                )
            },
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(searchHistory) { suggestion ->
                    ListItem(
                        headlineContent = { Text(suggestion) },
                        modifier = Modifier.clickable {
                            onQueryChange(suggestion)
                            onSearch(suggestion)
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun HomeBottomBar(
    screens: List<Screen>,
    currentScreen: Screen,
    onScreenSelected: (Int) -> Unit = {}
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth()
    ) {
        screens.forEachIndexed { index, screen ->
            NavigationBarItem(
                icon = {
                    Icon(
                        if (currentScreen.name == screen.name) screen.selectedIcon else screen.unselectedIcon,
                        contentDescription = screen.name
                    )
                },
                label = { Text(screen.name) },
                selected = currentScreen.name == screen.name,
                onClick = {
                    onScreenSelected(index)
                }
            )
        }

    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoardingPassesPlaceholder() {
    BoardingPassesPlaceholder(
        title = "No boarding passes yet",
        subtitle = "Scan your first one to get started!"
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoardingPassesPlaceholder(
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = MaterialShapes.SoftBurst.toShape(),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.AirplaneTicket,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(32.dp)
                        .padding(16.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
