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

data class TrainerProgressUiState(
    val progressLogs: List<ClientProgressLog> = emptyList(),
    val trainingSessions: List<ClientTrainingSession> = emptyList(),
    val clients: List<UserResponse> = emptyList(),
    val availableExercises: List<ClientExercise> = emptyList(),
    val sessionExercises: List<ClientSessionExercise> = emptyList(),
    val clientWorkouts: List<ClientWorkoutDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class TrainerProgressViewModel : ViewModel() {
    private val repository = FitManagerRepository()

    private val _state = MutableStateFlow(TrainerProgressUiState())
    val state: StateFlow<TrainerProgressUiState> = _state.asStateFlow()

    fun fetchData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            val progressResult = repository.getTrainerProgressLogs()
            val sessionsResult = repository.getTrainerSessions()
            val clientsResult = repository.getMyTrainerClients()
            val exercisesResult = repository.getTrainerExercises()
            val sessionExercisesResult = repository.getAllSessionExercises()
            val clientWorkoutsResult = repository.getTrainerClientWorkouts()
            
            val logs = if (progressResult is ApiResult.Success) progressResult.data else emptyList()
            val sessions = if (sessionsResult is ApiResult.Success) sessionsResult.data else emptyList()
            val clientsList = if (clientsResult is ApiResult.Success) clientsResult.data else emptyList()
            val availExercises = if (exercisesResult is ApiResult.Success) exercisesResult.data else emptyList()
            val sessExercises = if (sessionExercisesResult is ApiResult.Success) sessionExercisesResult.data else emptyList()
            val clientWorkouts = if (clientWorkoutsResult is ApiResult.Success) clientWorkoutsResult.data else emptyList()
            val errorMsg = if (progressResult is ApiResult.Error) progressResult.message else null
            
            _state.update { it.copy(
                progressLogs = logs, 
                trainingSessions = sessions, 
                clients = clientsList, 
                availableExercises = availExercises,
                sessionExercises = sessExercises,
                clientWorkouts = clientWorkouts,
                isLoading = false, 
                error = errorMsg
            ) }
        }
    }

    fun addProgress(clientId: Int, weight: Double, notes: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val request = CreateProgressRequest(clientId, weight, notes)
            when (val result = repository.addTrainerProgressLog(request)) {
                is ApiResult.Success -> fetchData() // Odśwież dane po sukcesie
                is ApiResult.Error -> _state.update { it.copy(error = result.message, isLoading = false) }
                else -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun addSession(clientId: Int, title: String, startTime: String, durationMinutes: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val request = CreateSessionRequest(clientId, title, startTime, durationMinutes)
            when (val result = repository.addTrainerSession(request)) {
                is ApiResult.Success -> fetchData() // Odśwież dane po sukcesie
                is ApiResult.Error -> _state.update { it.copy(error = result.message, isLoading = false) }
                else -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun sendSessionToClient(sessionId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.sendSessionToClient(sessionId)) {
                is ApiResult.Success -> fetchData()
                is ApiResult.Error -> _state.update { it.copy(error = result.message, isLoading = false) }
                else -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun addSessionExercise(sessionId: Int, exerciseId: Int, sets: Int, reps: Int, weight: Double) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val request = AddSessionExerciseRequest(exerciseId, sets, reps, weight)
            when (val result = repository.addSessionExercise(sessionId, request)) {
                is ApiResult.Success -> fetchData()
                is ApiResult.Error -> _state.update { it.copy(error = result.message, isLoading = false) }
                else -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun deleteSessionExercise(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.deleteSessionExercise(id)) {
                is ApiResult.Success -> fetchData()
                is ApiResult.Error -> _state.update { it.copy(error = result.message, isLoading = false) }
                else -> _state.update { it.copy(isLoading = false) }
            }
        }
    }
}