package timetablescheduler.domain.account

import timetablescheduler.domain.validation.ValidationError

/** FR-034: passwords need at least 10 characters. */
object PasswordPolicy {
    const val FIELD_PASSWORD = "password"
    const val MIN_LENGTH = 10

    fun validate(plainPassword: String): ValidationError? =
        if (plainPassword.length < MIN_LENGTH) ValidationError.OutOfRange(FIELD_PASSWORD, MIN_LENGTH, null) else null
}
