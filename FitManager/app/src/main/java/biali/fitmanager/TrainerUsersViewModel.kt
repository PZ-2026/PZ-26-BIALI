package biali.fitmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import biali.fitmanager.ClientTrainingSession
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
    val clients: List<UserResponse> = emptyList(),
    val trainingSessions: List<ClientTrainingSession> = emptyList()
)

class TrainerUsersViewModel : ViewModel() {
    private val repository = FitManagerRepository()
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

            val clientsResult = repository.getMyTrainerClients()
            val sessionsResult = repository.getTrainerSessions()

            when {
                clientsResult is ApiResult.Unauthorized || sessionsResult is ApiResult.Unauthorized -> {
                    _state.update { it.copy(isLoading = false, sessionExpired = true) }
                }
                else -> {
                    val errorMessage = when {
                        clientsResult is ApiResult.Error -> clientsResult.message
                        sessionsResult is ApiResult.Error -> sessionsResult.message
                        else -> null
                    }

                    _state.update {
                        it.copy(
                            isLoading = false,
                            clients = (clientsResult as? ApiResult.Success)?.data ?: emptyList(),
                            trainingSessions = (sessionsResult as? ApiResult.Success)?.data ?: emptyList(),
                            error = errorMessage
                        )
                    }
                }
            }
        }
    }
}

