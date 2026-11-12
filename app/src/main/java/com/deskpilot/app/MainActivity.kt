package com.deskpilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private val Bg = Color(0xFF090B10)
private val Panel = Color(0xFF151923)
private val Panel2 = Color(0xFF202735)
private val Blue = Color(0xFF2787F5)
private val TextMain = Color(0xFFF4F6FA)
private val Muted = Color(0xFF9AA6B7)

data class PcProfile(val id: Long, val name: String, val host: String, val user: String, val password: String, val port: Int)
data class MacroAction(val kind: String, val value: String = "")
data class MacroButton(val id: Long, val name: String, val actions: List<MacroAction>, val x: Float = .72f, val y: Float = .70f)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DeskPilotApp() }
    }
}

@Composable
fun DeskPilotApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("deskpilot", android.content.Context.MODE_PRIVATE) }
    var pcs by remember { mutableStateOf(loadPcs(prefs)) }
    var macros by remember { mutableStateOf(loadMacros(prefs)) }
    var page by remember { mutableStateOf("home") }
    var selectedPc by remember { mutableStateOf<PcProfile?>(null) }
    var mouseMode by remember { mutableStateOf("Touchpad Mode") }
    var editMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showMacro by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Blue, background = Bg, surface = Panel, onBackground = TextMain,
        onSurface = TextMain, secondary = Muted
    )) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            when (page) {
                "home" -> HomeScreen(pcs, onAdd = { showAdd = true }, onOpen = {
                    selectedPc = it; page = "viewer"; showMenu = false; editMode = false
                })
                "viewer" -> ViewerScreen(
                    pc = selectedPc,
                    macros = macros,
                    mouseMode = mouseMode,
                    editMode = editMode,
                    showMenu = showMenu,
                    onBack = { page = "home" },
                    onMenu = { showMenu = !showMenu },
                    onMouseMode = { mouseMode = it; showMenu = false },
                    onEdit = { editMode = !editMode; showMenu = false },
                    onAddMacro = { showMacro = true },
                    onMacroMove = { id, x, y ->
                        macros = macros.map { if (it.id == id) it.copy(x = x, y = y) else it }
                        saveMacros(prefs, macros)
                    },
                    onToast = { toast = it }
                )
            }
            if (showAdd) AddPcDialog(
                onDismiss = { showAdd = false },
                onSave = { profile ->
                    pcs = pcs + profile
                    savePcs(prefs, pcs)
                    showAdd = false
                }
            )
            if (showMacro) MacroDialog(
                onDismiss = { showMacro = false },
                onSave = { macro ->
                    macros = macros + macro
                    saveMacros(prefs, macros)
                    showMacro = false
                }
            )
            if (toast.isNotBlank()) AlertDialog(
                onDismissRequest = { toast = "" },
                title = { Text("DeskPilot") },
                text = { Text(toast) },
                confirmButton = { TextButton(onClick = { toast = "" }) { Text("OK") } },
                containerColor = Panel
            )
        }
    }
}

