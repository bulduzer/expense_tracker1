package com.naveenapps.expensemanager.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naveenapps.expensemanager.core.domain.usecase.account.GetAllAccountsUseCase
import com.naveenapps.expensemanager.core.domain.usecase.budget.BudgetUiModel
import com.naveenapps.expensemanager.core.domain.usecase.budget.GetBudgetsUseCase
import com.naveenapps.expensemanager.core.domain.usecase.settings.currency.GetCurrencyUseCase
import com.naveenapps.expensemanager.core.domain.usecase.settings.currency.GetFormattedAmountUseCase
import com.naveenapps.expensemanager.core.domain.usecase.transaction.GetAmountStateUseCase
import com.naveenapps.expensemanager.core.domain.usecase.transaction.GetTransactionGroupByCategoryUseCase
import com.naveenapps.expensemanager.core.domain.usecase.transaction.GetTransactionWithFilterUseCase
import com.naveenapps.expensemanager.core.model.AccountType
import com.naveenapps.expensemanager.core.model.AccountUiModel
import com.naveenapps.expensemanager.core.model.Amount
import com.naveenapps.expensemanager.core.model.AmountUiState
import com.naveenapps.expensemanager.core.model.CategoryTransactionUiModel
import com.naveenapps.expensemanager.core.model.CategoryType
import com.naveenapps.expensemanager.core.model.TransactionUiItem
import com.naveenapps.expensemanager.core.model.getAvailableCreditLimit
import com.naveenapps.expensemanager.core.model.toAccountUiModel
import com.naveenapps.expensemanager.core.model.toTransactionUIModel
import com.naveenapps.expensemanager.core.navigation.AppComposeNavigator
import com.naveenapps.expensemanager.core.navigation.ExpenseManagerScreens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    getTransactionWithFilterUseCase: GetTransactionWithFilterUseCase,
    getCurrencyUseCase: GetCurrencyUseCase,
    getFormattedAmountUseCase: GetFormattedAmountUseCase,
    getAmountStateUseCase: GetAmountStateUseCase,
    getAllAccountsUseCase: GetAllAccountsUseCase,
    getTransactionGroupByCategoryUseCase: GetTransactionGroupByCategoryUseCase,
    getBudgetsUseCase: GetBudgetsUseCase,
    private val appComposeNavigator: AppComposeNavigator,
) : ViewModel() {

    private val _amountUiState = MutableStateFlow(AmountUiState())
    val amountUiState = _amountUiState.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionUiItem>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _budgets = MutableStateFlow<List<BudgetUiModel>>(emptyList())
    val budgets = _budgets.asStateFlow()

    private val _accounts = MutableStateFlow<List<AccountUiModel>>(emptyList())
    val accounts = _accounts.asStateFlow()

    private val _categoryTransaction = MutableStateFlow(
        CategoryTransactionUiModel(
            pieChartData = listOf(),
            totalAmount = Amount(0.0),
            categoryTransactions = emptyList(),
        ),
    )
    val categoryTransaction = _categoryTransaction.asStateFlow()

    init {
        combine(
            getCurrencyUseCase.invoke(),
            getTransactionWithFilterUseCase.invoke(),
        ) { currency, response ->

            _transactions.value = (
                (
                    response?.map {
                        it.toTransactionUIModel(
                            getFormattedAmountUseCase.invoke(
                                it.amount.amount,
                                currency,
                            ),
                        )
                    } ?: emptyList()
                    ).take(MAX_TRANSACTIONS_IN_LIST)
                )
        }.launchIn(viewModelScope)

        getAmountStateUseCase.invoke().onEach {
            _amountUiState.value = it
        }.launchIn(viewModelScope)

        combine(
            getCurrencyUseCase.invoke(),
            getAllAccountsUseCase.invoke(),
        ) { currency, accounts ->
            _accounts.value = accounts.map {
                it.toAccountUiModel(
                    getFormattedAmountUseCase.invoke(
                        it.amount,
                        currency,
                    ),
                    if (it.type == AccountType.CREDIT) {
                        getFormattedAmountUseCase.invoke(
                            it.getAvailableCreditLimit(),
                            currency
                        )
                    } else {
                        null
                    }
                )
            }
        }.launchIn(viewModelScope)

        getTransactionGroupByCategoryUseCase.invoke(CategoryType.EXPENSE).onEach {
            _categoryTransaction.value = it.copy(
                pieChartData = it.pieChartData.take(4),
                categoryTransactions = it.categoryTransactions.take(4),
            )
        }.launchIn(viewModelScope)

        getBudgetsUseCase.invoke().onEach {
            _budgets.value = it
        }.launchIn(viewModelScope)
    }

    fun openSettings() {
        appComposeNavigator.navigate(ExpenseManagerScreens.Settings)
    }

    fun openAccountList() {
        appComposeNavigator.navigate(ExpenseManagerScreens.AccountList)
    }

    fun openAccountCreate(accountId: String?) {
        appComposeNavigator.navigate(
            ExpenseManagerScreens.AccountCreate(accountId),
        )
    }

    fun openBudgetList() {
        appComposeNavigator.navigate(ExpenseManagerScreens.BudgetList)
    }

    fun openBudgetCreate(budgetId: String?) {
        appComposeNavigator.navigate(
            ExpenseManagerScreens.BudgetDetails(budgetId),
        )
    }

    fun openTransactionList() {
        appComposeNavigator.navigate(ExpenseManagerScreens.TransactionList)
    }

    fun openTransactionCreate(transactionId: String? = null) {
        appComposeNavigator.navigate(
            ExpenseManagerScreens.TransactionCreate(transactionId),
        )
    }

    companion object {
        private const val MAX_TRANSACTIONS_IN_LIST = 10
    }
}
