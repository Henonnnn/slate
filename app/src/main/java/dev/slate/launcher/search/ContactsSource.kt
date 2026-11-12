package dev.slate.launcher.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.Contacts
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactHit(val id: Long, val lookupKey: String, val name: String) {
    fun viewIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Contacts.getLookupUri(id, lookupKey)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * Name lookups through the platform contacts provider. Optional: only with READ_CONTACTS,
 * which the search surface asks for with an inline explanation. Nothing leaves the device.
 */
class ContactsSource(private val context: Context) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun search(query: String, limit: Int = 4): List<ContactHit> {
        if (query.isBlank() || !hasPermission()) return emptyList()
        return withContext(Dispatchers.IO) {
            val uri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, Uri.encode(query.trim()))
            val projection = arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY)
            try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                    buildList {
                        while (c.moveToNext() && size < limit) {
                            val name = c.getString(2) ?: continue
                            add(ContactHit(c.getLong(0), c.getString(1) ?: continue, name))
                        }
                    }
                }.orEmpty()
            } catch (e: SecurityException) {
                emptyList() // Permission revoked mid-session.
            } catch (e: IllegalArgumentException) {
                Log.w("ContactsSource", "Contacts query failed", e)
                emptyList()
            }
        }
    }
}