@Composable
fun HomeScreen(pcs: List<PcProfile>, onAdd: () -> Unit, onOpen: (PcProfile) -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("DeskPilot", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextMain)
        Text("Remote desktop, with your own controls.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(34.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add PC", fontSize = 17.sp)
        }
        Spacer(Modifier.height(26.dp))
        Text("SAVED PCS", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (pcs.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Computer, null, tint = Muted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No PCs saved yet", color = Muted)
                    Text("Tap Add PC to create your first connection.", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pcs, key = { it.id }) { pc ->
                    Row(
                        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp))
                            .clickable { onOpen(pc) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(44.dp).background(Panel2, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Computer, null, tint = Blue)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pc.name, color = TextMain, fontWeight = FontWeight.SemiBold)
                            Text("${pc.host}:${pc.port}  •  ${pc.user}", color = Muted, fontSize = 12.sp)
                        }
                        Text("CONNECT", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text("DESKPILOT • UI PREVIEW 0.1", color = Color(0xFF566173), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
fun AddPcDialog(onDismiss: () -> Unit, onSave: (PcProfile) -> Unit) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("3389") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Add PC", color = TextMain) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                InputField("Display Name", name, { name = it })
                InputField("Host", host, { host = it })
                InputField("Username", user, { user = it })
                OutlinedTextField(pass, { pass = it }, label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                InputField("Port", port, { port = it }, KeyboardType.Number)
                Text("RDP default port: 3389", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = port.toIntOrNull() ?: 3389
                if (name.isNotBlank() && host.isNotBlank() && user.isNotBlank() && p in 1..65535)
                    onSave(PcProfile(System.currentTimeMillis(), name.trim(), host.trim(), user.trim(), pass, p))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun InputField(label: String, value: String, onChange: (String) -> Unit, type: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth(),
        colors = fieldColors())
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextMain, unfocusedTextColor = TextMain,
    focusedBorderColor = Blue, unfocusedBorderColor = Panel2,
    focusedLabelColor = Blue, unfocusedLabelColor = Muted,
    cursorColor = Blue
)

@Composable
fun ViewerScreen(
    pc: PcProfile?, macros: List<MacroButton>, mouseMode: String, editMode: Boolean, showMenu: Boolean,
    onBack: () -> Unit, onMenu: () -> Unit, onMouseMode: (String) -> Unit, onEdit: () -> Unit,
    onAddMacro: () -> Unit, onMacroMove: (Long, Float, Float) -> Unit, onToast: (String) -> Unit
) {
    var touchInfo by remember { mutableStateOf("RDP engine not connected yet") }
    var showMousePicker by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF10141C)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextMain) }
                Column(Modifier.weight(1f)) {
                    Text(pc?.name ?: "Remote PC", color = TextMain, fontWeight = FontWeight.SemiBold)
                    Text(pc?.host ?: "", color = Muted, fontSize = 11.sp)
                }
                Text("PREVIEW", color = Color(0xFFFFC857), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxSize().weight(1f).background(Color(0xFF050608)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Blue)
                    Spacer(Modifier.height(18.dp))
                    Text("RDP session view", color = TextMain, fontWeight = FontWeight.SemiBold)
                    Text(touchInfo, color = Muted, fontSize = 12.sp)
                    Text("FreeRDP native engine integration is the next build stage.", color = Muted, fontSize = 10.sp)
                }
            }
        }

        // Compact floating computer bubble and scrollable menu
        Column(Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 12.dp), horizontalAlignment = Alignment.End) {
            Box(Modifier.size(44.dp).background(Blue, CircleShape).clickable { onMenu() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Computer, null, tint = Color.White)
            }
            if (showMenu) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.width(190.dp).heightIn(max = 330.dp).background(Panel, RoundedCornerShape(16.dp)).padding(8.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item { MenuRow("Mouse", Icons.Default.Mouse) { showMousePicker = true } }
                        item { MenuRow(if (editMode) "Finish Edit Control" else "Edit Control", Icons.Default.Tune, onEdit) }
                        item { MenuRow("Keyboard", Icons.Default.Keyboard) { showKeyboard = true } }
                        item { MenuRow("Add Button", Icons.Default.Add, onAddMacro) }
                        item { MenuRow("Hide", Icons.Default.Computer, onMenu) }
                    }
                }
            }
        }

        // User-defined floating buttons, draggable only while editing
        macros.forEach { macro ->
            var offset by remember(macro.id, macro.x, macro.y) { mutableStateOf(Offset(macro.x, macro.y)) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val screenWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                val screenHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                val px = (offset.x * screenWidthPx).roundToInt().coerceIn(0, screenWidthPx.roundToInt() - 1)
                val py = (offset.y * screenHeightPx).roundToInt().coerceIn(0, screenHeightPx.roundToInt() - 1)
                Box(
                    Modifier.offset { IntOffset(px, py) }
                        .pointerInput(editMode, screenWidthPx, screenHeightPx) {
                            if (editMode) detectDragGestures { change, drag ->
                                change.consume()
                                val nx = (offset.x + drag.x / screenWidthPx).coerceIn(0f, .92f)
                                val ny = (offset.y + drag.y / screenHeightPx).coerceIn(0f, .92f)
                                offset = Offset(nx, ny)
                                onMacroMove(macro.id, nx, ny)
                            }
                        }
                        .background(if (editMode) Blue.copy(alpha = .8f) else Color(0xCC202735), RoundedCornerShape(12.dp))
                        .border(1.dp, if (editMode) Color.White else Blue.copy(alpha = .35f), RoundedCornerShape(12.dp))
                        .clickable { if (!editMode) onToast("Button '${macro.name}' is saved. RDP input will work after FreeRDP integration.") }
                        .padding(horizontal = 13.dp, vertical = 10.dp)
                ) { Text(macro.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        if (editMode) {
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Panel.copy(alpha = .96f)).padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("EDIT CONTROL • drag buttons to move", color = Muted, fontSize = 10.sp)
                Button(onClick = onEdit) { Text("Apply") }
            }
        }
    }

    if (showMousePicker) AlertDialog(
        onDismissRequest = { showMousePicker = false }, containerColor = Panel,
        title = { Text("Mouse mode") },
        text = {
            Column {
                listOf("Touchpad Mode", "Direct Mouse Mode").forEach { mode ->
                    Row(Modifier.fillMaxWidth().clickable { onMouseMode(mode); showMousePicker = false }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mouseMode == mode, onClick = { onMouseMode(mode); showMousePicker = false })
                        Text(mode)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showMousePicker = false }) { Text("Close") } }
    )

    if (showKeyboard) AlertDialog(
        onDismissRequest = { showKeyboard = false }, containerColor = Panel,
        title = { Text("Keyboard") },
        text = { Text("The full remote Windows keyboard will be provided by the RDP engine. This preview does not send keystrokes yet.", color = Muted) },
        confirmButton = { TextButton(onClick = { showKeyboard = false }) { Text("OK") } }
    )
}

