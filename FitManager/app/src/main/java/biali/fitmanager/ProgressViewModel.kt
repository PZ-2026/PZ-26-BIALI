package biali.fitmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.ProgressSummaryResponse
import biali.fitmanager.ClientWorkoutDto
import biali.fitmanager.network.ClientProgressLogDto
import biali.fitmanager.network.LogWeightRequest
import biali.fitmanager.validation.InputValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Tworzymy klasę stanu specyficzną dla ekranu postępów (wzorowaną na Waszym TrainersUiState)
data class ProgressUiState(
    val myWorkouts: List<ClientWorkoutDto> = emptyList(),
    val progressLogs: List<ClientProgressLogDto> = emptyList(),
    val progressSummary: ProgressSummaryResponse? = null,
    val isLoadingSummary: Boolean = false,
    val summaryError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false
)

class ProgressViewModel : ViewModel() {
    // Wstrzykujemy repozytorium tak samo jak u trenerów
    private val repository = FitManagerRepository()

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    fun fetchProgress() {
        viewModelScope.launch {
            // Pokazujemy loader i czyścimy błędy przed zapytaniem
            _state.update { it.copy(isLoading = true, error = null) }

            // Odpytujemy serwer przez Repozytorium
            val workoutsResult = repository.getMyWorkouts()
            val progressResult = repository.getClientProgressLogs()

            val workouts = if (workoutsResult is ApiResult.Success) workoutsResult.data else emptyList()
            val logs = if (progressResult is ApiResult.Success) progressResult.data else emptyList()
            val errorMsg = if (workoutsResult is ApiResult.Error) workoutsResult.message else null

            _state.update { it.copy(myWorkouts = workouts, progressLogs = logs, isLoading = false, error = errorMsg) }
        }
    }

    fun fetchProgressSummary() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingSummary = true, summaryError = null) }
            when (val result = repository.getProgressSummary()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(progressSummary = result.data, isLoadingSummary = false) }
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(summaryError = result.message, isLoadingSummary = false) }
                }
                else -> {
                    _state.update { it.copy(isLoadingSummary = false) }
                }
            }
        }
    }

    fun deleteWorkout(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.deleteClientWorkout(id)) {
                is ApiResult.Success -> fetchProgress()
                is ApiResult.Error -> _state.update { it.copy(error = result.message, isLoading = false) }
                else -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateWorkout(id: Int, weight: Double, sets: Int, reps: Int) {
        InputValidator.validateWeight(weight)?.let { error ->
            _state.update { it.copy(error = error) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val request = LogWorkoutRequest(0, weight, sets, reps, null) // exerciseId and sessionId are ignored in backend update query
            when (val result = repository.updateClientWorkout(id, request)) {
                is ApiResult.Success -> fetchProgress()
                is ApiResult.Error -> _state.update { it.copy(error = result.message, isLoading = false) }
                else -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun logWeight(weight: Double) {
        InputValidator.validateWeight(weight)?.let { error ->
            _state.update { it.copy(error = error) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.addClientProgressLog(LogWeightRequest(weight))) {
                is ApiResult.Success -> fetchProgress()
                is ApiResult.Error -> _state.update { it.copy(error = result.message, isLoading = false) }
                else -> _state.update { it.copy(isLoading = false) }
            }
        }
    }
}