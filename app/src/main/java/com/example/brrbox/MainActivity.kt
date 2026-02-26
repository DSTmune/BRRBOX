package com.example.brrbox

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.brrbox.ui.theme.BRRBOXTheme
import com.github.mikephil.charting.data.LineDataSet
import java.util.Locale
import java.util.UUID
import androidx.core.graphics.toColorInt
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    // Status states
    private var isConnected = mutableStateOf(false)
    private var isConnecting = mutableStateOf(false)
    private var debugLog = mutableStateOf(mutableListOf<String>())
    private val discoveredDevices = mutableSetOf<String>()

    private var showTemperatureDialog = mutableStateOf(false)
    private var showLoggingDialog = mutableStateOf(false)
    private var receivingLoggingData = mutableStateOf(false)

    private var currentTempCelsius = mutableStateOf(0f)
    private var logEntries = mutableStateListOf<Entry>()

    // BLE UUIDs - need to update
    private val SERVICE_UUID = UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455")
    private val RX_CHARACTERISTIC_UUID = UUID.fromString("49535343-8841-43F4-A8D4-ECBE34729BB3")
    private val TX_CHARACTERISTIC_UUID = UUID.fromString("49535343-1E4D-4BD9-BA61-23C647249616")

    private val BRRBOX_MAC = "40:84:32:01:3B:28"

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
        currentLog.add(0, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))+"  "+message) // Add to beginning
        if (currentLog.size > 100) { // Keep last 20 logs
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
                    isConnecting.value = false
                    isConnected.value = false
                    addLog("Disconnected from device")
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("Connected to device, ready to send messages.")
                simpleAlert("Connected!")
                isConnecting.value = false
                isConnected.value = true

                // Log all discovered services and characteristics
                gatt?.services?.forEach { service ->
                    addLog("Service: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        addLog("  Char: ${char.uuid}")
                    }
                }

                val service = gatt?.getService(SERVICE_UUID)
                val txChar = service?.getCharacteristic(TX_CHARACTERISTIC_UUID)

                if (txChar != null) {
                    gatt.setCharacteristicNotification(txChar, true)

                    // Step 2: tell the BRRBOX to actually start sending
                    val descriptor = txChar.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // standard CCCD UUID
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                    addLog("Subscribed to TX notifications")
                } else {
                    addLog("TX characteristic not found!")
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

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val message = value.toString(Charsets.UTF_8)
            addLog("From BRRBOX: $message")

            // Handle single-byte status codes
            if (value.size == 1) {
                when (value[0].toInt() and 0xFF) {
                    0x00 -> simpleAlert("Message received!")
                    0x01 -> simpleAlert("Connected to BRRBOX!")
                    0x02 -> simpleAlert("Device locked successfully.")
                    0x03 -> simpleAlert("Device unlocked successfully.")
                    0x04 -> simpleAlert("Temperature set successfully.")
                    0x10 -> simpleAlert("Warning: Low battery!")
                    0x11 -> {
                        simpleAlert("Receiving log data...")
                        receivingLoggingData.value = true
                        logEntries.clear()
                    }
                    0x12 -> {
                        receivingLoggingData.value = false
                    }
                    0xE0 -> simpleAlert("Error received from BRRBOX.")
                    else -> addLog("Unknown status code: 0x${value[0].toInt().and(0xFF).toString(16).uppercase()}")
                }
                return
            }

            if (receivingLoggingData.value && message.matches(Regex("T\\d{2}:\\d{2}:\\d{2},-?\\d+\\.?\\d*"))) {
                val (time, temp) = message.removePrefix("T").split(",")
                val hours = time.split(":").let { it[0].toFloat() + it[1].toFloat() / 60 }
                val temperature = temp.toFloat()
                logEntries.add(Entry(hours, temperature))
            }

            if (message.startsWith("M")) {
                currentTempCelsius.value = message.removePrefix("M").toFloatOrNull() ?: currentTempCelsius.value
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
    }    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Composable
    fun MonitorScreen(modifier: Modifier = Modifier) {
        Scaffold(
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
                    "Current Temperature",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { sendCommand("M") },
                    enabled = isConnected.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Get Live Temperature")
                }
                Spacer(modifier = Modifier.height(32.dp))
                ThermometerGraphic(
                    temperatureCelsius = currentTempCelsius.value,
                    minTemp = -20f,
                    maxTemp = 50f,
                    useFahrenheit = true,
                    thermometerHeight = 350.dp  // or however big you want
                )
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Composable
    fun TempDataScreen(modifier: Modifier = Modifier) {
        val entries = logEntries.toList()
        Scaffold(
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
                    "Data Logging",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                Spacer(modifier = Modifier.height(24.dp))

                AndroidView(
                    factory = { context ->

                        LineChart(context).apply {
                            xAxis.apply {
                                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                                granularity = 1f
                                setLabelCount(6, false)
                                textColor = android.graphics.Color.GRAY
                                gridColor = android.graphics.Color.LTGRAY
                                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                                    override fun getFormattedValue(value: Float): String {
                                        val h = value.toInt() % 24
                                        return when {
                                            h == 0  -> "12 AM"
                                            h < 12  -> "$h AM"
                                            h == 12 -> "12 PM"
                                            else    -> "${h - 12} PM"
                                        }
                                    }
                                }
                            }

                            axisLeft.apply {
                                textColor = android.graphics.Color.GRAY
                                gridColor = android.graphics.Color.LTGRAY
                                axisMinimum = 10f
                                axisMaximum = 32f
                                granularity = 5f
                                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                                    override fun getFormattedValue(value: Float) = "${value.toInt()}°C"
                                }
                            }

                            axisRight.isEnabled = false
                            description.isEnabled = false
                            legend.textColor = android.graphics.Color.GRAY
                            setTouchEnabled(true)
                            isDragEnabled = true
                            setScaleEnabled(true)
                            setPinchZoom(true)
                            setExtraOffsets(8f, 8f, 8f, 8f)
                            animateX(1000)
                        }
                    },
                    update = { chart ->
                        val dataSet = LineDataSet(entries, "Temperature (°C)").apply {
                        color = "#1C86FF".toColorInt()
                        setCircleColor("#1C86FF".toColorInt())
                        circleRadius = 3f
                        circleHoleRadius = 1.5f
                        circleHoleColor = android.graphics.Color.WHITE
                        lineWidth = 2f
                        setDrawValues(false)
                        setDrawFilled(true)
                        fillColor = "#1C86FF".toColorInt()
                        fillAlpha = 40
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        }
                        chart.data = LineData(dataSet)
                        chart.notifyDataSetChanged()
                        chart.invalidate()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            showLoggingDialog.value = true
                                  },
                        modifier = Modifier.weight(12f)
                    ) {
                        Text("Get Logging Data")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.weight(12f)
                    ) {
                        Text("Save Logging Data")
                    }
                }
            }
        }
        if (showLoggingDialog.value) {
            GlobalAlertDialog(
                {
                    showLoggingDialog.value = false
                },
                {
                    showLoggingDialog.value = false
                    if (bluetoothGatt == null && isConnected.value) {
                        // debug case with fake data
                        logEntries.clear()

                        val intervalsPerDay = 24 * 6

                        repeat(intervalsPerDay) { index ->
                            val minutes = index * 10
                            val xValue = minutes / 60f
                            val yValue = (15f + Math.random() * 10).toFloat()

                            logEntries.add(Entry(xValue, yValue))
                        }
                    } else {
                        sendCommand("D")
                    }
                },
                "Get Logs",
                "Where do you want to retrieve logging data?",
                "BRRBOX",
                "Internal Storage",
                Icons.Default.FileOpen
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
                    enabled = !isConnected.value && !isConnecting.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isConnecting.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting...")
                    } else {
                        Text("Connect to BRRBOX")
                    }
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
    fun LoginScreen(modifier: Modifier = Modifier) {
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var currentLogin = remember { mutableStateOf<String?>(null) }
        var visible by remember { mutableStateOf(false) }

        Scaffold(
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
                    "Account Login",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(if (currentLogin.value != null) "Signed in as ${currentLogin.value}" else "Not signed in")

                Spacer(modifier = Modifier.height(48.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Wrap in a Box so the icon sits inside/at the end of the field
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { visible = !visible }) {
                                Icon(
                                    imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (visible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            currentLogin.value = username
                            username = ""
                            password = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sign In")
                    }
                    Button(
                        onClick = { currentLogin.value = null },
                        enabled = currentLogin.value != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Log Out")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
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
        MONITOR("monitor", "Temp",Icons.Default.Thermostat,"View Current Device Temperature"),
        TEMPDATA("data", "Logs",Icons.Default.Archive,"View Temperature Logs"),
        BLUETOOTH("bluetooth", "Bluetooth",Icons.Default.Bluetooth,"Bluetooth Connection"),
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
                        Destination.MONITOR -> MonitorScreen()
                        Destination.TEMPDATA -> TempDataScreen()
                        Destination.BLUETOOTH -> BluetoothScreen()
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

        isConnecting.value = true

        // Try bonded first
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            if (device.address.equals(BRRBOX_MAC, ignoreCase = true)) {
                addLog("Found bonded BRRBOX - Connecting...")
                bluetoothGatt = device.connectGatt(this, false, gattCallback)
                return
            }
        }

        // Fall back to scanning
        simpleAlert("Searching...")
        addLog("Scanning for devices...")
        val scanSettings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            bluetoothAdapter?.bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
        } catch (e: SecurityException) {
            addLog("SecurityException on scan: ${e.message}")
            simpleAlert("Permission error. Check if location and bluetooth are enabled!")
            return
        } catch (e: Exception) {
            addLog("Scan error: ${e.message}")
            return
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            }
            if (!isConnected.value) {
                isConnecting.value = false
                simpleAlert("BRRBOX not found!")
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

    @Composable
    fun GlobalAlertDialog(
        onDismissRequest: () -> Unit,
        onConfirmation: () -> Unit,
        dialogTitle: String,
        dialogText: String,
        confirmText: String,
        dismissText: String,
        icon: ImageVector,
    ) {
        AlertDialog(
            icon = {
                Icon(icon, contentDescription = "Example Icon")
            },
            title = {
                Text(text = dialogTitle)
            },
            text = {
                Text(text = dialogText)
            },
            onDismissRequest = {
                onDismissRequest()
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirmation()
                    }
                ) {
                    Text(confirmText)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismissRequest()
                    }
                ) {
                    Text(dismissText)
                }
            }
        )
    }

    fun simpleAlert(message: String){
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun ThermometerGraphic(
        temperatureCelsius: Float,
        minTemp: Float = -20f,
        maxTemp: Float = 50f,
        useFahrenheit: Boolean = false,
        thermometerHeight: Dp = 200.dp,
        modifier: Modifier = Modifier
    ) {
        val thermometerWidth = thermometerHeight / 4
        val displayTemp = if (useFahrenheit) temperatureCelsius * 9f / 5f + 32f else temperatureCelsius
        val secondaryTemp = if (useFahrenheit) temperatureCelsius else temperatureCelsius * 9f / 5f + 32f
        val primaryUnit = if (useFahrenheit) "°F" else "°C"
        val secondaryUnit = if (useFahrenheit) "°C" else "°F"
        val secondaryValue = if (useFahrenheit) temperatureCelsius else temperatureCelsius * 9f / 5f + 32f

        val fraction = ((temperatureCelsius - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
        val animatedFraction by animateFloatAsState(
            targetValue = fraction,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "thermometer"
        )

        val color by animateColorAsState(
            targetValue = when {
                temperatureCelsius < 0f  -> Color(0xFF4FC3F7)
                temperatureCelsius < 15f -> Color(0xFF81C784)
                temperatureCelsius < 28f -> Color(0xFFFFB74D)
                else                     -> Color(0xFFE53935)
            },
            animationSpec = tween(600),
            label = "thermometerColor"
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            // Thermometer stem + bulb
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Canvas(
                    modifier = Modifier
                        .width(thermometerWidth)
                        .height(thermometerHeight)
                ) {
                    val stemWidth = size.width * 0.28f
                    val bulbRadius = size.width * 0.42f
                    val stemLeft = (size.width - stemWidth) / 2f
                    val stemRight = (size.width + stemWidth) / 2f
                    val bulbCenterY = size.height - bulbRadius
                    val stemBottom = bulbCenterY - bulbRadius * 0.6f
                    val stemTop = 12f

                    drawRoundRect(
                        color = Color.LightGray.copy(alpha = 0.4f),
                        topLeft = Offset(stemLeft, stemTop),
                        size = Size(stemWidth, stemBottom - stemTop),
                        cornerRadius = CornerRadius(stemWidth / 2f)
                    )

                    val fillHeight = (stemBottom - stemTop) * animatedFraction
                    val fillTop = stemBottom - fillHeight
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(stemLeft, fillTop),
                        size = Size(stemWidth, fillHeight + stemWidth / 2f),
                        cornerRadius = CornerRadius(stemWidth / 2f)
                    )

                    for (i in 0..7) {
                        val tickY = stemBottom - (stemBottom - stemTop) * (i.toFloat() / 7)
                        val isLong = i % 2 == 0
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.5f),
                            start = Offset(stemRight + 4f, tickY),
                            end = Offset(stemRight + if (isLong) 16f else 10f, tickY),
                            strokeWidth = if (isLong) 2f else 1f
                        )
                    }

                    drawCircle(
                        color = Color.LightGray.copy(alpha = 0.4f),
                        radius = bulbRadius,
                        center = Offset(size.width / 2f, bulbCenterY)
                    )
                    drawCircle(
                        color = color,
                        radius = bulbRadius * 0.85f,
                        center = Offset(size.width / 2f, bulbCenterY)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f),
                        radius = bulbRadius * 0.3f,
                        center = Offset(size.width / 2f - bulbRadius * 0.25f, bulbCenterY - bulbRadius * 0.25f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.width(thermometerWidth),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${minTemp.toInt()}°", fontSize = 9.sp, color = Color.Gray)
                    Text("${maxTemp.toInt()}°", fontSize = 9.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Labels to the right, vertically centered
            Column {
                Text(
                    text = "${String.format(Locale.US, "%.1f", displayTemp)}$primaryUnit",
                    fontSize = (thermometerHeight.value / 6).sp,   // e.g. 350dp → ~58sp
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", secondaryValue)}$secondaryUnit",
                    fontSize = (thermometerHeight.value / 14).sp,  // e.g. 350dp → ~25sp
                    color = color.copy(alpha = 0.7f)
                )
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