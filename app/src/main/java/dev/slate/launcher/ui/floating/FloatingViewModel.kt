package dev.slate.launcher.ui.floating

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.mapLatest
import dev.slate.launcher.floating.AudioPrompt
import dev.slate.launcher.floating.AudioRoutes
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.channelFlow
import android.os.SystemClock
import dev.slate.launcher.search.Torch
import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.slate.launcher.container
import dev.slate.launcher.data.SettingsRepository
import dev.slate.launcher.floating.FloatingChooser
import dev.slate.launcher.floating.FloatingItem
import dev.slate.launcher.floating.FloatingSources
import dev.slate.launcher.floating.MediaSessions
import dev.slate.launcher.floating.NowPlaying
import dev.slate.launcher.floating.nextAlarm
import dev.slate.launcher.notifications.NotificationStore
import dev.slate.launcher.floating.LiveActivity
import dev.slate.launcher.sendFromForeground
import android.app.PendingIntent
import android.content.Intent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

/**
 * Feeds The Pill. Each source only runs while it's switched on and allowed (media and live
 * activities need notification access), and only while home is visible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("StaticFieldLeak") // The Application, from the factory; it can't leak.
class FloatingViewModel(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val notificationStore: NotificationStore,
    private val media: MediaSessions,
    private val torch: Torch,
    private val routes: AudioRoutes,
) : ViewModel() {

    /** Music offered while Bluetooth is on or a headset is connected, as the user chose. */
    private val musicItem: Flow<FloatingItem.Music?> by lazy {
        sources.map { MusicOffer(it.audioPrompt, it.audioPromptPlayer, it.musicApp, it.lastPlayer) }.distinctUntilChanged().flatMapLatest { offer ->
            val trigger = when (offer.prompt) {
                AudioPrompt.Off -> flowOf(false)
                AudioPrompt.Bluetooth -> routes.bluetoothOn()
                AudioPrompt.Headset -> routes.headset()
            }
            trigger.mapLatest { on ->
                if (!on) return@mapLatest null
                routes.resolve(offer.app, offer.lastPlayer)?.let { (pkg, label) -> FloatingItem.Music(pkg, label, offer.player, musicSince(offer.prompt)) }
            }
        }
    }

    private data class MusicOffer(val prompt: AudioPrompt, val player: Boolean, val app: String?, val lastPlayer: String?)

    // One "since" per kind of trigger, so swiping the offer away holds until the app restarts.
    private val musicStarted = HashMap<AudioPrompt, Long>()
    private fun musicSince(prompt: AudioPrompt): Long = synchronized(musicStarted) { musicStarted.getOrPut(prompt) { SystemClock.elapsedRealtime() } }

    @Volatile
    private var rememberedPlayer: String? = null

    private fun rememberPlayer(packageName: String) {
        if (packageName == rememberedPlayer) return
        rememberedPlayer = packageName
        viewModelScope.launch {
            settingsRepository.update { s -> if (s.floating.lastPlayer == packageName) s else s.copy(floating = s.floating.copy(lastPlayer = packageName)) }
        }
    }


    @Volatile
    private var torchCamera: String? = null

    /**
     * The flashlight while it's lit, whoever lit it (the system reports every change), and for
     * [FloatingSources.torchLingerSeconds] after it goes out, so it can be lit again from the same place.
     */
    private val torchItem: Flow<FloatingItem.Torch?> by lazy {
        sources.map { it.torch to it.torchLingerSeconds }.distinctUntilChanged().flatMapLatest { (on, linger) ->
            if (!on) {
                flowOf(null)
            } else {
                channelFlow {
                    val camera = torch.cameraId().also { torchCamera = it }
                    var since = 0L
                    send(null)
                    torch.state(camera).distinctUntilChanged().collectLatest { lit ->
                        if (lit) {
                            if (since == 0L) since = SystemClock.elapsedRealtime()
                            send(FloatingItem.Torch(true, since))
                        } else if (since != 0L) {
                            send(FloatingItem.Torch(false, since))
                            delay(linger.coerceIn(1, 60) * 1000L)
                            since = 0L
                            send(null)
                        }
                    }
                }
            }
        }
    }


    private val dismissed = MutableStateFlow(emptySet<String>())

    private val sources: Flow<FloatingSources> = settingsRepository.settings.map { it.floating }.distinctUntilChanged()

    private val nowPlaying: Flow<NowPlaying?> = combine(sources, notificationStore.connected) { s, connected -> s.media && connected }
        .distinctUntilChanged()
        .flatMapLatest { on -> if (on) media.nowPlaying() else flowOf(null) }
        // Whoever plays is who the music offer points at next time.
        .onEach { playing -> playing?.packageName?.let(::rememberPlayer) }

    private val live: Flow<List<LiveActivity>> = sources.map { it.timers }.distinctUntilChanged()
        .flatMapLatest { on -> if (on) notificationStore.live else flowOf(emptyList()) }

    private val alarm: Flow<Instant?> = sources.map { it.alarm }.distinctUntilChanged()
        .flatMapLatest { on -> if (on) nextAlarm(context) else flowOf(null) }

    // Re-evaluated every 30 s so windows ("within the hour") open and close on time.
    private val clock: Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(30_000)
        }
    }

    /** The main item first, then the one beside it. */
    val items: StateFlow<List<FloatingItem>> = combine(clock, sources, nowPlaying, live, alarm) { now, s, m, l, a -> Inputs(now, s, m, l, a) }
        .combine(torchItem) { i, t -> i to t }
        .combine(musicItem) { (i, t), m -> Triple(i, t, m) }
        .combine(dismissed) { (i, t, m), d -> FloatingChooser.choose(i.now, i.sources, i.media, i.live, i.alarm, d, t, m) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class Inputs(val now: Instant, val sources: FloatingSources, val media: NowPlaying?, val live: List<LiveActivity>, val alarm: Instant?)

    fun dismiss(item: FloatingItem) {
        dismissed.value = dismissed.value + item.key
    }

    fun togglePlay() = media.togglePlay()

    fun next() = media.next()

    fun pressPlay() = routes.pressPlay()

    fun openApp(packageName: String) = routes.open(packageName)

    /** False when the camera is in use by another app. */
    fun setTorch(on: Boolean): Boolean = torchCamera?.let { torch.set(it, on) } ?: false

    fun previous() = media.previous()

    fun seekTo(positionMs: Long) = media.seekTo(positionMs)

    /** Opens what a live activity's notification points at. */
    fun openLive(context: Context, activity: LiveActivity) {
        activity.contentIntent?.sendFromForeground(context)
    }

    /** Presses one of a live activity's own buttons (pause, add a minute, dismiss). */
    fun liveAction(context: Context, intent: PendingIntent) {
        intent.sendFromForeground(context, Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND))
    }

    fun openMedia(context: Context) = media.open(context)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                val c = app.container
                FloatingViewModel(app, c.settingsRepository, c.notificationStore, c.mediaSessions, c.torch, c.audioRoutes)
            }
        }
    }
}
