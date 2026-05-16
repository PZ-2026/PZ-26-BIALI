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
    val actionSuccess: String? = null,
    val currentTrainerId: Int? = null,
    val currentTrainer: UserResponse? = null,
    val currentTrainerEndDate: String? = null
)

class TrainersViewModel : ViewModel() {
    private val repository = FitManagerRepository()

    private val _state = MutableStateFlow(TrainersUiState())
    val state: StateFlow<TrainersUiState> = _state.asStateFlow()

    fun fetchTrainers() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getAllTrainers()) {
                is ApiResult.Success -> {
                    // Also fetch current user info to check if they have a trainer
                    when (val meResult = repository.getMe()) {
                        is ApiResult.Success -> {
                            val trainerId = meResult.data.trainerId
                            val trainerEnd = meResult.data.trainerEndDate
                            if (trainerId != null) {
                                // Fetch current trainer details
                                when (val trainerResult = repository.getTrainerById(trainerId)) {
                                    is ApiResult.Success -> {
                                        _state.update { it.copy(trainers = result.data, currentTrainerId = trainerId, currentTrainer = trainerResult.data, currentTrainerEndDate = trainerEnd, isLoading = false) }
                                    }
                                    is ApiResult.Error -> {
                                        _state.update { it.copy(trainers = result.data, currentTrainerId = trainerId, error = "Nie udało się pobrać danych trenera", currentTrainerEndDate = trainerEnd, isLoading = false) }
                                    }
                                    is ApiResult.Unauthorized -> {
                                        _state.update { it.copy(sessionExpired = true, isLoading = false) }
                                    }
                                }
                            } else {
                                _state.update { it.copy(trainers = result.data, currentTrainerId = null, currentTrainer = null, currentTrainerEndDate = null, isLoading = false) }
                            }
                        }
                        is ApiResult.Error -> {
                            _state.update { it.copy(trainers = result.data, error = "Nie udało się pobrać informacji o użytkowniku", isLoading = false) }
                        }
                        is ApiResult.Unauthorized -> {
                            _state.update { it.copy(sessionExpired = true, isLoading = false) }
                        }
                    }
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
            
            when (val result = repository.chooseTrainer(trainerId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(actionSuccess = "Pomyślnie wybrano trenera!", isLoading = false) }
                    // refresh data to get trainer end date and current trainer info
                    fetchTrainers()
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

    fun resignTrainer(trainerId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, actionSuccess = null) }

            when (val result = repository.resignTrainer(trainerId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(actionSuccess = "Pomyślnie zrezygnowano z trenera!", isLoading = false) }
                    // refresh to clear trainer info
                    fetchTrainers()
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
}
