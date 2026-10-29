package com.ritam.azureload.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Add : Screen("add?url={url}") {
        fun createRoute(url: String? = null): String {
            return if (!url.isNullOrBlank()) "add?url=${java.net.URLEncoder.encode(url, "UTF-8")}" else "add"
        }
    }
    object Browser : Screen("browser?url={url}") {
        fun createRoute(url: String? = null): String {
            return if (!url.isNullOrBlank()) "browser?url=${java.net.URLEncoder.encode(url, "UTF-8")}" else "browser"
        }
    }
    object Detail : Screen("detail/{id}") {
        fun createRoute(id: Long): String = "detail/$id"
    }
    object Settings : Screen("settings")
    object Diagnostics : Screen("diagnostics")
}
