package dev.slate.launcher.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

internal val SettingsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal object LauncherSettingsSerializer : Serializer<LauncherSettings> {
    override val defaultValue = LauncherSettings()

    override suspend fun readFrom(input: InputStream): LauncherSettings = try {
        SettingsJson.decodeFromString(LauncherSettings.serializer(), input.readBytes().decodeToString()).migrated()
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable settings", e)
    }

    override suspend fun writeTo(t: LauncherSettings, output: OutputStream) {
        output.write(SettingsJson.encodeToString(LauncherSettings.serializer(), t).encodeToByteArray())
    }
}

class SettingsRepository(context: Context) {
    private val store: DataStore<LauncherSettings> = DataStoreFactory.create(
        serializer = LauncherSettingsSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { LauncherSettings() },
        produceFile = { context.dataStoreFile("settings.json") },
    )

    val settings: Flow<LauncherSettings> = store.data

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        store.updateData(transform)
    }
}
