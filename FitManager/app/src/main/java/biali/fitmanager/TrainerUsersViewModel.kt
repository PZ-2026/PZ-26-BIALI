package biali.fitmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.UserResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrainerUsersUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val clients: List<UserResponse> = emptyList()
)

class TrainerUsersViewModel(
    private val repository: FitManagerRepository = FitManagerRepository()
) : ViewModel() {
    private val _state = MutableStateFlow(TrainerUsersUiState())
    val state = _state.asStateFlow()

    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        loadClients()
    }

    fun refresh() {
        loadClients()
    }

    private fun loadClients() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            when (val result = repository.getMyTrainerClients()) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, clients = result.data, error = null) }
                is ApiResult.Unauthorized -> _state.update { it.copy(isLoading = false, sessionExpired = true) }
                is ApiResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }
}

