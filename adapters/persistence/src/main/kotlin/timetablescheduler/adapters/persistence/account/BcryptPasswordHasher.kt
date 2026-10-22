package timetablescheduler.adapters.persistence.account

import at.favre.lib.crypto.bcrypt.BCrypt
import timetablescheduler.application.port.PasswordHasher

/** bcrypt with cost 12 (R7); passwords are never stored or logged in readable form (FR-034). */
class BcryptPasswordHasher(
    private val cost: Int = 12,
) : PasswordHasher {
    override fun hash(plainPassword: String): String = BCrypt.withDefaults().hashToString(cost, plainPassword.toCharArray())

    override fun matches(
        plainPassword: String,
        passwordHash: String,
    ): Boolean = BCrypt.verifyer().verify(plainPassword.toCharArray(), passwordHash).verified
}
