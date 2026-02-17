package com.example.brrbox

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.controls.actions.CommandAction
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.brrbox.ui.theme.BRRBOXTheme
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    // Status states
    private var statusText = mutableStateOf("Disconnected")
    private var isConnected = mutableStateOf(false)
    private var debugLog = mutableStateOf(mutableListOf<String>())
    private val discoveredDevices = mutableSetOf<String>()

    private var showTemperatureDialog = mutableStateOf(false)

    // BLE UUIDs - need to update
    private val SERVICE_UUID = UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455")
    private val RX_CHARACTERISTIC_UUID = UUID.fromString("49535343-8841-43F4-A8D4-ECBE34729BB3")
    private val TX_CHARACTERISTIC_UUID = UUID.fromString("49535343-1E4D-4BD9-BA61-23C647249616")

    private val BRRBOX_MAC = "04:91:62:94:C6:F5"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            addLog("Ready to connect.")
        } else {
            addLog("Permissions required")
        }
    }

    private fun addLog(message: String) {
        val currentLog = debugLog.value.toMutableList()
        currentLog.add(0, message) // Add to beginning
        if (currentLog.size > 50) { // Keep last 20 logs
            currentLog.removeAt(currentLog.lastIndex)
        }
        debugLog.value = currentLog
        android.util.Log.d("BRRBOX", message)
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            result?.device?.let { device ->
                val deviceName = device.name ?: "Unknown"
                val deviceAddress = device.address
                val rssi = result.rssi

                // ✔ Only log NEW MAC addresses
                if (!discoveredDevices.contains(deviceAddress)) {
                    discoveredDevices.add(deviceAddress)
                    addLog("Found: $deviceName ($deviceAddress) RSSI: $rssi dBm")
                }

                // Existing logic: connect immediately if it's BRRBOX
                if (device.address.equals(BRRBOX_MAC, ignoreCase = true)) {
                    bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
                    addLog("Connecting to BRRBOX...")
                    bluetoothGatt = device.connectGatt(this@MainActivity, false, gattCallback)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            addLog("Scan failed with error code: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    addLog("Connected! Discovering services...")
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        gatt?.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected.value = false
                    addLog("Disconnected from device")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("Connected to device, ready to send messages.")
                isConnected.value = true

                // Log all discovered services and characteristics
                gatt?.services?.forEach { service ->
                    addLog("Service: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        addLog("  Char: ${char.uuid}")
                    }
                }
            } else {
                addLog("Service discovery failed: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("Command sent successfully")
            } else {
                addLog("Command failed with status: $status")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Composable
    fun CommandScreen(modifier: Modifier = Modifier) {
        Scaffold (
            modifier = Modifier.fillMaxSize(),
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "BRRBOX Controller",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { sendCommand("U") },
                        enabled = isConnected.value,
                        modifier = Modifier.weight(12f)
                    ) {
                        Text("Unlock")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { sendCommand("L") },
                        enabled = isConnected.value,
                        modifier = Modifier.weight(12f)
                    ) {
                        Text("Lock")
                    }
                }
                Button(
                    onClick = { showTemperatureDialog.value = true },
                    enabled = isConnected.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Temperature")
                }

                Button(
                    onClick = { sendCommand("D") },
                    enabled = isConnected.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Get Logging Data")
                }
            }
        }
        if (showTemperatureDialog.value) {
            TemperatureDialog(
                onDismiss = { showTemperatureDialog.value = false },
                onConfirm = { temp ->
                    sendCommand("T$temp")
                    showTemperatureDialog.value = false
                }
            )
        }
    }
    @Composable
    fun BluetoothScreen(modifier: Modifier = Modifier) {
        Scaffold (
            modifier = Modifier.fillMaxSize()
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Bluetooth Pairing",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = { connectToBRRBOX() },
                    enabled = !isConnected.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect to BRRBOX")
                }

                Button(
                    onClick = { disconnect() },
                    enabled = isConnected.value,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Disconnect from BRRBOX")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    @Composable
    fun TempDataScreen(modifier: Modifier = Modifier) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("We out here")
        }
    }
    @Composable
    fun LoginScreen(modifier: Modifier = Modifier) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("To be implemented!")
        }
    }
    @Composable
    fun DebugScreen(modifier: Modifier = Modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Debug",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Button(
                    onClick = { debugConnect() },
                    enabled = !isConnected.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect to BRRBOX Debug")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Debug Logs",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        reverseLayout = false
                    ) {
                        items(debugLog.value.size) { index ->
                            Text(
                                debugLog.value[index],
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                            if (index < debugLog.value.size - 1) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        debugLog.value = mutableListOf()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear Log")
                }
            }
        }
    }

    enum class Destination(
        val route: String,
        val label: String,
        val icon: ImageVector,
        val contentDescription: String
    ){
        COMMAND("sendcommand", "Control", Icons.Default.AcUnit,"Control the Device"),
        BLUETOOTH("bluetooth", "Bluetooth",Icons.Default.Bluetooth,"Bluetooth Connection"),
        TEMPDATA("data", "Logs",Icons.Default.Archive,"View Temperature Logs"),
        LOGIN("login", "Account",Icons.Default.AccountCircle,"Login to User Account"),
        DEBUG("debug", "Debug",Icons.Default.Terminal,"Debug Logs"),
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        requestBluetoothPermissions()
        
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
        
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Composable
    fun AppNavHost(
        navController: NavHostController,
        startDestination: Destination,
        modifier: Modifier = Modifier
    ) {
        NavHost(
            navController,
            startDestination = startDestination.route
        ) {
            Destination.entries.forEach { destination ->
                composable(destination.route) {
                    when (destination) {
                        Destination.COMMAND -> CommandScreen()
                        Destination.BLUETOOTH -> BluetoothScreen()
                        Destination.TEMPDATA -> TempDataScreen()
                        Destination.LOGIN -> LoginScreen()
                        Destination.DEBUG -> DebugScreen()
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Composable
    fun MainScreen(modifier: Modifier = Modifier) {
        val navController = rememberNavController()
        val startDestination = Destination.COMMAND
        var selectedDestination by rememberSaveable {mutableIntStateOf(startDestination.ordinal)}

        Scaffold(
            modifier = modifier,
            bottomBar = {
                NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
                    Destination.entries.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = selectedDestination == index,
                            onClick = {
                                navController.navigate(route = destination.route)
                                selectedDestination = index
                            },
                            icon = {
                                Icon(
                                    destination.icon,
                                    contentDescription = destination.contentDescription
                                )
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        ) { contentPadding ->
            AppNavHost(navController, startDestination, modifier = Modifier.padding(contentPadding))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun requestBluetoothPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    fun connectToBRRBOX() {
        discoveredDevices.clear()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            addLog("Bluetooth permission is required.")
            return
        }

        // Try bonded first
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            if (device.address.equals(BRRBOX_MAC, ignoreCase = true)) {
                addLog("Found bonded BRRBOX - Connecting...")
                bluetoothGatt = device.connectGatt(this, false, gattCallback)
                return
            }
        }

        // Fall back to scanning
        addLog("Scanning for devices...")
        val scanSettings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bluetoothAdapter?.bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            }
            if (!isConnected.value) {
                addLog("BRRBOX not found")
            }
        }, 10000)
    }

    fun debugConnect() {
        isConnected.value = !isConnected.value
        addLog(if (isConnected.value) "Debug Mode - Connected (Fake)" else "Debug Mode - Disconnected")
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun sendCommand(command: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        addLog("Sending message...")

        val service = bluetoothGatt?.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(RX_CHARACTERISTIC_UUID)

        if (characteristic != null) {
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                command.toByteArray(),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            addLog("Error: Service not found")
        }
    }

    @Composable
    fun TemperatureDialog(
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit
    ) {
        var temperature by remember { mutableStateOf("32") }
        val radioOptions = listOf("°F", "°C")
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(radioOptions[0]) }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        fun getTempInCelsius(): String {
            val tempValue = temperature.toFloatOrNull() ?: 0f
            val celsius = if (selectedOption == "°F") {
                (tempValue - 32) * 5f / 9f
            } else {
                tempValue
            }
            return String.format(Locale.US,"%.1f", celsius)
        }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        focusManager.clearFocus()
                    },
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(375.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Input a Temperature.",
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )

                        OutlinedTextField(
                            value = temperature,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.matches(Regex("^-?\\d*\\.?\\d*$"))) {
                                    temperature = newValue
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            ),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            radioOptions.forEach { option ->
                                Row(
                                    Modifier
                                        .height(56.dp)
                                        .selectable(
                                            selected = (option == selectedOption),
                                            onClick = {
                                                if (option != selectedOption) {
                                                    val tempValue = temperature.toFloatOrNull()
                                                    if (tempValue != null) {
                                                        val converted = if (selectedOption == "°F") {
                                                            (tempValue - 32) * 5f / 9f
                                                        } else {
                                                            tempValue * 9f / 5f + 32
                                                        }
                                                        temperature = String.format(Locale.US,"%.1f", converted)
                                                    }
                                                    onOptionSelected(option)
                                                }
                                            },
                                            role = Role.RadioButton
                                        )
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (option == selectedOption),
                                        onClick = null
                                    )
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(
                                onClick = {
                                    temperature = if (selectedOption == "°F") "32" else "0"
                                },
                                modifier = Modifier.padding(8.dp),
                            ) {
                                Text("Reset")
                            }
                            TextButton(
                                onClick = {
                                    onConfirm(getTempInCelsius())
                                },
                                modifier = Modifier.padding(8.dp),
                            ) {
                                Text("Set")
                            }
                            TextButton(
                                onClick = { onDismiss() },
                                modifier = Modifier.padding(8.dp),
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }

    fun disconnect() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        addLog("Disconnecting...")
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected.value = false
        addLog("Disconnected")
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}