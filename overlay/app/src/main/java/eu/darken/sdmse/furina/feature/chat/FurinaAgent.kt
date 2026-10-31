package eu.darken.sdmse.furina.feature.chat

import eu.darken.sdmse.furina.core.bridge.CleanTarget
import eu.darken.sdmse.furina.core.bridge.StorageProbe
import eu.darken.sdmse.furina.core.data.ChatMessage
import eu.darken.sdmse.furina.core.data.SecretStore
import eu.darken.sdmse.furina.core.network.FunctionCall
import eu.darken.sdmse.furina.core.network.GeminiClient
import eu.darken.sdmse.furina.core.network.GeminiException
import eu.darken.sdmse.furina.core.persona.PersonaFormatter
import eu.darken.sdmse.furina.core.persona.PersonaFormatter.Event
import eu.darken.sdmse.furina.core.persona.PersonaPrompt
import eu.darken.sdmse.furina.feature.automation.FurinaScheduler
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

sealed interface AgentReply {
    data class Text(val text: String) : AgentReply

    /** Deleting files always needs an explicit tap, whether Gemini or the regex fallback asked for it. */
    data class ConfirmClean(val text: String, val targets: Set<CleanTarget>) : AgentReply
}

/**
 * With an API key, Gemini decides via function calling. Read-only tools run immediately; clean_storage never
 * runs inside the loop but becomes [AgentReply.ConfirmClean]. Without key/network, [IntentRouter] takes over.
 */
@Singleton
class FurinaAgent @Inject constructor(
    private val gemini: GeminiClient,
    private val secrets: SecretStore,
    private val scheduler: FurinaScheduler,
    private val persona: PersonaFormatter,
) {
    private class LoopState {
        var pendingClean: Set<CleanTarget>? = null
    }

    suspend fun reply(history: List<ChatMessage>): AgentReply {
        val lastUserText = history.lastOrNull { it.role == ChatMessage.Role.USER }?.text.orEmpty()
        if (secrets.readApiKey() == null) return localReply(lastUserText, persona.say(Event.NO_KEY))

        return try {
            askGemini(history)
        } catch (e: GeminiException) {
            when (e.kind) {
                GeminiException.Kind.INVALID_KEY -> localReply(lastUserText, persona.say(Event.KEY_INVALID))
                GeminiException.Kind.BLOCKED -> AgentReply.Text(persona.say(Event.NOT_UNDERSTOOD) + "\n(${e.message})")
                else -> localReply(lastUserText, persona.say(Event.OFFLINE_NOTE) + " (${e.message})")
            }
        }
    }

    private suspend fun askGemini(history: List<ChatMessage>): AgentReply {
        val contents = toContents(history)
        val tools = FurinaTools.declarations()
        val state = LoopState()

        repeat(MAX_STEPS) {
            val turn = gemini.generate(PersonaPrompt.SYSTEM_INSTRUCTION, contents, tools)
            contents.put(turn.rawContent)

            if (turn.calls.isEmpty()) {
                val pending = state.pendingClean
                return if (pending != null) {
                    AgentReply.ConfirmClean(
                        turn.text.ifBlank { persona.say(Event.CONFIRM_CLEAN, persona.targetLabels(pending)) },
                        pending,
                    )
                } else {
                    AgentReply.Text(turn.text.ifBlank { persona.say(Event.NOT_UNDERSTOOD) })
                }
            }

            val responses = JSONArray()
            for (call in turn.calls) {
                responses.put(functionResponsePart(call, execute(call, state)))
            }
            contents.put(JSONObject().put("role", "user").put("parts", responses))
        }

        val pending = state.pendingClean
        return if (pending != null) {
            AgentReply.ConfirmClean(persona.say(Event.CONFIRM_CLEAN, persona.targetLabels(pending)), pending)
        } else {
            AgentReply.Text(persona.say(Event.NOT_UNDERSTOOD))
        }
    }

    private fun execute(call: FunctionCall, state: LoopState): JSONObject = when (call.name) {
        FurinaTools.GET_STORAGE_STATUS -> {
            val s = StorageProbe.snapshot()
            JSONObject()
                .put("total_bytes", s.totalBytes)
                .put("free_bytes", s.freeBytes)
                .put("used_percent", s.usedPercent)
                .put("total_readable", persona.bytes(s.totalBytes))
                .put("free_readable", persona.bytes(s.freeBytes))
        }

        FurinaTools.CLEAN_STORAGE -> {
            val targets = FurinaTools.parseTargets(call.args)
            state.pendingClean = targets
            JSONObject()
                .put("status", "awaiting_user_confirmation")
                .put("targets", JSONArray(targets.map { FurinaTools.toArg(it) }))
        }

        FurinaTools.SCHEDULE_CLEANING -> {
            val (spec, problem) = FurinaTools.parseSchedule(call.args)
            if (spec == null) {
                JSONObject().put("status", "error").put("reason", problem)
            } else {
                scheduler.schedule(spec)
                JSONObject().put("status", "scheduled").put("plan", spec.describe())
            }
        }

        FurinaTools.CANCEL_SCHEDULED_CLEANING -> {
            scheduler.cancel()
            JSONObject().put("status", "cancelled")
        }

        else -> JSONObject().put("status", "error").put("reason", "unknown function")
    }

    private fun functionResponsePart(call: FunctionCall, result: JSONObject): JSONObject {
        val response = JSONObject().put("name", call.name).put("response", result)
        call.id?.let { response.put("id", it) }
        return JSONObject().put("functionResponse", response)
    }

    /** Gemini wants strictly alternating roles that start with the user. */
    private fun toContents(history: List<ChatMessage>): JSONArray {
        val turns = ArrayList<Pair<String, String>>()
        for (message in history.filter { it.sendToModel }.takeLast(MAX_HISTORY)) {
            val role = if (message.role == ChatMessage.Role.USER) "user" else "model"
            val last = turns.lastOrNull()
            if (last != null && last.first == role) {
                turns[turns.lastIndex] = role to (last.second + "\n" + message.text)
            } else {
                turns += role to message.text
            }
        }
        while (turns.isNotEmpty() && turns.first().first != "user") turns.removeAt(0)

        val array = JSONArray()
        for ((role, text) in turns) {
            array.put(
                JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        return array
    }

    private fun localReply(userText: String, note: String): AgentReply {
        val body: AgentReply = when (val intent = IntentRouter.route(userText)) {
            IntentRouter.Intent.StorageStatus -> AgentReply.Text(persona.storageStatus(StorageProbe.snapshot()))
            is IntentRouter.Intent.Clean -> AgentReply.ConfirmClean(
                persona.say(Event.CONFIRM_CLEAN, persona.targetLabels(intent.targets)),
                intent.targets,
            )
            is IntentRouter.Intent.Schedule -> {
                scheduler.schedule(intent.spec)
                AgentReply.Text(persona.say(Event.SCHEDULE_SET, intent.spec.describe()))
            }
            IntentRouter.Intent.CancelSchedule -> {
                scheduler.cancel()
                AgentReply.Text(persona.say(Event.SCHEDULE_CANCELLED))
            }
            null -> AgentReply.Text(persona.say(Event.NOT_UNDERSTOOD))
        }
        return when (body) {
            is AgentReply.Text -> body.copy(text = note + "\n\n" + body.text)
            is AgentReply.ConfirmClean -> body.copy(text = note + "\n\n" + body.text)
        }
    }

    private companion object {
        const val MAX_STEPS = 4
        const val MAX_HISTORY = 24
    }
}
