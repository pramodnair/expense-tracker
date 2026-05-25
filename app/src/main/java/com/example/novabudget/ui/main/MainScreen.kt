package com.example.novabudget.ui.main

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.novabudget.data.AccountConfig
import com.example.novabudget.data.BluetoothSyncManager
import com.example.novabudget.data.CardConfig
import com.example.novabudget.data.DefaultDataRepository
import com.example.novabudget.data.Transaction
import com.example.novabudget.data.Subscription
import com.example.novabudget.data.SmsReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.example.novabudget.theme.*
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

// --- BEAUTIFUL CANVAS BRAND LOGO ---

@Composable
fun NovaLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(44.dp)) {
        val width = size.width
        val height = size.height
        
        // Draw elegant obsidian-shield background with dynamic green/teal brand gradient
        val shieldPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(width * 0.5f, 0f)
            cubicTo(width * 0.85f, height * 0.05f, width * 0.95f, height * 0.25f, width * 0.9f, height * 0.6f)
            cubicTo(width * 0.85f, height * 0.85f, width * 0.5f, height, width * 0.5f, height)
            cubicTo(width * 0.5f, height, width * 0.15f, height * 0.85f, width * 0.1f, height * 0.6f)
            cubicTo(width * 0.05f, height * 0.25f, width * 0.15f, height * 0.05f, width * 0.5f, 0f)
            close()
        }
        
        drawPath(
            path = shieldPath,
            brush = Brush.linearGradient(listOf(PrimaryEmerald, SecondaryTeal))
        )
        
        // Draw inner glowing shield border
        drawPath(
            path = shieldPath,
            color = DarkSurface,
            style = Stroke(width = 3.dp.toPx())
        )
        
        // Draw stylized futuristic Letter N
        val nPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(width * 0.32f, height * 0.68f)
            lineTo(width * 0.32f, height * 0.32f)
            lineTo(width * 0.68f, height * 0.68f)
            lineTo(width * 0.68f, height * 0.32f)
        }
        
        drawPath(
            path = nPath,
            color = Color.White,
            style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// --- UTILITY MONTH FORMATTER ---

fun formatMonthLabel(yearMonth: String): String {
    val parts = yearMonth.split("-")
    if (parts.size != 2) return yearMonth
    val year = parts[0]
    val monthNum = parts[1].toIntOrNull() ?: return yearMonth
    val dfs = DateFormatSymbols(Locale.US)
    val monthName = dfs.months[monthNum - 1]
    return "$monthName $year"
}

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: MainScreenViewModel = viewModel {
        MainScreenViewModel(DefaultDataRepository.getInstance(context))
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) }

    // Bluetooth Sync Manager integration
    var syncLog by remember { mutableStateOf("Ready to sync.") }
    var isServerActive by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    val syncCallback = remember {
        object : BluetoothSyncManager.SyncCallback {
            override fun onProgress(message: String) {
                syncLog = message
            }

            override fun onSuccess(sent: Int, received: Int) {
                syncLog = "Sync Success! Shared: $sent, Merged: $received"
                Toast.makeText(context, "Database synced successfully!", Toast.LENGTH_SHORT).show()
                viewModel.refresh()
            }

            override fun onError(error: String) {
                syncLog = "Error: $error"
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }

            override fun onServerStatusChanged(active: Boolean) {
                isServerActive = active
            }
        }
    }

    val bluetoothSyncManager = remember {
        BluetoothSyncManager(context, DefaultDataRepository.getInstance(context), syncCallback)
    }

    // Sync state initialization
    LaunchedEffect(Unit) {
        isServerActive = DefaultDataRepository.getInstance(context).isSyncServerActive()
        if (isServerActive) {
            bluetoothSyncManager.startSyncServer()
        }

        // Trigger silent background spouse sync automatically on app open!
        try {
            val spouseAddress = bluetoothSyncManager.getLastSyncedSpouseAddress()
            if (spouseAddress != null && bluetoothSyncManager.isBluetoothEnabled()) {
                val pairedDevices = bluetoothSyncManager.getPairedDevices()
                val spouseDevice = pairedDevices.firstOrNull { it.address == spouseAddress }
                if (spouseDevice != null) {
                    android.util.Log.d("MainScreen", "Auto-syncing with spouse on launch at $spouseAddress")
                    bluetoothSyncManager.syncWithSpouseSilent(spouseDevice)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainScreen", "Failed to auto-sync on launch", e)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.shadow(16.dp)
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryEmerald,
                        selectedTextColor = PrimaryEmerald,
                        unselectedIconColor = SlateGray,
                        unselectedTextColor = SlateGray,
                        indicatorColor = DarkSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Expenses") },
                    label = { Text("Expenses") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryEmerald,
                        selectedTextColor = PrimaryEmerald,
                        unselectedIconColor = SlateGray,
                        unselectedTextColor = SlateGray,
                        indicatorColor = DarkSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Rules") },
                    label = { Text("Rules") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryEmerald,
                        selectedTextColor = PrimaryEmerald,
                        unselectedIconColor = SlateGray,
                        unselectedTextColor = SlateGray,
                        indicatorColor = DarkSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "Sync") },
                    label = { Text("Sync") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryEmerald,
                        selectedTextColor = PrimaryEmerald,
                        unselectedIconColor = SlateGray,
                        unselectedTextColor = SlateGray,
                        indicatorColor = DarkSurfaceVariant
                    )
                )
            }
        },
        containerColor = DarkBackground,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        listOf(DarkBackground, Color(0xFF07090E))
                    )
                )
        ) {
            when (state) {
                MainScreenUiState.Loading -> {
                    CircularProgressIndicator(
                        color = PrimaryEmerald,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is MainScreenUiState.Success -> {
                    val data = state as MainScreenUiState.Success
                    when (activeTab) {
                        0 -> DashboardScreen(data, viewModel)
                        1 -> ExpensesScreen(data, viewModel)
                        2 -> ConfigScreen(data, viewModel)
                        3 -> SyncScreen(
                            data = data,
                            syncLog = syncLog,
                            isServerActive = isServerActive,
                            onToggleServer = { active ->
                                if (active) {
                                    bluetoothSyncManager.startSyncServer()
                                } else {
                                    bluetoothSyncManager.stopSyncServer()
                                }
                            },
                            onTriggerSync = {
                                pairedDevices = bluetoothSyncManager.getPairedDevices()
                                if (pairedDevices.isEmpty()) {
                                    Toast.makeText(context, "No paired devices found! Please pair in system settings first.", Toast.LENGTH_LONG).show()
                                } else {
                                    showDeviceDialog = true
                                }
                            },
                            onRefresh = { viewModel.refresh() }
                        )
                    }
                }
            }
        }
    }

    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = pairedDevices,
            onDismiss = { showDeviceDialog = false },
            onDeviceSelected = { device ->
                showDeviceDialog = false
                bluetoothSyncManager.syncWithSpouse(device)
            }
        )
    }
}

