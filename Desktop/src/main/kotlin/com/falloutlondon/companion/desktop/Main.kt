package com.falloutlondon.companion.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay

private val Phosphor = Color(0xFF00FF44)
private val ScreenBlack = Color(0xFF020A04)
private enum class Tab(val label: String) { STAT("STAT"), INV("INV"), DATA("DATA"), MAP("MAP"), RADIO("RADIO") }

fun main() = application {
    val state = rememberWindowState(width = 1280.dp, height = 820.dp)
    Window(onCloseRequest = ::exitApplication, title = "Fallout London Companion", state = state) { DesktopApp(state) }
}

@Composable
private fun DesktopApp(windowState: WindowState) {
    var boot by remember { mutableStateOf(true) }
    var bootText by remember { mutableStateOf("OFF") }
    var tab by remember { mutableStateOf(Tab.STAT) }
    var settings by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var demo by remember { mutableStateOf(false) }
    var autoStimpak by remember { mutableStateOf(true) }
    var threshold by remember { mutableStateOf(0.35f) }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val sequence = listOf(
            "ATTA-BOY\nPOSITIONING..." to 650L,
            "DISPLAY INITIALIZING..." to 650L,
            "ATTA-BOY SYSTEM\nINITIALIZING" to 800L,
            "SYSTEM CHECK ........ OK\nMEMORY ............. OK\nLINK ............... OK" to 750L,
            "SYSTEM READY\n\nWITH THANKS TO TEAM FOLON" to 1200L
        )
        for ((text, wait) in sequence) { bootText = text; delay(wait) }
        boot = false
    }

    if (boot) { BootScreen(bootText); return }

    Box(Modifier.fillMaxSize().background(Color.Black).padding(18.dp).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { settings = false; true } else false
    }) {
        Column(Modifier.fillMaxSize().border(2.dp, Phosphor.copy(alpha=.45f)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FOLON // ATTA-BOY", color=Phosphor, fontFamily=FontFamily.Monospace, fontSize=18.sp)
                Spacer(Modifier.weight(1f))
                Text(if (settings) "MAIN" else "SETTINGS", color=Phosphor, fontFamily=FontFamily.Monospace, fontSize=12.sp,
                    modifier=Modifier.clickable { settings=!settings }.padding(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical=6.dp)) {
                Text(if (demo) "DEMO CACHE" else if (connected) "CONNECTED" else "DISCONNECTED",
                    color=Phosphor, fontFamily=FontFamily.Monospace, fontSize=11.sp)
                Spacer(Modifier.weight(1f))
                Text("DESKTOP " + windowState.size.width + "x" + windowState.size.height, color=Phosphor.copy(alpha=.5f), fontFamily=FontFamily.Monospace, fontSize=10.sp)
            }
            Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, Phosphor.copy(alpha=.35f)).padding(16.dp)) {
                if (settings) SettingsPanel(connected, demo, autoStimpak, threshold,
                    { demo=it; if(it) connected=false }, { autoStimpak=it }, { threshold=it },
                    { connected=true }, { demo=false })
                else when(tab) {
                    Tab.STAT -> StatPanel(autoStimpak, threshold)
                    Tab.INV -> InventoryPanel(search, { search=it })
                    Tab.DATA -> DataPanel()
                    Tab.MAP -> MapPanel()
                    Tab.RADIO -> RadioPanel()
                }
            }
            Row(Modifier.fillMaxWidth().padding(top=12.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Tab.entries.forEach { item ->
                    Text(item.label, color=if(tab==item) Phosphor else Phosphor.copy(alpha=.45f), fontFamily=FontFamily.Monospace,
                        fontSize=14.sp, modifier=Modifier.weight(1f).border(1.dp, Phosphor.copy(alpha=.25f))
                            .clickable { settings=false; tab=item }.padding(10.dp))
                }
            }
        }
    }
}

