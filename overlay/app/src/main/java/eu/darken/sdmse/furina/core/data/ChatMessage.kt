package eu.darken.sdmse.furina.core.data

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    /** Local-only lines (greeting) are shown but never sent to the model. */
    val sendToModel: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
) {
    enum class Role { USER, FURINA }
}

/** In-memory chat history shared by UI and agent. Deliberately never written to disk. */
@Singleton
class ChatHistoryStore @Inject constructor() {
    private val nextId = AtomicLong(1)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun isEmpty(): Boolean = _messages.value.isEmpty()

    fun add(role: ChatMessage.Role, text: String, sendToModel: Boolean = true) {
        val message = ChatMessage(nextId.getAndIncrement(), role, text, sendToModel)
        _messages.value = (_messages.value + message).takeLast(MAX_MESSAGES)
    }

    fun clear() {
        _messages.value = emptyList()
    }

    private companion object {
        const val MAX_MESSAGES = 80
    }
}
