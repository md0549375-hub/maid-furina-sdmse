package eu.darken.sdmse.furina.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.furina.core.data.FurinaSettings
import eu.darken.sdmse.furina.core.data.ScheduleSpec
import eu.darken.sdmse.furina.core.data.SecretStore
import eu.darken.sdmse.furina.core.network.GeminiClient
import eu.darken.sdmse.furina.core.network.KeyCheck
import eu.darken.sdmse.furina.core.persona.PersonaFormatter
import eu.darken.sdmse.furina.core.persona.PersonaFormatter.Event
import eu.darken.sdmse.furina.feature.automation.FurinaScheduler
import eu.darken.sdmse.furina.feature.chat.IntentRouter
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class GeminiSettingsViewModel @Inject constructor(
    private val secrets: SecretStore,
    private val client: GeminiClient,
    private val settings: FurinaSettings,
    private val scheduler: FurinaScheduler,
    private val persona: PersonaFormatter,
) : ViewModel() {

    enum class Status { NO_KEY, CHECKING, ONLINE, INVALID, UNREACHABLE }

    data class UiState(
        val status: Status = Status.NO_KEY,
        val statusDetail: String? = null,
        val maskedKey: String? = null,
        val model: String = FurinaSettings.DEFAULT_MODEL,
        val modelError: Boolean = false,
        val schedule: ScheduleSpec? = null,
        val scheduleMessage: String? = null,
    )

    private val _state = MutableStateFlow(
        UiState(
            maskedKey = secrets.maskedApiKey(),
            model = settings.model,
            schedule = scheduler.current(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var checkJob: Job? = null

    init {
        recheck()
    }

    /** Validates the stored key (if any) and updates the status badge. */
    fun recheck() {
        val key = secrets.readApiKey()
        if (key == null) {
            _state.update { it.copy(status = Status.NO_KEY, statusDetail = null, maskedKey = null) }
            return
        }
        runCheck(key, persist = false)
    }

    /** Checks [input] first; a key that Gemini rejects is never stored. */
    fun saveKey(input: String) {
        val key = input.trim()
        if (key.isEmpty()) return
        runCheck(key, persist = true)
    }

    fun removeKey() {
        checkJob?.cancel()
        secrets.clearApiKey()
        _state.update { it.copy(status = Status.NO_KEY, statusDetail = null, maskedKey = null) }
    }

    fun saveModel(name: String) {
        val trimmed = name.trim()
        if (!GeminiClient.isValidModelName(trimmed)) {
            _state.update { it.copy(modelError = true) }
            return
        }
        settings.model = trimmed
        _state.update { it.copy(model = trimmed, modelError = false) }
    }

    /** Natural-language plan such as "her gece 03:00" or "her 6 saatte bir". */
    fun applySchedule(text: String) {
        val spec = IntentRouter.parseSchedule(text)
        if (spec == null) {
            _state.update { it.copy(scheduleMessage = persona.say(Event.SCHEDULE_INVALID)) }
            return
        }
        scheduler.schedule(spec)
        _state.update { it.copy(schedule = spec, scheduleMessage = persona.say(Event.SCHEDULE_SET, spec.describe())) }
    }

    fun cancelSchedule() {
        scheduler.cancel()
        _state.update { it.copy(schedule = null, scheduleMessage = persona.say(Event.SCHEDULE_CANCELLED)) }
    }

    private fun runCheck(key: String, persist: Boolean) {
        checkJob?.cancel()
        _state.update { it.copy(status = Status.CHECKING, statusDetail = null) }
        checkJob = viewModelScope.launch {
            when (val result = client.checkKey(key)) {
                KeyCheck.Valid -> {
                    if (persist) secrets.saveApiKey(key)
                    _state.update { it.copy(status = Status.ONLINE, statusDetail = null, maskedKey = secrets.maskedApiKey()) }
                }
                is KeyCheck.Invalid -> _state.update {
                    it.copy(status = Status.INVALID, statusDetail = result.reason, maskedKey = secrets.maskedApiKey())
                }
                is KeyCheck.Unavailable -> {
                    // Could not judge the key (offline / quota): keep what the user typed rather than losing it.
                    if (persist) secrets.saveApiKey(key)
                    _state.update {
                        it.copy(status = Status.UNREACHABLE, statusDetail = result.reason, maskedKey = secrets.maskedApiKey())
                    }
                }
            }
        }
    }
}
