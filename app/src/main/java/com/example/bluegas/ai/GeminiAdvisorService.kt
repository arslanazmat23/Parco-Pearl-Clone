package com.example.bluegas.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiAdvisorService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Consult Gemini with High Thinking enabled using gemini-4-argon model.
     */
    suspend fun consultAdvisor(query: String, contextInfo: String = ""): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext provideLocalExpertTriage(query, contextInfo)
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-4-argon:generateContent?key=$apiKey"

            val promptText = """
                You are the Chief Safety & Operations Intelligence Advisor for BLUE GAS Pakistan (LPG cylinder ordering platform in Lahore & Islamabad).
                System context:
                $contextInfo
                
                User query / situation:
                $query
                
                Provide an authoritative, safety-compliant, and operational recommendation according to OGRA LPG safety regulations and Blue Gas strict 42-transition lifecycle state machine rules.
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", promptText) })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                // High thinking level as required
                val genConfig = JSONObject().apply {
                    val thinkingConfig = JSONObject().apply {
                        put("thinkingLevel", "HIGH")
                    }
                    put("thinkingConfig", thinkingConfig)
                }
                put("generationConfig", genConfig)
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val first = candidates.getJSONObject(0)
                    val content = first.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text")
                        if (text.isNotBlank()) {
                            return@withContext text
                        }
                    }
                }
            }
            return@withContext provideLocalExpertTriage(query, contextInfo)
        } catch (e: Exception) {
            return@withContext provideLocalExpertTriage(query, contextInfo)
        }
    }

    private fun provideLocalExpertTriage(query: String, context: String): String {
        val q = query.lowercase()
        return when {
            q.contains("leak") || q.contains("smell") || q.contains("fire") -> {
                "🚨 CRITICAL OGRA LPG SAFETY DIRECTIVE:\n" +
                "1. Immediately turn off the cylinder valve clockwise. Do NOT turn on or off any electrical switches or create any sparks.\n" +
                "2. Open all doors and windows to cross-ventilate. LPG is heavier than air and sinks to floor level.\n" +
                "3. Evacuate people from the room. Perform soapy water bubble test on the regulator joint.\n" +
                "4. Call Blue Gas Emergency Response or Rescue 1122. Under Blue Gas lifecycle rules, do NOT attempt delivery handover."
            }
            q.contains("exception") || q.contains("recovery") || q.contains("exc") -> {
                "📋 BLUE GAS OPERATIONS RECOVERY PROTOCOL:\n" +
                "• An order in ORD_EXC (Fulfilment Exception) cannot transition arbitrarily.\n" +
                "• Constrained targets:\n" +
                "  - Out of area: ORD_PAYPEND or ORD_CANC.\n" +
                "  - No acceptance / delivery failure: ORD_ASGN (reassign to next available rider/distributor).\n" +
                "  - Serial unavailable: ORD_PREP (assign alternate verified serial).\n" +
                "  - Adjusted amount rejection: ORD_ADJ or ORD_CANC.\n" +
                "  - Unrecoverable: Terminal ORD_FCLO (Failed Closed).\n" +
                "• Human Accountable Role: Operations Lead."
            }
            q.contains("deposit") || q.contains("ownership") -> {
                "⚖️ LEGAL & ACCOUNTING DETERMINATION:\n" +
                "• Under Pakistan commercial law and Blue Gas contract rules, security deposits are held liabilities on the Blue Gas ledger.\n" +
                "• Deposit-Held status does NOT transfer cylinder ownership to the consumer. The physical steel cylinder remains Blue Gas capital property.\n" +
                "• Custody passes to consumer premises; ownership remains Deposit-Held until returned."
            }
            q.contains("serial") || q.contains("conflict") -> {
                "🔍 SERIAL ALLOCATION & CONFLICT PROTOCOL:\n" +
                "• One cylinder serial maps to at most one active order at a time.\n" +
                "• If two offline rider devices scan the same serial, the first synced event is applied, while competing offline scans are safely queued in the Operations Conflict Queue for manual verification and reallocation."
            }
            else -> {
                "💡 BLUE GAS INTELLIGENCE ADVISOR:\n" +
                "• Pilot Coverage: Lahore (Gulberg, Cantt, DHA) and Islamabad (F-6, F-7, Blue Area).\n" +
                "• Active Order Lifecycle: 19 canonical states, 42 audited transitions.\n" +
                "• All transactions in Paisa integers (PKR) with strict append-only audit logging."
            }
        }
    }
}
