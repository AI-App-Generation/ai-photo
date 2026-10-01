package com.bblvn.composebaseproject.core

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/*
 * ---------------------------------------------------------------------------------------------
 *  THE SCREEN-STATE CONVENTION FOR THIS APP  (every screen in phase05 follows it, no exceptions)
 * ---------------------------------------------------------------------------------------------
 *
 *  1. STATE — one immutable data class per screen, named `<Screen>UiState`, living in the
 *     screen's own package (`features/home/HomeUiState.kt`, or inside the ViewModel file when
 *     tiny). EVERY property has a default, so `<Screen>UiState()` is the valid initial state.
 *     Loading / error are plain properties (`isLoading`, `error: UiText?`) — do NOT model a
 *     screen as a sealed Loading|Ready|Error hierarchy, because almost every screen here needs
 *     to show content and a spinner at the same time. Derived values are computed `val`s on the
 *     state class, never recomputed inside composables.
 *
 *  2. VIEWMODEL — extends [StateViewModel] and exposes exactly two things:
 *          val state: StateFlow<HomeUiState>      (from StateViewModel)
 *          val events: Flow<UiEvent>              (from StateViewModel)
 *     ...plus plain functions the screen calls (`onTemplateClick(id)`, `onRetry()`).
 *     Mutate with `update { it.copy(...) }`. Never expose a MutableStateFlow.
 *     A screen with no real state needs no ViewModel at all.
 *
 *  3. ONE-SHOT EVENTS — anything that must happen exactly once (navigate, toast, open paywall,
 *     show a system dialog) is sent with `emit(UiEvent.X)` and never stored in the state class.
 *     They travel on a buffered [Channel], so nothing is lost while the screen is recomposing.
 *
 *  4. SCREEN — collects state with `collectAsStateWithLifecycle()`-style read and handles events
 *     with exactly one [OnUiEvent] block at the top of the composable. Navigation lives in the
 *     screen (it owns the NavController), never in the ViewModel.
 *
 *          @Composable
 *          fun HomeScreen(nav: NavController, vm: HomeViewModel = viewModel()) {
 *              val state by vm.state.collectAsState()
 *              OnUiEvent(vm.events) { event ->
 *                  when (event) {
 *                      is UiEvent.Navigate -> nav.navigate(event.route)
 *                      is UiEvent.NavigateBack -> nav.popBackStack()
 *                      is UiEvent.ShowToast -> toast(event.text.resolve(context))
 *                      else -> Unit
 *                  }
 *              }
 *              ...
 *          }
 *
 *  5. TEXT — user-visible strings are [UiText]. `UiText.Res(R.string.x)` for anything
 *     translatable (the default); `UiText.Raw` only for values that come from the server or the
 *     catalog (template names). Prompts are NEVER UiText — they must not be translated (R6.4).
 */

/** A string that may come from resources (translatable) or from data (already final). */
sealed interface UiText {

    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Raw(val value: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Raw -> value
        is Res -> if (args.isEmpty()) {
            context.getString(id)
        } else {
            context.getString(id, *args.toTypedArray())
        }
    }

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

/**
 * One-shot side effects. Screens handle the cases they care about and ignore the rest
 * (`else -> Unit`), so new cases can be added without touching every screen.
 */
sealed interface UiEvent {

    /** Navigate to a route string built by the app's route helpers. */
    data class Navigate(val route: String, val popUpToInclusiveRoute: String? = null) : UiEvent

    data object NavigateBack : UiEvent

    data class ShowToast(val text: UiText) : UiEvent

    /** A blocking-ish error the screen should surface (inline banner or popup, screen's choice). */
    data class ShowError(val text: UiText) : UiEvent

    /** A locked feature was tapped; the screen opens the paywall (modal slide-up). */
    data object OpenPaywall : UiEvent

    /** Something outside the app: a share sheet, app settings, a permission request. */
    data class RunAction(val action: UiAction) : UiEvent
}

/** External actions a screen may be asked to perform. Kept as data so ViewModels stay Android-free. */
sealed interface UiAction {
    data class ShareImage(val filePath: String) : UiAction
    data class OpenUrl(val url: String) : UiAction
    data object OpenAppSettings : UiAction
    data object RequestPhotoPermission : UiAction
    data object RequestCameraPermission : UiAction
}

/**
 * Base for every screen ViewModel in this app. Deliberately thin — it only removes the
 * MutableStateFlow / Channel boilerplate that would otherwise be repeated ~16 times.
 */
abstract class StateViewModel<S : Any>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    protected val currentState: S get() = _state.value

    protected fun update(transform: (S) -> S) {
        _state.value = transform(_state.value)
    }

    protected fun setState(value: S) {
        _state.value = value
    }

    /** Non-suspending on purpose: the channel is buffered, so this never blocks or drops. */
    protected fun emit(event: UiEvent) {
        _events.trySend(event)
    }

    protected fun toast(text: UiText) = emit(UiEvent.ShowToast(text))

    protected fun navigate(route: String, popUpToInclusiveRoute: String? = null) =
        emit(UiEvent.Navigate(route, popUpToInclusiveRoute))
}

/**
 * The single place a screen consumes [UiEvent]s. Put exactly one of these at the top of every
 * screen composable.
 */
@Composable
fun OnUiEvent(events: Flow<UiEvent>, onEvent: (UiEvent) -> Unit) {
    LaunchedEffect(events) {
        events.collect { onEvent(it) }
    }
}
