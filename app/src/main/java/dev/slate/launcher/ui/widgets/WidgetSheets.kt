package dev.slate.launcher.ui.widgets

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.slate.launcher.R
import dev.slate.launcher.container
import dev.slate.launcher.data.WeatherPlace
import dev.slate.launcher.data.WidgetRef
import dev.slate.launcher.data.Widgets
import dev.slate.launcher.notifications.NotificationAccess
import dev.slate.launcher.widgets.WeatherRepository
import dev.slate.launcher.widgets.WidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlateSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        properties = ModalBottomSheetProperties(
            isAppearanceLightStatusBars = false,
            isAppearanceLightNavigationBars = !isSystemInDarkTheme(),
        ),
    ) { content() }
}

@Composable
internal fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
    )
}

@Composable
internal fun SheetRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

/** Long-press on the clock: home-wide actions. Settings join this in a later milestone. */
@Composable
fun HomeMenuSheet(
    notificationAccess: Boolean,
    onAppearance: () -> Unit,
    onClock: () -> Unit,
    onMoveHome: () -> Unit,
    onScreenTime: () -> Unit,
    onSettings: () -> Unit,
    onAddWidget: () -> Unit,
    onMessageHub: () -> Unit,
    onCreateFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    SlateSheet(onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            SheetTitle(stringResource(R.string.app_name))
            SheetRow(stringResource(R.string.appearance), onAppearance)
            SheetRow(stringResource(R.string.clock_title), onClock)
            SheetRow(stringResource(R.string.move_home), onMoveHome)
            SheetRow(stringResource(R.string.screen_time), onScreenTime)
            SheetRow(stringResource(R.string.message_hub), onMessageHub)
            SheetRow(stringResource(R.string.folder_create), onCreateFolder)
            SheetRow(stringResource(R.string.add_widget), onAddWidget)
            // Toggled in system settings; this shows the state and goes there.
            SheetRow(
                stringResource(R.string.notification_dots) + " · " +
                    stringResource(if (notificationAccess) R.string.torch_on else R.string.torch_off),
            ) {
                onDismiss()
                NotificationAccess.open(context)
            }
            SheetRow(stringResource(R.string.settings), onSettings)
        }
    }
}

/** What a widget's long-press menu can do; the caller decides which apply. */
class WidgetMenu(
    val title: String,
    val heightDp: Int,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val canStackWithAbove: Boolean,
    val isStacked: Boolean,
    val canReconfigure: Boolean,
    val isWeather: Boolean,
)

/**
 * Widget menu. The height slider previews live through [onHeightPreview] and is saved when
 * the finger lifts ([onHeight]).
 */
@Composable
fun WidgetSheet(
    menu: WidgetMenu,
    onHeightPreview: (Int?) -> Unit,
    onHeight: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onStackWithAbove: () -> Unit,
    onUnstack: () -> Unit,
    onReconfigure: () -> Unit,
    onChangePlace: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var height by remember { mutableFloatStateOf(menu.heightDp.toFloat()) }
    SlateSheet({ onHeightPreview(null); onDismiss() }) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            SheetTitle(menu.title)
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.widget_height), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${height.roundToInt()} dp", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(
                value = height,
                onValueChange = {
                    height = (it / HEIGHT_STEP).roundToInt() * HEIGHT_STEP
                    onHeightPreview(height.roundToInt())
                },
                onValueChangeFinished = { onHeight(height.roundToInt()) },
                valueRange = Widgets.MIN_HEIGHT_DP.toFloat()..Widgets.MAX_HEIGHT_DP.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
            if (menu.canMoveUp) SheetRow(stringResource(R.string.move_up), onMoveUp)
            if (menu.canMoveDown) SheetRow(stringResource(R.string.move_down), onMoveDown)
            if (menu.canStackWithAbove) SheetRow(stringResource(R.string.widget_stack_with_above), onStackWithAbove)
            if (menu.isStacked) SheetRow(stringResource(R.string.widget_unstack), onUnstack)
            if (menu.canReconfigure) SheetRow(stringResource(R.string.widget_reconfigure), onReconfigure)
            if (menu.isWeather) SheetRow(stringResource(R.string.weather_change_place), onChangePlace)
            SheetRow(stringResource(R.string.widget_remove), onRemove)
        }
    }
}

private const val HEIGHT_STEP = 8f

/**
 * Picker: Slate's own widgets first (those not already placed), then every app widget the
 * system offers, grouped under its app, with the provider's preview image.
 */
