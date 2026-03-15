package com.ai.seller.domain

import com.ai.seller.domain.converter.CredentialListConverter
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(columnDefinition = "json")
    @Convert(converter = CredentialListConverter::class)
    var credentials: List<Credential> = emptyList(),
) {

    sealed class Credential {
        data class Naver(
            val clientId: String,
            val clientSecret: String,
        ) : Credential()
    }
}

interface UserRepository : JpaRepository<User, Long>

