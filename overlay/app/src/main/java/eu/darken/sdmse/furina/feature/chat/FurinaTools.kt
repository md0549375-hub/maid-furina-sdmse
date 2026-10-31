package eu.darken.sdmse.furina.feature.chat

import eu.darken.sdmse.furina.core.bridge.CleanTarget
import eu.darken.sdmse.furina.core.data.ScheduleSpec
import org.json.JSONArray
import org.json.JSONObject

/** Gemini function declarations + argument parsing for the four tools Furina may call. */
object FurinaTools {
    const val GET_STORAGE_STATUS = "get_storage_status"
    const val CLEAN_STORAGE = "clean_storage"
    const val SCHEDULE_CLEANING = "schedule_cleaning"
    const val CANCEL_SCHEDULED_CLEANING = "cancel_scheduled_cleaning"

    private const val ARG_APP_CACHE = "app_cache"
    private const val ARG_LEFTOVERS = "leftover_files"
    private const val ARG_SYSTEM_JUNK = "system_junk"

    fun declarations(): JSONArray {
        val functions = JSONArray()
            .put(
                function(
                    GET_STORAGE_STATUS,
                    "Cihazın dahili depolamasının toplam ve boş alanını döndürür. Kullanıcı depolama/hafıza durumunu sorduğunda çağır.",
                    null,
                )
            )
            .put(
                function(
                    CLEAN_STORAGE,
                    "SD Maid temizlik motorunu çalıştırmak için kullanıcıdan onay ister. Kullanıcı hafızayı/depolamayı temizlemeyi veya geçici dosyaları silmeyi açıkça istediğinde çağır.",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject().put(
                                "targets",
                                JSONObject()
                                    .put("type", "array")
                                    .put("description", "Temizlenecek alanlar. Belirtilmezse hepsi temizlenir.")
                                    .put(
                                        "items",
                                        JSONObject()
                                            .put("type", "string")
                                            .put("enum", JSONArray().put(ARG_APP_CACHE).put(ARG_LEFTOVERS).put(ARG_SYSTEM_JUNK))
                                    )
                            )
                        ),
                )
            )
            .put(
                function(
                    SCHEDULE_CLEANING,
                    "Tekrarlayan sessiz otomatik temizlik programı kurar. Örnek: 'her gece 03:00' -> mode=daily, hour=3, minute=0. 'her 6 saatte bir' -> mode=interval, interval_hours=6.",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("mode", JSONObject().put("type", "string").put("enum", JSONArray().put("daily").put("interval")))
                                .put("hour", JSONObject().put("type", "integer").put("description", "0-23, yalnızca daily için"))
                                .put("minute", JSONObject().put("type", "integer").put("description", "0-59, yalnızca daily için"))
                                .put("interval_hours", JSONObject().put("type", "integer").put("description", "1-168, yalnızca interval için"))
                        )
                        .put("required", JSONArray().put("mode")),
                )
            )
            .put(function(CANCEL_SCHEDULED_CLEANING, "Kurulu otomatik temizlik programını iptal eder.", null))

        return JSONArray().put(JSONObject().put("functionDeclarations", functions))
    }

    /** Unknown/invalid entries are ignored; an empty result means "everything". */
    fun parseTargets(args: JSONObject): Set<CleanTarget> {
        val array = args.optJSONArray("targets") ?: return CleanTarget.entries.toSet()
        val result = linkedSetOf<CleanTarget>()
        for (i in 0 until array.length()) {
            when (array.optString(i)) {
                ARG_APP_CACHE -> result += CleanTarget.APP_CACHE
                ARG_LEFTOVERS -> result += CleanTarget.CORPSES
                ARG_SYSTEM_JUNK -> result += CleanTarget.SYSTEM_JUNK
            }
        }
        return if (result.isEmpty()) CleanTarget.entries.toSet() else result
    }

    /** @return the plan, or a reason string when the model sent unusable arguments. */
    fun parseSchedule(args: JSONObject): Pair<ScheduleSpec?, String?> = when (args.optString("mode")) {
        "daily" -> {
            val hour = args.optInt("hour", -1)
            val minute = args.optInt("minute", 0)
            if (hour in 0..23 && minute in 0..59) ScheduleSpec.Daily(hour, minute) to null
            else null to "hour 0-23 ve minute 0-59 olmalı"
        }
        "interval" -> {
            val hours = args.optInt("interval_hours", -1)
            if (hours in 1..ScheduleSpec.MAX_INTERVAL_HOURS) ScheduleSpec.Interval(hours) to null
            else null to "interval_hours 1-${ScheduleSpec.MAX_INTERVAL_HOURS} olmalı"
        }
        else -> null to "mode daily veya interval olmalı"
    }

    fun toArg(target: CleanTarget): String = when (target) {
        CleanTarget.APP_CACHE -> ARG_APP_CACHE
        CleanTarget.CORPSES -> ARG_LEFTOVERS
        CleanTarget.SYSTEM_JUNK -> ARG_SYSTEM_JUNK
    }

    private fun function(name: String, description: String, parameters: JSONObject?): JSONObject =
        JSONObject().put("name", name).put("description", description).apply {
            if (parameters != null) put("parameters", parameters)
        }
}
