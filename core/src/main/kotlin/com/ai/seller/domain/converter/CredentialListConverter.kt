package com.ai.seller.domain.converter

import com.ai.seller.domain.User
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

@Converter
class CredentialListConverter : AttributeConverter<List<User.Credential>, String> {
    private val mapper = jacksonObjectMapper()

    override fun convertToDatabaseColumn(attribute: List<User.Credential>?): String {
        return mapper.writeValueAsString(attribute ?: emptyList<User.Credential>())
    }

    override fun convertToEntityAttribute(dbData: String?): List<User.Credential> {
        if (dbData.isNullOrBlank()) return emptyList()
        return mapper.readValue<List<User.Credential>>(dbData)
    }
}
