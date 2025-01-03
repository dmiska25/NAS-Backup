package com.example.nasbackup.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Properties
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile

class SmbFileContext {
    val ipAddress: String
    val shareName: String
    val username: String?
    val password: String?
    val route: String?

    companion object {
        val mapper = ObjectMapper()

        fun fromJson(json: String): SmbFileContext {
            return mapper.readValue(json, SmbFileContext::class.java)
        }

        private fun trimRoute(route: String?, ipAddress: String, shareName: String): String? {
            return route
                ?.removePrefix("smb://")
                ?.removePrefix("smbs://")
                ?.removePrefix(ipAddress)
                ?.removePrefix("/")
                ?.removePrefix(shareName)
                ?.removePrefix("/")
        }
    }

    @JsonCreator
    constructor(
        @JsonProperty("ipAddress")
        ipAddress: String,
        @JsonProperty("shareName")
        shareName: String,
        @JsonProperty("username")
        username: String? = null,
        @JsonProperty("password")
        password: String? = null,
        @JsonProperty("route")
        route: String? = null
    ) {
        this.ipAddress = ipAddress
        this.shareName = shareName
        this.username = username
        this.password = password
        this.route = trimRoute(route, ipAddress, shareName)
    }

    fun toJson(): String {
        return mapper.writeValueAsString(this)
    }

    fun toSmbFile(): SmbFile = toSmbFile(true)

    fun toSmbFileWithoutRoute(): SmbFile = toSmbFile(false)

    private fun toSmbFile(includeRoute: Boolean = true): SmbFile {
        val smbUrl = "smb://$ipAddress/$shareName/" +
                if (includeRoute && ! route.isNullOrBlank()) route else ""
        val properties = Properties().apply {
            put("jcifs.smb.client.minVersion", "SMB202")
            put("jcifs.smb.client.maxVersion", "SMB311")
        }
        val config = PropertyConfiguration(properties)
        val baseContext: CIFSContext = BaseContext(config)
        val authContext = baseContext.withCredentials(
            NtlmPasswordAuthenticator("", username, password)
        )
        return SmbFile(smbUrl, authContext)
    }
}
