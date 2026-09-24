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

    LaunchedEffect(Unit) {
        connection.onState = { state -> scope.launch { connectionState = state } }
        connection.onUpdate = { update ->
            scope.launch {
                db.apply(update)
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
            runCatching { connection.discoverAndConnect() }
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
                        MainTab.STAT -> StatusScreen(player)
                        MainTab.INV -> InventoryScreen(
                            store = inventory,
                            category = selectedCategory,
                            onCategory = {
                                selectedCategory = it
                                inventory.category = it
                                inventory.refresh(db)
                            }
                        )
                        MainTab.DATA -> DataScreen(db)
                        MainTab.MAP -> MapScreen(db)
                        MainTab.RADIO -> RadioScreen(db)
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
                    text = connectionState,
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
fun StatusScreen(player: PlayerState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("STAT", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        Gauge("HP", player.hp, player.maxHp)
        Gauge("AP", player.ap, player.maxAp)
        Text("RAD  " + format1(player.radiation), color = Phosphor, fontFamily = FontFamily.Monospace)
        Text("WT   " + format1(player.carryWeight) + " / " + format1(player.maxWeight), color = Phosphor, fontFamily = FontFamily.Monospace)
        Text("XP   " + format1(player.xpProgress * 100.0) + "%", color = Phosphor, fontFamily = FontFamily.Monospace)
        Text("LEVEL " + player.level + "   PERKS " + player.perkPoints, color = Phosphor, fontFamily = FontFamily.Monospace)
        Text("SPECIAL   " + player.special.joinToString(" "), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        player.limbConditions.forEach { entry ->
            Text(
                entry.key + "  " + (entry.value * 100.0).toInt() + "%",
                color = Phosphor.copy(alpha = .75f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
        if (player.activeEffects.isNotEmpty()) {
            Text("EFFECTS", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            player.activeEffects.forEach { effect ->
                Text("• " + effect, color = Phosphor.copy(alpha = .8f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
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
fun DataScreen(db: PipboyDatabase) {
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("DATA BROWSER", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
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
fun RadioScreen(db: PipboyDatabase) {
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
                Text(
                    key,
                    color = Phosphor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

private fun PipboyValue?.asString() = (this as? PipboyValue.StringValue)?.value

private fun PipboyValue?.asNumberText(): String = when (this) {
    is PipboyValue.Float32 -> format2(value)
    is PipboyValue.Int32 -> value.toString()
    is PipboyValue.UInt32 -> value.toString()
    else -> "--"
}

private fun format0(value: Double) = String.format("%.0f", value)
private fun format1(value: Double) = String.format("%.1f", value)
private fun format2(value: Float) = String.format("%.2f", value)
