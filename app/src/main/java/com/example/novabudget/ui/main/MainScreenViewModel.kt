package com.example.novabudget.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novabudget.data.AccountConfig
import com.example.novabudget.data.CardConfig
import com.example.novabudget.data.DefaultDataRepository
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
            repository.budgetLimit,
            repository.currencySymbol,
            repository.selectedMonth,
            repository.distinctMonths
        ) { limit, currency, selMonth, months ->
            Quad(limit, currency, selMonth, months)
        }
    ) { tx, cards, accounts, spent, quad ->
        MainScreenUiState.Success(
            transactions = tx as List<Transaction>,
            cardConfigs = cards as List<CardConfig>,
            accountConfigs = accounts as List<AccountConfig>,
            currentMonthSpent = spent as Double,
            budgetLimit = quad.limit,
            currencySymbol = quad.currency,
            selectedMonth = quad.selectedMonth,
            distinctMonths = quad.distinctMonths
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
        val distinctMonths: List<String>
    ) : MainScreenUiState
}
