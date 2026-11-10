package com.newsmead.research.study

import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Numerical snapshots only. Never accepts Views, images, camera frames or arbitrary app objects. */
object StudyJson {
    private data class JsonField(val field: java.lang.reflect.Field, val name: String)
    private val fields = ConcurrentHashMap<Class<*>, List<JsonField>>()
    private val camelBoundary = Regex("([a-z0-9])([A-Z])")
    fun obj(vararg values: Pair<String, Any?>): JSONObject = JSONObject().apply {
        values.forEach { (key, item) -> put(key, value(item)) }
    }
    fun value(input: Any?): Any = when (input) {
        null, JSONObject.NULL -> JSONObject.NULL
        is Double -> input.takeIf { it.isFinite() } ?: JSONObject.NULL
        is Float -> input.takeIf { it.isFinite() } ?: JSONObject.NULL
        is Number, is Boolean, is String -> input
        is Enum<*> -> input.name
        is JSONObject, is JSONArray -> input
        is Map<*, *> -> JSONObject().apply { input.forEach { (k, v) -> put(k.toString(), value(v)) } }
        is Pair<*, *> -> JSONArray().put(value(input.first)).put(value(input.second))
        is Iterable<*> -> JSONArray().apply { input.forEach { put(value(it)) } }
        is Array<*> -> JSONArray().apply { input.forEach { put(value(it)) } }
        is DoubleArray -> JSONArray().apply { input.forEach { put(value(it)) } }
        is FloatArray -> JSONArray().apply { input.forEach { put(value(it)) } }
        is IntArray -> JSONArray().apply { input.forEach { put(it) } }
        is LongArray -> JSONArray().apply { input.forEach { put(it) } }
        else -> {
            require(input.javaClass.name.startsWith("com.newsmead.gaze.")) { "Unsupported study value ${input.javaClass.name}" }
            JSONObject().apply {
                fields.getOrPut(input.javaClass) {
                    input.javaClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
                        .map { field -> field.isAccessible = true; JsonField(field, snake(field.name)) }
                }.forEach { member -> put(member.name, value(member.field.get(input))) }
            }
        }
    }
    fun snake(name: String): String = name.replace(camelBoundary, "$1_$2").lowercase(java.util.Locale.ROOT)
    fun hash(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
