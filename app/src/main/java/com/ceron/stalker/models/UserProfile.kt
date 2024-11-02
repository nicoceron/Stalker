package com.ceron.stalker.models

import java.util.Date

data class UserProfile(
    var name: String = "",
    var phone: String = "",
    var online: Boolean = false,
    var latitude: Double? = null,
    var longitude: Double? = null,
    val createdAt: Long = Date().time
) {
    constructor() : this("", "")
}
