package com.example.novabudget.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

class BluetoothSyncManager(
    private val context: Context,
    private val repo: DataRepository,
    private val callback: SyncCallback
) {
    interface SyncCallback {
        fun onProgress(message: String)
        fun onSuccess(sent: Int, received: Int)
        fun onError(error: String)
        fun onServerStatusChanged(active: Boolean)
    }

    companion object {
        private const val TAG = "BluetoothSyncManager"
        private val SYNC_UUID: UUID = UUID.fromString("9c279402-4876-4d2b-b6fb-f1a26d7f8723")
        private const val BUFFER_SIZE = 16384
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val handler = Handler(Looper.getMainLooper())

    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var isSilentSync = false

    // Helper to safely execute callbacks on UI thread
    private fun postProgress(msg: String) = handler.post { 
        if (!isSilentSync) callback.onProgress(msg) 
    }
    
    private fun postSuccess(sent: Int, rec: Int) = handler.post { 
        if (isSilentSync) {
            // Trigger local sync notification on silent background merge!
            NotificationHelper(context).showSyncNotification(sent, rec)
        }
        callback.onSuccess(sent, rec) 
    }
    
    private fun postError(err: String) = handler.post { 
        if (!isSilentSync) {
            callback.onError(err) 
        } else {
            Log.e(TAG, "Silent background sync failed: $err")
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Missing BLUETOOTH_CONNECT permission", e)
            emptyList()
        }
    }

    // --- Host Mode: Sync Server ---

    @SuppressLint("MissingPermission")
    @Synchronized
    fun startSyncServer() {
        if (bluetoothAdapter == null) {
            postError("Bluetooth is not supported on this device.")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            postError("Bluetooth is turned off. Please enable Bluetooth in settings.")
            return
        }

        stopSyncServer()
        postProgress("Starting Sync Server...")
        try {
            acceptThread = AcceptThread()
            acceptThread?.start()
            repo.setSyncServerActive(true)
            callback.onServerStatusChanged(true)
            postProgress("Server active. Waiting for spouse to connect...")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting server thread", e)
            postError("Failed to start server: ${e.message}")
        }
    }

    @Synchronized
    fun stopSyncServer() {
        acceptThread?.cancel()
        acceptThread = null
        repo.setSyncServerActive(false)
        callback.onServerStatusChanged(false)
        postProgress("Sync Server stopped.")
    }

    // --- Client Mode: Sync Client ---

    @SuppressLint("MissingPermission")
    @Synchronized
    fun syncWithSpouse(device: BluetoothDevice) {
        if (bluetoothAdapter == null) {
            postError("Bluetooth is not supported on this device.")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            postError("Bluetooth is turned off. Please enable Bluetooth.")
            return
        }

        isSilentSync = false
        postProgress("Connecting to ${device.name ?: "Spouse's Device"}...")
        connectThread?.cancel()
        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun syncWithSpouseSilent(device: BluetoothDevice) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return
        }
        isSilentSync = true
        connectThread?.cancel()
        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    fun getLastSyncedSpouseAddress(): String? {
        val prefs = context.getSharedPreferences("novabudget_prefs", Context.MODE_PRIVATE)
        return prefs.getString("last_synced_spouse_address", null)
    }

    // --- Server Socket Accept Thread ---

    @SuppressLint("MissingPermission")
    private inner class AcceptThread : Thread() {
        private var serverSocket: BluetoothServerSocket? = null
        private var isRunning = true

        init {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("NovaBudgetSync", SYNC_UUID)
            } catch (e: Exception) {
                Log.e(TAG, "ServerSocket listen() failed", e)
            }
        }

        override fun run() {
            setName("AcceptThread")
            while (isRunning) {
                val socket: BluetoothSocket? = try {
                    serverSocket?.accept()
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "ServerSocket accept() failed", e)
                        postError("Server stopped unexpectedly.")
                    }
                    break
                }

                socket?.let {
                    postProgress("Spouse connected! Synchronizing...")
                    handleSyncConnection(it)
                }
            }
        }

        fun cancel() {
            isRunning = false
            try {
                serverSocket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "ServerSocket close() failed", e)
            }
        }
    }

    // --- Client Socket Connect Thread ---

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private var socket: BluetoothSocket? = null

        init {
            try {
                socket = device.createRfcommSocketToServiceRecord(SYNC_UUID)
            } catch (e: Exception) {
                Log.e(TAG, "Socket create() failed", e)
            }
        }

        @SuppressLint("MissingPermission")
        override fun run() {
            setName("ConnectThread")
            // Always cancel discovery before connecting
            try {
                bluetoothAdapter?.cancelDiscovery()
            } catch (e: SecurityException) {
                e.printStackTrace()
            }

            try {
                socket?.connect()
            } catch (e: Exception) {
                Log.e(TAG, "Socket connect() failed", e)
                postError("Could not connect to spouse. Make sure their Sync Server is active.")
                cancel()
                return
            }

            socket?.let {
                postProgress("Connected! Synchronizing...")
                handleSyncConnection(it)
            }
        }

        fun cancel() {
            try {
                socket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Socket close() failed", e)
            }
        }
    }

    // --- Bidirectional Synchronization Protocol ---

    private fun handleSyncConnection(socket: BluetoothSocket) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        try {
            inputStream = socket.inputStream
            outputStream = socket.outputStream

            // 1. Fetch our own transactions
            val localTxList = repo.transactions.let {
                if (repo is DefaultDataRepository) {
                    NovaDatabaseHelper(context).getAllTransactions()
                } else {
                    emptyList()
                }
            }
            val localJson = Json.encodeToString(localTxList)
            val outputBytes = localJson.toByteArray(StandardCharsets.UTF_8)

            // 2. Send length header first (int, 4 bytes)
            val len = outputBytes.size
            val header = byteArrayOf(
                (len shr 24).toByte(),
                (len shr 16).toByte(),
                (len shr 8).toByte(),
                len.toByte()
            )
            outputStream.write(header)
            
            // Send JSON payload
            outputStream.write(outputBytes)
            outputStream.flush()
            Log.d(TAG, "Sent $len bytes of transaction JSON data.")

            // 3. Read incoming length header
            val lengthHeader = ByteArray(4)
            var bytesRead = 0
            while (bytesRead < 4) {
                val read = inputStream.read(lengthHeader, bytesRead, 4 - bytesRead)
                if (read == -1) throw Exception("Stream closed while reading header")
                bytesRead += read
            }

            val incomingLen = ((lengthHeader[0].toInt() and 0xFF) shl 24) or
                              ((lengthHeader[1].toInt() and 0xFF) shl 16) or
                              ((lengthHeader[2].toInt() and 0xFF) shl 8) or
                              (lengthHeader[3].toInt() and 0xFF)

            Log.d(TAG, "Incoming JSON data length: $incomingLen bytes")

            // 4. Read incoming JSON payload
            val buffer = ByteArray(incomingLen)
            var totalRead = 0
            while (totalRead < incomingLen) {
                val read = inputStream.read(buffer, totalRead, incomingLen - totalRead)
                if (read == -1) throw Exception("Stream closed while reading data")
                totalRead += read
            }

            val incomingJson = String(buffer, 0, totalRead, StandardCharsets.UTF_8)
            val incomingTxList = Json.decodeFromString<List<Transaction>>(incomingJson)
            Log.d(TAG, "Received ${incomingTxList.size} transactions from spouse.")

            // 5. Merge received transactions into local database
            var newRecordsMerged = 0
            for (tx in incomingTxList) {
                // Ensure marked as synced during spouse transfer
                val syncedTx = tx.copy(isSynced = 1)
                val success = repo.addTransaction(syncedTx)
                if (success) {
                    newRecordsMerged++
                }
            }

            // Mark our own as synced too
            val helper = NovaDatabaseHelper(context)
            helper.markAllAsSynced()
            repo.refresh()

            // Cache this successful connection's remote device address!
            try {
                val prefs = context.getSharedPreferences("novabudget_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("last_synced_spouse_address", socket.remoteDevice.address).apply()
                Log.d(TAG, "Cached spouse Bluetooth address: ${socket.remoteDevice.address}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache spouse Bluetooth address", e)
            }

            postSuccess(localTxList.size, newRecordsMerged)
            postProgress("Sync Successful! Merged $newRecordsMerged new expenses.")

        } catch (e: Exception) {
            Log.e(TAG, "Sync process failed", e)
            postError("Sync failed: ${e.localizedMessage ?: "Unknown error"}")
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
