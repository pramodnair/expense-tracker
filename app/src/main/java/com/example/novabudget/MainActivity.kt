package com.example.novabudget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.novabudget.data.DefaultDataRepository
import com.example.novabudget.theme.NovaBudgetTheme
import com.example.novabudget.ui.main.LockScreen

class MainActivity : FragmentActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 2001
    }

    private lateinit var repository: DefaultDataRepository
    private val isAppLocked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = DefaultDataRepository.getInstance(applicationContext)

        // Lock immediately if security enabled when starting up
        if (repository.isSecurityEnabled()) {
            isAppLocked.value = true
        }

        // Lock when app is resumed/brought to foreground
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (repository.isSecurityEnabled()) {
                    isAppLocked.value = true
                }
            }
        })

        enableEdgeToEdge()
        setContent {
            val locked by isAppLocked
            NovaBudgetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (locked) {
                        LockScreen(
                            masterPasscode = repository.getMasterPasscode(),
                            decoyPasscode = repository.getDecoyPasscode(),
                            onUnlock = { isDecoy ->
                                repository.setStealthModeActive(isDecoy)
                                isAppLocked.value = false
                            }
                        )
                    } else {
                        MainNavigation()
                    }
                }
            }
        }

        // Request runtime permissions on start for smooth local testing!
        requestRequiredPermissions()
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        // 1. SMS Receiver and Reader Permissions
        permissions.add(Manifest.permission.RECEIVE_SMS)
        permissions.add(Manifest.permission.READ_SMS)

        // 2. Local Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 3. Bluetooth Connect and Scan Permissions (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        // Filter out already granted permissions
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }
}
