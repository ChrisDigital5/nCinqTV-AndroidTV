package app.ncinq.tv.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import java.security.MessageDigest

data class ViewerProfile(
    val id: String,
    val name: String,
    val avatar: String = "face",
    val moviePreferences: List<String> = emptyList(),
    val showPreferences: List<String> = emptyList(),
    val kidsMode: Boolean = false,
    val pinHash: String? = null,
)

class ProfileRepository(context: Context) {
    private val preferences = context.getSharedPreferences("viewer_profiles", 0)
    private val gson = Gson()
    private val listType = object : TypeToken<List<ViewerProfile>>() {}.type

    fun profiles(): List<ViewerProfile> = runCatching {
        gson.fromJson<List<ViewerProfile>>(preferences.getString("profiles", null), listType)
    }.getOrNull().orEmpty().ifEmpty {
        listOf(
            ViewerProfile(id = "main", name = "Profile 1"),
            ViewerProfile(id = "kids", name = "Kids", avatar = "kids", showPreferences = listOf("Kids"), kidsMode = true),
        )
    }

    fun lastProfileId(): String? = preferences.getString("last_profile", null)

    fun select(profile: ViewerProfile) {
        preferences.edit().putString("last_profile", profile.id).apply()
    }

    fun save(profile: ViewerProfile): List<ViewerProfile> {
        val stored = profiles().toMutableList()
        val normalized = profile.copy(
            id = profile.id.ifBlank { UUID.randomUUID().toString() },
            name = profile.name.trim().ifBlank { "Profile" },
        )
        val index = stored.indexOfFirst { it.id == normalized.id }
        if (index >= 0) stored[index] = normalized else stored += normalized
        persist(stored)
        return stored
    }

    fun delete(profileId: String): List<ViewerProfile> {
        val remaining = profiles().filterNot { it.id == profileId }
            .ifEmpty { listOf(ViewerProfile(id = "main", name = "Profile 1")) }
        persist(remaining)
        return remaining
    }

    private fun persist(profiles: List<ViewerProfile>) {
        preferences.edit().putString("profiles", gson.toJson(profiles)).apply()
    }
}

fun hashProfilePin(pin: String): String = MessageDigest.getInstance("SHA-256")
    .digest(pin.toByteArray())
    .joinToString("") { "%02x".format(it) }
