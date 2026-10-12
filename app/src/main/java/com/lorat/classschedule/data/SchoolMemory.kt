package com.lorat.classschedule.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A remembered school entry used to prefill the "add school" form. */
@Serializable
data class SchoolDraft(
    val name: String = "",
    val url: String = "",
    val preset: String = TimetableExtractor.PRESET_AUTO,
)

/**
 * Remembers the last school you typed plus a short list of recent ones, so that
 * re-opening "add / change school" does not require retyping everything.
 */
class SchoolMemory(context: Context) {

    private val prefs = context.getSharedPreferences("school_memory", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(SchoolDraft.serializer())

    fun lastDraft(): SchoolDraft? = prefs.getString(KEY_DRAFT, null)?.let { decode(it) }

    fun saveDraft(draft: SchoolDraft) {
        runCatching {
            prefs.edit()
                .putString(KEY_DRAFT, json.encodeToString(SchoolDraft.serializer(), draft))
                .apply()
        }
    }

    fun recents(): List<SchoolDraft> =
        prefs.getString(KEY_RECENTS, null)?.let { raw ->
            runCatching { json.decodeFromString(listSerializer, raw) }.getOrNull()
        } ?: emptyList()

    fun addRecent(draft: SchoolDraft) {
        if (draft.name.isBlank() && draft.url.isBlank()) return
        val list = recents().filterNot { it.url == draft.url }.toMutableList()
        list.add(0, draft)
        runCatching {
            prefs.edit()
                .putString(KEY_RECENTS, json.encodeToString(listSerializer, list.take(5)))
                .apply()
        }
    }

    private fun decode(raw: String): SchoolDraft? =
        runCatching { json.decodeFromString(SchoolDraft.serializer(), raw) }.getOrNull()

    private companion object {
        const val KEY_DRAFT = "draft"
        const val KEY_RECENTS = "recents"
    }
}
