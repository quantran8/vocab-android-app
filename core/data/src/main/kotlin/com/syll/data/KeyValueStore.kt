package com.syll.data

import android.content.Context
import android.content.SharedPreferences

/**
 * The `UserDefaults` counterpart: small synchronous settings the stores read at launch. Stores take this
 * interface, so tests and previews pass [InMemoryKeyValueStore] (iOS `defaults: nil`).
 */
interface KeyValueStore {
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun remove(key: String)
}

class SharedPreferencesKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {
    constructor(context: Context, name: String = "com.syll.settings") :
        this(context.getSharedPreferences(name, Context.MODE_PRIVATE))

    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String?) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
}

class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Any?>()

    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getString(key: String) = values[key] as? String
    override fun putString(key: String, value: String?) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}
