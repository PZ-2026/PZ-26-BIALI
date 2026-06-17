package biali.fitmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.AssignedSessionDto
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.UserResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import biali.fitmanager.network.SessionManager
import biali.fitmanager.network.CompleteSessionRequest
import biali.fitmanager.network.SetLogDto
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class WorkoutResultDraft(
    val exerciseId: Int,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weight: Double
)

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
    val currentTrainerEndDate: String? = null,
    val currentTrainerSessions: List<AssignedSessionDto> = emptyList(),
    val pendingTrainerId: Int? = null,
    val pendingTrainerName: String? = null,
    val pendingTrainerEndDate: String? = null
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
                                SessionManager.clearPendingTrainerCooldown()
                                // Fetch current trainer details
                                when (val trainerResult = repository.getTrainerById(trainerId)) {
                                    is ApiResult.Success -> {
                                        when (val sessionsResult = repository.getMyAssignedSessions()) {
                                            is ApiResult.Success -> {
                                                _state.update {
                                                    it.copy(
                                                        trainers = result.data,
                                                        currentTrainerId = trainerId,
                                                        currentTrainer = trainerResult.data,
                                                        currentTrainerEndDate = trainerEnd,
                                                        currentTrainerSessions = sessionsResult.data,
                                                        pendingTrainerId = null,
                                                        pendingTrainerName = null,
                                                        pendingTrainerEndDate = null,
                                                        isLoading = false
                                                    )
                                                }
                                            }
                                            is ApiResult.Error -> {
                                                _state.update {
                                                    it.copy(
                                                        trainers = result.data,
                                                        currentTrainerId = trainerId,
                                                        currentTrainer = trainerResult.data,
                                                        currentTrainerEndDate = trainerEnd,
                                                        currentTrainerSessions = emptyList(),
                                                        pendingTrainerId = null,
                                                        pendingTrainerName = null,
                                                        pendingTrainerEndDate = null,
                                                        error = "Nie udało się pobrać planu treningowego",
                                                        isLoading = false
                                                    )
                                                }
                                            }
                                            is ApiResult.Unauthorized -> {
                                                _state.update { it.copy(sessionExpired = true, isLoading = false) }
                                            }
                                        }
                                    }
                                    is ApiResult.Error -> {
                                        _state.update { it.copy(trainers = result.data, currentTrainerId = trainerId, error = "Nie udało się pobrać danych trenera", currentTrainerEndDate = trainerEnd, currentTrainerSessions = emptyList(), isLoading = false) }
                                    }
                                    is ApiResult.Unauthorized -> {
                                        _state.update { it.copy(sessionExpired = true, isLoading = false) }
                                    }
                                }
                            } else {
                                val pendingId = SessionManager.getPendingTrainerId()
                                val pendingName = SessionManager.getPendingTrainerName()
                                val pendingEndDate = SessionManager.getPendingTrainerEndDate()
                                val pendingDaysLeft = pendingEndDate?.let(::calculateRemainingDays) ?: 0L

                                if (pendingId != null && !pendingName.isNullOrBlank() && !pendingEndDate.isNullOrBlank() && pendingDaysLeft > 0) {
                                    val pendingSessions = when (val sessionsResult = repository.getMyAssignedSessions()) {
                                        is ApiResult.Success -> sessionsResult.data
                                        else -> emptyList()
                                    }
                                    _state.update {
                                        it.copy(
                                            trainers = result.data,
                                            currentTrainerId = null,
                                            currentTrainer = null,
                                            currentTrainerEndDate = null,
                                            currentTrainerSessions = pendingSessions,
                                            pendingTrainerId = pendingId,
                                            pendingTrainerName = pendingName,
                                            pendingTrainerEndDate = pendingEndDate,
                                            isLoading = false
                                        )
                                    }
                                } else {
                                    SessionManager.clearPendingTrainerCooldown()
                                    _state.update {
                                        it.copy(
                                            trainers = result.data,
                                            currentTrainerId = null,
                                            currentTrainer = null,
                                            currentTrainerEndDate = null,
                                            currentTrainerSessions = emptyList(),
                                            pendingTrainerId = null,
                                            pendingTrainerName = null,
                                            pendingTrainerEndDate = null,
                                            isLoading = false
                                        )
                                    }
                                }
                            }
                        }
                        is ApiResult.Error -> {
                            _state.update { it.copy(trainers = result.data, error = "Nie udało się pobrać informacji o użytkowniku", currentTrainerSessions = emptyList(), isLoading = false) }
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
                    syncBalanceAfterTrainerPurchase()
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

    private suspend fun syncBalanceAfterTrainerPurchase() {
        when (val meResult = repository.getMe()) {
            is ApiResult.Success -> {
                meResult.data.balance?.let { SessionManager.saveBalance(it) }
                    ?: SessionManager.changeBalanceBy(-TRAINER_RENTAL_PRICE)
            }
            else -> {
                SessionManager.changeBalanceBy(-TRAINER_RENTAL_PRICE)
            }
        }
    }

    fun resignTrainer(trainerId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, actionSuccess = null) }
            val currentState = _state.value
            val resignedTrainerName = currentState.currentTrainer?.let { "${it.firstName} ${it.lastName}".trim() }
            val resignedTrainerEndDate = currentState.currentTrainerEndDate

            when (val result = repository.resignTrainer(trainerId)) {
                is ApiResult.Success -> {
                    if (!resignedTrainerName.isNullOrBlank() && !resignedTrainerEndDate.isNullOrBlank()) {
                        SessionManager.savePendingTrainerCooldown(trainerId, resignedTrainerName, resignedTrainerEndDate)
                    }
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

    fun saveWorkoutResults(sessionId: Int, results: List<WorkoutResultDraft>) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, actionSuccess = null) }

            val logs = mutableListOf<SetLogDto>()
            for (draft in results) {
                for (i in 1..draft.sets) {
                    logs.add(SetLogDto(draft.exerciseId, i, draft.weight, draft.reps))
                }
            }
            val request = CompleteSessionRequest(logs)

            when (val apiResult = repository.completeSession(sessionId, request)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(actionSuccess = "Wyniki treningu zostały zapisane!", isLoading = false) }
                    fetchTrainers()
                }
                is ApiResult.Error -> _state.update { it.copy(error = apiResult.message, isLoading = false) }
                else -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearActionSuccess() {
        _state.update { it.copy(actionSuccess = null) }
    }

    private fun calculateRemainingDays(isoDateString: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val endDate = sdf.parse(isoDateString) ?: return 0L
            val today = java.util.Date()
            val diff = endDate.time - today.time
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            maxOf(days, 0L)
        } catch (_: Exception) {
            0L
        }
    }
}
