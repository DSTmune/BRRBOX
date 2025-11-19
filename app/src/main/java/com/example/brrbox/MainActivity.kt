package com.example.brrbox

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.brrbox.ui.theme.BRRBOXTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    // Status states
    private var statusText = mutableStateOf("Disconnected")
    private var isConnected = mutableStateOf(false)

    // BLE UUIDs - You'll need to update these based on your microcontroller setup
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

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { sendOpenCommand() },
                            enabled = isConnected.value,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Send Open")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { disconnect() },
                            enabled = isConnected.value,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Disconnect")
                        }
                    }
                }
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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

    private fun sendOpenCommand() {
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
            val command = "OPEN"
            characteristic.value = command.toByteArray()
            bluetoothGatt?.writeCharacteristic(characteristic)
        } else {
            statusText.value = "Error: Service not found"
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