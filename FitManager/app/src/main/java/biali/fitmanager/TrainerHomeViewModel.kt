package biali.fitmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.UserResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrainerHomeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val displayName: String = "Trenerze",
    val clientsCount: Int = 0,
    val clients: List<UserResponse> = emptyList(),
    val draftSessions: Int = 0,
    val confirmedSessions: Int = 0,
    val completedSessions: Int = 0,
    val totalSessions: Int = 0,
    val progressLogsCount: Int = 0
)

class TrainerHomeViewModel : ViewModel() {
    private val repository = FitManagerRepository()

    private val _state = MutableStateFlow(TrainerHomeUiState())
    val state: StateFlow<TrainerHomeUiState> = _state.asStateFlow()

    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        fetchData()
    }

    fun refresh() {
        fetchData()
    }

    private fun fetchData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // Fetch display name from token
            val token = biali.fitmanager.network.SessionManager.getToken()
            val name = token?.let(biali.fitmanager.network.SessionManager::resolveDisplayNameFromToken)
            if (!name.isNullOrBlank()) {
                _state.update { it.copy(displayName = name) }
            }

            // Fetch all data in parallel
            val clientsResult = repository.getMyTrainerClients()
            val sessionsResult = repository.getTrainerSessions()

            val clients = if (clientsResult is ApiResult.Success) clientsResult.data else emptyList()
            val sessions = if (sessionsResult is ApiResult.Success) sessionsResult.data else emptyList()

            val draftCount = sessions.count { it.status == "DRAFT" }
            val confirmedCount = sessions.count { it.status == "CONFIRMED" }
            val completedCount = sessions.count { it.status == "COMPLETED" }

            // Fetch progress logs count
            val progressResult = repository.getTrainerProgressLogs()
            val progressCount = if (progressResult is ApiResult.Success) progressResult.data.size else 0

            val errorMsg = when {
                clientsResult is ApiResult.Error -> clientsResult.message
                sessionsResult is ApiResult.Error -> sessionsResult.message
                else -> null
            }

            val isExpired = clientsResult is ApiResult.Unauthorized || sessionsResult is ApiResult.Unauthorized

            _state.update {
                it.copy(
                    isLoading = false,
                    error = errorMsg,
                    sessionExpired = isExpired,
                    clientsCount = clients.size,
                    clients = clients,
                    draftSessions = draftCount,
                    confirmedSessions = confirmedCount,
                    completedSessions = completedCount,
                    totalSessions = sessions.size,
                    progressLogsCount = progressCount
                )
            }
        }
    }
}