@Composable private fun BootScreen(text:String) {
    Box(Modifier.fillMaxSize().background(ScreenBlack), contentAlignment=Alignment.Center) {
        Text(text, color=Phosphor, fontFamily=FontFamily.Monospace, fontSize=20.sp)
    }
}
@Composable private fun StatPanel(auto:Boolean, threshold:Float) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("STAT", color=Phosphor, fontFamily=FontFamily.Monospace, fontSize=28.sp)
        listOf("HP        -- / --","AP        -- / --","LEVEL     --      XP --","CARRY     -- / --","RADS      --",
            "SPECIAL   S --  P --  E --  C --  I --  A --  L --","LIMBS     HEAD --  TORSO --  L/R ARM --  L/R LEG --",
            "EFFECTS   LIVE DATA BOUNDARY").forEach { Text(it,color=Phosphor,fontFamily=FontFamily.Monospace) }
        Text("AUTO-STIMPAK " + if(auto) "ON" else "OFF" + "  •  THRESHOLD " + (threshold*100).toInt() + "%",color=Phosphor,fontFamily=FontFamily.Monospace)
    }
}
@Composable private fun InventoryPanel(search:String,onSearch:(String)->Unit) {
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("INV",color=Phosphor,fontFamily=FontFamily.Monospace,fontSize=28.sp)
        TextField(value=search,onValueChange=onSearch,singleLine=true,label={Text("SEARCH")})
        Text("WEAPONS    APPAREL    AID    MISC    JUNK    AMMO",color=Phosphor,fontFamily=FontFamily.Monospace)
        Text("Inventory item list / detail / favorite / equipped action boundary",color=Phosphor.copy(alpha=.7f),fontFamily=FontFamily.Monospace)
    }
}
@Composable private fun DataPanel() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("DATA",color=Phosphor,fontFamily=FontFamily.Monospace,fontSize=28.sp)
        listOf("QUESTS","LOGS","WORKSHOP","PLAYER / NOTES / STATISTICS / MISC").forEach {Text(it,color=Phosphor,fontFamily=FontFamily.Monospace)}
        Text("Database browser, cache and command-response boundary",color=Phosphor.copy(alpha=.7f),fontFamily=FontFamily.Monospace)
    }
}
@Composable private fun MapPanel() {
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("MAP",color=Phosphor,fontFamily=FontFamily.Monospace,fontSize=28.sp)
        Text("ORIGINAL   ENHANCED   TOPOGRAPHICAL",color=Phosphor,fontFamily=FontFamily.Monospace)
        Box(Modifier.weight(1f).fillMaxWidth().border(1.dp,Phosphor.copy(alpha=.3f)),contentAlignment=Alignment.Center) {
            Text("MAP CANVAS / LOCAL SNAPSHOT / MARKER LAYER",color=Phosphor.copy(alpha=.65f),fontFamily=FontFamily.Monospace)
        }
    }
}
@Composable private fun RadioPanel() {
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("RADIO",color=Phosphor,fontFamily=FontFamily.Monospace,fontSize=28.sp)
        Text("STATIONS",color=Phosphor,fontFamily=FontFamily.Monospace)
        Text("ON / OFF • CURRENT PROGRAM • RANGE • VOLUME",color=Phosphor,fontFamily=FontFamily.Monospace)
        Text("Verified RPC boundary remains capability-gated.",color=Phosphor.copy(alpha=.65f),fontFamily=FontFamily.Monospace)
    }
}
@Composable private fun SettingsPanel(connected:Boolean,demo:Boolean,auto:Boolean,threshold:Float,onDemo:(Boolean)->Unit,onAuto:(Boolean)->Unit,onThreshold:(Float)->Unit,onReconnect:()->Unit,onClear:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("SETTINGS",color=Phosphor,fontFamily=FontFamily.Monospace,fontSize=28.sp)
        Text("CONNECTION: " + if(connected) "CONNECTED" else "DISCONNECTED",color=Phosphor,fontFamily=FontFamily.Monospace)
        Text("RECONNECT",color=Phosphor,fontFamily=FontFamily.Monospace,modifier=Modifier.border(1.dp,Phosphor.copy(alpha=.3f)).clickable(onClick=onReconnect).padding(8.dp))
        Text("DEMO / CACHED DATA: " + if(demo) "ON" else "OFF",color=Phosphor,fontFamily=FontFamily.Monospace,modifier=Modifier.clickable{onDemo(!demo)})
        Text("AUTO-STIMPAK: " + if(auto) "ON" else "OFF",color=Phosphor,fontFamily=FontFamily.Monospace,modifier=Modifier.clickable{onAuto(!auto)})
        Text("AUTO-STIMPAK THRESHOLD: " + (threshold*100).toInt() + "%",color=Phosphor,fontFamily=FontFamily.Monospace)
        Text("DATA MANAGEMENT",color=Phosphor,fontFamily=FontFamily.Monospace)
        Text("CLEAR CACHED DATABASE",color=Phosphor,fontFamily=FontFamily.Monospace,modifier=Modifier.border(1.dp,Phosphor.copy(alpha=.3f)).clickable(onClick=onClear).padding(8.dp))
        Text("Desktop uses the same five-tab ATTA-BOY information architecture and release version as iOS/iPadOS and Android.",color=Phosphor.copy(alpha=.65f),fontFamily=FontFamily.Monospace)
    }
}
