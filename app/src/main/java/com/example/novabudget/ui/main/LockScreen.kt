package com.example.novabudget.ui.main

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.novabudget.theme.*

@Composable
fun LockScreen(
    masterPasscode: String,
    decoyPasscode: String,
    onUnlock: (isDecoy: Boolean) -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    // Shake offset animation for incorrect PIN entries
    val shakeOffset = remember { Animatable(0f) }

    // Trigger biometric prompt directly on launch if supported
    LaunchedEffect(Unit) {
        triggerBiometricAuth(context, onUnlock)
    }

    // Handle passcode checking
    LaunchedEffect(pin) {
        if (pin.length == 4) {
            when (pin) {
                masterPasscode -> {
                    onUnlock(false) // Master Mode
                    pin = ""
                }
                decoyPasscode -> {
                    onUnlock(true) // Stealth Mode
                    pin = ""
                }
                else -> {
                    isError = true
                    pin = ""
                    // Play shake animation
                    shakeOffset.animateTo(
                        targetValue = 20f,
                        animationSpec = keyframes {
                            durationMillis = 300
                            0f at 0 with LinearEasing
                            -20f at 75 with LinearEasing
                            20f at 150 with LinearEasing
                            -20f at 225 with LinearEasing
                            0f at 300 with LinearEasing
                        }
                    )
                    isError = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .offset(x = shakeOffset.value.dp)
        ) {
            // Nova Logo Shield
            NovaLogo(modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "NovaBudget Secure Lock",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = SlateWhite
            )
            Text(
                text = "Enter PIN or use Fingerprint sensor",
                fontSize = 12.sp,
                color = SlateGray,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Passcode Indicators (4 dots)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val filled = index < pin.length
                    val dotColor = when {
                        isError -> AlertCrimson
                        filled -> PrimaryEmerald
                        else -> Color(0xFF1E2332)
                    }
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .border(1.dp, if (filled) Color.Transparent else Color(0xFF2C354A), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Passcode Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )

                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { item ->
                            when (item) {
                                "BIO" -> {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF131722))
                                            .border(1.dp, Color(0xFF22283A), CircleShape)
                                            .clickable { triggerBiometricAuth(context, onUnlock) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Biometric Lock",
                                            tint = PrimaryEmerald,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                "DEL" -> {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF131722))
                                            .border(1.dp, Color(0xFF22283A), CircleShape)
                                            .clickable {
                                                if (pin.isNotEmpty()) {
                                                    pin = pin.substring(0, pin.length - 1)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = SlateGray,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E2433))
                                            .border(1.dp, Color(0xFF2C354A), CircleShape)
                                            .clickable {
                                                if (pin.length < 4) {
                                                    pin += item
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = item,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = SlateWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun triggerBiometricAuth(context: android.content.Context, onUnlock: (isDecoy: Boolean) -> Unit) {
    val activity = context as? FragmentActivity ?: return
    val executor = ContextCompat.getMainExecutor(context)

    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onUnlock(false) // On biometric success, always unlock real Master Mode!
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Fail silently or show minor feedback
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(context, "Biometric Error: $errString", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Verify Identity")
        .setSubtitle("Authenticate using your fingerprint or face")
        .setNegativeButtonText("Use PIN")
        .build()

    try {
        biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