// --- PREMIUM DROPDOWN MONTH SELECTOR ---

@Composable
fun MonthSelectorDropdown(
    selectedMonth: String,
    distinctMonths: List<String>,
    onMonthSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF22283A), RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = SecondaryTeal,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = formatMonthLabel(selectedMonth),
                color = SlateWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = SlateGray,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(DarkSurface)
                .border(1.dp, Color(0xFF22283A), RoundedCornerShape(8.dp))
        ) {
            distinctMonths.forEach { month ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatMonthLabel(month),
                            color = SlateWhite,
                            fontSize = 13.sp,
                            fontWeight = if (month == selectedMonth) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        onMonthSelected(month)
                    }
                )
            }
        }
    }
}

// --- TAB 1: DASHBOARD ---

@Composable
fun DashboardScreen(data: MainScreenUiState.Success, viewModel: MainScreenViewModel) {
    val currency = data.currencySymbol
    val spent = data.currentMonthSpent
    val limit = data.budgetLimit.toDouble()
    val percentage = if (limit > 0) (spent / limit).toFloat() else 0f

    val animateProgress by animateFloatAsState(
        targetValue = percentage.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000)
    )

    val remaining = (limit - spent).coerceAtLeast(0.0)
    val isOverBudget = spent > limit
    val isApproachingLimit = !isOverBudget && percentage >= 0.8f

    val statusMessage = when {
        isOverBudget -> "🚨 Budget Limit Exceeded!"
        isApproachingLimit -> "⚠️ Warning: Approaching Budget Limit"
        else -> "🟢 Budget is Healthy & Safe"
    }

    val statusColor = when {
        isOverBudget -> AlertCrimson
        isApproachingLimit -> AlertWarning
        else -> PrimaryEmerald
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            // Header with nice Canvas Logo!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NovaLogo()
                    Column {
                        Text(
                            text = "NovaBudget",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateWhite
                        )
                        Text(
                            text = "100% Local Financial Privacy",
                            fontSize = 11.sp,
                            color = SlateGray
                        )
                    }
                }

                // Month dropdown selector!
                MonthSelectorDropdown(
                    selectedMonth = data.selectedMonth,
                    distinctMonths = data.distinctMonths,
                    onMonthSelected = { viewModel.setSelectedMonth(it) }
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // Circular Gauge Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(Color(0xFF2C354A), Color(0xFF1E2332))
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .shadow(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Feature 2: Display month prominently on the dashboard circular gauge card!
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = SlateGray, modifier = Modifier.size(12.dp))
                        Text(
                            text = "BUDGET FOR ${formatMonthLabel(data.selectedMonth).uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateGray,
                            letterSpacing = 1.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(200.dp)
                    ) {
                        // Background track
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = Color(0xFF1A1F30),
                                startAngle = -220f,
                                sweepAngle = 260f,
                                useCenter = false,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Progress arc
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                brush = Brush.linearGradient(
                                    if (isOverBudget) WarningGradient else PrimaryGradient
                                ),
                                startAngle = -220f,
                                sweepAngle = animateProgress * 260f,
                                useCenter = false,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(percentage * 100).toInt()}%",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateWhite
                            )
                            Text(
                                text = "Spent",
                                fontSize = 14.sp,
                                color = SlateGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = statusMessage,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Financial Indicators Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardIndicatorCard(
                    title = "Total Spent",
                    value = "$currency ${formatMoney(spent)}",
                    color = AlertCrimson,
                    icon = Icons.Default.Info,
                    modifier = Modifier.weight(1f)
                )

                DashboardIndicatorCard(
                    title = "Remaining",
                    value = "$currency ${formatMoney(remaining)}",
                    color = if (remaining > 0) PrimaryEmerald else AlertCrimson,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            CashFlowForecastCard(data = data, viewModel = viewModel)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            SubscriptionDashboardCard(data = data, viewModel = viewModel)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Budget Limit Config Card inside Dashboard
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Color(0xFF22283A),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Budget Limit",
                            fontSize = 12.sp,
                            color = SlateGray
                        )
                        Text(
                            text = "$currency ${formatMoney(limit)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateWhite
                        )
                    }
                    Text(
                        text = "Edit Rules tab to configure",
                        fontSize = 11.sp,
                        color = SlateGray
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardIndicatorCard(
    title: String,
    value: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = SlateGray
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SlateWhite,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        }
    }
}

// --- TAB 2: EXPENSES LIST ---

