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

data class TrainersUiState(
    val trainers: List<UserResponse> = emptyList(),
    val selectedTrainer: UserResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val showDetails: Boolean = false,
    val actionSuccess: String? = null
)

class TrainersViewModel(
    private val repository: FitManagerRepository = FitManagerRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(TrainersUiState())
    val state: StateFlow<TrainersUiState> = _state.asStateFlow()

    fun fetchTrainers() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getAllTrainers()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(trainers = result.data, isLoading = false) }
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(error = result.message, isLoading = false) }
                }
                is ApiResult.Unauthorized -> {
                    _state.update { it.copy(sessionExpired = true, isLoading = false) }
                }
            }
        }
    }

    fun selectTrainer(trainerId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getTrainerById(trainerId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(selectedTrainer = result.data, showDetails = true, isLoading = false) }
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(error = result.message, isLoading = false) }
                }
                is ApiResult.Unauthorized -> {
                    _state.update { it.copy(sessionExpired = true, isLoading = false) }
                }
            }
        }
    }

    fun backToList() {
        _state.update { it.copy(showDetails = false, selectedTrainer = null, actionSuccess = null, error = null) }
    }

    fun pickTrainer(trainerId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, actionSuccess = null) }
            
            // We need current user ID to assign. 
            // In FitManagerApi, assignClientToTrainer takes (trainerId, clientId)
            // We assume the user is the client.
            
            val myInfo = repository.getMe()
            if (myInfo is ApiResult.Success) {
                val clientId = myInfo.data.id
                when (val result = repository.assignClientToTrainer(trainerId, clientId)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(actionSuccess = "Pomyślnie wybrano trenera!", isLoading = false) }
                    }
                    is ApiResult.Error -> {
                        _state.update { it.copy(error = result.message, isLoading = false) }
                    }
                    is ApiResult.Unauthorized -> {
                        _state.update { it.copy(sessionExpired = true, isLoading = false) }
                    }
                }
            } else if (myInfo is ApiResult.Unauthorized) {
                _state.update { it.copy(sessionExpired = true, isLoading = false) }
            } else if (myInfo is ApiResult.Error) {
                _state.update { it.copy(error = "Nie udało się pobrać informacji o użytkowniku", isLoading = false) }
            }
        }
    }
}