@Composable
fun WidgetPickerSheet(
    placedBuiltins: Set<WidgetRef.Kind>,
    onBuiltin: (WidgetRef) -> Unit,
    onProvider: (WidgetProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    val repository = LocalContext.current.container.widgetRepository
    val providers by produceState<List<WidgetProvider>?>(null) { value = repository.providers() }
    SlateSheet(onDismiss) {
        Column(Modifier.fillMaxHeight(0.85f).navigationBarsPadding()) {
            SheetTitle(stringResource(R.string.add_widget))
            LazyColumn(Modifier.fillMaxWidth()) {
                val builtins = listOf(
                    Triple(WidgetRef.calendar, R.string.widget_calendar, R.string.widget_calendar_desc),
                    Triple(WidgetRef.weather, R.string.widget_weather, R.string.widget_weather_desc),
                ).filterNot { it.first.kind in placedBuiltins }
                if (builtins.isNotEmpty()) {
                    item(key = "slate-label") { GroupLabel(R.string.app_name) }
                    items(builtins, key = { it.first.key }) { (ref, title, desc) ->
                        PickerItem(title = stringResource(title), subtitle = stringResource(desc), preview = null) { onBuiltin(ref) }
                    }
                }
                val list = providers ?: return@LazyColumn
                var lastApp: String? = null
                list.forEach { provider ->
                    if (provider.appLabel != lastApp) {
                        lastApp = provider.appLabel
                        item(key = "app:" + provider.appLabel + provider.user.hashCode()) { GroupLabelText(provider.appLabel) }
                    }
                    item(key = provider.key) {
                        val maxPx = with(LocalDensity.current) { 96.dp.roundToPx() }
                        val preview by produceState<ImageBitmap?>(null, provider.key) { value = repository.preview(provider, maxPx) }
                        val cells = sizeHint(provider)
                        PickerItem(title = provider.label, subtitle = cells, preview = preview) { onProvider(provider) }
                    }
                }
            }
        }
    }
}

@Composable
private fun sizeHint(provider: WidgetProvider): String {
    val density = LocalDensity.current.density
    val w = (provider.info.minWidth / density).roundToInt()
    val h = (provider.info.minHeight / density).roundToInt()
    return stringResource(R.string.widget_min_size, w, h)
}

@Composable
private fun GroupLabel(res: Int) = GroupLabelText(stringResource(res))

@Composable
private fun GroupLabelText(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickerItem(title: String, subtitle: String?, preview: ImageBitmap?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            if (preview != null) Image(preview, contentDescription = null, modifier = Modifier.heightIn(max = 72.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

/**
 * Where the weather widget looks: the device's coarse location, or a city found through
 * Open-Meteo's geocoding (the typed name is the only thing sent).
 */
@Composable
fun WeatherPlaceDialog(onPlace: (WeatherPlace) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository: WeatherRepository = context.container.weatherRepository
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherRepository.PlaceResult>?>(emptyList()) }
    var locating by remember { mutableStateOf(false) }
    var locationFailed by remember { mutableStateOf(false) }
    val hereName = stringResource(R.string.weather_here)

    fun useDeviceLocation() {
        locating = true
        scope.launch {
            val location = repository.deviceLocation()
            locating = false
            if (location == null) locationFailed = true else onPlace(WeatherPlace(hereName, location.latitude, location.longitude))
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useDeviceLocation() else locationFailed = true
    }

    // Debounced search: one request after typing pauses.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(450)
        results = repository.searchPlaces(query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weather_choose_place)) },
        text = {
            Column {
                Text(stringResource(R.string.weather_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = { if (repository.hasLocationPermission()) useDeviceLocation() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                    enabled = !locating,
                ) {
                    Text(stringResource(if (locating) R.string.weather_locating else R.string.weather_use_location), color = MaterialTheme.colorScheme.primary)
                }
                if (locationFailed) {
                    Text(stringResource(R.string.weather_location_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.weather_city_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { scope.launch { results = repository.searchPlaces(query) } }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, cursorColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                val list = results
                if (list == null && !repository.hasNetworkPermission()) {
                    Text(stringResource(R.string.weather_network_off), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { openAppSettings(context) }) {
                        Text(stringResource(R.string.open_settings), color = MaterialTheme.colorScheme.primary)
                    }
                } else if (list == null) {
                    Text(stringResource(R.string.weather_unreachable), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                } else {
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(list, key = { "${it.latitude},${it.longitude}" }) { place ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) { onPlace(WeatherPlace(place.name, place.latitude, place.longitude)) }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(place.name, style = MaterialTheme.typography.bodyLarge)
                                if (place.description.isNotEmpty()) {
                                    Text(place.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface) }
        },
    )
}
