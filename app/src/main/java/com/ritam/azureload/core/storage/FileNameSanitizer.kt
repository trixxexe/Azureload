package com.ritam.azureload.core.storage

object FileNameSanitizer {
    private val ILLEGAL_CHARACTERS = PatternRegex("[/\\\\:*?\"<>|\\x00-\\x1F]")

    fun sanitize(candidate: String, defaultName: String = "download"): String {
        var clean = candidate.trim()
        clean = ILLEGAL_CHARACTERS.replace(clean, "_")
        clean = clean.replace("^\\.+".toRegex(), "") // avoid hidden files or relative paths

        if (clean.isBlank()) {
            clean = defaultName
        }

        // Bound filename length to 200 characters to prevent filesystem overflow
        if (clean.length > 200) {
            val ext = clean.substringAfterLast('.', "")
            val base = clean.substringBeforeLast('.')
            clean = if (ext.isNotEmpty()) {
                base.take(190) + "." + ext
            } else {
                clean.take(200)
            }
        }
        return clean
    }

    private class PatternRegex(regex: String) {
        private val r = Regex(regex)
        fun replace(input: String, replacement: String): String = r.replace(input, replacement)
    }
}
