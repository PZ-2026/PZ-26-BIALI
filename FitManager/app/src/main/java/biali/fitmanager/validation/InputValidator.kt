package biali.fitmanager.validation

object InputValidator {

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_TOP_UP_AMOUNT = 1000.0
    private const val MAX_PASSWORD_LENGTH = 128
    private const val MAX_NAME_LENGTH = 100
    private const val MAX_EMAIL_LENGTH = 255

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val phoneRegex = Regex("^[0-9]{9}$")
    private val allowedRoles = setOf("ADMIN", "TRAINER", "CLIENT")

    fun validateLogin(email: String, password: String): String? {
        validateEmail(email)?.let { return it }
        if (password.isBlank()) return "Hasło jest wymagane."
        return null
    }

    fun validateRegister(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        phone: String?
    ): String? {
        validateName(firstName, "Imię")?.let { return it }
        validateName(lastName, "Nazwisko")?.let { return it }
        validateEmail(email)?.let { return it }
        validatePassword(password)?.let { return it }
        if (!phone.isNullOrBlank()) {
            validatePhone(phone)?.let { return it }
        }
        return null
    }

    fun validateProfileUpdate(
        firstName: String,
        lastName: String,
        phone: String?
    ): String? {
        validateName(firstName, "Imię")?.let { return it }
        validateName(lastName, "Nazwisko")?.let { return it }
        if (!phone.isNullOrBlank()) {
            validatePhone(phone)?.let { return it }
        }
        return null
    }

    fun validatePasswordChange(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): String? {
        if (currentPassword.isBlank()) return "Podaj aktualne hasło."
        validatePassword(newPassword)?.let { return it }
        if (newPassword != confirmPassword) return "Hasła nie są zgodne."
        if (currentPassword == newPassword) return "Nowe hasło musi różnić się od aktualnego."
        return null
    }

    fun validateAdminUserForm(
        email: String,
        password: String?,
        role: String,
        firstName: String,
        lastName: String,
        phone: String?,
        isCreate: Boolean
    ): String? {
        validateEmail(email)?.let { return it }
        if (isCreate) {
            validatePassword(password)?.let { return it }
        } else if (!password.isNullOrBlank()) {
            validatePassword(password)?.let { return it }
        }
        validateName(firstName, "Imię")?.let { return it }
        validateName(lastName, "Nazwisko")?.let { return it }
        validateRole(role)?.let { return it }
        if (!phone.isNullOrBlank()) {
            validatePhone(phone)?.let { return it }
        }
        return null
    }

    fun validateMembershipTypeForm(
        name: String,
        priceText: String,
        durationDaysText: String
    ): String? {
        if (name.isBlank()) return "Nazwa karnetu jest wymagana."
        if (name.trim().length > 100) return "Nazwa karnetu jest zbyt długa."

        val price = priceText.replace(',', '.').toDoubleOrNull()
            ?: return "Podaj poprawną cenę."
        if (price <= 0.0) return "Cena musi być większa od zera."

        val durationDays = durationDaysText.toIntOrNull()
            ?: return "Podaj poprawną liczbę dni."
        if (durationDays < 1) return "Czas trwania musi wynosić co najmniej 1 dzień."

        return null
    }

    fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return "Email jest wymagany."
        if (trimmed.length > MAX_EMAIL_LENGTH) return "Email jest zbyt długi."
        if (!emailRegex.matches(trimmed)) return "Podaj poprawny adres email."
        return null
    }

    fun validatePassword(password: String?): String? {
        if (password.isNullOrBlank()) return "Hasło jest wymagane."
        if (password.length < MIN_PASSWORD_LENGTH) {
            return "Hasło musi mieć co najmniej $MIN_PASSWORD_LENGTH znaków."
        }
        if (password.length > MAX_PASSWORD_LENGTH) return "Hasło jest zbyt długie."
        return null
    }

    fun validateName(name: String, fieldLabel: String): String? {
        if (name.isBlank()) return "$fieldLabel jest wymagane."
        if (name.trim().length > MAX_NAME_LENGTH) return "$fieldLabel jest zbyt długie."
        return null
    }

    fun validatePhone(phone: String): String? {
        if (!phoneRegex.matches(phone.trim())) return "Numer telefonu musi składać się z 9 cyfr."
        return null
    }

    fun validateWeight(weight: Double?): String? {
        if (weight == null) return "Podaj poprawną wagę."
        if (weight < 0.0) return "Ciężar nie może być ujemny."
        return null
    }

    fun validateTopUpAmount(amount: Double?): String? {
        if (amount == null) return "Wprowadź poprawną kwotę."
        if (amount <= 0.0) return "Kwota musi być większa od zera."
        if (amount > MAX_TOP_UP_AMOUNT) return "Maksymalna kwota doładowania to ${MAX_TOP_UP_AMOUNT.toInt()} zł."
        return null
    }

    private fun validateRole(role: String): String? {
        if (role.isBlank()) return "Rola jest wymagana."
        if (role.trim().uppercase() !in allowedRoles) return "Niepoprawna rola."
        return null
    }
}
