package com.silora.app

/** Only fixed operation names and exception types/frames enter diagnostic logs. */
internal object LogPrivacy {
    private val actions = setOf("status", "save", "delete", "preview", "import", "settings",
        "proposal_apply", "proposal_clear", "period_save", "period_clear", "automation_preview",
        "automation_save", "automation_clear", "reminder_settings", "reminder_test", "tutorial_save",
        "ai_history", "ai_settings", "ai_key", "ai_test", "ai_delete", "ai_clear", "ai_remove_image",
        "ai_chat", "ai_image", "ai_cancel", "permission", "open_api_help", "log_export")
    fun action(value: String) = value.takeIf { it in actions } ?: "unknown"
    fun exception(stage: String, error: Throwable): String = buildString {
        append("exception stage=").append(stage).append('\n')
        var cause: Throwable? = error
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        while (cause != null && seen.add(cause) && seen.size <= 8) {
            append(cause.javaClass.name).append('\n')
            cause.stackTrace.take(32).forEach { append("  at ").append(it.toString()).append('\n') }
            cause = cause.cause
        }
        // Do not call Throwable.toString(), message or printStackTrace(): they can contain input/key/body.
    }
}
