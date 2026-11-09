package com.siddesh.smsalerts.utils

/** Never log runtime payloads, auth values, URLs, names or coordinates. Keep class/stack diagnostics. */
object PrivacyLog {
    private fun safe(message: String, error: Throwable?): String {
        val code = message.substringBefore(':').substringBefore(' ').substringBefore('=')
            .takeIf { it.matches(Regex("[A-Z_]{3,60}")) } ?: "APPLICATION_EVENT"
        return code + (error?.let { "\n" + getStackTraceString(it) } ?: "")
    }
    fun d(tag: String, message: String, error: Throwable? = null): Int = android.util.Log.d(tag, safe(message, error))
    fun i(tag: String, message: String, error: Throwable? = null): Int = android.util.Log.i(tag, safe(message, error))
    fun w(tag: String, message: String, error: Throwable? = null): Int = android.util.Log.w(tag, safe(message, error))
    fun e(tag: String, message: String, error: Throwable? = null): Int = android.util.Log.e(tag, safe(message, error))
    fun v(tag: String, message: String, error: Throwable? = null): Int = android.util.Log.v(tag, safe(message, error))
    fun getStackTraceString(error: Throwable): String = generateSequence(error) { it.cause }.take(6).joinToString("\nCaused by: ") {
        it.javaClass.name + "\n" + it.stackTrace.take(40).joinToString("\n") { frame -> "    at $frame" }
    }
}
