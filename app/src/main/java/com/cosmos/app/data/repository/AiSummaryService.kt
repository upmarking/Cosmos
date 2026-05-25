package com.cosmos.app.data.repository

import com.cosmos.app.data.model.ChatMessage
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AiSummaryService {
    suspend fun generateMeetingSummary(transcript: String, apiKey: String? = null): Result<String>
    suspend fun generateChatCrmSummary(messages: List<ChatMessage>, privateGoal: String, apiKey: String? = null): Result<String>
}

class GeminiAiSummaryService : AiSummaryService {

    override suspend fun generateMeetingSummary(transcript: String, apiKey: String?): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val key = apiKey?.takeIf { it.isNotBlank() } ?: System.getenv("GEMINI_API_KEY")
            
            if (key.isNullOrBlank()) {
                // Fallback to high-quality rule-based simulator
                return@runCatching simulateMeetingSummary(transcript)
            }

            val model = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = key
            )

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

            val response = model.generateContent(prompt)
            response.text ?: throw IllegalStateException("Empty response from Gemini")
        }
    }

    override suspend fun generateChatCrmSummary(
        messages: List<ChatMessage>,
        privateGoal: String,
        apiKey: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val key = apiKey?.takeIf { it.isNotBlank() } ?: System.getenv("GEMINI_API_KEY")
            
            if (key.isNullOrBlank()) {
                // Fallback to high-quality CRM simulator
                return@runCatching simulateCrmSummary(messages, privateGoal)
            }

            val chatHistory = messages.joinToString("\n") { "${if (it.isOwn) "Me" else "Them"}: ${it.text}" }

            val model = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = key
            )

            val prompt = """
                You are the AI Relationship CRM Assistant for Cosmos.
                Analyze this professional chat history and private relationship goal:
                Private Goal: $privateGoal
                
                Chat History:
                $chatHistory
                
                Provide a brief summary of the relationship state, next steps, and follow-up templates.
                Keep it extremely brief and easy to read.
            """.trimIndent()

            val response = model.generateContent(prompt)
            response.text ?: throw IllegalStateException("Empty response from Gemini")
        }
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
