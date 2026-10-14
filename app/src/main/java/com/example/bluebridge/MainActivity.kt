package com.example.bluebridge

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.bluebridge.ui.theme.BlueBridgeTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val sharedTextState = mutableStateOf("")
    private val sharedUriState = mutableStateOf<Uri?>(null)
    private val initialTabState = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var useGruvbox by remember { mutableStateOf(value = false) }
            var useDynamicColor by remember { mutableStateOf(value = true) }

            BlueBridgeTheme(
                darkTheme = systemDark,
                dynamicColor = useDynamicColor,
                useGruvbox = useGruvbox,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigationContainer(
                        useGruvbox = useGruvbox,
                        onThemeToggle = { useGruvbox = !useGruvbox },
                        useDynamicColor = useDynamicColor,
                        onDynamicColorToggle = { useDynamicColor = !useDynamicColor },
                        initialText = sharedTextState.value,
                        initialUri = sharedUriState.value,
                        initialTab = initialTabState.intValue
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if ((intent.type == "text/plain" || intent.type == "text/markdown") && intent.hasExtra(Intent.EXTRA_TEXT)) {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    sharedTextState.value = text
                    initialTabState.intValue = 0
                } else {
                    @Suppress("DEPRECATION")
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    if (uri != null) {
                        sharedUriState.value = uri
                        initialTabState.intValue = 1
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (!uris.isNullOrEmpty()) {
                    shareMultipleFiles(this, uris)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun AppNavigationContainer(
    useGruvbox: Boolean,
    onThemeToggle: () -> Unit,
    useDynamicColor: Boolean,
    onDynamicColorToggle: () -> Unit,
    initialText: String = "",
    initialUri: Uri? = null,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    var currentText by remember { mutableStateOf(initialText) }
    var incomingUri by remember { mutableStateOf(initialUri) }

    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val bluetoothAdapter = bluetoothManager?.adapter
    var isBluetoothOn by remember { mutableStateOf(bluetoothAdapter?.isEnabled ?: false) }

    var hasBluetoothPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasBluetoothPermission = isGranted
        if (isGranted) {
            isBluetoothOn = bluetoothAdapter?.isEnabled ?: false
            Toast.makeText(context, "Bluetooth permission granted.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Bluetooth permission denied.", Toast.LENGTH_LONG).show()
        }
    }

    fun toggleBluetooth() {
        if (bluetoothAdapter == null) {
            Toast.makeText(context, "Device does not support Bluetooth.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasBluetoothPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            return
        }
        try {
            if (isBluetoothOn) {
                bluetoothAdapter.disable()
                isBluetoothOn = false
                Toast.makeText(context, "Bluetooth OFF", Toast.LENGTH_SHORT).show()
            } else {
                bluetoothAdapter.enable()
                isBluetoothOn = true
                Toast.makeText(context, "Bluetooth ON", Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) {
            Toast.makeText(context, "Permission error controlling Bluetooth.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(initialText) {
        if (initialText.isNotBlank()) {
            currentText = initialText
            selectedTab = 0
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            incomingUri = initialUri
            selectedTab = 1
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let { shareFile(context, it) }
        }
    )

    fun fetchClipboardText(): String {
        return clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ">_ ",
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "BLUEBRIDGE",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { toggleBluetooth() }) {
                            Icon(
                                imageVector = if (isBluetoothOn) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                                contentDescription = if (isBluetoothOn) "Bluetooth On" else "Bluetooth Off",
                                tint = if (isBluetoothOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        if (currentText.isBlank()) {
                            currentText = fetchClipboardText()
                        }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.Notes, "[TEXT]") },
                    label = {
                        Text(
                            text = "[1] TEXT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.AttachFile, "[FILE]") },
                    label = {
                        Text(
                            text = "[2] FILE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, "[CONF]") },
                    label = {
                        Text(
                            text = "[3] CONF",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> TextTabScreen(
                    textValue = currentText,
                    onTextChange = { currentText = it },
                    onPasteClipboard = { currentText = fetchClipboardText() },
                    onClear = { currentText = "" },
                    onShare = { shareTextAsFile(context, currentText) }
                )
                1 -> FileTabScreen(
                    incomingUri = incomingUri,
                    onPickFile = { filePickerLauncher.launch("*/*") },
                    onShareIncoming = { uri -> shareFile(context, uri) }
                )
                2 -> SettingsTabScreen(
                    useGruvbox = useGruvbox,
                    onThemeToggle = onThemeToggle,
                    useDynamicColor = useDynamicColor,
                    onDynamicColorToggle = onDynamicColorToggle,
                    isBluetoothOn = isBluetoothOn,
                    hasBluetoothPermission = hasBluetoothPermission,
                    onToggleBluetooth = { toggleBluetooth() },
                    onClearCache = { clearCache(context) }
                )
            }
        }
    }
}

// TAB 1: TEXT (.MD)
@Composable
fun TextTabScreen(
    textValue: String,
    onTextChange: (String) -> Unit,
    onPasteClipboard: () -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$ ",
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (textValue.isNotBlank()) "clip.md [READY]" else "clip.md [EMPTY]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = if (textValue.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onPasteClipboard,
                        shape = RectangleShape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text(
                            text = "[PASTE]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (textValue.isNotBlank()) {
                        OutlinedButton(
                            onClick = onClear,
                            shape = RectangleShape,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                text = "[CLEAR]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = textValue,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = {
                Text(
                    text = "# Enter or paste Markdown text...",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            },
            shape = RectangleShape,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp
            )
        )

        Button(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = textValue.isNotBlank(),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = "$ SHARE AS .MD VIA BLUETOOTH",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

// TAB 2: FILE
@Composable
fun FileTabScreen(
    incomingUri: Uri?,
    onPickFile: () -> Unit,
    onShareIncoming: (Uri) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$ ", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("share-file --picker", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select any local file using your system file manager (e.g., Fossify Files) to stream over Bluetooth.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (incomingUri != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "[!] INCOMING FILE DETECTED FROM SHARE MENU",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onShareIncoming(incomingUri) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text(
                            text = "$ FORWARD FILE VIA BLUETOOTH",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = onPickFile,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CHOOSE FILE TO SHARE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSecondary
            )
        }
    }
}

// TAB 3: SETTINGS
@Composable
fun SettingsTabScreen(
    useGruvbox: Boolean,
    onThemeToggle: () -> Unit,
    useDynamicColor: Boolean,
    onDynamicColorToggle: () -> Unit,
    isBluetoothOn: Boolean,
    hasBluetoothPermission: Boolean,
    onToggleBluetooth: () -> Unit,
    onClearCache: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Bluetooth Quick Status Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(2.dp, if (isBluetoothOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "$ bluetoothctl",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isBluetoothOn) "STATE: [ ON ]" else "STATE: [ OFF ]",
                        color = if (isBluetoothOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                if (!hasBluetoothPermission) {
                    Button(
                        onClick = onToggleBluetooth,
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text(
                            text = "[ GRANT ]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                } else {
                    Switch(checked = isBluetoothOn, onCheckedChange = { onToggleBluetooth() })
                }
            }
        }

        // Theme Options Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "THEME = [ GRUVBOX DARK ]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(checked = useGruvbox, onCheckedChange = { onThemeToggle() })
                }

                if (!useGruvbox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "DYNAMIC MATERIAL COLOR",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(checked = useDynamicColor, onCheckedChange = { onDynamicColorToggle() })
                    }
                }
            }
        }

        // Cache Clean Button
        Button(
            onClick = onClearCache,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Text(
                text = "$ rm -rf ~/.cache/*.md",
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // About Terminal Box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "$ cat /etc/about",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "BLUEBRIDGE v1.0 (Terminal Brutalist Utility)\n" +
                        "=========================================\n" +
                        "A zero-dependency, privacy-first tool to\n" +
                        "bridge copied Markdown snippets & files\n" +
                        "from Android to Linux over Bluetooth.\n\n" +
                        "* Material Day/Night & Gruvbox support\n" +
                        "* Bluetooth toggle on top navigation bar\n" +
                        "* Generates valid UTF-8 .md files\n" +
                        "* Native Wayland / Arch workflow friendly",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// --- HELPER FUNCTIONS ---

private fun shareTextAsFile(context: Context, text: String) {
    if (text.isBlank()) {
        Toast.makeText(context, "Cannot share empty text.", Toast.LENGTH_SHORT).show()
        return
    }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val file = File(context.cacheDir, "clip_$timestamp.md")
    file.writeText(text)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    shareFile(context, uri, "text/markdown")
}

private fun shareFile(context: Context, uri: Uri, mimeType: String? = null) {
    val type = mimeType ?: context.contentResolver.getType(uri) ?: "*/*"
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, uri)
        this.type = type
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, "Share via...")
    context.startActivity(chooser)
}

private fun shareMultipleFiles(context: Context, uris: ArrayList<Uri>) {
    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        type = "*/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, "Share files via...")
    context.startActivity(chooser)
}

private fun clearCache(context: Context) {
    val cacheDir = context.cacheDir
    val files = cacheDir.listFiles()
    var deletedCount = 0
    files?.forEach { file ->
        if ((file.name.startsWith("clip_") && (file.name.endsWith(".md") || file.name.endsWith(".txt"))) && file.delete()) {
            deletedCount++
        }
    }
    Toast.makeText(context, "Deleted $deletedCount cached .md file(s).", Toast.LENGTH_SHORT).show()
}
