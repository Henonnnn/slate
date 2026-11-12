package dev.slate.launcher.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.slate.launcher.container
import dev.slate.launcher.data.Backup
import dev.slate.launcher.data.BackupFile
import dev.slate.launcher.data.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val backups: BackupRepository) : ViewModel() {

    sealed interface BackupState {
        data object Idle : BackupState
        data object Saved : BackupState
        data object SaveFailed : BackupState
        /** A valid file was read; waiting for the user to confirm replacing their settings. */
        data class Confirm(val file: BackupFile) : BackupState
        data class Restored(val appWidgetsLeftOut: Int) : BackupState
        data object NotABackup : BackupState
        data object TooNew : BackupState
    }

    private val _backup = MutableStateFlow<BackupState>(BackupState.Idle)
    val backup: StateFlow<BackupState> = _backup

    fun export(uri: Uri) {
        viewModelScope.launch {
            _backup.value = if (backups.export(uri)) BackupState.Saved else BackupState.SaveFailed
        }
    }

    fun read(uri: Uri) {
        viewModelScope.launch {
            _backup.value = when (val d = backups.read(uri)) {
                is Backup.Decoded.Ok -> BackupState.Confirm(d.file)
                Backup.Decoded.NotABackup -> BackupState.NotABackup
                Backup.Decoded.TooNew -> BackupState.TooNew
            }
        }
    }

    fun confirmRestore(file: BackupFile) {
        viewModelScope.launch {
            backups.restore(file)
            _backup.value = BackupState.Restored(file.appWidgetsLeftOut)
        }
    }

    fun dismiss() {
        _backup.value = BackupState.Idle
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(this[APPLICATION_KEY]!!.container.backupRepository) }
        }
    }
}
