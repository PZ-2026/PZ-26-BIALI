package biali.fitmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.ProgressSummaryResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Tworzymy klasę stanu specyficzną dla ekranu postępów (wzorowaną na Waszym TrainersUiState)
data class ProgressUiState(
    val progressData: ProgressSummaryResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false
)

class ProgressViewModel(
    // Wstrzykujemy repozytorium tak samo jak u trenerów
    private val repository: FitManagerRepository = FitManagerRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    fun fetchProgress() {
        viewModelScope.launch {
            // Pokazujemy loader i czyścimy błędy przed zapytaniem
            _state.update { it.copy(isLoading = true, error = null) }

            // Odpytujemy serwer przez Repozytorium
            when (val result = repository.getProgressSummary()) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(progressData = result.data, isLoading = false)
                    }
                }
                is ApiResult.Error -> {
                    _state.update {
                        it.copy(error = result.message ?: "Wystąpił błąd podczas pobierania postępów", isLoading = false)
                    }
                }
                is ApiResult.Unauthorized -> {
                    _state.update {
                        it.copy(sessionExpired = true, isLoading = false)
                    }
                }
            }
        }
    }
}