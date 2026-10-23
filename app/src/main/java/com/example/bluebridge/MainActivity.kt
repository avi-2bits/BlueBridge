package com.example.bluebridge

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.example.bluebridge.ui.theme.AppThemeMode
import com.example.bluebridge.ui.theme.BlueBridgeTheme
import com.example.bluebridge.ui.theme.getThemeSwatches
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed class AppStatus(val message: String) {
    class Success(message: String) : AppStatus(message)
    class Warning(message: String) : AppStatus(message)
    class Error(message: String) : AppStatus(message)
    class Information(message: String) : AppStatus(message)
}

class MainActivity : ComponentActivity() {
    private val sharedTextState = mutableStateOf("")
    private val sharedUriState = mutableStateOf<Uri?>(null)
    private val initialTabState = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            val context = LocalContext.current
            val systemDark = isSystemInDarkTheme()
            var currentThemeMode by remember { mutableStateOf(PreferencesManager.getThemeMode(context)) }

            BlueBridgeTheme(
                themeMode = currentThemeMode,
                darkTheme = systemDark
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigationContainer(
                        currentThemeMode = currentThemeMode,
                        onThemeModeSelect = { newMode ->
                            PreferencesManager.setThemeMode(context, newMode)
                            currentThemeMode = newMode
                        },
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
                    shareMultipleFiles(this, uris) {}
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun AppNavigationContainer(
    currentThemeMode: AppThemeMode,
    onThemeModeSelect: (AppThemeMode) -> Unit,
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

    var currentStatus by remember { mutableStateOf<AppStatus?>(null) }

    var hasBluetoothPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Auto-dismiss in-app status message after 2.5 seconds
    LaunchedEffect(currentStatus) {
        if (currentStatus != null) {
            delay(2500)
            currentStatus = null
        }
    }

    // Dynamic BroadcastReceiver to update Bluetooth icon in topbar & clear warning when Bluetooth is enabled
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    isBluetoothOn = (state == BluetoothAdapter.STATE_ON)
                    if (isBluetoothOn && currentStatus is AppStatus.Warning) {
                        currentStatus = null
                    }
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasBluetoothPermission = isGranted
        if (isGranted) {
            isBluetoothOn = bluetoothAdapter?.isEnabled ?: false
            currentStatus = AppStatus.Information("Bluetooth permission granted.")
        } else {
            currentStatus = AppStatus.Error("Bluetooth permission denied.")
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isBluetoothOn = bluetoothAdapter?.isEnabled ?: false
        if (isBluetoothOn && currentStatus is AppStatus.Warning) {
            currentStatus = null
        }
    }

    fun toggleBluetooth() {
        if (bluetoothAdapter == null) {
            currentStatus = AppStatus.Error("Device does not support Bluetooth.")
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
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                context.startActivity(intent)
            } else {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(intent)
            }
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            context.startActivity(intent)
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
            uri?.let { shareFile(context, it) { status -> currentStatus = status } }
        }
    )

    fun fetchClipboardText(): String {
        return clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
    }

    Scaffold(
        topBar = {
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Unified In-App Status Message Banner
            when (val status = currentStatus) {
                is AppStatus.Warning -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RectangleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BluetoothDisabled,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = status.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        currentStatus = null
                                        try {
                                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    shape = RectangleShape,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                ) {
                                    Text(
                                        text = "[SETTINGS]",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                IconButton(
                                    onClick = { currentStatus = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                is AppStatus.Success -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RectangleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = status.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { currentStatus = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                is AppStatus.Error -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RectangleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = status.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { currentStatus = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                is AppStatus.Information -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RectangleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = status.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { currentStatus = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                null -> {}
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> TextTabScreen(
                        textValue = currentText,
                        bluetoothAdapter = bluetoothAdapter,
                        hasBluetoothPermission = hasBluetoothPermission,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        },
                        onStatusMessage = { status ->
                            currentStatus = status
                        },
                        onTextChange = { currentText = it },
                        onPasteClipboard = { currentText = fetchClipboardText() },
                        onClear = { currentText = "" },
                        onShareGeneral = { shareTextAsFileGeneral(context, currentText) { status -> currentStatus = status } }
                    )
                    1 -> FileTabScreen(
                        incomingUri = incomingUri,
                        onPickFile = { filePickerLauncher.launch("*/*") },
                        onShareIncoming = { uri -> shareFile(context, uri) { status -> currentStatus = status } }
                    )
                    2 -> SettingsTabScreen(
                        currentThemeMode = currentThemeMode,
                        onThemeModeSelect = onThemeModeSelect,
                        isBluetoothOn = isBluetoothOn,
                        hasBluetoothPermission = hasBluetoothPermission,
                        bluetoothAdapter = bluetoothAdapter,
                        onRequestBluetoothPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        },
                        onToggleBluetooth = { toggleBluetooth() },
                        onClearCache = { clearCache(context) { status -> currentStatus = status } }
                    )
                }
            }
        }
    }
}

// TAB 1: TEXT (.MD) WITH DIRECT OBEX BLUETOOTH PINNED ACTION
@Composable
fun TextTabScreen(
    textValue: String,
    bluetoothAdapter: BluetoothAdapter?,
    hasBluetoothPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStatusMessage: (AppStatus) -> Unit,
    onTextChange: (String) -> Unit,
    onPasteClipboard: () -> Unit,
    onClear: () -> Unit,
    onShareGeneral: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pinnedDeviceName by remember { mutableStateOf(PreferencesManager.getPinnedDeviceName(context)) }
    var pinnedDeviceAddress by remember { mutableStateOf(PreferencesManager.getPinnedDeviceAddress(context)) }
    var showDevicePickerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status Bar
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

        // Text Editor
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

        // Two Separate Rectangular Share Controls: [ $ Share .md ]    [ [arch] ]
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Control: $ Share .md (Opens standard Android Bluetooth / Sharesheet flow)
            Button(
                onClick = onShareGeneral,
                enabled = textValue.isNotBlank(),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                BoxWithConstraints {
                    val label = if (maxWidth < 180.dp) "$ Share" else "$ Share .md"
                    Text(
                        text = label,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Control: Direct OBEX Bluetooth transfer button [arch] / [select device]
            val displayLabel = pinnedDeviceName.ifBlank { "select device" }

            Surface(
                modifier = Modifier
                    .widthIn(min = 90.dp, max = 160.dp)
                    .fillMaxHeight()
                    .combinedClickable(
                        enabled = textValue.isNotBlank(),
                        onClick = {
                            if (pinnedDeviceAddress.isBlank()) {
                                showDevicePickerDialog = true
                            } else {
                                sendDirectObexFile(
                                    context = context,
                                    text = textValue,
                                    targetName = pinnedDeviceName,
                                    targetAddress = pinnedDeviceAddress,
                                    bluetoothAdapter = bluetoothAdapter,
                                    hasBluetoothPermission = hasBluetoothPermission,
                                    onRequestPermission = onRequestPermission,
                                    onStatusMessage = onStatusMessage,
                                    scope = coroutineScope
                                )
                            }
                        },
                        onLongClick = {
                            showDevicePickerDialog = true
                        }
                    ),
                shape = RectangleShape,
                color = if (textValue.isNotBlank()) MaterialTheme.colorScheme.tertiary
                       else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[$displayLabel]",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (textValue.isNotBlank()) MaterialTheme.colorScheme.onTertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // Device Selection & Pinning Dialog
    if (showDevicePickerDialog) {
        DeviceSelectionDialog(
            bluetoothAdapter = bluetoothAdapter,
            hasBluetoothPermission = hasBluetoothPermission,
            onRequestPermission = onRequestPermission,
            onDismiss = { showDevicePickerDialog = false },
            onSelectDevice = { name, address ->
                PreferencesManager.setPinnedDevice(context, name, address)
                pinnedDeviceName = name
                pinnedDeviceAddress = address
                showDevicePickerDialog = false
                onStatusMessage(AppStatus.Information("Pinned target: $name"))
            },
            onClearPin = {
                PreferencesManager.clearPinnedDevice(context)
                pinnedDeviceName = ""
                pinnedDeviceAddress = ""
                showDevicePickerDialog = false
                onStatusMessage(AppStatus.Information("Cleared pinned target."))
            }
        )
    }
}

// DEVICE SELECTION & PINNING DIALOG
@Composable
fun DeviceSelectionDialog(
    bluetoothAdapter: BluetoothAdapter?,
    hasBluetoothPermission: Boolean,
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit,
    onSelectDevice: (name: String, address: String) -> Unit,
    onClearPin: () -> Unit
) {
    @Suppress("DEPRECATION")
    val bondedDevices: List<BluetoothDevice> = remember(hasBluetoothPermission) {
        if (hasBluetoothPermission && bluetoothAdapter != null) {
            try {
                bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
            } catch (_: SecurityException) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "$ select-device --pin",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                if (!hasBluetoothPermission) {
                    Text(
                        text = "Bluetooth permission required to list paired devices.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = onRequestPermission,
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("GRANT PERMISSION", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                } else if (bondedDevices.isEmpty()) {
                    Text(
                        text = "No paired Bluetooth devices found on phone.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Paired Devices (Tap to pin as favorite):",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(bondedDevices) { device ->
                            @Suppress("DEPRECATION")
                            val name = try { device.name ?: "Unknown Device" } catch (_: SecurityException) { "Unknown Device" }
                            val address = device.address ?: ""

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(onClick = { onSelectDevice(name, address) }),
                                shape = RectangleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = name,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = address,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "[PIN]",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onClearPin, shape = RectangleShape) {
                        Text("RESET PIN", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    TextButton(onClick = onDismiss, shape = RectangleShape) {
                        Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
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
    currentThemeMode: AppThemeMode,
    onThemeModeSelect: (AppThemeMode) -> Unit,
    isBluetoothOn: Boolean,
    hasBluetoothPermission: Boolean,
    bluetoothAdapter: BluetoothAdapter?,
    onRequestBluetoothPermission: () -> Unit,
    onToggleBluetooth: () -> Unit,
    onClearCache: () -> Unit
) {
    val context = LocalContext.current
    var filenameMode by remember { mutableStateOf(PreferencesManager.getFilenameMode(context)) }
    var customFilenameInput by remember { mutableStateOf(PreferencesManager.getCustomFilename(context)) }
    var pinnedDeviceName by remember { mutableStateOf(PreferencesManager.getPinnedDeviceName(context)) }
    var showDevicePickerDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Bluetooth Quick Status
        item {
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
        }

        // Pinned Device Configuration
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$ PINNED DEVICE CONFIGURATION",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Pinned Target: [${pinnedDeviceName.ifBlank { "none" }}]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showDevicePickerDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RectangleShape
                        ) {
                            Text("SELECT DEVICE", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                        if (pinnedDeviceName.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    PreferencesManager.clearPinnedDevice(context)
                                    pinnedDeviceName = ""
                                },
                                shape = RectangleShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            ) {
                                Text("CLEAR", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Theme Selection (Brutalist TUI Theme Selector)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "APPEARANCE / THEME",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            val isSelected = (currentThemeMode == mode)
                            val darkTheme = isSystemInDarkTheme()
                            val swatches = getThemeSwatches(mode, darkTheme, context)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(onClick = { onThemeModeSelect(mode) }),
                                shape = RectangleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh
                                       else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Swatch Strip
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            swatches.forEach { swatchColor ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 8.dp, height = 20.dp)
                                                        .background(swatchColor)
                                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                                )
                                            }
                                        }

                                        Column {
                                            Text(
                                                text = mode.displayName,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = mode.subtitle,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Text(
                                            text = "[ ACTIVE ]",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filename Mode Configuration
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$ FILENAME MODE CONFIGURATION",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = {
                                PreferencesManager.setFilenameMode(context, FilenameMode.TIMESTAMP)
                                filenameMode = FilenameMode.TIMESTAMP
                            })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (filenameMode == FilenameMode.TIMESTAMP),
                            onClick = {
                                PreferencesManager.setFilenameMode(context, FilenameMode.TIMESTAMP)
                                filenameMode = FilenameMode.TIMESTAMP
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Automatic Timestamp Mode", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Pattern: clipboard-YYYY-MM-DD-HHmm.md", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = {
                                PreferencesManager.setFilenameMode(context, FilenameMode.CUSTOM)
                                filenameMode = FilenameMode.CUSTOM
                            })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (filenameMode == FilenameMode.CUSTOM),
                            onClick = {
                                PreferencesManager.setFilenameMode(context, FilenameMode.CUSTOM)
                                filenameMode = FilenameMode.CUSTOM
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Custom Fixed Filename Mode", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    if (filenameMode == FilenameMode.CUSTOM) {
                        OutlinedTextField(
                            value = customFilenameInput,
                            onValueChange = { input ->
                                customFilenameInput = input
                                PreferencesManager.setCustomFilename(context, input)
                            },
                            label = { Text("Filename (e.g. clipboard.md)", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RectangleShape,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
        }

        // Cache Clean Button
        item {
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
        }

        // About Terminal Box
        item {
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
                            "* Direct Bluetooth OBEX Client Stream\n" +
                            "* Material You, Gruvbox, Moss, Paper & Water\n" +
                            "* Timestamp & Custom Filename Modes\n" +
                            "* Dynamic Device Pinning\n" +
                            "* Native Wayland / Arch obexd friendly",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showDevicePickerDialog) {
        DeviceSelectionDialog(
            bluetoothAdapter = bluetoothAdapter,
            hasBluetoothPermission = hasBluetoothPermission,
            onRequestPermission = onRequestBluetoothPermission,
            onDismiss = { showDevicePickerDialog = false },
            onSelectDevice = { name, address ->
                PreferencesManager.setPinnedDevice(context, name, address)
                pinnedDeviceName = name
                showDevicePickerDialog = false
            },
            onClearPin = {
                PreferencesManager.clearPinnedDevice(context)
                pinnedDeviceName = ""
                showDevicePickerDialog = false
            }
        )
    }
}

// --- PREFERENCES & CONFIGURATION MANAGER ---

object PreferencesManager {
    private const val PREFS_NAME = "bluebridge_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_FILENAME_MODE = "filename_mode"
    private const val KEY_CUSTOM_FILENAME = "custom_filename"
    private const val KEY_PINNED_NAME = "pinned_device_name"
    private const val KEY_PINNED_ADDRESS = "pinned_device_address"

    fun getThemeMode(context: Context): AppThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME, AppThemeMode.GRUVBOX_DARK.name)
        return try {
            AppThemeMode.valueOf(name ?: AppThemeMode.GRUVBOX_DARK.name)
        } catch (_: Exception) {
            AppThemeMode.GRUVBOX_DARK
        }
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_THEME, mode.name)
        }
    }

    fun getFilenameMode(context: Context): FilenameMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_FILENAME_MODE, FilenameMode.TIMESTAMP.name)
        return try {
            FilenameMode.valueOf(name ?: FilenameMode.TIMESTAMP.name)
        } catch (_: Exception) {
            FilenameMode.TIMESTAMP
        }
    }

    fun setFilenameMode(context: Context, mode: FilenameMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_FILENAME_MODE, mode.name)
        }
    }

    fun getCustomFilename(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_FILENAME, "clipboard.md") ?: "clipboard.md"
    }

    fun setCustomFilename(context: Context, filename: String) {
        val sanitized = sanitizeFilename(filename)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_CUSTOM_FILENAME, sanitized)
        }
    }

    fun getPinnedDeviceName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PINNED_NAME, "") ?: ""
    }

    fun getPinnedDeviceAddress(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PINNED_ADDRESS, "") ?: ""
    }

    fun setPinnedDevice(context: Context, name: String, address: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_PINNED_NAME, name)
            putString(KEY_PINNED_ADDRESS, address)
        }
    }

    fun clearPinnedDevice(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_PINNED_NAME)
            remove(KEY_PINNED_ADDRESS)
        }
    }

    private fun sanitizeFilename(filename: String): String {
        var clean = filename.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
        if (clean.isBlank()) {
            clean = "clipboard.md"
        }
        if (!clean.endsWith(".md", ignoreCase = true)) {
            if (clean.endsWith(".txt", ignoreCase = true)) {
                clean = clean.substring(0, clean.length - 4)
            }
            clean += ".md"
        }
        return clean
    }
}

enum class FilenameMode {
    TIMESTAMP,
    CUSTOM
}

// --- DIRECT BLUETOOTH OBEX OPP CLIENT & SHARING FUNCTIONS ---

private val OBEX_OPP_UUID: UUID = UUID.fromString("00001105-0000-1000-8000-00805F9B34FB")

@android.annotation.SuppressLint("MissingPermission")
private fun sendDirectObexFile(
    context: Context,
    text: String,
    targetName: String,
    targetAddress: String,
    bluetoothAdapter: BluetoothAdapter?,
    hasBluetoothPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStatusMessage: (AppStatus) -> Unit,
    scope: CoroutineScope
) {
    if (text.isBlank()) {
        onStatusMessage(AppStatus.Warning("Cannot share empty text."))
        return
    }
    if (bluetoothAdapter == null) {
        onStatusMessage(AppStatus.Error("Device does not support Bluetooth."))
        return
    }
    if (!bluetoothAdapter.isEnabled) {
        onStatusMessage(AppStatus.Warning("Bluetooth is off"))
        return
    }
    if (!hasBluetoothPermission) {
        onRequestPermission()
        return
    }
    if (targetAddress.isBlank()) {
        onStatusMessage(AppStatus.Warning("No device address pinned. Long-press button to select target."))
        return
    }

    @Suppress("DEPRECATION")
    val device: BluetoothDevice = try {
        bluetoothAdapter.getRemoteDevice(targetAddress)
    } catch (_: Exception) {
        onStatusMessage(AppStatus.Error("Invalid Bluetooth address: $targetAddress"))
        return
    }

    scope.launch(Dispatchers.IO) {
        try {
            val file = createMarkdownFileFromText(context, text)
            val fileName = file.name
            val fileBytes = file.readBytes()

            @Suppress("DEPRECATION")
            try {
                bluetoothAdapter.cancelDiscovery()
            } catch (_: SecurityException) {}

            @Suppress("DEPRECATION")
            val socket = device.createRfcommSocketToServiceRecord(OBEX_OPP_UUID)
            socket.connect()

            val out = socket.outputStream
            val input = socket.inputStream

            // 1. OBEX CONNECT (0x80)
            val connectPacket = byteArrayOf(
                0x80.toByte(), 0x00.toByte(), 0x07.toByte(),
                0x10.toByte(), 0x00.toByte(), 0x20.toByte(), 0x00.toByte()
            )
            out.write(connectPacket)
            out.flush()

            val connectResp = ByteArray(256)
            val bytesRead = input.read(connectResp)
            if (bytesRead <= 0 || connectResp[0] != 0xA0.toByte()) {
                try { socket.close() } catch (_: Exception) {}
                withContext(Dispatchers.Main) {
                    onStatusMessage(AppStatus.Error("OBEX connection rejected by $targetName."))
                }
                return@launch
            }

            // 2. OBEX PUT FINAL PACKET (0x82)
            val nameBytes = fileName.toByteArray(Charsets.UTF_16BE)
            val nameHeaderLen = nameBytes.size + 5
            val nameHeader = ByteArray(nameHeaderLen)
            nameHeader[0] = 0x01.toByte()
            nameHeader[1] = (nameHeaderLen shr 8).toByte()
            nameHeader[2] = (nameHeaderLen and 0xFF).toByte()
            System.arraycopy(nameBytes, 0, nameHeader, 3, nameBytes.size)
            nameHeader[nameHeaderLen - 2] = 0x00.toByte()
            nameHeader[nameHeaderLen - 1] = 0x00.toByte()

            val fileLen = fileBytes.size
            val lengthHeader = byteArrayOf(
                0xC3.toByte(),
                (fileLen shr 24).toByte(),
                (fileLen shr 16).toByte(),
                (fileLen shr 8).toByte(),
                (fileLen and 0xFF).toByte()
            )

            val typeStr = "text/markdown\u0000"
            val typeBytes = typeStr.toByteArray(Charsets.ISO_8859_1)
            val typeHeaderLen = typeBytes.size + 3
            val typeHeader = ByteArray(typeHeaderLen)
            typeHeader[0] = 0x42.toByte()
            typeHeader[1] = (typeHeaderLen shr 8).toByte()
            typeHeader[2] = (typeHeaderLen and 0xFF).toByte()
            System.arraycopy(typeBytes, 0, typeHeader, 3, typeBytes.size)

            val bodyHeaderLen = fileBytes.size + 3
            val bodyHeader = ByteArray(bodyHeaderLen)
            bodyHeader[0] = 0x49.toByte()
            bodyHeader[1] = (bodyHeaderLen shr 8).toByte()
            bodyHeader[2] = (bodyHeaderLen and 0xFF).toByte()
            System.arraycopy(fileBytes, 0, bodyHeader, 3, fileBytes.size)

            val totalPutLen = 3 + nameHeader.size + lengthHeader.size + typeHeader.size + bodyHeader.size
            val putPacket = ByteArray(totalPutLen)
            putPacket[0] = 0x82.toByte()
            putPacket[1] = (totalPutLen shr 8).toByte()
            putPacket[2] = (totalPutLen and 0xFF).toByte()

            var offset = 3
            System.arraycopy(nameHeader, 0, putPacket, offset, nameHeader.size)
            offset += nameHeader.size
            System.arraycopy(lengthHeader, 0, putPacket, offset, lengthHeader.size)
            offset += lengthHeader.size
            System.arraycopy(typeHeader, 0, putPacket, offset, typeHeader.size)
            offset += typeHeader.size
            System.arraycopy(bodyHeader, 0, putPacket, offset, bodyHeader.size)

            out.write(putPacket)
            out.flush()

            val putResp = ByteArray(256)
            val putRead = input.read(putResp)

            val disconnectPacket = byteArrayOf(0x81.toByte(), 0x00.toByte(), 0x03.toByte())
            try { out.write(disconnectPacket); out.flush() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                if (putRead > 0 && putResp[0] == 0xA0.toByte()) {
                    onStatusMessage(AppStatus.Success("Successfully sent to $targetName"))
                } else {
                    onStatusMessage(AppStatus.Error("File transfer rejected or failed on $targetName."))
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onStatusMessage(AppStatus.Error("OBEX transfer to $targetName failed: ${e.localizedMessage ?: "Connection refused"}."))
            }
        }
    }
}

private fun createMarkdownFileFromText(context: Context, text: String): File {
    val mode = PreferencesManager.getFilenameMode(context)
    val file: File = if (mode == FilenameMode.CUSTOM) {
        val customName = PreferencesManager.getCustomFilename(context)
        File(context.cacheDir, customName)
    } else {
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(Date())
        val baseName = "clipboard-$timestamp"
        var tempFile = File(context.cacheDir, "$baseName.md")
        var counter = 1
        while (tempFile.exists() && counter < 100) {
            tempFile = File(context.cacheDir, "${baseName}_$counter.md")
            counter++
        }
        tempFile
    }
    file.writeText(text)
    return file
}

private fun shareTextAsFileGeneral(context: Context, text: String, onStatusMessage: (AppStatus) -> Unit) {
    if (text.isBlank()) {
        onStatusMessage(AppStatus.Warning("Cannot share empty text."))
        return
    }
    try {
        val file = createMarkdownFileFromText(context, text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "text/plain"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "Share Markdown via...")
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        onStatusMessage(AppStatus.Error("Sharing failed: ${e.localizedMessage ?: "Unknown error"}"))
    }
}

private fun shareFile(context: Context, uri: Uri, mimeType: String? = null, onStatusMessage: (AppStatus) -> Unit) {
    try {
        val type = mimeType ?: context.contentResolver.getType(uri) ?: "*/*"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            this.type = type
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(shareIntent, "Share via...")
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        onStatusMessage(AppStatus.Error("File sharing failed: ${e.localizedMessage ?: "Unknown error"}"))
    }
}

private fun shareMultipleFiles(context: Context, uris: ArrayList<Uri>, onStatusMessage: (AppStatus) -> Unit) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(shareIntent, "Share files via...")
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        onStatusMessage(AppStatus.Error("Multiple file sharing failed: ${e.localizedMessage ?: "Unknown error"}"))
    }
}

private fun clearCache(context: Context, onStatusMessage: (AppStatus) -> Unit) {
    val cacheDir = context.cacheDir
    val files = cacheDir.listFiles()
    var deletedCount = 0
    files?.forEach { file ->
        if ((file.name.startsWith("clip_") || file.name.startsWith("clipboard")) && file.delete()) {
            deletedCount++
        }
    }
    onStatusMessage(AppStatus.Success("Deleted $deletedCount cached file(s)."))
}
