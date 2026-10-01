package com.falloutlondon.companion.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import java.util.prefs.Preferences

import kotlinx.coroutines.launch
private val prefs = Preferences.userRoot().node("fallout-london-companion")

private val Phosphor = Color(0xFF00FF44)
private val ScreenBlack = Color(0xFF020A04)

@Composable
fun FalloutLondonApp() {
    val scope = rememberCoroutineScope()
    val connection = remember { PipboyConnection() }
    val db = remember { PipboyDatabase() }
    val inventory = remember { InventoryStore() }
    val medical = remember { MedicalController() }

    var player by remember { mutableStateOf(PlayerState()) }
    var selectedTab by remember { mutableStateOf(MainTab.STAT) }
    var selectedCategory by remember { mutableStateOf(InventoryCategory.WEAPONS) }
    var boot by remember { mutableStateOf("OFF") }
    var connectionState by remember { mutableStateOf("DISCONNECTED") }
    var demoMode by remember { mutableStateOf(loadDemoMode()) }
    var autoStimpak by remember { mutableStateOf(loadAutoStimpak()) }
    var stimpakThreshold by remember { mutableStateOf(loadAutoStimpakThreshold()) }
    var showingSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (demoMode) {
            loadCachedDatabase(db)
            player = PlayerState.from(db, player)
            inventory.refresh(db)
        }
        connection.onState = { state ->
            scope.launch {
                connectionState = state
                if (state == "CONNECTED" && !demoMode) {
                    // A new TCP session starts a fresh live database stream.
                    // Drop stale values from the previous session before applying
                    // the first live update.
                    db.reset()
                    player = PlayerState()
                    inventory.refresh(db)
                }
            }
        }
        connection.onUpdate = { update ->
            scope.launch {
                db.apply(update)
                saveCachedDatabase(db)
                player = PlayerState.from(db, player)
                inventory.category = selectedCategory
                inventory.refresh(db)
                medical.consume(player, db, connection)
            }
        }

        delay(450)
        boot = "ATTA-BOY\nPOSITIONING..."
        delay(700)
        boot = "DISPLAY INITIALIZING..."
        delay(650)
        boot = "ATTA-BOY SYSTEM\nINITIALIZING"
        delay(900)
        boot = "SYSTEM CHECK ........ OK\nMEMORY ............. OK\nLINK ............... OK"
        delay(750)
        boot = "SYSTEM READY\n\nWITH THANKS TO TEAM FOLON"
        delay(1400)
        boot = "READY"

        scope.launch {
            runCatching { if (!demoMode) connection.discoverAndConnect() }
                .onFailure { connectionState = "FAILED: " + (it.message ?: "DISCOVERY ERROR") }
        }
    }

    if (boot != "READY") {
        BootScreen(boot)
        return
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .padding(10.dp)
    ) {
        AttaBoyShell {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("FOLON // ATTA-BOY", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (showingSettings) "MAIN" else "SETTINGS", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 8.sp,
                        modifier = Modifier.clickable { showingSettings = !showingSettings }.padding(5.dp))
                }
                ConnectionStatusBanner(connectionState, demoMode)
                CrtFrame {
                    if (showingSettings) {
                        SettingsPanel(
                            demoMode = demoMode, autoStimpak = autoStimpak, threshold = stimpakThreshold, connectionState = connectionState,
                            onDemoMode = { enabled ->
                                demoMode = enabled; saveDemoMode(enabled)
                                if (enabled) { scope.launch { connection.disconnect() }; loadCachedDatabase(db); player = PlayerState.from(db, player); inventory.refresh(db) }
                                else scope.launch { runCatching { connection.discoverAndConnect() } }
                            },
                            onAutoStimpak = { enabled -> autoStimpak = enabled; medical.autoStimpakEnabled = enabled; saveAutoStimpak(enabled) },
                            onThreshold = { value -> stimpakThreshold = value; medical.threshold = value; saveAutoStimpakThreshold(value) },
                            onReconnect = { scope.launch { connection.disconnect(); if (!demoMode) runCatching { connection.discoverAndConnect() } } },
                            onClearCache = { clearCachedDatabase(); db.reset(); player = PlayerState(); inventory.refresh(db) }
                        )
                    } else when (selectedTab) {
                        MainTab.STAT -> StatusScreen(player, medical, db, connection, scope, autoStimpak, stimpakThreshold,
                            onAutoStimpakChanged = { enabled ->
                                autoStimpak = enabled
                                saveAutoStimpak(enabled)
                            },
                            onThresholdChanged = { value ->
                                stimpakThreshold = value
                                saveAutoStimpakThreshold(value)
                            }
                        )
                        MainTab.INV -> InventoryScreen(
                            store = inventory,
                            category = selectedCategory,
                            onCategory = {
                                selectedCategory = it
                                inventory.category = it
                                inventory.refresh(db)
                            }
                        )
                        MainTab.DATA -> DataScreen(db, connection, scope, demoMode) { enabled ->
                            demoMode = enabled
                            saveDemoMode(enabled)
                            if (enabled) {
                                scope.launch { connection.disconnect() }
                                loadCachedDatabase(db)
                                player = PlayerState.from(db, player)
                                inventory.refresh(db)
                            } else {
                                scope.launch { runCatching { connection.discoverAndConnect() } }
                            }
                        }
                        MainTab.MAP -> MapScreen(db)
                        MainTab.RADIO -> RadioPanel(db, connection, scope)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MainTab.entries.forEach { tab ->
                        Text(
                            text = tab.label,
                            modifier = Modifier.weight(1f).clickable { showingSettings = false; selectedTab = tab },
                            color = if (selectedTab == tab) Phosphor else Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }

                Text(
                    text = if (demoMode) "DEMO CACHE" else connectionState,
                    color = Phosphor.copy(alpha = .5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsPanel(
    demoMode: Boolean, autoStimpak: Boolean, threshold: Double, connectionState: String,
    onDemoMode: (Boolean) -> Unit, onAutoStimpak: (Boolean) -> Unit, onThreshold: (Double) -> Unit,
    onReconnect: () -> Unit, onClearCache: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SETTINGS", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        Text("CONNECTION", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text(connectionState, color = Phosphor.copy(alpha = .75f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text("RECONNECT", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            modifier = Modifier.border(1.dp, Phosphor.copy(alpha = .35f)).clickable { onReconnect() }.padding(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DEMO / CACHED DATA", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Spacer(Modifier.weight(1f)); Switch(checked = demoMode, onCheckedChange = onDemoMode)
        }
        Text("DISPLAY & FEEDBACK", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text("CRT SCANLINES / AUDIO / HAPTICS", color = Phosphor.copy(alpha = .7f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text("Preferences and hardware feedback boundaries are scaffolded; platform playback/haptic integration remains pending.", color = Phosphor.copy(alpha = .55f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        Text("MEDICAL", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AUTO-STIMPAK", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Spacer(Modifier.weight(1f)); Switch(checked = autoStimpak, onCheckedChange = onAutoStimpak)
        }
        Text("AUTO-STIMPAK THRESHOLD " + (threshold * 100).toInt() + "%", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Slider(value = threshold.toFloat(), onValueChange = { onThreshold(it.toDouble()) }, valueRange = 0.10f..0.90f, steps = 15)
        Text("DATA MANAGEMENT", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text("CLEAR CACHED DATABASE", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            modifier = Modifier.border(1.dp, Phosphor.copy(alpha = .35f)).clickable { onClearCache() }.padding(6.dp))
        Text("Cache clearing removes the local snapshot only; it does not alter game data.", color = Phosphor.copy(alpha = .55f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        Text("DATABASE BROWSER / EXPORT AVAILABLE FROM DATA", color = Phosphor.copy(alpha = .65f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
    }
}

@Composable
fun ConnectionStatusBanner(state: String, demoMode: Boolean) {
    val label = when {
        demoMode -> "DEMO CACHE"
        state == "CONNECTED" -> "LINK ONLINE"
        state.startsWith("FAILED") -> "LINK ERROR"
        state == "RECONNECTING" -> "RECONNECTING"
        state == "CONNECTING" -> "CONNECTING"
        state == "DISCOVERING" -> "DISCOVERING"
        else -> "LINK OFFLINE"
    }
    Text(
        label,
        color = if (state.startsWith("FAILED")) Color(0xFFFFB000) else Phosphor.copy(alpha = .75f),
        fontFamily = FontFamily.Monospace,
        fontSize = 8.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun BootScreen(text: String) {
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Phosphor,
            fontFamily = FontFamily.Monospace,
            fontSize = 18.sp
        )
    }
}

@Composable
fun AttaBoyShell(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .padding(6.dp)
            .background(Color.Black, RoundedCornerShape(24.dp))
            .border(2.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(24.dp))
            .padding(22.dp)
    ) {
        content()
    }
}

@Composable
fun CrtFrame(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .aspectRatio(1.45f)
            .background(ScreenBlack, RoundedCornerShape(18.dp))
            .border(1.dp, Phosphor.copy(alpha = .28f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        content()
        Column(Modifier.fillMaxSize()) {
            repeat(120) {
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .height(1.dp)
                        .background(Phosphor.copy(alpha = .025f))
                )
            }
        }
    }
}

@Composable
fun StatusScreen(
    player: PlayerState,
    medical: MedicalController,
    db: PipboyDatabase,
    connection: PipboyConnection,
    scope: kotlinx.coroutines.CoroutineScope,
    autoStimpak: Boolean,
    threshold: Double,
    onAutoStimpakChanged: (Boolean) -> Unit,
    onThresholdChanged: (Double) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("STAT", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        Gauge("HP", player.hp, player.maxHp)
        Gauge("AP", player.ap, player.maxAp)
        Text("RAD  " + format1(player.radiation) + "   " + if (player.radiation >= 80) "CRITICAL" else if (player.radiation >= 40) "ELEVATED" else "NOMINAL", color = Phosphor, fontFamily = FontFamily.Monospace)
        Text("ACTIVE EFFECTS", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        if (player.activeEffects.isEmpty()) Text("NO ACTIVE EFFECTS RECEIVED", color = Phosphor.copy(alpha = .55f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        Text("WT   " + format1(player.carryWeight) + " / " + format1(player.maxWeight), color = Phosphor, fontFamily = FontFamily.Monospace)
        Text("XP   " + format1(player.xpProgress * 100.0) + "%", color = Phosphor, fontFamily = FontFamily.Monospace)
        Text("LEVEL " + player.level + "   PERKS " + player.perkPoints, color = Phosphor, fontFamily = FontFamily.Monospace)
        Text(if (player.perkPoints > 0) "PERK SELECTION AVAILABLE" else "NO LEVEL-UP AVAILABLE", color = Phosphor.copy(alpha = .7f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        Text("CHARACTER REACTION  " + if (player.maxHp > 0 && player.hp / player.maxHp <= .2) "CRITICAL" else if (player.maxHp > 0 && player.hp / player.maxHp <= .5) "WARNING" else "NORMAL", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        Text("SPECIAL   " + player.special.joinToString(" "), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text("MEDICAL", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("STIMPAK", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                modifier = Modifier.clickable { scope.launch { medical.useStimpak(db, connection) } }.border(1.dp, Phosphor.copy(alpha=.35f)).padding(6.dp))
            Text("RADAWAY", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                modifier = Modifier.clickable { scope.launch { medical.useRadAway(db, connection) } }.border(1.dp, Phosphor.copy(alpha=.35f)).padding(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AUTO-STIMPAK", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Switch(checked = autoStimpak, onCheckedChange = {
                medical.autoStimpakEnabled = it
                onAutoStimpakChanged(it)
            })
        }
        Text("THRESHOLD " + (threshold * 100).toInt() + "%", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        Slider(value = threshold.toFloat(), onValueChange = {
            val value = it.toDouble()
            medical.threshold = value
            onThresholdChanged(value)
        }, valueRange = 0.10f..0.90f, steps = 15)
        player.limbConditions.forEach { entry ->
            Text(entry.key + "  " + (entry.value * 100.0).toInt() + "%", color = Phosphor.copy(alpha=.75f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
        if (player.activeEffects.isNotEmpty()) {
            Text("EFFECTS", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            player.activeEffects.forEach { effect ->
                Text("• " + effect, color = Phosphor.copy(alpha=.8f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun Gauge(label: String, value: Double, max: Double) {
    Column {
        Text(label, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text(
            format0(value) + " / " + format0(max),
            color = Phosphor,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}

@Composable
fun InventoryScreen(store: InventoryStore, category: InventoryCategory, onCategory: (InventoryCategory) -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var sort by rememberSaveable { mutableStateOf("NAME") }

    val baseItems = store.items(category)
    val visibleItems = remember(baseItems, search, filter, sort) {
        var result = baseItems
        if (search.isNotBlank()) result = result.filter { it.name.contains(search, ignoreCase = true) }
        result = when (filter) {
            "EQUIPPED" -> result.filter { it.equipped }
            "FAVORITE" -> result.filter { it.favoriteSlot != null }
            "LEGENDARY" -> result.filter { it.legendary }
            else -> result
        }
        when (sort) {
            "COUNT" -> result.sortedWith(compareByDescending<InventoryItem> { it.count }.thenBy { it.name.lowercase() })
            "VALUE" -> result.sortedWith(compareByDescending<InventoryItem> { it.value }.thenBy { it.name.lowercase() })
            "WEIGHT" -> result.sortedWith(compareBy<InventoryItem> { it.weight }.thenBy { it.name.lowercase() })
            else -> result.sortedBy { it.name.lowercase() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("INV", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Text("${visibleItems.size} / ${baseItems.size} ITEMS", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
            InventoryCategory.entries.forEach { itemCategory ->
                Text(itemCategory.label, modifier = Modifier.padding(end = 8.dp).clickable {
                    selectedId = null
                    onCategory(itemCategory)
                }, color = if (category == itemCategory) Color.Black else Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
        }
        TextField(value = search, onValueChange = { search = it }, singleLine = true,
            placeholder = { Text("SEARCH ITEMS", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
            modifier = Modifier.fillMaxWidth().height(48.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL", "EQUIPPED", "FAVORITE", "LEGENDARY").forEach { option ->
                Text("FILTER: $option", color = if (filter == option) Color.Black else Phosphor,
                    fontFamily = FontFamily.Monospace, fontSize = 8.sp,
                    modifier = Modifier.background(if (filter == option) Phosphor else Color.Transparent)
                        .border(1.dp, Phosphor.copy(alpha = .3f)).clickable { filter = option }.padding(5.dp))
            }
            listOf("NAME", "COUNT", "VALUE", "WEIGHT").forEach { option ->
                Text("SORT: $option", color = if (sort == option) Color.Black else Phosphor,
                    fontFamily = FontFamily.Monospace, fontSize = 8.sp,
                    modifier = Modifier.background(if (sort == option) Phosphor else Color.Transparent)
                        .border(1.dp, Phosphor.copy(alpha = .3f)).clickable { sort = option }.padding(5.dp))
            }
        }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                visibleItems.forEach { item ->
                    Text(item.name + (if (item.equipped) " E" else "") + (if (item.favoriteSlot != null) " ★" else "") +
                        (if (item.legendary) " L" else "") + (if (item.count > 1) " x" + item.count else ""),
                        color = if (selectedId == item.id) Color.Black else Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().background(if (selectedId == item.id) Phosphor else Color.Transparent)
                            .clickable { selectedId = item.id }.padding(vertical = 5.dp, horizontal = 4.dp))
                }
                if (visibleItems.isEmpty()) Text(if (search.isBlank()) "NO MATCHING ITEMS" else "NO SEARCH RESULTS",
                    color = Phosphor.copy(alpha = .6f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            val item = store.items.firstOrNull { it.id == selectedId }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp).border(1.dp, Phosphor.copy(alpha = .35f)).padding(7.dp)) {
                if (item == null) Text("SELECT ITEM", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                else {
                    Text(item.name, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                    Text("ACTIONS: USE/EQUIP • DROP • FAVORITE", color = Phosphor.copy(alpha = .5f), fontFamily = FontFamily.Monospace, fontSize = 7.sp)
                    Text("RPC ACTIONS LOCKED UNTIL VERIFIED", color = Phosphor.copy(alpha = .45f), fontFamily = FontFamily.Monospace, fontSize = 7.sp)
                    if (item.legendary) Text("★ LEGENDARY", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    if (item.equipped) Text("EQUIPPED", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Text("COUNT  " + item.count, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Text("WEIGHT " + format1(item.weight), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Text("VALUE  " + item.value, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    item.damage?.let { Text("DMG    " + format0(it), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                    item.armor?.let { Text("ARMOR  " + format0(it), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                    item.radiationResistance?.let { Text("RAD RES " + format0(it), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                    item.energyResistance?.let { Text("ENG RES " + format0(it), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                    item.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Phosphor.copy(alpha = .8f), fontFamily = FontFamily.Monospace, fontSize = 9.sp, maxLines = 5) }
                }
            }
        }
    }
}

@Composable
fun DataScreen(db: PipboyDatabase, connection: PipboyConnection, scope: kotlinx.coroutines.CoroutineScope, demoMode: Boolean, onDemoMode: (Boolean) -> Unit) {
    var search by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val export = remember(db.nodes.size, db.localMap) { db.exportText() }
    Column(Modifier.fillMaxSize().padding(2.dp)) {
        QuestPanel(db, connection, scope)
        DataSummarySection(db, "NOTES", "notes"); DataSummarySection(db, "STATISTICS", "stats")
        DataSummarySection(db, "WORKSHOP", "workshop"); DataSummarySection(db, "MISC", "misc")
        Text(if (demoMode) "STALE-CACHE / DEMO DATA: LIVE ACTIONS DISABLED" else "LIVE DATA: ACTION FEEDBACK FOLLOWS VERIFIED RESPONSES", color=Phosphor.copy(alpha=.6f), fontFamily=FontFamily.Monospace, fontSize=8.sp)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(if(demoMode)"DATA BROWSER / DEMO" else "DATA BROWSER",color=Phosphor,fontFamily=FontFamily.Monospace,fontSize=17.sp);Spacer(Modifier.weight(1f))
            Text(if(copied)"COPIED" else "COPY ALL",color=Phosphor,fontFamily=FontFamily.Monospace,fontSize=8.sp,modifier=Modifier.clickable{Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(export),null);copied=true}.padding(5.dp))
            Text("SAVE TXT",color=Phosphor,fontFamily=FontFamily.Monospace,fontSize=8.sp,modifier=Modifier.clickable{
                val chooser=JFileChooser();chooser.dialogTitle="Save Fallout London Data";chooser.selectedFile=java.io.File("Fallout-London-Data.txt");chooser.fileFilter=FileNameExtensionFilter("Text files","txt")
                if(chooser.showSaveDialog(null)==JFileChooser.APPROVE_OPTION)chooser.selectedFile.writeText(export)
            }.padding(5.dp))
        }
        TextField(search,{search=it},singleLine=true,placeholder={Text("SEARCH PATH / VALUE")},modifier=Modifier.fillMaxWidth().height(48.dp))
        if(search.isNotBlank()){
            val q=search.lowercase();val matches=db.flattenedValues().filter{(k,v)->k.lowercase().contains(q)||v.displayText().lowercase().contains(q)}.toSortedMap()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Text(matches.size.toString()+" MATCHES",color=Phosphor.copy(alpha=.6f),fontFamily=FontFamily.Monospace,fontSize=8.sp);matches.forEach{(k,v)->Text(k+" = "+v.displayText(),color=Phosphor,fontFamily=FontFamily.Monospace,fontSize=8.sp,modifier=Modifier.padding(vertical=2.dp))}}
        }else{
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
                if(path.isNotEmpty())Text("‹ ROOT",color=Phosphor,fontFamily=FontFamily.Monospace,fontSize=9.sp,modifier=Modifier.clickable{path=""}.padding(vertical=5.dp))
                val children=db.objectChildren(path).toSortedMap();val arrays=db.nodeId(path)?.let{db.arrayChildren(it)}?:emptyList()
                if(children.isEmpty()&&arrays.isEmpty())Text(path+" = "+(db.value(path)?.displayText()?:""),color=Phosphor,fontFamily=FontFamily.Monospace,fontSize=9.sp)
                else{children.forEach{(k,_)->val child=if(path.isEmpty())k else path+"."+k;Text("› "+k.uppercase(), color=Phosphor, fontFamily=FontFamily.Monospace, fontSize=9.sp, modifier=Modifier.fillMaxWidth().clickable{path=child}.padding(vertical=3.dp))};arrays.forEachIndexed{i,_->Text("› ["+i+"]", color=Phosphor, fontFamily=FontFamily.Monospace, fontSize=9.sp, modifier=Modifier.fillMaxWidth().clickable{path=path+"["+i+"]"}.padding(vertical=3.dp))}}
            }
        }
    }
}

@Composable
fun MapScreen(db: PipboyDatabase) {
    var mode by rememberSaveable { mutableStateOf("ORIGINAL") }
    var zoom by rememberSaveable { mutableStateOf(1f) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MAP", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Text(if (db.localMap != null) "SNAPSHOT READY" else "NO SNAPSHOT", color = Phosphor.copy(alpha = .6f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("ORIGINAL", "ENHANCED", "TOPOGRAPHICAL").forEach { option ->
                Text(option, color = if (mode == option) Color.Black else Phosphor, fontFamily = FontFamily.Monospace, fontSize = 8.sp,
                    modifier = Modifier.background(if (mode == option) Phosphor else Color.Transparent)
                        .border(1.dp, Phosphor.copy(alpha = .3f)).clickable { mode = option }.padding(5.dp))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).border(1.dp, Phosphor.copy(alpha = .3f)).padding(10.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("MAP CANVAS", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                Text("MODE " + mode + "   ZOOM " + String.format("%.1fx", zoom), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
                if (db.localMap != null) Text("LOCAL SNAPSHOT PAYLOAD AVAILABLE", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                else Text("NO LOCAL MAP SNAPSHOT", color = Phosphor.copy(alpha = .6f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                Text("PLAYER / QUEST / DISCOVERED / CUSTOM MARKER LAYERS", color = Phosphor.copy(alpha = .6f), fontFamily = FontFamily.Monospace, fontSize = 7.sp)
                Text("ARTWORK + COORDINATE TRANSFORM AWAIT LIVE CAPTURE VERIFICATION", color = Phosphor.copy(alpha = .45f), fontFamily = FontFamily.Monospace, fontSize = 7.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("X " + db.value("map.world.player.x").asNumberText() + "  Y " + db.value("map.world.player.y").asNumberText(), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
            Spacer(Modifier.weight(1f))
            Text("ZOOM -", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 8.sp, modifier = Modifier.clickable { zoom = (zoom - .25f).coerceAtLeast(.75f) }.padding(5.dp))
            Text("ZOOM +", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 8.sp, modifier = Modifier.clickable { zoom = (zoom + .25f).coerceAtMost(4f) }.padding(5.dp))\n            Text("REQUEST MAP", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 8.sp, modifier = Modifier.clickable { scope.launch { connection.sendRPC(13, emptyList()) } }.padding(5.dp))
        }
    }
}


@Composable
fun RadioPanel(db: PipboyDatabase, connection: PipboyConnection, scope: kotlinx.coroutines.CoroutineScope) {
    val stations = db.objectChildren("radio").toSortedMap()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("RADIO", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 22.sp)
        Text("LIVE STATION DATA", color = Phosphor.copy(alpha = .7f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        if (stations.isEmpty()) {
            Text("NO RADIO DATA RECEIVED", color = Phosphor.copy(alpha = .6f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        } else {
            stations.forEach { (key, nodeId) ->
                fun str(name: String): String? = (db.objectValue(nodeId, name) as? PipboyValue.StringValue)?.value
                fun bool(name: String): Boolean? = (db.objectValue(nodeId, name) as? PipboyValue.Bool)?.value
                val name = str("name") ?: key
                val frequency = str("frequency")
                val text = str("text")
                val active = bool("active") ?: false
                val inRange = bool("inrange") ?: true
                if (inRange || active) {
                    Column(Modifier.fillMaxWidth().border(1.dp, Phosphor.copy(alpha = if (active) .55f else .2f)).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            if (active) Text("ON AIR", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
                        }
                        frequency?.let { Text(it, color = Phosphor.copy(alpha = .7f), fontFamily = FontFamily.Monospace, fontSize = 9.sp) }
                        text?.let { Text(it, color = Phosphor.copy(alpha = .8f), fontFamily = FontFamily.Monospace, fontSize = 9.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun DataSummarySection(db: PipboyDatabase, title: String, path: String) {
    val children = db.objectChildren(path)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, Phosphor.copy(alpha = .25f)).padding(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(children.size.toString() + " NODES", color = Phosphor.copy(alpha = .6f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        }
        if (children.isEmpty()) Text("NO DATA RECEIVED", color = Phosphor.copy(alpha = .5f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        else children.keys.sorted().take(6).forEach { Text("• " + it.uppercase(), color = Phosphor.copy(alpha = .8f), fontFamily = FontFamily.Monospace, fontSize = 8.sp) }
    }
}

@Composable
fun QuestPanel(db: PipboyDatabase, connection: PipboyConnection, scope: kotlinx.coroutines.CoroutineScope) {
    val questNode = db.nodeId("quests")
    val questIds = questNode?.let { db.arrayChildren(it) }.orEmpty()
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("QUESTS", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text("${questIds.size} ACTIVE/AVAILABLE", color = Phosphor.copy(alpha = .6f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        }
        questIds.forEachIndexed { index, _ ->
            val path = "quests[$index]"
            val name = db.value("$path.text").asString() ?: return@forEachIndexed
            val form = db.value("$path.formid").asUInt() ?: return@forEachIndexed
            val instance = db.value("$path.instance").asUInt() ?: return@forEachIndexed
            val type = db.value("$path.type").asUInt() ?: return@forEachIndexed
            val active = db.value("$path.active").asBool() ?: false
            val enabled = db.value("$path.enabled").asBool() ?: true
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, Phosphor.copy(alpha = if (active) .5f else .2f)).padding(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (active) "ACTIVE" else "SET", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 8.sp,
                        modifier = Modifier.clickable { scope.launch { connection.sendRPC(5, listOf(form, instance, type)) } }.padding(end = 7.dp))
                    Text(name, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    if (!enabled) Text("DISABLED", color = Phosphor.copy(alpha = .5f), fontFamily = FontFamily.Monospace, fontSize = 7.sp)
                }
                val objNode = db.nodeId("$path.objectives")
                val objIds = objNode?.let { db.arrayChildren(it) }.orEmpty()
                objIds.forEachIndexed { oi, _ ->
                    val op = "$path.objectives[$oi]"
                    val text = db.value("$op.text").asString() ?: return@forEachIndexed
                    val done = db.value("$op.completed").asBool() ?: false
                    val failed = db.value("$op.failed").asBool() ?: false
                    Text((if (done) "✓ " else if (failed) "✗ " else "· ") + text, color = Phosphor.copy(alpha = if (done) .5f else .82f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
                }
            }
        }
    }
}

private fun PipboyValue?.asString() = (this as? PipboyValue.StringValue)?.value
private fun PipboyValue?.asBool() = (this as? PipboyValue.Bool)?.value
private fun PipboyValue?.asUInt(): Long? = when (this) {
    is PipboyValue.UInt32 -> value
    is PipboyValue.Int32 -> value.toLong().takeIf { it >= 0 }
    is PipboyValue.UInt8 -> value.toLong()
    is PipboyValue.Int8 -> value.toLong().takeIf { it >= 0 }
    else -> null
}

private fun PipboyValue?.asNumberText(): String = when (this) {
    is PipboyValue.Float32 -> format2(value)
    is PipboyValue.Int32 -> value.toString()
    is PipboyValue.UInt32 -> value.toString()
    else -> "--"
}

private fun format0(value: Double) = String.format("%.0f", value)
private fun format1(value: Double) = String.format("%.1f", value)
private fun format2(value: Float) = String.format("%.2f", value)


private fun loadDemoMode()=prefs.getBoolean("demoMode",false)
private fun saveDemoMode(v:Boolean){prefs.putBoolean("demoMode",v)}
private fun loadAutoStimpak()=prefs.getBoolean("autoStimpak",true)
private fun saveAutoStimpak(v:Boolean){prefs.putBoolean("autoStimpak",v)}
private fun loadAutoStimpakThreshold()=prefs.getDouble("autoStimpakThreshold",0.35)
private fun saveAutoStimpakThreshold(v:Double){prefs.putDouble("autoStimpakThreshold",v)}
private fun saveCachedDatabase(db:PipboyDatabase){prefs.put("cache",db.snapshotJson())}
private fun loadCachedDatabase(db:PipboyDatabase){prefs.get("cache",null)?.let{db.restoreSnapshot(it)}}
private fun clearCachedDatabase(){prefs.remove("cache")}
