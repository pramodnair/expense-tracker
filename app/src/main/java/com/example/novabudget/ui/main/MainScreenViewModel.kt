package com.example.novabudget.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novabudget.data.AccountConfig
import com.example.novabudget.data.CardConfig
import com.example.novabudget.data.DefaultDataRepository
import com.example.novabudget.data.Subscription
import com.example.novabudget.data.Transaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MainScreenViewModel(private val repository: DefaultDataRepository) : ViewModel() {

    val uiState: StateFlow<MainScreenUiState> = combine(
        repository.transactions,
        repository.cardConfigs,
        repository.accountConfigs,
        repository.currentMonthSpent,
        combine(
            combine(
                combine(
                    repository.budgetLimit,
                    repository.currencySymbol,
                    repository.selectedMonth,
                    repository.distinctMonths
                ) { limit, currency, selMonth, months ->
                    Quad(limit, currency, selMonth, months)
                },
                combine(
                    repository.startingBalance,
                    repository.monthlyIncome,
                    repository.incomeDay
                ) { startBal, income, day ->
                    Triple(startBal, income, day)
                }
            ) { quad, trip ->
                SettingsBundle(
                    limit = quad.limit,
                    currency = quad.currency,
                    selectedMonth = quad.selectedMonth,
                    distinctMonths = quad.distinctMonths,
                    startingBalance = trip.first,
                    monthlyIncome = trip.second,
                    incomeDay = trip.third
                )
            },
            combine(
                repository.subscriptions,
                repository.suggestedSubscriptions
            ) { subs, suggestions ->
                Pair(subs, suggestions)
            }
        ) { bundle, subPair ->
            Pair(bundle, subPair)
        }
    ) { tx, cards, accounts, spent, bigBundle ->
        val bundle = bigBundle.first
        val subPair = bigBundle.second
        MainScreenUiState.Success(
            transactions = tx,
            cardConfigs = cards,
            accountConfigs = accounts,
            currentMonthSpent = spent,
            budgetLimit = bundle.limit,
            currencySymbol = bundle.currency,
            selectedMonth = bundle.selectedMonth,
            distinctMonths = bundle.distinctMonths,
            startingBalance = bundle.startingBalance,
            monthlyIncome = bundle.monthlyIncome,
            incomeDay = bundle.incomeDay,
            subscriptions = subPair.first,
            suggestedSubscriptions = subPair.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainScreenUiState.Loading
    )

    fun addTransaction(tx: Transaction) {
        repository.addTransaction(tx)
    }

    fun setBudgetLimit(limit: Float) {
        repository.setBudgetLimit(limit)
    }

    fun setStartingBalance(bal: Double) {
        repository.setStartingBalance(bal)
    }

    fun setMonthlyIncome(income: Double) {
        repository.setMonthlyIncome(income)
    }

    fun setIncomeDay(day: Int) {
        repository.setIncomeDay(day)
    }

    fun setCurrencySymbol(currency: String) {
        repository.setCurrency(currency)
    }

    fun setSelectedMonth(yearMonth: String) {
        repository.setSelectedMonth(yearMonth)
    }

    fun deleteTransaction(id: Long) {
        repository.deleteTransaction(id)
    }

    fun clearAllTransactions() {
        repository.clearAllTransactions()
    }

    fun addCardConfig(name: String, lastDigits: String, keywords: String, billingCycleDay: Int = 0) {
        repository.addCardConfig(name, lastDigits, keywords, billingCycleDay)
    }

    fun deleteCardConfig(id: Long) {
        repository.deleteCardConfig(id)
    }

    fun updateCardConfig(id: Long, name: String, lastDigits: String, keywords: String, billingCycleDay: Int = 0) {
        repository.updateCardConfig(id, name, lastDigits, keywords, billingCycleDay)
    }

    fun addAccountConfig(name: String, lastDigits: String, keywords: String) {
        repository.addAccountConfig(name, lastDigits, keywords)
    }

    fun deleteAccountConfig(id: Long) {
        repository.deleteAccountConfig(id)
    }

    fun updateAccountConfig(id: Long, name: String, lastDigits: String, keywords: String) {
        repository.updateAccountConfig(id, name, lastDigits, keywords)
    }

    fun addSubscription(name: String, amount: Double, billingCycle: String, nextRenewalDate: Long) {
        val sub = Subscription(
            name = name,
            amount = amount,
            billingCycle = billingCycle,
            nextRenewalDate = nextRenewalDate,
            isReminderEnabled = 1,
            isAutoDetected = 0
        )
        repository.addSubscription(sub)
    }

    fun confirmSuggestedSubscription(sub: Subscription) {
        val confirmed = sub.copy(isAutoDetected = 0)
        repository.addSubscription(confirmed)
    }

    fun deleteSubscription(id: Long) {
        repository.deleteSubscription(id)
    }

    fun updateSubscription(sub: Subscription) {
        repository.updateSubscription(sub)
    }

    fun refresh() {
        repository.refresh()
    }
}

// Custom data container for combining multiple flows
private data class Quad(
    val limit: Float,
    val currency: String,
    val selectedMonth: String,
    val distinctMonths: List<String>
)

private data class SettingsBundle(
    val limit: Float,
    val currency: String,
    val selectedMonth: String,
    val distinctMonths: List<String>,
    val startingBalance: Double,
    val monthlyIncome: Double,
    val incomeDay: Int
)

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Success(
        val transactions: List<Transaction>,
        val cardConfigs: List<CardConfig>,
        val accountConfigs: List<AccountConfig>,
        val currentMonthSpent: Double,
        val budgetLimit: Float,
        val currencySymbol: String,
        val selectedMonth: String,
        val distinctMonths: List<String>,
        val startingBalance: Double,
        val monthlyIncome: Double,
        val incomeDay: Int,
        val subscriptions: List<Subscription>,
        val suggestedSubscriptions: List<Subscription>
    ) : MainScreenUiState
}
