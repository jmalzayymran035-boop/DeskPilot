package com.deskpilot

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

private data class PcForm(
    val host: String = "",
    val user: String = "",
    val password: String = "",
    val port: String = "3389",
    val size: String = "Medium"
)

class MainActivity : ComponentActivity() {
    private var sessionActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DeskPilotApp(
            onSessionState = { active ->
                sessionActive = active
                requestedOrientation = if (active)
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        ) }
    }

    override fun onDestroy() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        super.onDestroy()
    }
}

@Composable
private fun DeskPilotApp(onSessionState: (Boolean) -> Unit) {
    var form by remember { mutableStateOf(PcForm()) }
    var active by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }
    var rdpView by remember { mutableStateOf<RdpSurfaceView?>(null) }
    val context = LocalContext.current
    val secure = remember { SecurePrefs(context) }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme()) {
        if (!active) {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("DeskPilot", style = MaterialTheme.typography.headlineLarge)
                    Text("Remote Desktop", color = MaterialTheme.colorScheme.secondary)
                    OutlinedTextField(form.host, { form = form.copy(host = it) },
                        label = { Text("Host / IP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(form.user, { form = form.copy(user = it) },
                        label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(form.password, { form = form.copy(password = it) },
                        label = { Text("Password") }, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(form.port, { form = form.copy(port = it.filter(Char::isDigit)) },
                        label = { Text("Port") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = form.size, onValueChange = {}, readOnly = true,
                            label = { Text("Computer Size") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("Small", "Medium", "Large", "Auto").forEach {
                                DropdownMenuItem(text = { Text(it) }, onClick = {
                                    form = form.copy(size = it); expanded = false
                                })
                            }
                        }
                    }
                    Text(status, color = MaterialTheme.colorScheme.secondary)
                    Button(
                        enabled = form.host.isNotBlank() && form.port.toIntOrNull() in 1..65535,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            secure.savePassword(form.host, form.password)
                            active = true
                            onSessionState(true)
                        }
                    ) { Text("Connect") }
                }
            }
        } else {
            BackHandler {
                rdpView?.disconnect()
                active = false
                onSessionState(false)
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        RdpSurfaceView(ctx).also { view ->
                            rdpView = view
                            view.isFocusableInTouchMode = true
                            view.requestFocus()
                            view.setStatusListener { status = it }
                            val (w, h) = when (form.size) {
                                "Small" -> 800 to 600
                                "Large" -> 1920 to 1080
                                "Auto" -> 0 to 0
                                else -> 1280 to 720
                            }
                            view.connect(form.host, form.user, form.password,
                                form.port.toIntOrNull() ?: 3389, w, h)
                        }
                    },
                    update = { rdpView = it }
                )
                DeskPilotOverlay(
                    status = status,
                    onMouse = { rdpView?.setPointerMode(PointerMode.TOUCHPAD) },
                    onDirect = { rdpView?.setPointerMode(PointerMode.DIRECT) },
                    onKey = { rdpView?.requestFocus() },
                    onAction = { action ->
                        when (action) {
                            "Ctrl+C" -> { rdpView?.let { it.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_CTRL_LEFT)); it.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_C)); it.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_C)); it.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_CTRL_LEFT)) } }
                            "Alt+Tab" -> { rdpView?.let { it.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ALT_LEFT)); it.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_TAB)); it.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_TAB)); it.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ALT_LEFT)) } }
                            "Disconnect" -> { rdpView?.disconnect(); active = false; onSessionState(false) }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DeskPilotOverlay(
    status: String,
    onMouse: () -> Unit,
    onDirect: () -> Unit,
    onKey: () -> Unit,
    onAction: (String) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf(true) }
    var cursorSize by remember { mutableFloatStateOf(1f) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopEnd).padding(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            FilledTonalIconButton(onClick = { menu = !menu }) {
                Icon(Icons.Default.Computer, contentDescription = "DeskPilot controls")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Touchpad mouse") }, leadingIcon = { Icon(Icons.Default.Mouse, null) },
                    onClick = { onMouse(); menu = false })
                DropdownMenuItem(text = { Text("Direct mouse") }, onClick = { onDirect(); menu = false })
                DropdownMenuItem(text = { Text("Keyboard") }, leadingIcon = { Icon(Icons.Default.Keyboard, null) },
                    onClick = { onKey(); menu = false })
                DropdownMenuItem(text = { Text(if (controller) "Hide controller" else "Show controller") },
                    onClick = { controller = !controller; menu = false })
                DropdownMenuItem(text = { Text("Disconnect") }, onClick = { onAction("Disconnect"); menu = false })
            }
            Text(status, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }

        if (controller) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(14.dp)
                    .background(Color(0xCC202938), RoundedCornerShape(16.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Controller", color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { onAction("Ctrl+C") }) { Text("Ctrl+C") }
                    Button(onClick = { onAction("Alt+Tab") }) { Text("Alt+Tab") }
                }
                Text("Cursor size", color = Color.White)
                Slider(value = cursorSize, onValueChange = { cursorSize = it }, valueRange = 0.5f..3f)
            }
        }
    }
}
