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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import com.example.brrbox.ui.theme.BRRBOXTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    // Status states
    private var statusText = mutableStateOf("Disconnected")
    private var isConnected = mutableStateOf(false)

    private var showTemperatureDialog = mutableStateOf(false)

    // BLE UUIDs - need to update
    private val SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    private val CHARACTERISTIC_UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            statusText.value = "Ready to connect"
        } else {
            statusText.value = "Permissions required"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    statusText.value = "Connected - Discovering services..."
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        gatt?.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    statusText.value = "Disconnected"
                    isConnected.value = false
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                statusText.value = "Connected - Ready to send"
                isConnected.value = true
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                statusText.value = "Message received!"
            } else {
                statusText.value = "Send failed"
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        requestBluetoothPermissions()

        setContent {
            BRRBOXTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "BRRBOX",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            statusText.value,
                            fontSize = 18.sp,
                            color = when {
                                statusText.value.contains("Connected") -> MaterialTheme.colorScheme.primary
                                statusText.value.contains("failed") -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
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
                            onClick = { debugConnect() },
                            enabled = !isConnected.value,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect to BRRBOX Debug")
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
                                Text("Send Unlock")
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = { sendCommand("L") },
                                enabled = isConnected.value,
                                modifier = Modifier.weight(12f)
                            ) {
                                Text("Send Lock")
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
        }
    }
    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun connectToBRRBOX() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            statusText.value = "Bluetooth permission required"
            return
        }

        statusText.value = "Scanning for BRRBOX..."

        // Find device by name "BRRBOX" or address
        val pairedDevices = bluetoothAdapter?.bondedDevices
        val brrboxDevice = pairedDevices?.find { device ->
            device.name?.contains("BRRBOX", ignoreCase = true) == true ||
                    device.name?.contains("RN4870", ignoreCase = true) == true  // Microchip module name
        }

        if (brrboxDevice != null) {
            statusText.value = "Connecting..."
            bluetoothGatt = brrboxDevice.connectGatt(this, false, gattCallback)
        } else {
            statusText.value = "BRRBOX not found - Please pair device first"
        }
    }

    private fun debugConnect() {
        isConnected.value = !isConnected.value
        statusText.value = if (isConnected.value) {
            "Debug Mode - Connected (Fake)"
        } else {
            "Debug Mode - Disconnected"
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun sendCommand(command: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        statusText.value = "Sending message..."

        val service = bluetoothGatt?.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)

        if (characteristic != null) {
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                command.toByteArray(),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            statusText.value = "Error: Service not found"
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
            return String.format("%.1f", celsius)
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
                                                        temperature = String.format("%.1f", converted)
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

    private fun disconnect() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected.value = false
        statusText.value = "Disconnected"
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}