@Composable
fun ExpensesScreen(data: MainScreenUiState.Success, viewModel: MainScreenViewModel) {
    val context = LocalContext.current
    var showManualAddDialog by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(0) } // 0 = Detailed List, 1 = Category Breakdown

    val expandedStates = remember { 
        mutableStateMapOf("Credit Cards" to true, "Bank Accounts" to true, "Cash & Others" to true) 
    }

    // Spending Categorizer Helper (Feature 1)
    fun getTransactionCategory(tx: Transaction): String {
        val body = tx.smsBody.lowercase()
        val merchant = tx.merchant.lowercase()
        val combined = "$body $merchant"
        
        return when {
            combined.contains("swiggy") || combined.contains("zomato") || 
            combined.contains("restaurant") || combined.contains("food") || 
            combined.contains("cafe") || combined.contains("hotel") || 
            combined.contains("bakery") || combined.contains("eats") || 
            combined.contains("dine") || combined.contains("bharatpe") || 
            combined.contains("merchant") -> "🍲 Food & Dining"
            
            combined.contains("amazon") || combined.contains("flipkart") || 
            combined.contains("myntra") || combined.contains("ikea") || 
            combined.contains("lifestyle") || combined.contains("nykaa") || 
            combined.contains("ajio") || combined.contains("mart") || 
            combined.contains("store") || combined.contains("supermarket") || 
            combined.contains("groceries") -> "🛍️ Shopping & Retail"
            
            combined.contains("bill") || combined.contains("electricity") || 
            combined.contains("telecom") || combined.contains("insurance") || 
            combined.contains("recharge") || combined.contains("phonepe") || 
            combined.contains("gpay") || combined.contains("gas") || 
            combined.contains("water") || combined.contains("axis bank l") -> "⚡ Bills & Utilities"
            
            combined.contains("uber") || combined.contains("ola") || 
            combined.contains("petrol") || combined.contains("fuel") || 
            combined.contains("metro") || combined.contains("irctc") || 
            combined.contains("rail") || combined.contains("flight") || 
            combined.contains("cab") -> "🚗 Commute & Travel"
            
            else -> "📦 Others"
        }
    }

    val creditCards = data.cardConfigs
    val bankAccounts = data.accountConfigs
    
    // Categorize transactions into: Credit Cards, Bank Accounts, Cash/Others
    val txsByMainCategory = remember(data.transactions, creditCards, bankAccounts) {
        data.transactions.groupBy { tx ->
            val isCC = creditCards.any { it.lastDigits == tx.lastDigits || it.cardName == tx.accountName }
            val isBank = bankAccounts.any { it.lastDigits == tx.lastDigits || it.accountName == tx.accountName }
            
            when {
                isCC -> "Credit Cards"
                isBank -> "Bank Accounts"
                else -> "Cash & Others"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Expenses List",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateWhite
                )
                MonthSelectorDropdown(
                    selectedMonth = data.selectedMonth,
                    distinctMonths = data.distinctMonths,
                    onMonthSelected = { viewModel.setSelectedMonth(it) }
                )
            }
            IconButton(
                onClick = { showManualAddDialog = true },
                colors = IconButtonDefaults.iconButtonColors(containerColor = DarkSurfaceVariant)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Manually", tint = PrimaryEmerald)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Premium Segmented Toggle Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (viewMode == 0) Color(0xFF1E2332) else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        1.dp,
                        if (viewMode == 0) PrimaryEmerald.copy(alpha = 0.5f) else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { viewMode = 0 }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Detailed List",
                    color = if (viewMode == 0) PrimaryEmerald else SlateGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (viewMode == 1) Color(0xFF1E2332) else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        1.dp,
                        if (viewMode == 1) PrimaryEmerald.copy(alpha = 0.5f) else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { viewMode = 1 }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Category Breakdown",
                    color = if (viewMode == 1) PrimaryEmerald else SlateGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (data.transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SlateGray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No expenses logged in ${formatMonthLabel(data.selectedMonth)}.",
                        color = SlateGray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "They will appear here as soon as an SMS debits your accounts, or if you add one manually.",
                        color = SlateGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else if (viewMode == 0) {
            // VIEW MODE 0: Standard Linear Transaction List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(data.transactions) { tx ->
                    TransactionItemRow(tx = tx, currencySymbol = data.currencySymbol) {
                        viewModel.deleteTransaction(tx.id)
                    }
                }
            }
        } else {
            // VIEW MODE 1: Hierarchical Category Breakdown Tree View (Feature 1)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                val mainCategories = listOf("Credit Cards", "Bank Accounts", "Cash & Others")
                
                mainCategories.forEach { mainCat ->
                    val txs = txsByMainCategory[mainCat] ?: emptyList()
                    val totalSpent = txs.sumOf { it.amount }
                    
                    if (txs.isNotEmpty() || mainCat != "Cash & Others") {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Expandable Category Header Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                expandedStates[mainCat] = !(expandedStates[mainCat] ?: false) 
                                            }
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = when (mainCat) {
                                                    "Credit Cards" -> Icons.Default.ShoppingCart
                                                    "Bank Accounts" -> Icons.Default.AccountBox
                                                    else -> Icons.Default.Info
                                                },
                                                contentDescription = null,
                                                tint = if (mainCat == "Credit Cards") SecondaryTeal else if (mainCat == "Bank Accounts") PrimaryEmerald else SlateGray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = mainCat,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SlateWhite
                                                )
                                                Text(
                                                    text = "${txs.size} spends registered",
                                                    fontSize = 11.sp,
                                                    color = SlateGray
                                                )
                                            }
                                        }
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "${data.currencySymbol} ${formatMoney(totalSpent)}",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SlateWhite
                                            )
                                            Icon(
                                                imageVector = if (expandedStates[mainCat] == true) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = SlateGray
                                            )
                                        }
                                    }
                                    
                                    // Category Details Block
                                    AnimatedVisibility(visible = expandedStates[mainCat] == true) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(DarkBackground)
                                                .padding(bottom = 16.dp)
                                        ) {
                                            if (txs.isEmpty()) {
                                                Text(
                                                    text = "No spends in this category.",
                                                    color = SlateGray,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                )
                                            } else {
                                                // Group by Account/Card Config Labels
                                                val txsByAccount = txs.groupBy { it.accountName }
                                                
                                                txsByAccount.forEach { (accountName, accountTxs) ->
                                                    val accountTotal = accountTxs.sumOf { it.amount }
                                                    
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                                            .background(DarkSurface, RoundedCornerShape(12.dp))
                                                            .border(1.dp, Color(0xFF22283A), RoundedCornerShape(12.dp))
                                                            .padding(12.dp)
                                                    ) {
                                                        // Specific account total header
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = accountName,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = SlateWhite
                                                            )
                                                            Text(
                                                                text = "${data.currencySymbol} ${formatMoney(accountTotal)}",
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = PrimaryEmerald
                                                            )
                                                        }
                                                        
                                                        Divider(color = Color(0xFF1E2332), modifier = Modifier.padding(vertical = 8.dp))
                                                        
                                                        // Sub-group by Dynamic Spending Categories
                                                        val txsByCategory = accountTxs.groupBy { getTransactionCategory(it) }
                                                        
                                                        txsByCategory.forEach { (catName, catTxs) ->
                                                            val catTotal = catTxs.sumOf { it.amount }
                                                            
                                                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = catName,
                                                                        fontSize = 12.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = SlateWhite.copy(alpha = 0.9f)
                                                                    )
                                                                    Text(
                                                                        text = "${data.currencySymbol} ${formatMoney(catTotal)}",
                                                                        fontSize = 12.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = SlateWhite
                                                                    )
                                                                }
                                                                
                                                                // Merchant level dynamic rollups
                                                                val txsByMerchant = catTxs.groupBy { it.merchant }
                                                                txsByMerchant.forEach { (merchant, merchantTxs) ->
                                                                    val merchantTotal = merchantTxs.sumOf { it.amount }
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .padding(start = 12.dp, top = 2.dp),
                                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                                    ) {
                                                                        Text(
                                                                            text = "• $merchant",
                                                                            fontSize = 11.sp,
                                                                            color = SlateGray
                                                                        )
                                                                        Text(
                                                                            text = "${data.currencySymbol} ${formatMoney(merchantTotal)}",
                                                                            fontSize = 11.sp,
                                                                            color = SlateGray
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
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManualAddDialog) {
        ManualAddTransactionDialog(
            currencySymbol = data.currencySymbol,
            selectedMonth = data.selectedMonth,
            onDismiss = { showManualAddDialog = false },
            onSave = { amount, accName, digits, merchant, manualTimestamp ->
                showManualAddDialog = false
                val hash = UUID.randomUUID().toString()
                val tx = Transaction(
                    amount = amount,
                    accountName = accName,
                    lastDigits = digits,
                    merchant = merchant,
                    timestamp = manualTimestamp,
                    smsSender = "Manual Entry",
                    smsBody = "Manually entered transaction of $amount",
                    isSynced = 0,
                    syncHash = hash
                )
                viewModel.addTransaction(tx)
                Toast.makeText(context, "Transaction saved!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun TransactionItemRow(tx: Transaction, currencySymbol: String, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val formattedDate = remember(tx.timestamp) {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(tx.timestamp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF1E2332), CircleShape)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (tx.smsSender == "Manual Entry") Icons.Default.AccountBox else Icons.Default.Email,
                            tint = if (tx.isSynced == 1) TertiaryIndigo else PrimaryEmerald,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tx.merchant,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${tx.accountName} (ending ${tx.lastDigits})",
                            fontSize = 11.sp,
                            color = SlateGray
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$currencySymbol ${formatMoney(tx.amount)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlertCrimson
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = SlateGray.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF1E2332), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SMS Sender: ${tx.smsSender}",
                        fontSize = 10.sp,
                        color = SlateGray
                    )
                    Text(
                        text = "Time: $formattedDate",
                        fontSize = 10.sp,
                        color = SlateGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tx.smsBody,
                        fontSize = 11.sp,
                        color = SlateWhite.copy(alpha = 0.8f),
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F111A), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

// --- TAB 3: CONFIGURATION ---

@Composable
fun ConfigScreen(data: MainScreenUiState.Success, viewModel: MainScreenViewModel) {
    val context = LocalContext.current
    var budgetInput by remember { mutableStateOf(data.budgetLimit.toString()) }
    var selectedCurrency by remember { mutableStateOf(data.currencySymbol) }

    // Cards configuration inputs
    var newCardName by remember { mutableStateOf("") }
    var newCardDigits by remember { mutableStateOf("") }
    var newCardKeywords by remember { mutableStateOf("spent,debited,charged") }
    var newCardBillingCycleDay by remember { mutableStateOf("0") }

    // Bank Account configuration inputs
    var newAccountName by remember { mutableStateOf("") }
    var newAccountDigits by remember { mutableStateOf("") }
    var newAccountKeywords by remember { mutableStateOf("debited,withdrawn") }

    // Rule Edit Dialog triggers
    var cardToEdit by remember { mutableStateOf<CardConfig?>(null) }
    var accountToEdit by remember { mutableStateOf<AccountConfig?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NovaLogo()
                Column {
                    Text(
                        text = "Rules & Configs",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateWhite
                    )
                    Text(
                        text = "Manage limits, accounts, and triggers.",
                        fontSize = 12.sp,
                        color = SlateGray
                    )
                }
            }
        }

        // Section 1: Monthly Budget & Currency
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Monthly Budget Limits",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateWhite
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextField(
                            value = budgetInput,
                            onValueChange = { budgetInput = it },
                            label = { Text("Budget Limit") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground,
                                focusedIndicatorColor = PrimaryEmerald,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedLabelColor = PrimaryEmerald,
                                unfocusedLabelColor = SlateGray
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        TextField(
                            value = selectedCurrency,
                            onValueChange = { selectedCurrency = it },
                            label = { Text("Currency") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground,
                                focusedIndicatorColor = PrimaryEmerald,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedLabelColor = PrimaryEmerald,
                                unfocusedLabelColor = SlateGray
                            ),
                            modifier = Modifier.width(90.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val limit = budgetInput.toFloatOrNull() ?: 50000f
                            viewModel.setBudgetLimit(limit)
                            viewModel.setCurrencySymbol(selectedCurrency)
                            Toast.makeText(context, "Budget settings updated!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Budget Configuration", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section 2: Credit Card Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Credit Cards Configuration",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateWhite
                    )
                    Text(
                        text = "App scans for card last digits and keywords in SMS.",
                        fontSize = 10.sp,
                        color = SlateGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Input Form
                    TextField(
                        value = newCardName,
                        onValueChange = { newCardName = it },
                        label = { Text("Card Label (e.g. Primary Card)") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedIndicatorColor = PrimaryEmerald
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = newCardDigits,
                            onValueChange = { newCardDigits = it },
                            label = { Text("Last Digits (e.g. 1234)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground,
                                focusedIndicatorColor = PrimaryEmerald
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        TextField(
                            value = newCardKeywords,
                            onValueChange = { newCardKeywords = it },
                            label = { Text("SMS Keywords") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground,
                                focusedIndicatorColor = PrimaryEmerald
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = newCardBillingCycleDay,
                        onValueChange = { newCardBillingCycleDay = it },
                        label = { Text("Billing Cutoff Day (1-28, 0 = Calendar Month)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedIndicatorColor = PrimaryEmerald
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (newCardName.isEmpty() || newCardDigits.isEmpty()) {
                                Toast.makeText(context, "Fill all card fields!", Toast.LENGTH_SHORT).show()
                            } else {
                                val day = newCardBillingCycleDay.toIntOrNull() ?: 0
                                viewModel.addCardConfig(newCardName, newCardDigits, newCardKeywords, day)
                                newCardName = ""
                                newCardDigits = ""
                                newCardBillingCycleDay = "0"
                                Toast.makeText(context, "Credit Card rule added!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Credit Card Rule", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("ACTIVE CREDIT CARD RULES", fontSize = 11.sp, color = SlateGray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    data.cardConfigs.forEach { card ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBackground, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(card.cardName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SlateWhite)
                                Text(
                                    text = "Ends in: ${card.lastDigits} | Keywords: ${card.keywords}" + 
                                           if (card.billingCycleDay > 0) " | Cutoff: Day ${card.billingCycleDay}" else " | Calendar Month",
                                    fontSize = 10.sp,
                                    color = SlateGray
                                )
                            }
                            
                            // Edit & Delete buttons for Feature 3 card updates!
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Card",
                                    tint = SecondaryTeal,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { cardToEdit = card }
                                )
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Card",
                                    tint = AlertCrimson,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { viewModel.deleteCardConfig(card.id) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Section 3: Bank Accounts Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Bank Accounts Configuration",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateWhite
                    )
                    Text(
                        text = "App scans for account digits and keywords in SMS.",
                        fontSize = 10.sp,
                        color = SlateGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Input Form
                    TextField(
                        value = newAccountName,
                        onValueChange = { newAccountName = it },
                        label = { Text("Account Label (e.g. Primary Savings)") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedIndicatorColor = PrimaryEmerald
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = newAccountDigits,
                            onValueChange = { newAccountDigits = it },
                            label = { Text("Last Digits (e.g. 5678)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground,
                                focusedIndicatorColor = PrimaryEmerald
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        TextField(
                            value = newAccountKeywords,
                            onValueChange = { newAccountKeywords = it },
                            label = { Text("SMS Keywords") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground,
                                focusedIndicatorColor = PrimaryEmerald
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (newAccountName.isEmpty() || newAccountDigits.isEmpty()) {
                                Toast.makeText(context, "Fill all account fields!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.addAccountConfig(newAccountName, newAccountDigits, newAccountKeywords)
                                newAccountName = ""
                                newAccountDigits = ""
                                Toast.makeText(context, "Bank Account rule added!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Bank Account Rule", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("ACTIVE BANK ACCOUNT RULES", fontSize = 11.sp, color = SlateGray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    data.accountConfigs.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBackground, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.accountName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SlateWhite)
                                Text("Digits: ${acc.lastDigits} | Keywords: ${acc.keywords}", fontSize = 10.sp, color = SlateGray)
                            }
                            
                            // Edit & Delete buttons for Feature 3 bank account updates!
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Account",
                                    tint = SecondaryTeal,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { accountToEdit = acc }
                                )
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Account",
                                    tint = AlertCrimson,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { viewModel.deleteAccountConfig(acc.id) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Danger zone: Reset app transactions
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AlertCrimson.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Danger Zone", color = AlertCrimson, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.clearAllTransactions()
                            Toast.makeText(context, "All transaction records deleted!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertCrimson),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear All Transactions History", color = SlateWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // --- FEATURE 3 EDIT DIALOGS ---

    cardToEdit?.let { card ->
        EditRuleDialog(
            title = "Edit Credit Card Rule",
            initialName = card.cardName,
            initialDigits = card.lastDigits,
            initialKeywords = card.keywords,
            initialBillingCycleDay = card.billingCycleDay,
            onDismiss = { cardToEdit = null },
            onSave = { name, digits, keywords, billingCycleDay ->
                cardToEdit = null
                viewModel.updateCardConfig(card.id, name, digits, keywords, billingCycleDay)
                Toast.makeText(context, "Credit Card rule updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    accountToEdit?.let { acc ->
        EditRuleDialog(
            title = "Edit Bank Account Rule",
            initialName = acc.accountName,
            initialDigits = acc.lastDigits,
            initialKeywords = acc.keywords,
            initialBillingCycleDay = -1,
            onDismiss = { accountToEdit = null },
            onSave = { name, digits, keywords, _ ->
                accountToEdit = null
                viewModel.updateAccountConfig(acc.id, name, digits, keywords)
                Toast.makeText(context, "Bank Account rule updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// --- TAB 4: BLUETOOTH SYNC SCREEN ---

@Composable
fun SyncScreen(
    data: MainScreenUiState.Success,
    syncLog: String,
    isServerActive: Boolean,
    onToggleServer: (Boolean) -> Unit,
    onTriggerSync: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NovaLogo()
                Column {
                    Text(
                        text = "P2P Offline Sync",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateWhite
                    )
                    Text(
                        text = "Securely synchronize spends completely offline.",
                        fontSize = 12.sp,
                        color = SlateGray
                    )
                }
            }
        }

        // Bluetooth Permissions & Pairing Guide
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = TertiaryIndigo, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "P2P Bluetooth Sync Guide",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "1. First, pair both android phones together in your Android system Bluetooth settings.",
                        fontSize = 12.sp,
                        color = SlateWhite.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "2. On the Host phone, turn ON the \"Sync Server Active\" switch below.",
                        fontSize = 12.sp,
                        color = SlateWhite.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "3. On the other phone, tap \"Sync Database\" and select the Host device from the paired devices list.",
                        fontSize = 12.sp,
                        color = SlateWhite.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "4. Your databases will merge bidirectionally in seconds, keeping notifications and spends perfectly shared!",
                        fontSize = 12.sp,
                        color = SlateWhite.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Server Switch Panel
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Sync Server Mode",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateWhite
                            )
                            Text(
                                text = "Enables this phone to host connection.",
                                fontSize = 11.sp,
                                color = SlateGray
                            )
                        }
                        Switch(
                            checked = isServerActive,
                            onCheckedChange = onToggleServer,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PrimaryEmerald,
                                checkedTrackColor = PrimaryEmerald.copy(alpha = 0.5f),
                                uncheckedThumbColor = SlateGray,
                                uncheckedTrackColor = Color(0xFF1E2332)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBackground, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isServerActive) PrimaryEmerald else SlateGray, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isServerActive) "Server Running: Listening for spouse..." else "Server Offline",
                            fontSize = 11.sp,
                            color = SlateWhite
                        )
                    }
                }
            }
        }

        // Sync Trigger Panel
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sync Client Mode",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateWhite
                    )
                    Text(
                        text = "Connects to spouse's running server to merge records.",
                        fontSize = 11.sp,
                        color = SlateGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onTriggerSync,
                        colors = ButtonDefaults.buttonColors(containerColor = TertiaryIndigo),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = SlateWhite)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Database With Spouse", color = SlateWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // SMS Historical Import Panel (Feature 4)
        item {
            var selectedMonths by remember { mutableStateOf(6) }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var isImporting by remember { mutableStateOf(false) }
            var importStatusText by remember { mutableStateOf("") }

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Import Historical SMS",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateWhite
                            )
                            Text(
                                text = "Scan local inbox to parse messages before app was installed",
                                fontSize = 11.sp,
                                color = SlateGray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "SELECT SCAN DURATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateGray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card 1: 6 Months
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selectedMonths == 6) Color(0xFF1E2332) else DarkBackground,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selectedMonths == 6) PrimaryEmerald else Color(0xFF22283A),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedMonths = 6 }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Last 6 Months",
                                color = if (selectedMonths == 6) PrimaryEmerald else SlateWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Card 2: 1 Year
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selectedMonths == 12) Color(0xFF1E2332) else DarkBackground,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selectedMonths == 12) PrimaryEmerald else Color(0xFF22283A),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedMonths = 12 }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Last 1 Year",
                                color = if (selectedMonths == 12) PrimaryEmerald else SlateWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isImporting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = PrimaryEmerald,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Scanning SMS Inbox...",
                                color = SlateWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                isImporting = true
                                importStatusText = ""
                                scope.launch {
                                    val count = withContext(Dispatchers.IO) {
                                        SmsReceiver.scanHistoricalSms(context, selectedMonths)
                                    }
                                    isImporting = false
                                    importStatusText = "Scan Complete! Imported $count new transactions."
                                    Toast.makeText(context, "Imported $count transactions!", Toast.LENGTH_SHORT).show()
                                    onRefresh()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan & Import Local Inbox", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (importStatusText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F261B), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF1E5336), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = importStatusText,
                                fontSize = 11.sp,
                                color = SlateWhite,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Live Log Output
        item {
            Column {
                Text(
                    text = "SYNC TRANSACTION LOGS",
                    fontSize = 11.sp,
                    color = SlateGray,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF22283A), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = syncLog,
                        color = SlateWhite.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

// --- DIALOGS & UTILS ---

@SuppressLint("MissingPermission")
@Composable
fun DeviceSelectionDialog(
    devices: List<BluetoothDevice>,
    onDismiss: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Select Spouse's Phone",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateWhite
                )
                Text(
                    text = "Choose from currently paired devices",
                    fontSize = 11.sp,
                    color = SlateGray
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 240.dp)
                ) {
                    items(devices) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBackground, RoundedCornerShape(8.dp))
                                .clickable { onDeviceSelected(device) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccountBox, contentDescription = null, tint = SecondaryTeal)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = device.name ?: "Unknown Device",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateWhite
                                )
                                Text(
                                    text = device.address ?: "",
                                    fontSize = 10.sp,
                                    color = SlateGray
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = PrimaryEmerald)
                }
            }
        }
    }
}

// Dialog for adding manual entries inside a specific selected month segment
@Composable
fun ManualAddTransactionDialog(
    currencySymbol: String,
    selectedMonth: String,
    onDismiss: () -> Unit,
    onSave: (amount: Double, accName: String, digits: String, merchant: String, timestamp: Long) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("Cash") }
    var digits by remember { mutableStateOf("0000") }
    var merchant by remember { mutableStateOf("Local Store") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Add Transaction",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateWhite
                )
                Text(
                    text = "Will be added to ${formatMonthLabel(selectedMonth)}",
                    fontSize = 11.sp,
                    color = SlateGray
                )
                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedIndicatorColor = PrimaryEmerald
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant Name") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedIndicatorColor = PrimaryEmerald
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text("Account/Card Label") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedIndicatorColor = PrimaryEmerald
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    TextField(
                        value = digits,
                        onValueChange = { digits = it },
                        label = { Text("Last 4 Digits") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedIndicatorColor = PrimaryEmerald
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SlateGray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            if (amt > 0.0) {
                                // Smart timestamp generation:
                                // If the selected month is the current calendar month, use the current active time.
                                // Otherwise, use the 1st of that historical month so it belongs to the segment!
                                val currentSdf = SimpleDateFormat("yyyy-MM", Locale.US)
                                val currentMonthString = currentSdf.format(Date())
                                
                                val finalTimestamp = if (selectedMonth == currentMonthString) {
                                    System.currentTimeMillis()
                                } else {
                                    val cal = Calendar.getInstance()
                                    val parts = selectedMonth.split("-")
                                    val year = parts[0].toIntOrNull() ?: cal.get(Calendar.YEAR)
                                    val month = parts[1].toIntOrNull()?.minus(1) ?: cal.get(Calendar.MONTH)
                                    cal.set(Calendar.YEAR, year)
                                    cal.set(Calendar.MONTH, month)
                                    cal.set(Calendar.DAY_OF_MONTH, 1)
                                    cal.set(Calendar.HOUR_OF_DAY, 12) // midday
                                    cal.timeInMillis
                                }
                                
                                onSave(amt, accountName, digits, merchant, finalTimestamp)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Dialog for editing credit cards or bank account configurations (Feature 3)
@Composable
fun EditRuleDialog(
    title: String,
    initialName: String,
    initialDigits: String,
    initialKeywords: String,
    initialBillingCycleDay: Int = -1,
    onDismiss: () -> Unit,
    onSave: (name: String, digits: String, keywords: String, billingCycleDay: Int) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var digits by remember { mutableStateOf(initialDigits) }
    var keywords by remember { mutableStateOf(initialKeywords) }
    var billingCycleDayStr by remember { mutableStateOf(if (initialBillingCycleDay >= 0) initialBillingCycleDay.toString() else "0") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateWhite
                )
                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Label") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedIndicatorColor = PrimaryEmerald
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = digits,
                        onValueChange = { digits = it },
                        label = { Text("Last Digits") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedIndicatorColor = PrimaryEmerald
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    TextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text("SMS Keywords") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedIndicatorColor = PrimaryEmerald
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (initialBillingCycleDay >= 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = billingCycleDayStr,
                        onValueChange = { billingCycleDayStr = it },
                        label = { Text("Billing Cutoff Day (1-28, 0 = Calendar Month)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedIndicatorColor = PrimaryEmerald
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SlateGray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotEmpty() && digits.isNotEmpty()) {
                                val day = billingCycleDayStr.toIntOrNull() ?: 0
                                onSave(name, digits, keywords, day)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Changes", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatMoney(value: Double): String {
    return String.format("%,.2f", value)
}

data class CashFlowPoint(val dayLabel: String, val balance: Double)

fun calculateCashFlowProjections(
    transactions: List<Transaction>,
    cardConfigs: List<CardConfig>,
    startingBalance: Double,
    monthlyIncome: Double,
    salaryDay: Int
): List<CashFlowPoint> {
    val list = mutableListOf<CashFlowPoint>()
    val cal = java.util.Calendar.getInstance()
    val todayMs = cal.timeInMillis

    // rolling average spend over last 30 days
    val thirtyDaysAgo = todayMs - 30L * 24 * 60 * 60 * 1000
    val recentTx = transactions.filter { it.timestamp >= thirtyDaysAgo }
    val totalRecentSpend = recentTx.sumOf { it.amount }
    val dailySpendEstimate = if (recentTx.isNotEmpty()) totalRecentSpend / 30.0 else 1000.0

    var currentProjBalance = startingBalance
    val sdf = java.text.SimpleDateFormat("dd MMM", java.util.Locale.US)

    for (dayOffset in 0..30) {
        val projCal = java.util.Calendar.getInstance().apply {
            timeInMillis = todayMs
            add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
        }
        val projDay = projCal.get(java.util.Calendar.DAY_OF_MONTH)

        if (dayOffset > 0) {
            currentProjBalance -= dailySpendEstimate
        }

        if (projDay == salaryDay) {
            currentProjBalance += monthlyIncome
        }

        // CC Payoff dates: cutoffDay + 15 days
        for (card in cardConfigs) {
            if (card.billingCycleDay > 0) {
                val payoffDay = (card.billingCycleDay + 15)
                val isPayoffToday = if (payoffDay > 30) {
                    val wrappedPayoff = payoffDay - 30
                    projDay == wrappedPayoff
                } else {
                    projDay == payoffDay
                }

                if (isPayoffToday) {
                    val cardSpend = transactions.filter {
                        (it.accountName == card.cardName || it.lastDigits == card.lastDigits)
                    }.sumOf { it.amount }
                    val statementDeduction = if (cardSpend > 0.0) cardSpend else 5000.0
                    currentProjBalance -= statementDeduction
                }
            }
        }

        list.add(CashFlowPoint(sdf.format(projCal.time), currentProjBalance))
    }
    return list
}

@Composable
fun CashFlowLineChart(
    points: List<CashFlowPoint>,
    currency: String,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val maxVal = points.maxOf { it.balance }.coerceAtLeast(10000.0)
    val minVal = points.minOf { it.balance }.coerceAtMost(0.0)
    val valRange = (maxVal - minVal).coerceAtLeast(1.0)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val spacingX = width / (points.size - 1)

            fun getY(balance: Double): Float {
                val ratio = (balance - minVal) / valRange
                return (height - (ratio * height)).toFloat()
            }

            if (minVal < 0.0 && maxVal > 0.0) {
                val zeroY = getY(0.0)
                drawLine(
                    color = Color(0xFFEF4444).copy(alpha = 0.3f),
                    start = androidx.compose.ui.geometry.Offset(0f, zeroY),
                    end = androidx.compose.ui.geometry.Offset(width, zeroY),
                    strokeWidth = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            val linePath = Path()
            val fillPath = Path()

            points.forEachIndexed { idx, pt ->
                val x = idx * spacingX
                val y = getY(pt.balance)

                if (idx == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }

                if (idx == points.size - 1) {
                    fillPath.lineTo(x, height)
                    fillPath.close()
                }
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF10B981).copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )

            drawPath(
                path = linePath,
                brush = Brush.linearGradient(
                    listOf(Color(0xFF10B981), Color(0xFF06B6D4))
                ),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            val minPtIdx = points.indexOfFirst { it.balance == points.minOf { p -> p.balance } }
            val maxPtIdx = points.indexOfFirst { it.balance == points.maxOf { p -> p.balance } }

            listOf(0, points.size - 1, minPtIdx, maxPtIdx).distinct().forEach { idx ->
                if (idx in points.indices) {
                    val pt = points[idx]
                    val x = idx * spacingX
                    val y = getY(pt.balance)

                    drawCircle(
                        color = Color(0xFF121622),
                        radius = 6.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                    drawCircle(
                        brush = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF06B6D4))),
                        radius = 4.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                }
            }
        }
    }
}

@Composable
fun CashFlowForecastCard(
    data: MainScreenUiState.Success,
    viewModel: MainScreenViewModel
) {
    var showDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val currency = data.currencySymbol
    val startingBalance = data.startingBalance
    val monthlyIncome = data.monthlyIncome
    val incomeDay = data.incomeDay

    val points = remember(data.transactions, data.cardConfigs, startingBalance, monthlyIncome, incomeDay) {
        calculateCashFlowProjections(
            data.transactions,
            data.cardConfigs,
            startingBalance,
            monthlyIncome,
            incomeDay
        )
    }

    val minBalance = points.minOf { it.balance }
    val endBalance = points.lastOrNull()?.balance ?: startingBalance

    val isAlert = minBalance < 0.0
    val isWarning = !isAlert && minBalance < 10000.0

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isAlert) Color(0xFFEF4444).copy(alpha = 0.5f) else if (isWarning) Color(0xFFF59E0B).copy(alpha = 0.5f) else Color(0xFF22283A),
                RoundedCornerShape(24.dp)
            )
            .shadow(6.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (isAlert) AlertCrimson else if (isWarning) AlertWarning else PrimaryEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "30-DAY CASH FORECAST",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateWhite,
                            letterSpacing = 1.2.sp
                        )
                    }
                    Text(
                        text = "Predicted cash levels based on billing cycles.",
                        fontSize = 10.sp,
                        color = SlateGray
                    )
                }

                IconButton(
                    onClick = { showDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Forecast Settings",
                        tint = SlateGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            CashFlowLineChart(
                points = points,
                currency = currency,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Today\n${currency} ${formatMoney(startingBalance)}",
                    fontSize = 10.sp,
                    color = SlateGray,
                    textAlign = TextAlign.Start
                )
                Text(
                    text = "Day 30\n${currency} ${formatMoney(endBalance)}",
                    fontSize = 10.sp,
                    color = if (endBalance < 0.0) AlertCrimson else PrimaryEmerald,
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF22283A), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isAlert) AlertCrimson.copy(alpha = 0.1f) else if (isWarning) AlertWarning.copy(alpha = 0.1f) else PrimaryEmerald.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Text(
                        text = when {
                            isAlert -> "🚨 Critical: Cash projected to run out!"
                            isWarning -> "⚠️ Alert: Low balance projected before salary."
                            else -> "🟢 Projected Cash Levels are Healthy"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAlert) AlertCrimson else if (isWarning) AlertWarning else PrimaryEmerald
                    )
                    Text(
                        text = when {
                            isAlert -> "CC bill statements will push your balance negative (Min: ${currency} ${formatMoney(minBalance)})."
                            isWarning -> "Estimated spend will dip balance below ₹10,000 threshold before your salary on day $incomeDay."
                            else -> "Estimated payoff dates are well-cushioned by your ${currency} ${formatMoney(monthlyIncome)} salary."
                        },
                        fontSize = 10.sp,
                        color = SlateGray
                    )
                }
            }
        }
    }

    if (showDialog) {
        var salaryStr by remember { mutableStateOf(monthlyIncome.toInt().toString()) }
        var salaryDayStr by remember { mutableStateOf(incomeDay.toString()) }
        var startingBalStr by remember { mutableStateOf(startingBalance.toInt().toString()) }

        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Forecast Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateWhite
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = startingBalStr,
                        onValueChange = { startingBalStr = it },
                        label = { Text("Starting Bank Account Balance") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedIndicatorColor = PrimaryEmerald
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = salaryStr,
                            onValueChange = { salaryStr = it },
                            label = { Text("Monthly Salary") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground,
                                focusedIndicatorColor = PrimaryEmerald
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        TextField(
                            value = salaryDayStr,
                            onValueChange = { salaryDayStr = it },
                            label = { Text("Salary Day (1-31)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground,
                                focusedIndicatorColor = PrimaryEmerald
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDialog = false }) {
                            Text("Cancel", color = SlateGray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val bal = startingBalStr.toDoubleOrNull() ?: 50000.0
                                val salary = salaryStr.toDoubleOrNull() ?: 100000.0
                                val day = salaryDayStr.toIntOrNull() ?: 30
                                viewModel.setStartingBalance(bal)
                                viewModel.setMonthlyIncome(salary)
                                viewModel.setIncomeDay(day.coerceIn(1, 31))
                                showDialog = false
                                Toast.makeText(context, "Forecast configurations saved!", Toast.LENGTH_SHORT).show()
                            },
                             colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Configurations", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// --- FEATURE 2: SUBSCRIPTION MANAGER ---

@Composable
fun SubscriptionDashboardCard(data: MainScreenUiState.Success, viewModel: MainScreenViewModel) {
    val currency = data.currencySymbol
    val subscriptions = data.subscriptions
    val suggestions = data.suggestedSubscriptions
    val context = LocalContext.current

    // Calculate total monthly overhead
    val totalOverhead = subscriptions.sumOf { sub ->
        if (sub.billingCycle.lowercase() == "yearly") {
            sub.amount / 12.0
        } else {
            sub.amount
        }
    }

    var isExpanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color(0xFF2C354A), Color(0xFF1E2332))),
                RoundedCornerShape(24.dp)
            )
            .shadow(8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Subscriptions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateWhite
                        )
                        Text(
                            text = "Recurring bills & alerts",
                            fontSize = 11.sp,
                            color = SlateGray
                        )
                    }
                }

                // Total text
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$currency ${formatMoney(totalOverhead)}/mo",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryEmerald
                    )
                    Text(
                        text = "${subscriptions.size} tracked",
                        fontSize = 10.sp,
                        color = SlateGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Suggestions Area (if available)
            if (suggestions.isNotEmpty()) {
                Text(
                    text = "POTENTIAL SUBSCRIPTIONS DETECTED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AlertWarning,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                suggestions.forEach { sug ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkBackground),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .border(1.dp, Color(0xFF3B2F1F), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sug.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateWhite
                                )
                                Text(
                                    text = "Billed monthly: $currency ${formatMoney(sug.amount)}",
                                    fontSize = 11.sp,
                                    color = SlateGray
                                )
                            }

                            TextButton(
                                onClick = {
                                    viewModel.confirmSuggestedSubscription(sug)
                                    Toast.makeText(context, "Tracking ${sug.name}!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = PrimaryEmerald),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("Track", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 2. Expand/Collapse toggle and List of Active Subscriptions
            if (subscriptions.isNotEmpty()) {
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = SlateWhite)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "Hide Subscription Details" else "Show Tracking Details",
                            fontSize = 12.sp
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    subscriptions.forEach { sub ->
                        val renewalText = remember(sub.nextRenewalDate) {
                            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
                            sdf.format(java.util.Date(sub.nextRenewalDate))
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF131722))
                                .border(1.dp, Color(0xFF1E2332), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sub.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateWhite
                                )
                                Text(
                                    text = "Renews: $renewalText (${sub.billingCycle})",
                                    fontSize = 11.sp,
                                    color = SlateGray
                                )
                                Text(
                                    text = "$currency ${formatMoney(sub.amount)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryEmerald
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Alarm Alert Toggle
                                Icon(
                                    imageVector = if (sub.isReminderEnabled == 1) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = if (sub.isReminderEnabled == 1) PrimaryEmerald else SlateGray,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable {
                                            val updated = sub.copy(isReminderEnabled = if (sub.isReminderEnabled == 1) 0 else 1)
                                            viewModel.updateSubscription(updated)
                                            Toast.makeText(
                                                context, 
                                                if (updated.isReminderEnabled == 1) "Alarms enabled for ${sub.name}" else "Alarms disabled for ${sub.name}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                )

                                // Delete Button
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = AlertCrimson.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            viewModel.deleteSubscription(sub.id)
                                            Toast.makeText(context, "Deleted tracking for ${sub.name}", Toast.LENGTH_SHORT).show()
                                        }
                                )
                            }
                        }
                    }
                }
            } else if (suggestions.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF131722), RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No subscriptions tracked yet.\nScanner auto-detects recurring SMS spends!",
                        fontSize = 12.sp,
                        color = SlateGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add Custom Subscription button
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Add Custom Subscription", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            currencySymbol = currency,
            onDismiss = { showAddDialog = false },
            onSave = { name, amount, cycle, dayNumber ->
                // Construct next occurrence date
                val cal = java.util.Calendar.getInstance()
                val currentDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
                if (currentDay > dayNumber) {
                    cal.add(java.util.Calendar.MONTH, 1)
                }
                cal.set(java.util.Calendar.DAY_OF_MONTH, dayNumber)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)

                viewModel.addSubscription(name, amount, cycle, cal.timeInMillis)
                showAddDialog = false
                Toast.makeText(context, "Added subscription to $name!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// Dialog for adding manual recurring subscriptions
@Composable
fun AddSubscriptionDialog(
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, cycle: String, dayNumber: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var cycle by remember { mutableStateOf("Monthly") }
    var dayNumberStr by remember { mutableStateOf("1") }
    
    var cycleMenuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Add Subscription",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateWhite
                )
                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subscription Name") },
                    placeholder = { Text("Netflix, Spotify, Gym...") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedIndicatorColor = PrimaryEmerald
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Amount ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedIndicatorColor = PrimaryEmerald
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    TextField(
                        value = dayNumberStr,
                        onValueChange = { dayNumberStr = it },
                        label = { Text("Billing Day (1-28)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedIndicatorColor = PrimaryEmerald
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Cycle Selector (Frosted Dropdown Box)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { cycleMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateWhite),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(Color(0xFF2C354A), Color(0xFF1E2332))))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Billing Cycle: $cycle", fontSize = 14.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = cycleMenuExpanded,
                        onDismissRequest = { cycleMenuExpanded = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Monthly", color = SlateWhite) },
                            onClick = {
                                cycle = "Monthly"
                                cycleMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Yearly", color = SlateWhite) },
                            onClick = {
                                cycle = "Yearly"
                                cycleMenuExpanded = false
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SlateGray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = amountStr.toDoubleOrNull() ?: 0.0
                            val day = dayNumberStr.toIntOrNull()?.coerceIn(1, 28) ?: 1
                            if (name.trim().isNotEmpty() && amount > 0) {
                                onSave(name.trim(), amount, cycle, day)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add", color = Color.White)
                    }
                }
            }
        }
    }
}


