package biali.fitmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.ClientWorkoutDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Tworzymy klasę stanu specyficzną dla ekranu postępów (wzorowaną na Waszym TrainersUiState)
data class ProgressUiState(
    val myWorkouts: List<ClientWorkoutDto> = emptyList(),
    val exercises: List<ClientExercise> = emptyList(),
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
            val exercisesResult = repository.getClientExercises()

            val workouts = if (workoutsResult is ApiResult.Success) workoutsResult.data else emptyList()
            val exercises = if (exercisesResult is ApiResult.Success) exercisesResult.data else emptyList()
            val errorMsg = if (workoutsResult is ApiResult.Error) workoutsResult.message else null

            _state.update { it.copy(myWorkouts = workouts, exercises = exercises, isLoading = false, error = errorMsg) }
        }
    }

    fun logWorkout(exerciseId: Int, weight: Double, reps: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.logClientWorkout(LogWorkoutRequest(exerciseId, weight, reps))) {
                is ApiResult.Success -> fetchProgress() // odśwież po dodaniu
                is ApiResult.Error -> _state.update { it.copy(error = result.message, isLoading = false) }
                else -> _state.update { it.copy(isLoading = false) }
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
}