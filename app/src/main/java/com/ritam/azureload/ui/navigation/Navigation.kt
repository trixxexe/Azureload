package com.ritam.azureload.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Add : Screen("add?url={url}&cookie={cookie}&userAgent={userAgent}&fileName={fileName}") {
        fun createRoute(
            url: String? = null,
            cookie: String? = null,
            userAgent: String? = null,
            fileName: String? = null
        ): String {
            val params = mutableListOf<String>()
            if (!url.isNullOrBlank()) params.add("url=${URLEncoder.encode(url, "UTF-8")}")
            if (!cookie.isNullOrBlank()) params.add("cookie=${URLEncoder.encode(cookie, "UTF-8")}")
            if (!userAgent.isNullOrBlank()) params.add("userAgent=${URLEncoder.encode(userAgent, "UTF-8")}")
            if (!fileName.isNullOrBlank()) params.add("fileName=${URLEncoder.encode(fileName, "UTF-8")}")
            return if (params.isEmpty()) "add" else "add?" + params.joinToString("&")
        }
    }
    object Browser : Screen("browser?url={url}") {
        fun createRoute(url: String? = null): String {
            return if (!url.isNullOrBlank()) "browser?url=${URLEncoder.encode(url, "UTF-8")}" else "browser"
        }
    }
    object Detail : Screen("detail/{id}") {
        fun createRoute(id: Long): String = "detail/$id"
    }
    object Settings : Screen("settings")
    object Diagnostics : Screen("diagnostics")
}
