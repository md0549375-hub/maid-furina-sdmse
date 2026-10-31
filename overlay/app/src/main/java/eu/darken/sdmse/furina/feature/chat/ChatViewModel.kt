package eu.darken.sdmse.furina.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.furina.core.bridge.CleanTarget
import eu.darken.sdmse.furina.core.bridge.CleanerGateway
import eu.darken.sdmse.furina.core.data.ChatHistoryStore
import eu.darken.sdmse.furina.core.data.ChatMessage
import eu.darken.sdmse.furina.core.persona.PersonaFormatter
import eu.darken.sdmse.furina.core.persona.PersonaFormatter.Event
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agent: FurinaAgent,
    private val gateway: CleanerGateway,
    private val persona: PersonaFormatter,
    private val store: ChatHistoryStore,
) : ViewModel() {

    data class UiState(
        val messages: List<ChatMessage> = emptyList(),
        val busy: Boolean = false,
        val pendingTargets: Set<CleanTarget>? = null,
        val pendingLabels: String = "",
    )

    private val busy = MutableStateFlow(false)
    private val pending = MutableStateFlow<Set<CleanTarget>?>(null)

    val state: StateFlow<UiState> = combine(store.messages, busy, pending) { messages, isBusy, targets ->
        UiState(
            messages = messages,
            busy = isBusy,
            pendingTargets = targets,
            pendingLabels = targets?.let { persona.targetLabels(it) }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        if (store.isEmpty()) store.add(ChatMessage.Role.FURINA, persona.say(Event.GREETING), sendToModel = false)
    }

    fun send(input: String) {
        val text = input.trim().take(MAX_INPUT_CHARS)
        if (text.isEmpty() || busy.value) return

        store.add(ChatMessage.Role.USER, text)
        pending.value = null
        busy.value = true
        viewModelScope.launch {
            try {
                when (val reply = agent.reply(store.messages.value)) {
                    is AgentReply.Text -> store.add(ChatMessage.Role.FURINA, reply.text)
                    is AgentReply.ConfirmClean -> {
                        store.add(ChatMessage.Role.FURINA, reply.text)
                        pending.value = reply.targets
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.add(ChatMessage.Role.FURINA, persona.say(Event.ERROR_GENERIC) + " (${e.message ?: e.javaClass.simpleName})")
            } finally {
                busy.value = false
            }
        }
    }

    fun confirmClean() {
        val targets = pending.value ?: return
        if (busy.value) return
        pending.value = null
        busy.value = true
        viewModelScope.launch {
            try {
                // Leaving the screen must not abort a half-finished deletion run.
                val report = withContext(NonCancellable) { gateway.clean(targets) }
                store.add(ChatMessage.Role.FURINA, persona.cleanReport(report))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.add(ChatMessage.Role.FURINA, persona.say(Event.CLEAN_FAILED) + " (${e.message ?: e.javaClass.simpleName})")
            } finally {
                busy.value = false
            }
        }
    }

    fun dismissClean() {
        if (pending.value == null) return
        pending.value = null
        store.add(ChatMessage.Role.FURINA, persona.say(Event.CLEAN_CANCELLED))
    }

    fun clearConversation() {
        if (busy.value) return
        pending.value = null
        store.clear()
        store.add(ChatMessage.Role.FURINA, persona.say(Event.GREETING), sendToModel = false)
    }

    private companion object {
        const val MAX_INPUT_CHARS = 1000
    }
}
