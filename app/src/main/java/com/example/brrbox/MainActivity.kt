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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.brrbox.ui.theme.BRRBOXTheme
import com.github.mikephil.charting.data.LineDataSet
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import java.util.Locale
import java.util.UUID
import androidx.core.graphics.toColorInt
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private val receiveBuffer = StringBuilder()

    // Status states
    private var isConnected = mutableStateOf(false)
    private var isConnecting = mutableStateOf(false)
    private var isScanning = mutableStateOf(false)
    private var debugLog = mutableStateOf(mutableListOf<String>())
    private val discoveredDevices = mutableSetOf<String>()
    private val discoveredBRRBOXList = mutableStateListOf<ScannedDevice>()
    private val currentDeviceName = mutableStateOf<String?>(null)

    private var showTemperatureDialog = mutableStateOf(false)
    private var showLoggingDialog = mutableStateOf(false)
    private var showSaveDialog = mutableStateOf(false)
    private var showGetSavedLogDialog = mutableStateOf(false)

    private var receivingLoggingData = mutableStateOf(false)

    private var currentTempCelsius = mutableStateOf(0f)
    private var logEntries = mutableStateListOf<Entry>()

    private val docDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

    // BLE UUID
    private val SERVICE_UUID = UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455")
    private val RX_CHARACTERISTIC_UUID = UUID.fromString("49535343-8841-43F4-A8D4-ECBE34729BB3")
    private val TX_CHARACTERISTIC_UUID = UUID.fromString("49535343-1E4D-4BD9-BA61-23C647249616")

    // To be removed.
    private var BRRBOX_MAC_SEARCHING = ""
    private val BRRBOX_MAC = "40:84:32:01:3B:28"

    private lateinit var supabase: SupabaseClient

    private var isAuthenticating = mutableStateOf(false)
    private var pendingSecretKey: String? = null
    private val currentDeviceAddress = mutableStateOf<String?>(null)
    private val deviceAliases = mutableStateMapOf<String, String>()
    private val ALIAS_PREFS = "brrbox_device_aliases"

    private val TEMP_PREFS = "brrbox_temp_prefs"
    private var defaultTempUnit = mutableStateOf("°F")

    private fun loadTempUnit() {
        val prefs = getSharedPreferences(TEMP_PREFS, MODE_PRIVATE)
        defaultTempUnit.value = prefs.getString("temp_unit", "°F") ?: "°F"
    }

    private fun saveTempUnit(unit: String) {
        getSharedPreferences(TEMP_PREFS, MODE_PRIVATE).edit().putString("temp_unit", unit).apply()
        defaultTempUnit.value = unit
    }

    private fun loadAliases() {
        val prefs = getSharedPreferences(ALIAS_PREFS, MODE_PRIVATE)
        prefs.all.forEach { (mac, name) ->
            if (name is String) deviceAliases[mac] = name
        }
    }

    private fun saveAlias(mac: String, alias: String) {
        getSharedPreferences(ALIAS_PREFS, MODE_PRIVATE).edit().putString(mac, alias).apply()
        deviceAliases[mac] = alias
    }

    private fun deleteAlias(mac: String) {
        getSharedPreferences(ALIAS_PREFS, MODE_PRIVATE).edit().remove(mac).apply()
        deviceAliases.remove(mac)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            addLog("Ready to connect.")
        } else {
            addLog("Permissions required")
        }
    }

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contentResolver.openInputStream(it)?.use { stream ->
                parseLines(stream.bufferedReader().readLines())
            }
        }
    }

    private fun addLog(message: String) {
        val currentLog = debugLog.value.toMutableList()
        currentLog.add(0, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))+"  "+message) // Add to beginning
        if (currentLog.size > 100) { // Keep last 20 logs
            currentLog.removeAt(currentLog.lastIndex)
        }
        debugLog.value = currentLog
        Log.d("BRRBOX", message)
    }
    private val scanMACCallback = object : ScanCallback() {
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
                if (device.address.equals(BRRBOX_MAC_SEARCHING, ignoreCase = true)) {
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

    @Serializable
    data class UserProfile(
        val company_id: String?
    )

    @Serializable
    data class DeviceRecord(
        val id: String,
        val secret_key: String
    )

    @Serializable
    data class OwnedDeviceRecord(
        val id: String
    )

// ── Data model for a scan result ────────────────────────────────────────────

    data class ScannedDevice(
        val address: String,
        val advertisedName: String?,   // from SN command on RNBD350 (IA,09)
        val rssi: Int,
        val manufacturerId: Int?,      // company ID from IA,FF (e.g. 0x004C = Apple)
        val manufacturerPayload: String?,  // remaining bytes after company ID, as hex string
        val serviceUuids: List<String>
    ) {
        // Map company ID to a human-readable name
        // IA,FF on RNBD350 puts your custom company ID as the SparseArray key
        val manufacturerName: String
            get() = when (manufacturerId) {
                0x0006 -> "Microsoft"
                0x004C -> "Apple"
                0x0075 -> "Samsung"
                0x00E0 -> "Google"
                0x0822 -> "Microchip"
                0x20BB -> "BRRBOX"
                else   -> if (manufacturerId != null)
                    "Unknown (0x${"%04X".format(manufacturerId)})"
                else "None"
            }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) return

            result?.let { scanResult ->
                val device     = scanResult.device
                val address    = device.address
                val rssi       = scanResult.rssi
                val scanRecord = scanResult.scanRecord

                if (discoveredDevices.contains(address)) return
                discoveredDevices.add(address)

                // ── Device name ──────────────────────────────────────────────────
                // advName comes from the advertisement packet (set via SN on RNBD350).
                // device.name is the cached system name, which may be stale — prefer advName.
                val advName = scanRecord?.deviceName ?: device.name

                val manufacturerData = scanRecord?.manufacturerSpecificData
                var manufacturerId: Int?    = null
                var manufacturerPayload: String? = null

                if (manufacturerData != null && manufacturerData.size() > 0) {
                    manufacturerId = manufacturerData.keyAt(0)
                    val rawBytes   = manufacturerData.valueAt(0)

                    manufacturerPayload = rawBytes.joinToString(" ") {
                        "%02X".format(it.toInt() and 0xFF)  // unsigned hex, e.g. "DE AD BE EF"
                    }
                }

                val serviceUuids = scanRecord?.serviceUuids
                    ?.map { it.uuid.toString().uppercase() }
                    ?: emptyList()

                val scanned = ScannedDevice(
                    address           = address,
                    advertisedName    = advName,
                    rssi              = rssi,
                    manufacturerId    = manufacturerId,
                    manufacturerPayload = manufacturerPayload,
                    serviceUuids      = serviceUuids
                )

                if(scanned.manufacturerName == "BRRBOX") {
                    discoveredBRRBOXList.add(scanned)
                    addLog("DISCOVERED A BRRBOX!")
                }

                // Debug log (concise)
                addLog("Found: ${advName ?: "?"} ($address)  RSSI: $rssi dBm")
                if (manufacturerId != null) {
                    addLog("  Manufacturer: ${scanned.manufacturerName} | Payload: $manufacturerPayload")
                }
                serviceUuids.forEach { addLog("  Service: $it") }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            addLog("Scan failed: error $errorCode")
            isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    addLog("BLE connected. Discovering services...")
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
                    isAuthenticating.value = false
                    isConnected.value = false
                    addLog("Disconnected from device")
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                isConnecting.value = false

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
                    val descriptor = txChar.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
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

                val key = pendingSecretKey
                if (key != null) {
                    // Key available — enter authenticating state and send it after
                    // a short delay to let the descriptor write settle.
                    isAuthenticating.value = true
                    addLog("Sending secret key for validation...")
                    lifecycleScope.launch {
                        delay(500)
                        if (ActivityCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val rxService = bluetoothGatt?.getService(SERVICE_UUID)
                            val rxChar = rxService?.getCharacteristic(RX_CHARACTERISTIC_UUID)
                            if (rxChar != null) {
                                val msg = "K$key\n".toByteArray()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    bluetoothGatt?.writeCharacteristic(
                                        rxChar,
                                        msg,
                                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    rxChar.value = msg
                                    @Suppress("DEPRECATION")
                                    bluetoothGatt?.writeCharacteristic(rxChar)
                                }
                                addLog("Secret key sent — awaiting XAA/XA0 response")
                            } else {
                                addLog("Key send failed: RX characteristic not found")
                                isAuthenticating.value = false
                                disconnect()
                            }
                        }
                    }
                } else {
                    // No key (direct MAC / debug path) — skip auth and mark connected.
                    isConnected.value = true
                    addLog("No secret key provided — skipping auth (debug mode)")
                    simpleAlert("Connected!")
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
            receiveBuffer.append(value.toString(Charsets.UTF_8))

            while (receiveBuffer.contains('\n')) {
                val newlineIndex = receiveBuffer.indexOf('\n')
                val line = receiveBuffer.substring(0, newlineIndex).trim()
                receiveBuffer.delete(0, newlineIndex + 1)

                if (line.isNotEmpty()) {
                    processMessage(line)
                }
            }
        }
    }



    private fun processMessage(message: String) {
        addLog("From BRRBOX: $message")

        if (message.matches(Regex("X[0-9A-Fa-f]{2}"))) {
            // Parse the two hex digits directly — the original used .toByte() which
            // mis-parses anything above 0x09 and throws on A-F characters.
            val code = message.removePrefix("X").toInt(16) and 0xFF
            when (code) {
                0xAA -> {
                    // Secret key accepted — device is now ready for commands.
                    isAuthenticating.value = false
                    isConnected.value = true
                    addLog("Secret key accepted — device ready.")
                    simpleAlert("Connected!")
                }
                0xA0 -> {
                    // Secret key rejected — BRRBOX will also force-disconnect on its end.
                    isAuthenticating.value = false
                    addLog("Secret key rejected by BRRBOX.")
                    simpleAlert("Authentication failed: invalid key.")
                    disconnect()
                }
                0xA1 -> {
                    // BRRBOX is still in the waiting-for-key state, which means our
                    // key write may not have arrived yet. Log it; the send path already
                    // handles retries if needed.
                    addLog("BRRBOX waiting for secret key (XA1 — key may not have arrived yet).")
                }
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
                0xE1 -> simpleAlert("Error received from BRRBOX: No logging data available!")
                else -> addLog("Unknown status code: 0x${code.toString(16).uppercase()}")
            }
            return
        }

        if (receivingLoggingData.value && message.matches(Regex("T\\d{2}:\\d{2}:\\d{2},-?\\d+\\.?\\d*"))) {
            val (time, temp) = message.removePrefix("T").split(",")
            val parts = time.split(":")
            val elapsedHours = parts[0].toFloat() + parts[1].toFloat() / 60f + parts[2].toFloat() / 3600f
            val temperature = temp.toFloat()
            logEntries.add(Entry(elapsedHours, temperature))
        }

        if (message.startsWith("M")) {
            currentTempCelsius.value = message.removePrefix("M").toFloatOrNull() ?: currentTempCelsius.value
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
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "BRRBOX Controller",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
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
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unlock")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { sendCommand("L") },
                        enabled = isConnected.value,
                        modifier = Modifier.weight(12f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
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
                onConfirm = { command ->
                    sendCommand(command)
                    showTemperatureDialog.value = false
                }
            )
        }
    }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Composable
    fun MonitorScreen(modifier: Modifier = Modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Current Temperature",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(32.dp))

                ThermometerGraphic(
                    temperatureCelsius = currentTempCelsius.value,
                    minTemp = -20f,
                    maxTemp = 50f,
                    useFahrenheit = defaultTempUnit.value == "°F",
                    thermometerHeight = 350.dp
                )
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                if (isConnected.value) {
                    sendCommand("M")
                }
                delay(1000)
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Composable
    fun TempDataScreen(modifier: Modifier = Modifier) {
        // logEntries is always in Celsius — convert visually only
        val entries = logEntries.toList()
        val useFahrenheit = defaultTempUnit.value == "°F"
        val unitLabel = if (useFahrenheit) "°F" else "°C"
        val displayEntries = if (useFahrenheit)
            entries.map { Entry(it.x, it.y * 9f / 5f + 32f) }
        else
            entries

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Data Logging",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))

                AndroidView(
                    factory = { context ->
                        LineChart(context).apply {
                            xAxis.apply {
                                position = XAxis.XAxisPosition.BOTTOM
                                granularity = 1f
                                setLabelCount(6, false)
                                textColor = android.graphics.Color.GRAY
                                gridColor = android.graphics.Color.LTGRAY
                                valueFormatter = object : ValueFormatter() {
                                    override fun getFormattedValue(value: Float): String {
                                        val totalMinutes = (value * 60).roundToInt()
                                        val h = totalMinutes / 60
                                        val m = totalMinutes % 60
                                        return if (h > 0) "${h}h ${m}m" else "${m}m"
                                    }
                                }
                            }

                            axisLeft.apply {
                                textColor = android.graphics.Color.GRAY
                                gridColor = android.graphics.Color.LTGRAY
                                granularity = 0.1f
                                isGranularityEnabled = true
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

                            onChartGestureListener = object : OnChartGestureListener {
                                override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                                    updateXAxisGranularity(this@apply)
                                    updateYAxisGranularity(this@apply)
                                }
                                override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {
                                    updateXAxisGranularity(this@apply)
                                    updateYAxisGranularity(this@apply)
                                }
                                override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                                    updateXAxisGranularity(this@apply)
                                    updateYAxisGranularity(this@apply)
                                }
                                override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
                                override fun onChartLongPressed(me: MotionEvent?) {}
                                override fun onChartDoubleTapped(me: MotionEvent?) {}
                                override fun onChartSingleTapped(me: MotionEvent?) {}
                                override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
                            }
                        }
                    },
                    update = { chart ->
                        // Y-axis formatter uses the current display unit
                        chart.axisLeft.valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float) =
                                if (value % 1f == 0f) "${value.toInt()}$unitLabel"
                                else "${"%.1f".format(value)}$unitLabel"
                        }

                        val dataSet = LineDataSet(displayEntries, "Temperature ($unitLabel)").apply {
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

                        if (displayEntries.isNotEmpty()) {
                            val minDisplayTemp = displayEntries.minOf { it.y }
                            val maxDisplayTemp = displayEntries.maxOf { it.y }
                            val maxX = displayEntries.maxOf { it.x }

                            // Sensible default bounds: pad 10 degrees, floor/ceil in the display unit
                            chart.axisLeft.apply {
                                axisMinimum = minOf(minDisplayTemp - 10f, if (useFahrenheit) 32f else 0f)
                                axisMaximum = maxOf(maxDisplayTemp + 10f, if (useFahrenheit) 86f else 30f)
                            }

                            chart.xAxis.apply {
                                axisMinimum = 0f
                                axisMaximum = maxX
                                setLabelCount(6, false)
                                valueFormatter = object : ValueFormatter() {
                                    override fun getFormattedValue(value: Float): String {
                                        val totalMinutes = (value * 60).toInt()
                                        val h = totalMinutes / 60
                                        val m = totalMinutes % 60
                                        return if (h > 0) "${h}h ${m}m" else "${m}m"
                                    }
                                }
                            }
                        }

                        chart.data = LineData(dataSet)
                        chart.notifyDataSetChanged()
                        chart.invalidate()
                        updateXAxisGranularity(chart)
                        updateYAxisGranularity(chart)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { showLoggingDialog.value = true },
                        modifier = Modifier.weight(12f)
                    ) {
                        Text("Get Logging Data")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { showSaveDialog.value = true },
                        enabled = logEntries.isNotEmpty(),
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
                    showGetSavedLogDialog.value = true
                },
                {
                    showLoggingDialog.value = false
                    if (bluetoothGatt == null && isConnected.value) {
                        logEntries.clear()
                        val intervalsPerDay = 24 * 6
                        repeat(intervalsPerDay) { index ->
                            val minutes = index * 10
                            val xValue = minutes / 60f
                            // Fake data generated in Celsius — matches real device data
                            val yValue = (4f + Math.sin(index * 0.3) * 1.5f + (Math.random() - 0.5f) * 0.8f).toFloat()
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

        if (showSaveDialog.value) {
            val time = LocalDateTime.now()
            GlobalTextInputDialog(
                onDismissRequest = { showSaveDialog.value = false },
                onConfirmation = { name ->
                    var fileName = name
                    showSaveDialog.value = false
                    if (!fileName.endsWith(".csv")) fileName = "$fileName.csv"
                    val file = File(getExternalFilesDir(null), fileName)
                    file.printWriter().use { out ->
                        // CSV is always written in Celsius regardless of display preference
                        out.println(listOf("Elapsed Time", "Temperature (°C)").joinToString(","))
                        logEntries.forEach { entry ->
                            out.println(listOf(entry.x.toString(), entry.y.toString()).joinToString(","))
                        }
                    }
                    simpleAlert("File saved!")
                },
                dialogTitle = "Save File",
                dialogText = "Enter a name for your file.",
                confirmText = "Save",
                dismissText = "Cancel",
                icon = Icons.Default.Save,
                defaultText = time.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")),
                validationRegex = Regex("^[\\w\\-. ]+$"),
                errorMessage = "Invalid file name. Avoid special characters like / \\ : * ? \" < > |",
            )
        }

        if (showGetSavedLogDialog.value) {
            GetSavedLogDialog(
                { showGetSavedLogDialog.value = false },
                { showGetSavedLogDialog.value = false }
            )
        }
    }
    @Composable
    fun BluetoothScreen(modifier: Modifier = Modifier) {
        val scope = rememberCoroutineScope()
        var deviceToRename by remember { mutableStateOf<ScannedDevice?>(null) }

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    "Bluetooth Pairing",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val statusText = when {
                    isConnected.value -> {
                        val alias = currentDeviceAddress.value?.let { deviceAliases[it] }
                        "Connected to ${alias ?: currentDeviceName.value ?: "BRRBOX"}"
                    }
                    isAuthenticating.value -> {
                        val alias = currentDeviceAddress.value?.let { deviceAliases[it] }
                        "Authenticating with ${alias ?: currentDeviceName.value ?: "BRRBOX"}..."
                    }
                    isConnecting.value -> "Connecting..."
                    else -> "Not connected"
                }
                Text(statusText)

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { scanForBRRBOX() },
                    enabled = !isScanning.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isScanning.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning...")
                    } else {
                        Text("Scan for BRRBOX")
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 80.dp // 👈 adjust as needed
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discoveredBRRBOXList) { item ->
                        ListRow(
                            item = item,
                            displayName = deviceAliases[item.address]
                                ?: item.advertisedName
                                ?: "Unnamed BRRBOX",
                            onTopButtonClick = {
                                scope.launch {
                                    val allowed = validateAndConnect(item)
                                    if (allowed) {
                                        currentDeviceName.value = item.advertisedName
                                        currentDeviceAddress.value = item.address
                                        connectToMacAddress(item.address)
                                    }
                                }
                            },
                            onBottomButtonClick = {
                                deviceToRename = item
                            }
                        )
                    }
                }
            }
        }

        // Rename dialog — shown when the user taps Rename on a list row.
        // Saving a blank name removes the alias and reverts to the advertised name.
        deviceToRename?.let { device ->
            GlobalTextInputDialog(
                onDismissRequest = { deviceToRename = null },
                onConfirmation = { newName ->
                    if (newName.isBlank()) deleteAlias(device.address)
                    else saveAlias(device.address, newName)
                    deviceToRename = null
                },
                dialogTitle = "Rename BRRBOX",
                dialogText = "Enter a local nickname for this BRRBOX. Leave blank to reset to its default name.",
                confirmText = "Save",
                dismissText = "Cancel",
                icon = Icons.Default.Kitchen,
                defaultText = deviceAliases[device.address] ?: device.advertisedName ?: "",
                validationRegex = Regex("^[\\w\\- ]*$"),
                errorMessage = "Use only letters, numbers, spaces, hyphens, or underscores."
            )
        }
    }
    @Composable
    fun LoginScreen(modifier: Modifier = Modifier) {
        var emailInput by remember { mutableStateOf("") }
        var passwordInput by remember { mutableStateOf("") }
        var currentLogin = remember { mutableStateOf<String?>(null) }
        var visible by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val user = supabase.auth.currentUserOrNull()
            if (user != null) {
                currentLogin.value = user.email
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Account Login",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    buildAnnotatedString {
                        if (currentLogin.value != null) {
                            append("Signed in as ")
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(currentLogin.value!!)
                            }
                        } else {
                            append("Not signed in")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(48.dp))

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    singleLine = true,
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && currentLogin.value == null
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Wrap in a Box so the icon sits inside/at the end of the field
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
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
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && currentLogin.value == null
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            isLoading = true
                            lifecycleScope.launch {
                                try {
                                    supabase.auth.signInWith(Email) {
                                        email = emailInput
                                        password = passwordInput
                                    }
                                    currentLogin.value = supabase.auth.currentUserOrNull()?.email
                                    simpleAlert("Signed in successfully!")
                                } catch (e: Exception) {
                                    simpleAlert("Login failed: ${e.message}")
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && currentLogin.value == null && emailInput.isNotEmpty() && passwordInput.isNotEmpty(),
                    ) {
                        Text("Sign In")
                    }
                    Button(
                        onClick = {
                            isLoading = true
                            lifecycleScope.launch {
                                try {
                                    supabase.auth.signOut()
                                    currentLogin.value = null
                                    emailInput = ""
                                    passwordInput = ""
                                    simpleAlert("Logged out.")
                                } catch (e: Exception) {
                                    simpleAlert("Logout failed: ${e.message}")
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading && currentLogin.value != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Log Out")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            isLoading = true
                            lifecycleScope.launch {
                                try {
                                    supabase.auth.signUpWith(Email) {
                                        email = emailInput
                                        password = passwordInput
                                    }
                                    simpleAlert("Sign up successful! Please check your email for verification.")
                                } catch (e: Exception) {
                                    simpleAlert("Sign up failed: ${e.message}")
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading && currentLogin.value == null && emailInput.isNotEmpty() && passwordInput.isNotEmpty(),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Create Account")
                    }

                    TextButton(
                        onClick = {
                            if (emailInput.isEmpty()) {
                                simpleAlert("Please enter your email address first.")
                                return@TextButton
                            }
                            isLoading = true
                            lifecycleScope.launch {
                                try {
                                    supabase.auth.resetPasswordForEmail(emailInput)
                                    simpleAlert("Password reset email sent!")
                                } catch (e: Exception) {
                                    simpleAlert("Error: ${e.message}")
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading && currentLogin.value == null,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Forgot Password?")
                    }
                }


                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Composable
    fun SettingsScreen(modifier: Modifier = Modifier) {
        var command by remember { mutableStateOf("") }
        val tempOptions = listOf("°F", "°C")

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Settings",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    "Default Temperature",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tempOptions.forEach { option ->
                        Row(
                            Modifier
                                .height(48.dp)
                                .selectable(
                                    selected = (option == defaultTempUnit.value),
                                    onClick = { saveTempUnit(option) },
                                    role = Role.RadioButton
                                )
                                .padding(end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (option == defaultTempUnit.value),
                                onClick = null
                            )
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Debug Commands",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { connectToMacAddress(BRRBOX_MAC) },
                        enabled = !isConnected.value && !isConnecting.value,
                        modifier = Modifier.weight(10f)
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
                            Text("Connect via MAC")
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = { debugConnect() },
                        enabled = !isConnected.value,
                        modifier = Modifier.weight(10f)
                    ) {
                        Text("Fake Connect")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = { disconnect() },
                        enabled = isConnected.value,
                        modifier = Modifier.weight(10f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Disconnect")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    singleLine = true,
                    label = { Text("Custom Command...") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { sendCommand(command) },
                    enabled = isConnected.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send Command")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Debug Logs ───────────────────────────────────────────────────────
                Text(
                    "Debug Logs",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        reverseLayout = false
                    ) {
                        items(debugLog.value.size) { index ->
                            Text(
                                debugLog.value[index],
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
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
                    onClick = { debugLog.value = mutableListOf() },
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
        LOGIN("login", "Account", Icons.Default.AccountCircle,"Login to User Account"),
        SETTINGS("settings", "Settings",Icons.Default.Settings,"Settings Page"),
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        supabase = createSupabaseClient(
            supabaseUrl = "https://rbpcrenvnzbizcrdjwog.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJicGNyZW52bnpiaXpjcmRqd29nIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQ5ODA0ODIsImV4cCI6MjA5MDU1NjQ4Mn0.GFwszcXf_55XpN3u1LC4MnDyAp3FZaqHToW-xEBAkqM"
        ) {
            install(Auth)
            install(Postgrest)
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        loadAliases()
        requestBluetoothPermissions()

        setContent {
            BRRBOXTheme {
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
                        Destination.SETTINGS -> SettingsScreen()
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
                            label = { Text(destination.label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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

    fun scanForBRRBOX() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            addLog("Bluetooth permissions required.")
            return
        }

        discoveredDevices.clear()
        discoveredBRRBOXList.clear()
        isScanning.value = true

        addLog("Scanning for devices...")
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothAdapter?.bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
        } catch (e: SecurityException) {
            addLog("Permission error on scan: ${e.message}")
            isScanning.value = false
            return
        } catch (e: Exception) {
            addLog("Scan error: ${e.message}")
            isScanning.value = false
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (_: SecurityException) {}

            addLog("Scan complete — found ${discoveredBRRBOXList.size} BRRBOXes.")
            isScanning.value = false
            simpleAlert("Scan complete!")
        }, 10_000)
    }

    fun connectToMacAddress(macToSearch: String) {
        discoveredDevices.clear()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            addLog("Bluetooth permission is required.")
            return
        }

        isConnecting.value = true

        BRRBOX_MAC_SEARCHING = macToSearch

        // Try bonded first
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            if (device.address.equals(BRRBOX_MAC_SEARCHING, ignoreCase = true)) {
                addLog("Found bonded BRRBOX - Connecting...")
                bluetoothGatt = device.connectGatt(this, false, gattCallback)
                return
            }
        }

        // Fall back to scanning
        simpleAlert("Searching...")
        addLog("Scanning for devices...")
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            bluetoothAdapter?.bluetoothLeScanner?.startScan(null, scanSettings, scanMACCallback)
        } catch (e: SecurityException) {
            addLog("SecurityException on scan: ${e.message}")
            simpleAlert("Permission error. Check if location and bluetooth are enabled!")
            return
        } catch (e: Exception) {
            addLog("Scan error: ${e.message}")
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanMACCallback)
            }
            if (!isConnected.value) {
                isConnecting.value = false
                simpleAlert("BRRBOX not found!")
                addLog("BRRBOX not found")
            }
        }, 10000)
    }

    @Composable
    fun ListRow(
        item: ScannedDevice,
        displayName: String,
        onTopButtonClick: () -> Unit,
        onBottomButtonClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Kitchen,
                contentDescription = displayName,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = displayName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )

            Column(
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxHeight()
            ) {
                Button(
                    onClick = onTopButtonClick,
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    enabled = !isConnected.value && !isAuthenticating.value && !isConnecting.value
                ) {
                    Text("Connect", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = onBottomButtonClick,
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Rename", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    fun debugConnect() {
        if (isConnected.value) {
            isConnected.value = false
            isAuthenticating.value = false
            addLog("Debug Mode - Disconnected")
        } else {
            // Jump straight to connected — no auth handshake in debug mode.
            isConnected.value = true
            isAuthenticating.value = false
            addLog("Debug Mode - Connected (fake, auth skipped)")
        }
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

        val message = command + "\n"

        addLog("Sending message: $message")

        val service = bluetoothGatt?.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(RX_CHARACTERISTIC_UUID)

        if (characteristic != null) {
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                message.toByteArray(),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            addLog("Error: Service not found")
        }
    }

    private fun parseLines(lines : List<String>) {
        logEntries.clear()
        lines.drop(1).forEach { line -> // skip header
            val parts = line.split(",")
            if (parts.size == 2) {
                val x = parts[0].toFloatOrNull()
                val y = parts[1].toFloatOrNull()
                if (x != null && y != null) logEntries.add(Entry(x, y))
            }
        }
    }

    @Composable
    fun TemperatureDialog(
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit
    ) {
        val radioOptions = listOf("°F", "°C")
        // Seed from the app-wide preference instead of always defaulting to °F
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(defaultTempUnit.value) }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        var isRangeMode by remember { mutableStateOf(false) }

        // Default display values reflect the preferred unit
        var singleTemp by remember {
            mutableStateOf(if (defaultTempUnit.value == "°F") "32" else "0")
        }
        var minTemp by remember {
            mutableStateOf(if (defaultTempUnit.value == "°F") "32" else "0")
        }
        var maxTemp by remember {
            mutableStateOf(if (defaultTempUnit.value == "°F") "33" else "1")
        }

        fun formatSigned(value: Float): String {
            val sign = if (value >= 0f) "+" else "-"
            return "$sign%05.1f".format(Math.abs(value))
        }

        fun toCelsius(value: Float): Float =
            if (selectedOption == "°F") (value - 32f) * 5f / 9f else value

        fun buildCommand(): String {
            return if (isRangeMode) {
                val lo = toCelsius(minTemp.toFloatOrNull() ?: 0f)
                val hi = toCelsius(maxTemp.toFloatOrNull() ?: 0f)
                "T${formatSigned(lo)}${formatSigned(hi)}"
            } else {
                val t = toCelsius(singleTemp.toFloatOrNull() ?: 0f)
                "T${formatSigned(t - 0.1f)}${formatSigned(t + 0.1f)}"
            }
        }

        fun convertAll(fromFahrenheit: Boolean) {
            fun conv(s: String): String {
                val v = s.toFloatOrNull() ?: return s
                val converted = if (fromFahrenheit) (v - 32f) * 5f / 9f else v * 9f / 5f + 32f
                return String.format(Locale.US, "%.1f", converted)
            }
            singleTemp = conv(singleTemp)
            minTemp = conv(minTemp)
            maxTemp = conv(maxTemp)
        }

        val rangeInvalid = isRangeMode &&
                (minTemp.toFloatOrNull() ?: 0f) >= (maxTemp.toFloatOrNull() ?: 0f)

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { focusManager.clearFocus() }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Set Temperature",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Single",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (!isRangeMode) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = isRangeMode,
                                onCheckedChange = { isRangeMode = it }
                            )
                            Text(
                                "Range",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isRangeMode) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!isRangeMode) {
                            OutlinedTextField(
                                value = singleTemp,
                                onValueChange = { if (it.isEmpty() || it.matches(Regex("^-?\\d*\\.?\\d*$"))) singleTemp = it },
                                label = { Text("Temperature") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide(); focusManager.clearFocus() }),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Sends ±0.1° tolerance range",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            OutlinedTextField(
                                value = minTemp,
                                onValueChange = { if (it.isEmpty() || it.matches(Regex("^-?\\d*\\.?\\d*$"))) minTemp = it },
                                label = { Text("Min Temperature") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                singleLine = true,
                                isError = rangeInvalid,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = maxTemp,
                                onValueChange = { if (it.isEmpty() || it.matches(Regex("^-?\\d*\\.?\\d*$"))) maxTemp = it },
                                label = { Text("Max Temperature") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide(); focusManager.clearFocus() }),
                                singleLine = true,
                                isError = rangeInvalid,
                                supportingText = {
                                    if (rangeInvalid) Text("Max must be greater than Min", color = MaterialTheme.colorScheme.error)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            radioOptions.forEach { option ->
                                Row(
                                    Modifier
                                        .height(48.dp)
                                        .selectable(
                                            selected = (option == selectedOption),
                                            onClick = {
                                                if (option != selectedOption) {
                                                    convertAll(fromFahrenheit = selectedOption == "°F")
                                                    onOptionSelected(option)
                                                }
                                            },
                                            role = Role.RadioButton
                                        )
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = (option == selectedOption), onClick = null)
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) { Text("Cancel") }
                            TextButton(
                                onClick = { onConfirm(buildCommand()) },
                                enabled = !rangeInvalid
                            ) { Text("Set") }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun GetSavedLogDialog(
        onDismissRequest: () -> Unit,
        onFileSelected: (File) -> Unit
    ) {
        val logFolder = getExternalFilesDir(null)
        val files = remember {
            logFolder?.listFiles { f -> f.extension == "csv" }
                ?.also { addLog("Found ${it.size} files: ${it.map { f -> f.name }}") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            icon = { Icon(Icons.Default.FileOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Open Log File", color = MaterialTheme.colorScheme.primary) },
            text = {
                Column {
                    OutlinedButton(
                        onClick = {
                            openFileLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv"))
                            onDismissRequest()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Get somewhere else...")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (files.isEmpty()) {
                        Text(
                            "No saved logs found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            items(files.size) { index ->
                                val file = files[index]
                                TextButton(
                                    onClick = {
                                        parseLines(file.bufferedReader().readLines())
                                        onFileSelected(file)
                                        onDismissRequest()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            file.name,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm")
                                                .format(
                                                    Instant.ofEpochMilli(file.lastModified())
                                                    .atZone(ZoneId.systemDefault())
                                                    .toLocalDateTime()),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                if (index < files.size - 1) {
                                    HorizontalDivider(thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissRequest) { Text("Cancel") }
            }
        )
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
                Icon(icon, contentDescription = "Example Icon", tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text(text = dialogTitle, color = MaterialTheme.colorScheme.primary)
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

    @Composable
    fun GlobalTextInputDialog(
        onDismissRequest: () -> Unit,
        onConfirmation: (String) -> Unit,
        dialogTitle: String,
        dialogText: String,
        confirmText: String,
        dismissText: String,
        icon: ImageVector,
        defaultText: String = "",
        validationRegex: Regex? = null,
        errorMessage: String = "Invalid input",
    ) {
        var textValue by remember { mutableStateOf(defaultText) }
        val isError = validationRegex != null && !validationRegex.matches(textValue)

        AlertDialog(
            icon = {
                Icon(icon, contentDescription = "Dialog Icon", tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text(text = dialogTitle, color = MaterialTheme.colorScheme.primary)
            },
            text = {
                Column {
                    Text(text = dialogText)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        singleLine = true,
                        isError = isError,
                        supportingText = {
                            if (isError) {
                                Text(
                                    text = errorMessage,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
            },
            onDismissRequest = {
                onDismissRequest()
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isError) onConfirmation(textValue)
                    },
                    enabled = !isError
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

    private fun updateXAxisGranularity(chart: LineChart) {
        val visibleRange = chart.visibleXRange // in hours
        val granularityHours = when {
            visibleRange <= 0.1f  -> 1f / 60f
            visibleRange <= 0.25f -> 5f / 60f
            visibleRange <= 0.5f  -> 10f / 60f
            visibleRange <= 1f    -> 15f / 60f
            visibleRange <= 2f    -> 30f / 60f
            visibleRange <= 6f    -> 1f
            visibleRange <= 12f   -> 2f
            else                  -> 4f
        }
        chart.xAxis.granularity = granularityHours
        chart.xAxis.setLabelCount(6, false)
        chart.invalidate()
    }

    private fun updateYAxisGranularity(chart: LineChart) {
        val transformer = chart.getTransformer(YAxis.AxisDependency.LEFT)
        val bounds = chart.contentRect
        val topLeft = transformer.getValuesByTouchPoint(bounds.left, bounds.top)
        val bottomLeft = transformer.getValuesByTouchPoint(bounds.left, bounds.bottom)
        val visibleYRange = topLeft.y.toFloat() - bottomLeft.y.toFloat()

        chart.axisLeft.granularity = when {
            visibleYRange <= 1f  -> 0.1f
            visibleYRange <= 5f  -> 0.5f
            visibleYRange <= 10f -> 1f
            visibleYRange <= 20f -> 2f
            visibleYRange <= 40f -> 5f
            else                 -> 10f
        }
        chart.invalidate()
    }

    private suspend fun validateAndConnect(item: ScannedDevice): Boolean {
        // ── Step 1: Require the user to be signed in ─────────────────────────────
        val user = supabase.auth.currentUserOrNull()
        if (user == null) {
            simpleAlert("You must be signed in to connect to a BRRBOX.")
            addLog("Connection blocked: user not signed in.")
            return false
        }

        // ── Step 2: Fetch the user's company_id from public.users ────────────────
        val userProfile = try {
            supabase.from("users")
                .select { filter { eq("id", user.id) } }
                .decodeSingleOrNull<UserProfile>()
        } catch (e: Exception) {
            simpleAlert("Failed to fetch your account profile.")
            addLog("validateAndConnect: user profile error — ${e.message}")
            return false
        }

        val companyId = userProfile?.company_id
        if (companyId == null) {
            simpleAlert("Your account is not linked to a company. Contact your administrator.")
            addLog("Connection blocked: user has no company_id.")
            return false
        }

        // ── Step 3: Look up the device by advertised name in the devices table ───
        val deviceName = item.advertisedName
        if (deviceName == null) {
            simpleAlert("This BRRBOX has no advertised name and cannot be verified.")
            addLog("Connection blocked: device has no advertised name.")
            return false
        }

        val deviceRecord = try {
            supabase.from("devices")
                .select { filter { eq("device_name", deviceName) } }
                .decodeSingleOrNull<DeviceRecord>()
        } catch (e: Exception) {
            simpleAlert("Failed to look up this BRRBOX in the database.")
            addLog("validateAndConnect: device lookup error — ${e.message}")
            return false
        }

        if (deviceRecord == null) {
            simpleAlert("This BRRBOX ($deviceName) is not registered in the system.")
            addLog("Connection blocked: device \"$deviceName\" not found in devices table.")
            return false
        }

        // ── Step 4: Verify ownership via owned_devices ───────────────────────────
        val owned = try {
            supabase.from("owned_devices")
                .select {
                    filter {
                        eq("company_id", companyId)
                        eq("device_id", deviceRecord.id)
                    }
                }
                .decodeSingleOrNull<OwnedDeviceRecord>()
        } catch (e: Exception) {
            simpleAlert("Failed to verify device ownership.")
            addLog("validateAndConnect: owned_devices error — ${e.message}")
            return false
        }

        if (owned == null) {
            simpleAlert("Your company does not have access to this BRRBOX.")
            addLog("Connection blocked: no owned_devices match for company=$companyId, device=${deviceRecord.id}.")
            return false
        }

        // ── Step 5: Cache the secret key — sent to the device after BLE connects ─
        pendingSecretKey = deviceRecord.secret_key
        addLog("Ownership verified for \"$deviceName\". Secret key cached. Proceeding to connect.")
        return true
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
        isAuthenticating.value = false
        pendingSecretKey = null
        addLog("Disconnected")
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}
