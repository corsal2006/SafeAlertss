package com.siddesh.smsalerts.data.model

data class UserProfile(
    val uid: String = "",
<<<<<<< HEAD
    val isLoaded: Boolean = true,
=======
>>>>>>> origin/main
    val name: String = "",
    val fullName: String = name,
    val email: String = "",
    val phoneNumber: String = "",
<<<<<<< HEAD
    val dateOfBirth: String? = null,
    val homeAddress: String = "",
=======
>>>>>>> origin/main
    val photoUrl: String? = null,
    val authProvider: String = "email_password",
    val provider: String = authProvider,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
<<<<<<< HEAD
    val age: Int? get() = com.siddesh.smsalerts.utils.ProfileDates.age(dateOfBirth)
    val isComplete: Boolean get() = fullName.isNotBlank() && age != null

=======
>>>>>>> origin/main
    fun toMap(): Map<String, Any?> {
        val effectiveName = if (fullName.isNotBlank()) fullName else name
        val effectiveProvider = if (provider.isNotBlank()) provider else authProvider
        return mapOf(
            "uid" to uid,
            "fullName" to effectiveName,
            "name" to effectiveName,
            "email" to email,
            "phoneNumber" to phoneNumber,
<<<<<<< HEAD
            "dateOfBirth" to dateOfBirth,
            "homeAddress" to homeAddress,
=======
>>>>>>> origin/main
            "photoUrl" to photoUrl,
            "authProvider" to effectiveProvider,
            "provider" to effectiveProvider,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }
}
