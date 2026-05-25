package com.example.novabudget.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar
import com.example.novabudget.BuildConfig

class NovaDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val prefs = context.getSharedPreferences("nova_prefs", Context.MODE_PRIVATE)

    init {
        try {
            val personalJson = BuildConfig.PERSONAL_RULES_JSON
            if (personalJson.isNotEmpty()) {
                val db = this.writableDatabase
                val cardConfigs = getAllCardConfigs()
                val isOnlyDummyCard = cardConfigs.size == 1 && cardConfigs[0].lastDigits == "1234"
                
                val accountConfigs = getAllAccountConfigs()
                val isOnlyDummyAccount = accountConfigs.size == 1 && accountConfigs[0].lastDigits == "5678"
                
                if (isOnlyDummyCard || isOnlyDummyAccount || (cardConfigs.isEmpty() && accountConfigs.isEmpty())) {
                    db.delete(TABLE_CARDS, null, null)
                    db.delete(TABLE_ACCOUNTS, null, null)
                    
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    val rules = json.decodeFromString<PersonalRules>(personalJson)
                    
                    for (card in rules.cards) {
                        val values = ContentValues().apply {
                            put(COL_CFG_NAME, card.cardName)
                            put(COL_CFG_LAST_DIGITS, card.lastDigits)
                            put(COL_CFG_KEYWORDS, card.keywords.lowercase())
                            put(COL_CFG_BILLING_CYCLE_DAY, card.billingCycleDay)
                        }
                        db.insert(TABLE_CARDS, null, values)
                    }

                    for (acc in rules.accounts) {
                        val values = ContentValues().apply {
                            put(COL_CFG_NAME, acc.accountName)
                            put(COL_CFG_LAST_DIGITS, acc.lastDigits)
                            put(COL_CFG_KEYWORDS, acc.keywords.lowercase())
                        }
                        db.insert(TABLE_ACCOUNTS, null, values)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val DATABASE_NAME = "novabudget.db"
        private const val DATABASE_VERSION = 2

        // Tables
        const val TABLE_TRANSACTIONS = "transactions"
        const val TABLE_CARDS = "card_configs"
        const val TABLE_ACCOUNTS = "account_configs"

        // Transactions Columns
        const val COL_TX_ID = "id"
        const val COL_TX_AMOUNT = "amount"
        const val COL_TX_ACCOUNT = "account_name"
        const val COL_TX_LAST_DIGITS = "last_digits"
        const val COL_TX_MERCHANT = "merchant"
        const val COL_TX_TIMESTAMP = "timestamp"
        const val COL_TX_SENDER = "sms_sender"
        const val COL_TX_BODY = "sms_body"
        const val COL_TX_SYNCED = "is_synced"
        const val COL_TX_HASH = "sync_hash"

        // Cards/Accounts Columns
        const val COL_CFG_ID = "id"
        const val COL_CFG_NAME = "name"
        const val COL_CFG_LAST_DIGITS = "last_digits"
        const val COL_CFG_KEYWORDS = "keywords"
        const val COL_CFG_BILLING_CYCLE_DAY = "billing_cycle_day"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Transactions Table
        val createTxTable = """
            CREATE TABLE $TABLE_TRANSACTIONS (
                $COL_TX_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TX_AMOUNT REAL,
                $COL_TX_ACCOUNT TEXT,
                $COL_TX_LAST_DIGITS TEXT,
                $COL_TX_MERCHANT TEXT,
                $COL_TX_TIMESTAMP INTEGER,
                $COL_TX_SENDER TEXT,
                $COL_TX_BODY TEXT,
                $COL_TX_SYNCED INTEGER DEFAULT 0,
                $COL_TX_HASH TEXT UNIQUE
            )
        """.trimIndent()
        db.execSQL(createTxTable)

        // Cards Config Table
        val createCardsTable = """
            CREATE TABLE $TABLE_CARDS (
                $COL_CFG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CFG_NAME TEXT,
                $COL_CFG_LAST_DIGITS TEXT,
                $COL_CFG_KEYWORDS TEXT,
                $COL_CFG_BILLING_CYCLE_DAY INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createCardsTable)

        // Accounts Config Table
        val createAccountsTable = """
            CREATE TABLE $TABLE_ACCOUNTS (
                $COL_CFG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CFG_NAME TEXT,
                $COL_CFG_LAST_DIGITS TEXT,
                $COL_CFG_KEYWORDS TEXT
            )
        """.trimIndent()
        db.execSQL(createAccountsTable)

        var loadedPersonalRules = false
        val personalJson = BuildConfig.PERSONAL_RULES_JSON
        if (personalJson.isNotEmpty()) {
            try {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val rules = json.decodeFromString<PersonalRules>(personalJson)
                
                for (card in rules.cards) {
                    val values = ContentValues().apply {
                        put(COL_CFG_NAME, card.cardName)
                        put(COL_CFG_LAST_DIGITS, card.lastDigits)
                        put(COL_CFG_KEYWORDS, card.keywords.lowercase())
                        put(COL_CFG_BILLING_CYCLE_DAY, card.billingCycleDay)
                    }
                    db.insert(TABLE_CARDS, null, values)
                }

                for (acc in rules.accounts) {
                    val values = ContentValues().apply {
                        put(COL_CFG_NAME, acc.accountName)
                        put(COL_CFG_LAST_DIGITS, acc.lastDigits)
                        put(COL_CFG_KEYWORDS, acc.keywords.lowercase())
                    }
                    db.insert(TABLE_ACCOUNTS, null, values)
                }
                
                if (rules.cards.isNotEmpty() || rules.accounts.isNotEmpty()) {
                    loadedPersonalRules = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!loadedPersonalRules) {
            // Insert some default configs for out-of-the-box usability!
            db.execSQL("""
                INSERT INTO $TABLE_CARDS ($COL_CFG_NAME, $COL_CFG_LAST_DIGITS, $COL_CFG_KEYWORDS, $COL_CFG_BILLING_CYCLE_DAY) 
                VALUES ('Dummy Credit Card', '1234', 'spent,debited,charged', 0)
            """.trimIndent())

            db.execSQL("""
                INSERT INTO $TABLE_ACCOUNTS ($COL_CFG_NAME, $COL_CFG_LAST_DIGITS, $COL_CFG_KEYWORDS) 
                VALUES ('Dummy Bank Account', '5678', 'debited')
            """.trimIndent())
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_CARDS ADD COLUMN $COL_CFG_BILLING_CYCLE_DAY INTEGER DEFAULT 0")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- SharedPreferences Settings ---

    fun getBudgetLimit(): Float {
        return prefs.getFloat("budget_limit", 50000.0f) // Default limit 50,000
    }

    fun setBudgetLimit(limit: Float) {
        prefs.edit().putFloat("budget_limit", limit).apply()
    }

    fun getCurrency(): String {
        return prefs.getString("currency", "INR") ?: "INR"
    }

    fun setCurrency(curr: String) {
        prefs.edit().putString("currency", curr).apply()
    }

    fun isSyncServerActive(): Boolean {
        return prefs.getBoolean("sync_server_active", false)
    }

    fun setSyncServerActive(active: Boolean) {
        prefs.edit().putBoolean("sync_server_active", active).apply()
    }

    fun getStartingBalance(): Double {
        return prefs.getFloat("starting_balance", 50000.0f).toDouble()
    }

    fun setStartingBalance(bal: Double) {
        prefs.edit().putFloat("starting_balance", bal.toFloat()).apply()
    }

    fun getMonthlyIncome(): Double {
        return prefs.getFloat("monthly_income", 100000.0f).toDouble()
    }

    fun setMonthlyIncome(income: Double) {
        prefs.edit().putFloat("monthly_income", income.toFloat()).apply()
    }

    fun getIncomeDay(): Int {
        return prefs.getInt("income_day", 30)
    }

    fun setIncomeDay(day: Int) {
        prefs.edit().putInt("income_day", day).apply()
    }


    // --- CRUD Transactions ---

    @Synchronized
    fun addTransaction(tx: Transaction): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_TX_AMOUNT, tx.amount)
            put(COL_TX_ACCOUNT, tx.accountName)
            put(COL_TX_LAST_DIGITS, tx.lastDigits)
            put(COL_TX_MERCHANT, tx.merchant)
            put(COL_TX_TIMESTAMP, tx.timestamp)
            put(COL_TX_SENDER, tx.smsSender)
            put(COL_TX_BODY, tx.smsBody)
            put(COL_TX_SYNCED, tx.isSynced)
            put(COL_TX_HASH, tx.syncHash)
        }
        val result = db.insertWithOnConflict(TABLE_TRANSACTIONS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        return result != -1L
    }

    @Synchronized
    fun getAllTransactions(): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_TRANSACTIONS ORDER BY $COL_TX_TIMESTAMP DESC", null)
        
        if (cursor.moveToFirst()) {
            val idIdx = cursor.getColumnIndexOrThrow(COL_TX_ID)
            val amtIdx = cursor.getColumnIndexOrThrow(COL_TX_AMOUNT)
            val accIdx = cursor.getColumnIndexOrThrow(COL_TX_ACCOUNT)
            val lastIdx = cursor.getColumnIndexOrThrow(COL_TX_LAST_DIGITS)
            val merchIdx = cursor.getColumnIndexOrThrow(COL_TX_MERCHANT)
            val timeIdx = cursor.getColumnIndexOrThrow(COL_TX_TIMESTAMP)
            val sendIdx = cursor.getColumnIndexOrThrow(COL_TX_SENDER)
            val bodyIdx = cursor.getColumnIndexOrThrow(COL_TX_BODY)
            val syncIdx = cursor.getColumnIndexOrThrow(COL_TX_SYNCED)
            val hashIdx = cursor.getColumnIndexOrThrow(COL_TX_HASH)

            do {
                list.add(
                    Transaction(
                        id = cursor.getLong(idIdx),
                        amount = cursor.getDouble(amtIdx),
                        accountName = cursor.getString(accIdx),
                        lastDigits = cursor.getString(lastIdx),
                        merchant = cursor.getString(merchIdx),
                        timestamp = cursor.getLong(timeIdx),
                        smsSender = cursor.getString(sendIdx),
                        smsBody = cursor.getString(bodyIdx),
                        isSynced = cursor.getInt(syncIdx),
                        syncHash = cursor.getString(hashIdx)
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    @Synchronized
    fun getTransactionsForCurrentMonth(): List<Transaction> {
        val calendar = Calendar.getInstance()
        
        // Start of month
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startMs = calendar.timeInMillis
        
        // End of month
        calendar.add(Calendar.MONTH, 1)
        val endMs = calendar.timeInMillis

        val list = mutableListOf<Transaction>()
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_TRANSACTIONS WHERE $COL_TX_TIMESTAMP >= ? AND $COL_TX_TIMESTAMP < ? ORDER BY $COL_TX_TIMESTAMP DESC",
            arrayOf(startMs.toString(), endMs.toString())
        )

        if (cursor.moveToFirst()) {
            val idIdx = cursor.getColumnIndexOrThrow(COL_TX_ID)
            val amtIdx = cursor.getColumnIndexOrThrow(COL_TX_AMOUNT)
            val accIdx = cursor.getColumnIndexOrThrow(COL_TX_ACCOUNT)
            val lastIdx = cursor.getColumnIndexOrThrow(COL_TX_LAST_DIGITS)
            val merchIdx = cursor.getColumnIndexOrThrow(COL_TX_MERCHANT)
            val timeIdx = cursor.getColumnIndexOrThrow(COL_TX_TIMESTAMP)
            val sendIdx = cursor.getColumnIndexOrThrow(COL_TX_SENDER)
            val bodyIdx = cursor.getColumnIndexOrThrow(COL_TX_BODY)
            val syncIdx = cursor.getColumnIndexOrThrow(COL_TX_SYNCED)
            val hashIdx = cursor.getColumnIndexOrThrow(COL_TX_HASH)

            do {
                list.add(
                    Transaction(
                        id = cursor.getLong(idIdx),
                        amount = cursor.getDouble(amtIdx),
                        accountName = cursor.getString(accIdx),
                        lastDigits = cursor.getString(lastIdx),
                        merchant = cursor.getString(merchIdx),
                        timestamp = cursor.getLong(timeIdx),
                        smsSender = cursor.getString(sendIdx),
                        smsBody = cursor.getString(bodyIdx),
                        isSynced = cursor.getInt(syncIdx),
                        syncHash = cursor.getString(hashIdx)
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    @Synchronized
    fun getCurrentMonthTotalSpent(): Double {
        val transactions = getTransactionsForCurrentMonth()
        return transactions.sumOf { it.amount }
    }

    @Synchronized
    fun getTransactionsForMonth(yearMonth: String): List<Transaction> {
        val parts = yearMonth.split("-")
        if (parts.size != 2) return emptyList()
        val year = parts[0].toIntOrNull() ?: return emptyList()
        val month = parts[1].toIntOrNull()?.minus(1) ?: return emptyList() // Calendar month is 0-indexed

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startMs = calendar.timeInMillis
        
        calendar.add(Calendar.MONTH, 1)
        val endMs = calendar.timeInMillis

        val list = mutableListOf<Transaction>()
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM ${TABLE_TRANSACTIONS} WHERE ${COL_TX_TIMESTAMP} >= ? AND ${COL_TX_TIMESTAMP} < ? ORDER BY ${COL_TX_TIMESTAMP} DESC",
            arrayOf(startMs.toString(), endMs.toString())
        )

        if (cursor.moveToFirst()) {
            val idIdx = cursor.getColumnIndexOrThrow(COL_TX_ID)
            val amtIdx = cursor.getColumnIndexOrThrow(COL_TX_AMOUNT)
            val accIdx = cursor.getColumnIndexOrThrow(COL_TX_ACCOUNT)
            val lastIdx = cursor.getColumnIndexOrThrow(COL_TX_LAST_DIGITS)
            val merchIdx = cursor.getColumnIndexOrThrow(COL_TX_MERCHANT)
            val timeIdx = cursor.getColumnIndexOrThrow(COL_TX_TIMESTAMP)
            val sendIdx = cursor.getColumnIndexOrThrow(COL_TX_SENDER)
            val bodyIdx = cursor.getColumnIndexOrThrow(COL_TX_BODY)
            val syncIdx = cursor.getColumnIndexOrThrow(COL_TX_SYNCED)
            val hashIdx = cursor.getColumnIndexOrThrow(COL_TX_HASH)

            do {
                list.add(
                    Transaction(
                        id = cursor.getLong(idIdx),
                        amount = cursor.getDouble(amtIdx),
                        accountName = cursor.getString(accIdx),
                        lastDigits = cursor.getString(lastIdx),
                        merchant = cursor.getString(merchIdx),
                        timestamp = cursor.getLong(timeIdx),
                        smsSender = cursor.getString(sendIdx),
                        smsBody = cursor.getString(bodyIdx),
                        isSynced = cursor.getInt(syncIdx),
                        syncHash = cursor.getString(hashIdx)
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    @Synchronized
    fun getDistinctMonthsWithTransactions(): List<String> {
        val list = mutableSetOf<String>()
        // Always include current month
        val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        list.add(sdf.format(java.util.Date()))
        
        val allTx = getAllTransactions()
        for (tx in allTx) {
            list.add(sdf.format(java.util.Date(tx.timestamp)))
        }
        return list.sortedDescending()
    }

    @Synchronized
    fun deleteTransaction(id: Long) {
        val db = this.writableDatabase
        db.delete(TABLE_TRANSACTIONS, "$COL_TX_ID = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun clearAllTransactions() {
        val db = this.writableDatabase
        db.delete(TABLE_TRANSACTIONS, null, null)
    }

    @Synchronized
    fun markAllAsSynced() {
        val db = this.writableDatabase
        val values = ContentValues().apply { put(COL_TX_SYNCED, 1) }
        db.update(TABLE_TRANSACTIONS, values, null, null)
    }

    // --- CRUD Card Configuration ---

    @Synchronized
    fun addCardConfig(name: String, lastDigits: String, keywords: String, billingCycleDay: Int = 0): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_CFG_NAME, name)
            put(COL_CFG_LAST_DIGITS, lastDigits)
            put(COL_CFG_KEYWORDS, keywords.lowercase())
            put(COL_CFG_BILLING_CYCLE_DAY, billingCycleDay)
        }
        val result = db.insert(TABLE_CARDS, null, values)
        return result != -1L
    }

    @Synchronized
    fun getAllCardConfigs(): List<CardConfig> {
        val list = mutableListOf<CardConfig>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_CARDS ORDER BY $COL_CFG_ID DESC", null)
        if (cursor.moveToFirst()) {
            val idIdx = cursor.getColumnIndexOrThrow(COL_CFG_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(COL_CFG_NAME)
            val lastIdx = cursor.getColumnIndexOrThrow(COL_CFG_LAST_DIGITS)
            val keyIdx = cursor.getColumnIndexOrThrow(COL_CFG_KEYWORDS)
            val billIdx = cursor.getColumnIndexOrThrow(COL_CFG_BILLING_CYCLE_DAY)
            do {
                list.add(
                    CardConfig(
                        id = cursor.getLong(idIdx),
                        cardName = cursor.getString(nameIdx),
                        lastDigits = cursor.getString(lastIdx),
                        keywords = cursor.getString(keyIdx),
                        billingCycleDay = cursor.getInt(billIdx)
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    @Synchronized
    fun deleteCardConfig(id: Long) {
        val db = this.writableDatabase
        db.delete(TABLE_CARDS, "$COL_CFG_ID = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun updateCardConfig(id: Long, name: String, lastDigits: String, keywords: String, billingCycleDay: Int = 0): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_CFG_NAME, name)
            put(COL_CFG_LAST_DIGITS, lastDigits)
            put(COL_CFG_KEYWORDS, keywords.lowercase())
            put(COL_CFG_BILLING_CYCLE_DAY, billingCycleDay)
        }
        return db.update(TABLE_CARDS, values, "$COL_CFG_ID = ?", arrayOf(id.toString())) > 0
    }

    // --- CRUD Account Configuration ---

    @Synchronized
    fun addAccountConfig(name: String, lastDigits: String, keywords: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_CFG_NAME, name)
            put(COL_CFG_LAST_DIGITS, lastDigits)
            put(COL_CFG_KEYWORDS, keywords.lowercase())
        }
        val result = db.insert(TABLE_ACCOUNTS, null, values)
        return result != -1L
    }

    @Synchronized
    fun getAllAccountConfigs(): List<AccountConfig> {
        val list = mutableListOf<AccountConfig>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_ACCOUNTS ORDER BY $COL_CFG_ID DESC", null)
        if (cursor.moveToFirst()) {
            val idIdx = cursor.getColumnIndexOrThrow(COL_CFG_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(COL_CFG_NAME)
            val lastIdx = cursor.getColumnIndexOrThrow(COL_CFG_LAST_DIGITS)
            val keyIdx = cursor.getColumnIndexOrThrow(COL_CFG_KEYWORDS)
            do {
                list.add(
                    AccountConfig(
                        id = cursor.getLong(idIdx),
                        accountName = cursor.getString(nameIdx),
                        lastDigits = cursor.getString(lastIdx),
                        keywords = cursor.getString(keyIdx)
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    @Synchronized
    fun deleteAccountConfig(id: Long) {
        val db = this.writableDatabase
        db.delete(TABLE_ACCOUNTS, "$COL_CFG_ID = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun updateAccountConfig(id: Long, name: String, lastDigits: String, keywords: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_CFG_NAME, name)
            put(COL_CFG_LAST_DIGITS, lastDigits)
            put(COL_CFG_KEYWORDS, keywords.lowercase())
        }
        return db.update(TABLE_ACCOUNTS, values, "$COL_CFG_ID = ?", arrayOf(id.toString())) > 0
    }
}

@kotlinx.serialization.Serializable
private data class PersonalRules(
    val cards: List<CardConfig> = emptyList(),
    val accounts: List<AccountConfig> = emptyList()
)
