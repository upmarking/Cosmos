package com.cosmos.app.data

/**
 * Centralized validation utilities for all form inputs across the app.
 */
object ValidationUtils {

    // ── Email ───────────────────────────────────────────────────────────────
    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    fun validateEmail(email: String): ValidationResult {
        val trimmed = email.trim()
        return when {
            trimmed.isBlank() -> ValidationResult.Error("Email is required")
            !EMAIL_REGEX.matches(trimmed) -> ValidationResult.Error("Please enter a valid email address")
            else -> ValidationResult.Valid
        }
    }

    // ── Password ────────────────────────────────────────────────────────────
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult.Error("Password is required")
            password.length < 8 -> ValidationResult.Error("Password must be at least 8 characters")
            !password.any { it.isUpperCase() } -> ValidationResult.Error("Password must contain an uppercase letter")
            !password.any { it.isDigit() } -> ValidationResult.Error("Password must contain a number")
            else -> ValidationResult.Valid
        }
    }

    fun validatePasswordMatch(password: String, confirmPassword: String): ValidationResult {
        return when {
            confirmPassword.isBlank() -> ValidationResult.Error("Please confirm your password")
            password != confirmPassword -> ValidationResult.Error("Passwords do not match")
            else -> ValidationResult.Valid
        }
    }

    // ── Name ────────────────────────────────────────────────────────────────
    fun validateName(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isBlank() -> ValidationResult.Error("Name is required")
            trimmed.length < 2 -> ValidationResult.Error("Name must be at least 2 characters")
            trimmed.length > 100 -> ValidationResult.Error("Name is too long")
            else -> ValidationResult.Valid
        }
    }

    // ── Generic required field ──────────────────────────────────────────────
    fun validateRequired(value: String, fieldName: String): ValidationResult {
        return if (value.trim().isBlank()) {
            ValidationResult.Error("$fieldName is required")
        } else {
            ValidationResult.Valid
        }
    }

    // ── Generic length validation ───────────────────────────────────────────
    fun validateLength(value: String, fieldName: String, min: Int = 0, max: Int = Int.MAX_VALUE): ValidationResult {
        val trimmed = value.trim()
        return when {
            trimmed.length < min -> ValidationResult.Error("$fieldName must be at least $min characters")
            trimmed.length > max -> ValidationResult.Error("$fieldName must be at most $max characters")
            else -> ValidationResult.Valid
        }
    }

    // ── URL validation ──────────────────────────────────────────────────────
    private val URL_REGEX = Regex(
        "^(https?://)?([\\w-]+\\.)+[\\w-]+(/[\\w-./?%&=]*)?$"
    )

    fun validateUrl(url: String, required: Boolean = false): ValidationResult {
        val trimmed = url.trim()
        return when {
            trimmed.isBlank() && required -> ValidationResult.Error("URL is required")
            trimmed.isBlank() -> ValidationResult.Valid
            !URL_REGEX.matches(trimmed) -> ValidationResult.Error("Please enter a valid URL")
            else -> ValidationResult.Valid
        }
    }

    // ── Tags / selection validation ─────────────────────────────────────────
    fun validateMinSelection(items: List<Any>, fieldName: String, min: Int = 1): ValidationResult {
        return if (items.size < min) {
            ValidationResult.Error("Please select at least $min $fieldName")
        } else {
            ValidationResult.Valid
        }
    }

    // ── Price / number validation ────────────────────────────────────────────
    fun validatePositiveNumber(value: String, fieldName: String): ValidationResult {
        val trimmed = value.trim()
        return when {
            trimmed.isBlank() -> ValidationResult.Error("$fieldName is required")
            trimmed.toDoubleOrNull() == null -> ValidationResult.Error("$fieldName must be a valid number")
            trimmed.toDouble() <= 0 -> ValidationResult.Error("$fieldName must be greater than 0")
            else -> ValidationResult.Valid
        }
    }

    // ── Aggregate validation ────────────────────────────────────────────────
    fun validateAll(vararg results: ValidationResult): ValidationResult {
        val firstError = results.filterIsInstance<ValidationResult.Error>().firstOrNull()
        return firstError ?: ValidationResult.Valid
    }
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Error(val message: String) : ValidationResult()

    val isValid: Boolean get() = this is Valid
    val errorMessage: String? get() = (this as? Error)?.message
}
