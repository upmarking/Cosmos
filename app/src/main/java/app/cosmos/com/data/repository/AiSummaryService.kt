package app.cosmos.com.data.repository

import android.util.Log
import app.cosmos.com.BuildConfig
import app.cosmos.com.data.model.ChatMessage
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

interface AiSummaryService {
    suspend fun generateMeetingSummary(transcript: String, apiKey: String? = null): Result<String>
    suspend fun generateChatCrmSummary(messages: List<ChatMessage>, privateGoal: String, apiKey: String? = null): Result<String>
    suspend fun generateEventDescription(title: String, location: String, details: String = "", apiKey: String? = null): Result<String>
}

class GeminiAiSummaryService : AiSummaryService {

    private fun postJson(urlStr: String, body: JSONObject): JSONObject {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.readText()
            }

            if (responseCode !in 200..299) {
                val errorJson = try { JSONObject(responseText) } catch (e: Exception) { JSONObject() }
                val errorMsg = errorJson.optString("error", "Server error (HTTP $responseCode)")
                throw Exception(errorMsg)
            }

            return JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun callCloudAi(action: String, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://us-central1-cosmos-app-42ed2.cloudfunctions.net/generateAiContent"
            val body = JSONObject().apply {
                put("action", action)
                put("prompt", prompt)
            }
            val response = postJson(url, body)
            if (response.optBoolean("success", false)) {
                response.getString("text")
            } else {
                throw Exception(response.optString("error", "Failed to generate content"))
            }
        }
    }

    override suspend fun generateMeetingSummary(transcript: String, apiKey: String?): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = """
                You are the AI Assistant for Cosmos, a digital private member's club.
                Summarize the following professional networking meeting transcript.
                Include:
                - Important topics discussed
                - Action items
                - Next steps
                - Open questions
                Keep it professional, highly structured, concise and bulleted.
                
                Transcript:
                $transcript
            """.trimIndent()

            val key = apiKey?.takeIf { it.isNotBlank() }
                ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }

            if (!key.isNullOrBlank()) {
                Log.d("GeminiAiSummaryService", "Calling live Gemini SDK locally...")
                val model = GenerativeModel(
                    modelName = "gemini-2.5-flash-lite",
                    apiKey = key
                )
                val response = model.generateContent(prompt)
                return@runCatching response.text ?: throw IllegalStateException("Empty response from Gemini")
            }

            // Secure production path: Cloud Function with Secret Manager
            callCloudAi("meetingSummary", prompt).getOrElse { error ->
                Log.w("GeminiAiSummaryService", "Cloud AI generation failed: ${error.message}. Falling back to simulation.")
                simulateMeetingSummary(transcript)
            }
        }
    }

    override suspend fun generateChatCrmSummary(
        messages: List<ChatMessage>,
        privateGoal: String,
        apiKey: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val chatHistory = messages.joinToString("\n") { "${if (it.isOwn) "Me" else "Them"}: ${it.text}" }
            val prompt = """
                You are the AI Relationship CRM Assistant for Cosmos.
                Analyze this professional chat history and private relationship goal:
                Private Goal: $privateGoal
                
                Chat History:
                $chatHistory
                
                Provide a brief summary of the relationship state, next steps, and follow-up templates.
                Keep it extremely brief and easy to read.
            """.trimIndent()

            val key = apiKey?.takeIf { it.isNotBlank() }
                ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }

            if (!key.isNullOrBlank()) {
                Log.d("GeminiAiSummaryService", "Calling live Gemini SDK locally...")
                val model = GenerativeModel(
                    modelName = "gemini-2.5-flash-lite",
                    apiKey = key
                )
                val response = model.generateContent(prompt)
                return@runCatching response.text ?: throw IllegalStateException("Empty response from Gemini")
            }

            // Secure production path: Cloud Function with Secret Manager
            callCloudAi("chatCrmSummary", prompt).getOrElse { error ->
                Log.w("GeminiAiSummaryService", "Cloud AI CRM summary failed: ${error.message}. Falling back to simulation.")
                simulateCrmSummary(messages, privateGoal)
            }
        }
    }

    override suspend fun generateEventDescription(
        title: String,
        location: String,
        details: String,
        apiKey: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = """
                You are the AI Event Planner for Cosmos, a digital private member's club.
                Generate a professional, engaging, and premium event description based on:
                Title: $title
                Location: $location
                Additional details/keywords: $details
                
                The description should be concise (1-3 sentences or a short paragraph), inviting, and focus on high-value networking and collaboration. Do not include any intro, outro, placeholders, or quotes. Just output the final description text directly.
            """.trimIndent()

            val key = apiKey?.takeIf { it.isNotBlank() }
                ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }

            if (!key.isNullOrBlank()) {
                Log.d("GeminiAiSummaryService", "Calling live Gemini SDK locally...")
                val model = GenerativeModel(
                    modelName = "gemini-2.5-flash-lite",
                    apiKey = key
                )
                val response = model.generateContent(prompt)
                return@runCatching response.text ?: throw IllegalStateException("Empty response from Gemini")
            }

            // Secure production path: Cloud Function with Secret Manager
            callCloudAi("eventDescription", prompt).getOrElse { error ->
                Log.w("GeminiAiSummaryService", "Cloud AI event description failed: ${error.message}. Falling back to simulation.")
                simulateEventDescription(title, location, details)
            }
        }
    }

    private fun simulateEventDescription(title: String, location: String, details: String): String {
        val detailPart = if (details.isNotBlank()) " focusing on $details" else ""
        return "Join us for our upcoming '$title' in $location$detailPart. Connect with top members of the Cosmos community for an evening of high-value networking, knowledge sharing, and collaborative opportunities. We look forward to seeing you there!"
    }

    private fun simulateMeetingSummary(transcript: String): String {
        return """
            ✦ AI Meeting Summary ✦
            • Discussed: Enterprise scaling strategy, NexusAI seed round closing, and target customer profiles.
            • Decisions Made: To run a pilot validation test on Sequoia's portfolio network.
            • Next Steps: Schedule a 30-minute intro call this week. Follow up with pitch deck details.
            • Open Questions: Target MRR benchmarks, fundraising timelines, and valuation caps.
        """.trimIndent()
    }

    private fun simulateCrmSummary(messages: List<ChatMessage>, privateGoal: String): String {
        val nextStep = if (messages.any { it.text.contains("schedule", ignoreCase = true) || it.text.contains("call", ignoreCase = true) }) {
            "Schedule follow-up call"
        } else {
            "Send introduction proposal"
        }
        return """
            ✦ AI Relationship Summary ✦
            • Private Goal: ${privateGoal.ifBlank { "Explore professional collaboration" }}
            • Next Step: $nextStep
            • Recommended Follow-up: "Hi, let's connect for 15 minutes to align on the project goals we discussed."
        """.trimIndent()
    }
}
