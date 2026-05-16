package biali.fitmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.MembershipTypeUpsertRequest
import biali.fitmanager.network.UserResponse
import biali.fitmanager.network.UserUpsertRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUserFormState(
    val id: String = "",
    val email: String = "",
    val password: String = "",
    val role: String = "CLIENT",
    val firstName: String = "",
    val lastName: String = "",
    val phoneNumber: String = ""
)

data class AdminMembershipTypeFormState(
    val id: String = "",
    val name: String = "",
    val price: String = "",
    val durationDays: String = "",
    val description: String = ""
)

data class AdminDashboardUiState(
    val isLoading: Boolean = false,
    val isTrainerClientsLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val selectedUserRoleFilter: String = "CLIENT",
    val users: List<UserResponse> = emptyList(),
    val trainers: List<UserResponse> = emptyList(),
    val trainerClients: List<UserResponse> = emptyList(),
    val trainerIdForClients: String = "",
    val clientIdForAssignment: String = "",
    val form: AdminUserFormState = AdminUserFormState(),
    val membershipTypes: List<biali.fitmanager.network.MembershipTypeResponse> = emptyList(),
    val membershipTypeForm: AdminMembershipTypeFormState = AdminMembershipTypeFormState()
)

class AdminDashboardViewModel : ViewModel() {
    private val repository = FitManagerRepository()
    private val _state = MutableStateFlow(AdminDashboardUiState())
    val state = _state.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, message = null) }
            loadUsersInternal(_state.value.selectedUserRoleFilter)
            loadTrainersInternal()
            loadMembershipTypesInternal()
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun setUserFilter(role: String) {
        _state.update { it.copy(selectedUserRoleFilter = role) }
        loadUsers(role)
    }

    fun onTrainerIdChanged(value: String) {
        _state.update { it.copy(trainerIdForClients = value) }
    }

    fun onClientIdChanged(value: String) {
        _state.update { it.copy(clientIdForAssignment = value) }
    }

    fun onFormChanged(transform: (AdminUserFormState) -> AdminUserFormState) {
        _state.update { current -> current.copy(form = transform(current.form)) }
    }

    fun onMembershipTypeFieldChange(transform: (AdminMembershipTypeFormState) -> AdminMembershipTypeFormState) {
        _state.update { current -> current.copy(membershipTypeForm = transform(current.membershipTypeForm)) }
    }

    fun fillFormFromUser(user: UserResponse) {
        _state.update {
            it.copy(
                form = AdminUserFormState(
                    id = user.id.toString(),
                    email = user.email,
                    password = "",
                    role = user.role,
                    firstName = user.firstName,
                    lastName = user.lastName,
                    phoneNumber = user.phoneNumber.orEmpty()
                ),
                message = null,
                error = null
            )
        }
    }

    fun clearForm() {
        _state.update { it.copy(form = AdminUserFormState(), message = null, error = null) }
    }

    fun fillMembershipTypeForm(membershipType: biali.fitmanager.network.MembershipTypeResponse) {
        _state.update {
            it.copy(
                membershipTypeForm = AdminMembershipTypeFormState(
                    id = membershipType.id.toString(),
                    name = membershipType.name,
                    price = membershipType.price.toString(),
                    durationDays = membershipType.durationDays.toString(),
                    description = membershipType.description.orEmpty()
                ),
                message = null,
                error = null
            )
        }
    }

    fun clearMembershipTypeForm() {
        _state.update { it.copy(membershipTypeForm = AdminMembershipTypeFormState(), message = null, error = null) }
    }

    fun loadUsers(role: String? = _state.value.selectedUserRoleFilter) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, message = null) }
            loadUsersInternal(role)
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun loadTrainers() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, message = null) }
            loadTrainersInternal()
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun saveForm() {
        val form = _state.value.form
        val request = UserUpsertRequest(
            email = form.email.trim(),
            password = form.password.ifBlank { null },
            role = form.role.trim().uppercase(),
            firstName = form.firstName.trim(),
            lastName = form.lastName.trim(),
            phoneNumber = form.phoneNumber.trim().ifBlank { null }
        )
        val id = form.id.toIntOrNull()

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, message = null) }
            val result = when (request.role) {
                "TRAINER" -> {
                    if (id == null) repository.createTrainer(request) else repository.updateTrainer(id, request)
                }
                else -> {
                    if (id == null) repository.createUser(request) else repository.updateUser(id, request)
                }
            }

            handleMutationResult(result, refreshClientsIfNeeded = false)
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun saveMembershipTypeForm() {
        val form = _state.value.membershipTypeForm
        val request = MembershipTypeUpsertRequest(
            name = form.name.trim(),
            price = form.price.toDoubleOrNull() ?: 0.0,
            durationDays = form.durationDays.toIntOrNull() ?: 1,
            description = form.description.trim().ifBlank { null }
        )
        val id = form.id.toIntOrNull()

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, message = null) }
            val result = if (id == null) {
                repository.createMembershipType(request)
            } else {
                repository.updateMembershipType(id, request)
            }

            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(message = "Typ karnetu zapisany pomyślnie.", error = null) }
                    loadMembershipTypesInternal()
                    clearMembershipTypeForm()
                }
                is ApiResult.Unauthorized -> _state.update { it.copy(sessionExpired = true) }
                is ApiResult.Error -> _state.update { it.copy(error = result.message, message = null) }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun deleteUser(user: UserResponse) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, message = null) }
            val result = if (user.role.equals("TRAINER", ignoreCase = true)) {
                repository.deleteTrainer(user.id)
            } else {
                repository.deleteUser(user.id)
            }
            handleMutationResult(result, refreshClientsIfNeeded = false)
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun deleteMembershipType(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, message = null) }
            when (val result = repository.deleteMembershipType(id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(message = "Typ karnetu usunięty pomyślnie.", error = null) }
                    loadMembershipTypesInternal()
                    clearMembershipTypeForm()
                }
                is ApiResult.Unauthorized -> _state.update { it.copy(sessionExpired = true) }
                is ApiResult.Error -> _state.update { it.copy(error = result.message, message = null) }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun loadTrainerClients() {
        val trainerId = _state.value.trainerIdForClients.toIntOrNull()
        if (trainerId == null) {
            _state.update { it.copy(error = "Podaj poprawny trainerId.", message = null) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isTrainerClientsLoading = true, error = null, message = null) }
            when (val result = repository.getTrainerClients(trainerId)) {
                is ApiResult.Success -> _state.update { it.copy(trainerClients = result.data, isTrainerClientsLoading = false) }
                is ApiResult.Unauthorized -> _state.update { it.copy(isTrainerClientsLoading = false, sessionExpired = true) }
                is ApiResult.Error -> _state.update { it.copy(isTrainerClientsLoading = false, error = result.message) }
            }
        }
    }

    fun assignClient() {
        val trainerId = _state.value.trainerIdForClients.toIntOrNull()
        val clientId = _state.value.clientIdForAssignment.toIntOrNull()
        if (trainerId == null || clientId == null) {
            _state.update { it.copy(error = "Podaj poprawny trainerId i clientId.", message = null) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isTrainerClientsLoading = true, error = null, message = null) }
            handleMutationResult(repository.assignClientToTrainer(trainerId, clientId), refreshClientsIfNeeded = true)
            _state.update { it.copy(isTrainerClientsLoading = false) }
        }
    }

    fun unassignClient() {
        val trainerId = _state.value.trainerIdForClients.toIntOrNull()
        val clientId = _state.value.clientIdForAssignment.toIntOrNull()
        if (trainerId == null || clientId == null) {
            _state.update { it.copy(error = "Podaj poprawny trainerId i clientId.", message = null) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isTrainerClientsLoading = true, error = null, message = null) }
            handleMutationResult(repository.unassignClientFromTrainer(trainerId, clientId), refreshClientsIfNeeded = true)
            _state.update { it.copy(isTrainerClientsLoading = false) }
        }
    }

    private suspend fun loadUsersInternal(role: String?) {
        when (val result = repository.getUsers(if (role.equals("ALL", ignoreCase = true)) null else role)) {
            is ApiResult.Success -> _state.update { it.copy(users = result.data) }
            is ApiResult.Unauthorized -> _state.update { it.copy(sessionExpired = true) }
            is ApiResult.Error -> _state.update { it.copy(error = result.message) }
        }
    }

    private suspend fun loadTrainersInternal() {
        when (val result = repository.getTrainers()) {
            is ApiResult.Success -> _state.update { it.copy(trainers = result.data) }
            is ApiResult.Unauthorized -> _state.update { it.copy(sessionExpired = true) }
            is ApiResult.Error -> _state.update { it.copy(error = result.message) }
        }
    }

    private suspend fun loadMembershipTypesInternal() {
        when (val result = repository.getMembershipTypes()) {
            is ApiResult.Success -> _state.update { it.copy(membershipTypes = result.data) }
            is ApiResult.Unauthorized -> _state.update { it.copy(sessionExpired = true) }
            is ApiResult.Error -> _state.update { it.copy(error = result.message) }
        }
    }

    private fun handleMutationResult(result: ApiResult<*>, refreshClientsIfNeeded: Boolean) {
        when (result) {
            is ApiResult.Success -> {
                _state.update { it.copy(message = "Operacja zakończona sukcesem.", error = null) }
                refreshAll()
                if (refreshClientsIfNeeded) {
                    loadTrainerClients()
                }
                clearForm()
            }
            is ApiResult.Unauthorized -> _state.update { it.copy(sessionExpired = true) }
            is ApiResult.Error -> _state.update { it.copy(error = result.message, message = null) }
        }
    }
}