@Composable
fun MenuRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Blue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextMain, fontSize = 13.sp)
    }
}

@Composable
fun MacroDialog(onDismiss: () -> Unit, onSave: (MacroButton) -> Unit) {
    var name by remember { mutableStateOf("") }
    var actions by remember { mutableStateOf(listOf<MacroAction>()) }
    var showPicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("Add Button") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InputField("Button Name", name, { name = it })
                Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                    Icon(Icons.Default.Keyboard, null); Spacer(Modifier.width(8.dp)); Text("Keyboard")
                }
                OutlinedButton(onClick = { actions = actions + MacroAction("Left Click") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add Left Click")
                }
                OutlinedButton(onClick = { actions = actions + MacroAction("Right Click") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add Right Click")
                }
                Text("Action sequence", color = Muted, fontSize = 12.sp)
                if (actions.isEmpty()) Text("No actions selected", color = Muted, fontSize = 11.sp)
                actions.forEachIndexed { index, action ->
                    Row(Modifier.fillMaxWidth().background(Panel2, RoundedCornerShape(8.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. ${action.kind} ${action.value}", Modifier.weight(1f), color = TextMain, fontSize = 12.sp)
                        TextButton(onClick = { actions = actions.filterIndexed { i, _ -> i != index } }) { Text("Remove") }
                    }
                }
                Text("Tip: add Left Click twice to create a double-click macro.", color = Muted, fontSize = 10.sp)
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank() && actions.isNotEmpty()) onSave(MacroButton(System.currentTimeMillis(), name.trim(), actions)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (showPicker) AlertDialog(
        onDismissRequest = { showPicker = false }, containerColor = Panel,
        title = { Text("Choose a key") },
        text = {
            val keys = listOf("Enter", "Esc", "Tab", "Space", "Backspace", "Delete", "Ctrl", "Alt", "Shift", "Win", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "A", "C", "V", "X", "Z", "Up", "Down", "Left", "Right")
            LazyColumn(Modifier.heightIn(max = 330.dp)) {
                items(keys) { key ->
                    Text(key, Modifier.fillMaxWidth().clickable {
                        actions = actions + MacroAction("Key", key)
                        showPicker = false
                    }.padding(11.dp), color = TextMain)
                }
            }
        },
        confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Close") } }
    )
}

fun savePcs(prefs: android.content.SharedPreferences, pcs: List<PcProfile>) {
    val arr = JSONArray()
    pcs.forEach { p ->
        arr.put(JSONObject().put("id", p.id).put("name", p.name).put("host", p.host)
            .put("user", p.user).put("password", p.password).put("port", p.port))
    }
    prefs.edit().putString("pcs", arr.toString()).apply()
}
fun loadPcs(prefs: android.content.SharedPreferences): List<PcProfile> = try {
    val arr = JSONArray(prefs.getString("pcs", "[]"))
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        PcProfile(o.getLong("id"), o.getString("name"), o.getString("host"),
            o.getString("user"), o.optString("password"), o.optInt("port", 3389))
    }
} catch (_: Exception) { emptyList() }

fun saveMacros(prefs: android.content.SharedPreferences, macros: List<MacroButton>) {
    val arr = JSONArray()
    macros.forEach { m ->
        val acts = JSONArray()
        m.actions.forEach { acts.put(JSONObject().put("kind", it.kind).put("value", it.value)) }
        arr.put(JSONObject().put("id", m.id).put("name", m.name).put("x", m.x).put("y", m.y).put("actions", acts))
    }
    prefs.edit().putString("macros", arr.toString()).apply()
}
fun loadMacros(prefs: android.content.SharedPreferences): List<MacroButton> = try {
    val arr = JSONArray(prefs.getString("macros", "[]"))
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val aa = o.getJSONArray("actions")
        val actions = (0 until aa.length()).map { j ->
            val a = aa.getJSONObject(j); MacroAction(a.getString("kind"), a.optString("value"))
        }
        MacroButton(o.getLong("id"), o.getString("name"), actions, o.optDouble("x", .72).toFloat(), o.optDouble("y", .70).toFloat())
    }
} catch (_: Exception) { emptyList() }
