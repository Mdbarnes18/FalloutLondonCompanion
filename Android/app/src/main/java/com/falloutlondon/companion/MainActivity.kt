package com.falloutlondon.companion

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Phosphor = Color(0xFF00FF44)
private val ScreenBlack = Color(0xFF020A04)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContext = applicationContext
        setContent { FalloutLondonApp() }
    }
}

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
                CrtFrame {
                    when (selectedTab) {
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
                        MainTab.RADIO -> RadioScreen(db, connection, scope)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MainTab.entries.forEach { tab ->
                        Text(
                            text = tab.label,
                            modifier = Modifier.weight(1f).clickable { selectedTab = tab },
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
        Text("RAD  " + format1(player.radiation), color = Phosphor, fontFamily = FontFamily.Monospace)
        Text("WT   " + format1(player.carryWeight) + " / " + format1(player.maxWeight), color = Phosphor, fontFamily = FontFamily.Monospace)
        Text("XP   " + format1(player.xpProgress * 100.0) + "%", color = Phosphor, fontFamily = FontFamily.Monospace)
        Text("LEVEL " + player.level + "   PERKS " + player.perkPoints, color = Phosphor, fontFamily = FontFamily.Monospace)
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
fun InventoryScreen(
    store: InventoryStore,
    category: InventoryCategory,
    onCategory: (InventoryCategory) -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text("INV", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
            InventoryCategory.entries.forEach { itemCategory ->
                Text(
                    itemCategory.label,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable {
                            selectedId = null
                            onCategory(itemCategory)
                        },
                    color = if (category == itemCategory) Color.Black else Phosphor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }
        }

        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                store.items.forEach { item ->
                    Text(
                        item.name + "  " +
                            (if (item.equipped) "E" else "") +
                            (if (item.favoriteSlot != null) " ★" else "") +
                            (if (item.count > 1) " x" + item.count else ""),
                        color = if (selectedId == item.id) Color.Black else Phosphor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selectedId == item.id) Phosphor else Color.Transparent)
                            .clickable { selectedId = item.id }
                            .padding(vertical = 5.dp, horizontal = 4.dp)
                    )
                }
                if (store.items.isEmpty()) {
                    Text("NO ITEMS", color = Phosphor.copy(alpha = .6f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }

            val item = store.items.firstOrNull { it.id == selectedId }
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .padding(start = 8.dp)
                    .border(1.dp, Phosphor.copy(alpha = .35f))
                    .padding(7.dp)
            ) {
                if (item == null) {
                    Text("SELECT ITEM", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                } else {
                    Text(item.name, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                    if (item.legendary) Text("★ LEGENDARY", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    if (item.equipped) Text("EQUIPPED", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Text("COUNT  " + item.count, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Text("WEIGHT " + format1(item.weight), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Text("VALUE  " + item.value, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    item.damage?.let { Text("DMG    " + format0(it), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                    item.armor?.let { Text("ARMOR  " + format0(it), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                    item.radiationResistance?.let { Text("RAD RES " + format0(it), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                    item.energyResistance?.let { Text("ENG RES " + format0(it), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                    item.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Phosphor.copy(alpha = .8f), fontFamily = FontFamily.Monospace, fontSize = 9.sp, maxLines = 5)
                    }
                }
            }
        }
    }
}

@Composable
fun DataScreen(db: PipboyDatabase, connection: PipboyConnection, scope: kotlinx.coroutines.CoroutineScope, demoMode: Boolean, onDemoMode: (Boolean) -> Unit) {
    val context = LocalContext.current
    var path by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val export = remember(db.nodes.size, db.localMap) { db.exportText() }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(export.toByteArray(Charsets.UTF_8))
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(2.dp)) {
        QuestPanel(db, connection, scope)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (demoMode) "DATA BROWSER / DEMO" else "DATA BROWSER", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text(if (demoMode) "DEMO" else "LIVE", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 8.sp,
                modifier = Modifier.clickable { onDemoMode(!demoMode) }.padding(5.dp))
            Text(if (demoMode) "DEMO CACHE" else "LIVE", color = Phosphor.copy(alpha = .7f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
            Text(
                if (copied) "COPIED" else "COPY ALL",
                color = Phosphor,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                modifier = Modifier.clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Fallout London data", export))
                    copied = true
                }.padding(5.dp)
            )
            Text(
                "SAVE TXT",
                color = Phosphor,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                modifier = Modifier.clickable { saveLauncher.launch("Fallout-London-Data.txt") }.padding(5.dp)
            )
        }

        TextField(
            value = search,
            onValueChange = { search = it },
            singleLine = true,
            placeholder = { Text("SEARCH PATH / VALUE", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )

        if (search.isNotBlank()) {
            val q = search.lowercase()
            val matches = db.flattenedValues().filter { (key, value) ->
                key.lowercase().contains(q) || value.displayText().lowercase().contains(q)
            }.toSortedMap()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(matches.size.toString() + " MATCHES", color = Phosphor.copy(alpha = .6f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
                matches.forEach { (key, value) ->
                    Text(
                        key + " = " + value.displayText(),
                        color = Phosphor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (path.isNotEmpty()) {
                    Text(
                        "‹ ROOT",
                        color = Phosphor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        modifier = Modifier.clickable { path = "" }.padding(vertical = 5.dp)
                    )
                }
                val children = db.objectChildren(path).toSortedMap()
                val arrayChildren = db.nodeId(path)?.let { db.arrayChildren(it) } ?: emptyList()
                if (children.isEmpty() && arrayChildren.isEmpty()) {
                    Text(
                        path + " = " + (db.value(path) ?: ""),
                        color = Phosphor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                } else {
                    children.forEach { (key, _) ->
                        val childPath = if (path.isEmpty()) key else path + "." + key
                        DataBrowserRow(db, key.uppercase(), childPath, 0) { path = it }
                    }
                    arrayChildren.forEachIndexed { index, _ ->
                        val childPath = path + "[" + index + "]"
                        DataBrowserRow(db, "[" + index + "]", childPath, 0) { path = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataBrowserRow(
    db: PipboyDatabase,
    title: String,
    path: String,
    depth: Int,
    open: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { open(path) }.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("  ".repeat(depth) + "› " + title, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Spacer(Modifier.weight(1f))
        val value = db.value(path)
        if (value != null) {
            Text(value.toString(), color = Phosphor.copy(alpha = .65f), fontFamily = FontFamily.Monospace, fontSize = 8.sp, maxLines = 1)
        } else {
            val count = db.objectChildren(path).size + (db.nodeId(path)?.let { db.arrayChildren(it).size } ?: 0)
            Text(count.toString(), color = Phosphor.copy(alpha = .5f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        }
    }
}

@Composable
fun MapScreen(db: PipboyDatabase) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("MAP", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        Text("WORLDSPACE: " + (db.value("map.currworldspace").asString() ?: "UNKNOWN"), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text("X  " + db.value("map.world.player.x").asNumberText(), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text("Y  " + db.value("map.world.player.y").asNumberText(), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text("ROT " + db.value("map.world.player.rotation").asNumberText(), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text(
            db.localMap?.let { "LOCAL SNAPSHOT  " + it.width + " × " + it.height } ?: "NO LOCAL MAP SNAPSHOT",
            color = Phosphor,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp
        )
    }
}

@Composable
fun RadioScreen(db: PipboyDatabase, connection: PipboyConnection, scope: kotlinx.coroutines.CoroutineScope) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("RADIO", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        val stations = db.objectChildren("radio")
        if (stations.isEmpty()) {
            Text("NO RADIO DATA RECEIVED", color = Phosphor.copy(alpha = .6f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
        stations.keys.sorted().forEach { key ->
            val id = stations[key] ?: return@forEach
            val active = (db.objectValue(id, "active") as? PipboyValue.Bool)?.value ?: false
            val inRange = (db.objectValue(id, "inrange") as? PipboyValue.Bool)?.value ?: true
            if (active || inRange) {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (active) "■" else "▶", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        modifier = Modifier.clickable { scope.launch { connection.sendRPC(12, listOf(id)) } }.padding(end = 7.dp))
                    Text(key, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    if (active) Text("ON AIR", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
                }
            }
        }
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

private lateinit var appContext: android.content.Context
private fun loadDemoMode(): Boolean = appContext.getSharedPreferences("attaboy", android.content.Context.MODE_PRIVATE).getBoolean("demoMode", false)
private fun loadAutoStimpak(): Boolean = appContext.getSharedPreferences("attaboy", android.content.Context.MODE_PRIVATE).getBoolean("autoStimpak", true)
private fun saveAutoStimpak(value: Boolean) { appContext.getSharedPreferences("attaboy", android.content.Context.MODE_PRIVATE).edit().putBoolean("autoStimpak", value).apply() }
private fun loadAutoStimpakThreshold(): Double = appContext.getSharedPreferences("attaboy", android.content.Context.MODE_PRIVATE).getFloat("autoStimpakThreshold", 0.35f).toDouble()
private fun saveAutoStimpakThreshold(value: Double) { appContext.getSharedPreferences("attaboy", android.content.Context.MODE_PRIVATE).edit().putFloat("autoStimpakThreshold", value.toFloat()).apply() }
private fun saveDemoMode(value: Boolean) {
    appContext.getSharedPreferences("attaboy", android.content.Context.MODE_PRIVATE).edit().putBoolean("demoMode", value).apply()
}
private fun loadCachedDatabase(db: PipboyDatabase) {
    if (::appContext.isInitialized) appContext.getSharedPreferences("attaboy", android.content.Context.MODE_PRIVATE)
        .getString("cache", null)?.let { db.restoreSnapshot(it) }
}
private fun saveCachedDatabase(db: PipboyDatabase) {
    if (::appContext.isInitialized) appContext.getSharedPreferences("attaboy", android.content.Context.MODE_PRIVATE)
        .edit().putString("cache", db.snapshotJson()).apply()
}
