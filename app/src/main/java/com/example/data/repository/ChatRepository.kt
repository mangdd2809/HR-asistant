package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.AiConfigEntity
import com.example.data.local.ChatDao
import com.example.data.local.ChatMessage
import com.example.data.local.ChatSession
import com.example.data.local.KnowledgeItem
import com.example.data.remote.GeminiApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(
    private val chatDao: ChatDao,
    private val knowledgeRepository: KnowledgeRepository
) {

    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessionsFlow()
    val aiConfigFlow: Flow<AiConfigEntity?> = chatDao.getAiConfigFlow()

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSessionFlow(sessionId)
    }

    suspend fun getOrCreateLatestSession(): ChatSession = withContext(Dispatchers.IO) {
        val existing = chatDao.getAllSessionsFlow()
        val defaultTitle = "Konsultasi Kasus Kepegawaian #1"
        val session = ChatSession(
            title = defaultTitle,
            customerName = "Petugas HR",
            lastMessage = "Selamat datang di HR Case Advisor AI"
        )
        val id = chatDao.insertSession(session)
        val created = chatDao.getSessionById(id) ?: session.copy(id = id)

        // Add welcome message
        val welcomeMsg = ChatMessage(
            sessionId = created.id,
            sender = "BOT",
            message = """
                Halo Rekan HR! Saya adalah **HR Case Legal Advisor AI**.
                
                Saya siap membantu menganalisis kronologi dan kasus ketenagakerjaan di perusahaan Anda berdasarkan file-file ketentuan, SOP perusahaan, dan regulasi PP No. 35/2021 yang tersimpan dalam basis data.
                
                Silakan ceritakan kronologi kasus yang sedang Anda tangani (misalnya: *karyawan mangkir berturut-turut, pelanggaran disiplin kerja, prosedur pemberian SP, sengketa lembur, atau tata cara PHK & pesangon*).
            """.trimIndent(),
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(welcomeMsg)

        created
    }

    suspend fun createNewSession(customerName: String = "Petugas HR", title: String? = null): Long =
        withContext(Dispatchers.IO) {
            val sessionTitle = title ?: "Kasus Kepegawaian #${System.currentTimeMillis() % 10000}"
            val session = ChatSession(
                title = sessionTitle,
                customerName = customerName,
                lastMessage = "Sesi analisis baru dimulai"
            )
            val newId = chatDao.insertSession(session)

            val welcomeMsg = ChatMessage(
                sessionId = newId,
                sender = "BOT",
                message = "Sesi analisis kasus kepegawaian baru dibuka. Silakan masukkan kronologi kejadian atau pertanyaan regulasi HR yang ingin dianalisis.",
                timestamp = System.currentTimeMillis()
            )
            chatDao.insertMessage(welcomeMsg)

            newId
        }

    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        chatDao.clearMessagesForSession(sessionId)
        chatDao.deleteSession(sessionId)
    }

    suspend fun updateFeedback(messageId: Long, isHelpful: Boolean) = withContext(Dispatchers.IO) {
        chatDao.updateMessageFeedback(messageId, isHelpful)
    }

    suspend fun getAiConfig(): AiConfigEntity = withContext(Dispatchers.IO) {
        val config = chatDao.getAiConfig()
        if (config == null) {
            val defaultConfig = AiConfigEntity(
                id = 1,
                apiKey = "",
                modelName = "gemini-3.5-flash",
                businessName = "Divisi Human Resources & Hubungan Industrial",
                serviceTone = "Profesional, Legal & Solutif",
                customSystemPrompt = "Anda adalah Konsultan Hukum Kepegawaian & HR Specialist senior. Berikan pemahaman yang cermat, objektif, dan sesuai hukum.",
                useOfflineFallback = true
            )
            chatDao.saveAiConfig(defaultConfig)
            defaultConfig
        } else {
            config
        }
    }

    suspend fun saveAiConfig(config: AiConfigEntity) = withContext(Dispatchers.IO) {
        chatDao.saveAiConfig(config)
    }

    /**
     * Process HR Case inquiry:
     * 1. Save HR officer's case inquiry.
     * 2. RAG retrieve matched company regulations, PKB, and labor laws from Room.
     * 3. Construct Legal HR Advisor system prompt with Grounded Knowledge.
     * 4. Call Gemini AI (or structured local KB legal advice).
     * 5. Save bot advice into Room DB.
     */
    suspend fun processCustomerMessage(
        sessionId: Long,
        userQuery: String,
        recentMessages: List<ChatMessage>
    ): ChatMessage = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 1. Save HR user inquiry
        val userMsg = ChatMessage(
            sessionId = sessionId,
            sender = "USER",
            message = userQuery,
            timestamp = startTime
        )
        chatDao.insertMessage(userMsg)

        // 2. Retrieve grounded knowledge from Room database
        val relevantItems = knowledgeRepository.findRelevantKnowledge(userQuery, limit = 3)
        val primarySource = relevantItems.firstOrNull()

        // 3. Load AI Config
        val aiConfig = getAiConfig()
        val apiKey = when {
            aiConfig.apiKey.isNotBlank() -> aiConfig.apiKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY.trim()
            else -> ""
        }

        // Build conversation history
        val historyTurns = recentMessages.takeLast(6).map {
            Pair(it.sender, it.message)
        }

        // Build grounded system prompt
        val kbContextBuilder = StringBuilder()
        if (relevantItems.isNotEmpty()) {
            kbContextBuilder.append("\n\n=== BERIKUT FILE KETENTUAN, SOP, DAN REGULASI DARI BASIS DATA HR PERUSAHAAN (DASAR HUKUM RESMI) ===\n")
            relevantItems.forEachIndexed { index, item ->
                kbContextBuilder.append("[Ketentuan ${index + 1}: ${item.title} (Kategori: ${item.category})]\n")
                kbContextBuilder.append("${item.content}\n\n")
            }
            kbContextBuilder.append("=== AKHIR BASIS DATA KETENTUAN ===\n")
            kbContextBuilder.append("""
                ATURAN ANALISIS WAJIB:
                Gunakan data ketentuan resmi di atas sebagai dasar analisis utama Anda. Susun jawaban Anda secara profesional dengan struktur:
                1. 📋 Identifikasi Masalah & Klasifikasi Pelanggaran
                2. ⚖️ Dasar Ketentuan / Regulasi Terkait (Kutip rujukan dari basis data di atas)
                3. 🛠️ Rekomendasi Tindakan HR Prosedural (Langkah konkret bertahap yang harus dilakukan petugas HR)
                4. 🛡️ Mitigasi Risiko Hukum & Hubungan Industrial (Cara mencegah gugatan ke Disnaker / PHI)
            """.trimIndent())
        } else {
            kbContextBuilder.append("\n(Catatan: Tidak ditemukan klausul khusus dalam basis data yang cocok persis dengan kata kunci kasus ini. Berikan panduan berdasarkan prinsip umum hukum ketenagakerjaan Indonesia dan sarankan HR mengecek klausul Perjanjian Kerja / PP perusahaan).")
        }

        val fullSystemPrompt = """
            Anda adalah HR Legal & Industrial Relations Advisor AI untuk "${aiConfig.businessName}".
            Audience Anda: Petugas Human Resources (HR Officer / HR Manager / HR Legal).
            Gaya Bahasa: ${aiConfig.serviceTone}.
            Instruksi Khusus: ${aiConfig.customSystemPrompt}
            $kbContextBuilder
            Gunakan bahasa Indonesia yang profesional, tegas, jelas, dan berorientasi pada kepatuhan hukum ketenagakerjaan (compliance).
        """.trimIndent()

        var botReplyText = ""
        var usedKnowledgeItem: KnowledgeItem? = primarySource

        if (apiKey.isNotBlank()) {
            val apiResult = GeminiApiClient.generateAnswer(
                apiKey = apiKey,
                modelName = aiConfig.modelName,
                systemPrompt = fullSystemPrompt,
                conversationHistory = historyTurns,
                userQuestion = userQuery
            )

            if (apiResult.isSuccess) {
                botReplyText = apiResult.getOrNull() ?: ""
            } else {
                val errorMsg = apiResult.exceptionOrNull()?.message ?: "Gagal terhubung ke API AI."
                if (aiConfig.useOfflineFallback && primarySource != null) {
                    botReplyText = buildLocalHrAdvice(aiConfig.businessName, primarySource, userQuery)
                } else if (aiConfig.useOfflineFallback) {
                    botReplyText = "Rekan HR, terjadi kendala koneksi ke server AI ($errorMsg). Anda dapat meninjau dokumen ketentuan di menu 'Basis Data' atau mengonfigurasi ulang API Key."
                    usedKnowledgeItem = null
                } else {
                    botReplyText = "Kendala koneksi ke server AI: $errorMsg"
                    usedKnowledgeItem = null
                }
            }
        } else {
            // Local fallback RAG directly from Room Database
            if (primarySource != null) {
                botReplyText = buildLocalHrAdvice(aiConfig.businessName, primarySource, userQuery)
            } else {
                botReplyText = """
                    Rekan HR, pertanyaan kasus Anda belum memiliki rujukan yang cocok di basis data regulasi saat ini.
                    
                    Rekomendasi Tindakan:
                    1. Buka menu **Basis Data** untuk menambahkan ketentuan SOP, Peraturan Perusahaan (PP), atau PKB terkait.
                    2. Atau gunakan menu **Impor File** untuk mengunggah dokumen peraturan kepegawaian perusahaan Anda.
                    3. Masukkan API Key di tab **Konfigurasi AI** untuk analisis berbasis penalaran mendalam.
                """.trimIndent()
                usedKnowledgeItem = null
            }
        }

        val responseTime = System.currentTimeMillis() - startTime

        // 4. Save Bot Message
        val botMessage = ChatMessage(
            sessionId = sessionId,
            sender = "BOT",
            message = botReplyText,
            timestamp = System.currentTimeMillis(),
            referencedKnowledgeId = usedKnowledgeItem?.id,
            referencedKnowledgeTitle = usedKnowledgeItem?.title,
            responseTimeMs = responseTime
        )
        val msgId = chatDao.insertMessage(botMessage)

        // 5. Update session
        val currentSession = chatDao.getSessionById(sessionId)
        if (currentSession != null) {
            val truncatedLastMsg = if (userQuery.length > 50) userQuery.take(50) + "..." else userQuery
            chatDao.updateSession(currentSession.copy(lastMessage = truncatedLastMsg))
        }

        botMessage.copy(id = msgId)
    }

    private fun buildLocalHrAdvice(businessName: String, item: KnowledgeItem, query: String): String {
        return """
            ### ⚖️ Analisis Kasus Kepegawaian (Berdasarkan Basis Data Ketentuan)
            
            **Rujukan Ketentuan:** ${item.title} (${item.category})
            
            **Isi Ketentuan Resmi:**
            ${item.content}
            
            ---
            **Rekomendasi Tindakan untuk Petugas HR:**
            1. **Verifikasi Bukti:** Pastikan kelengkapan dokumen pendukung (absensi, bukti tertulis, BAP klarifikasi).
            2. **Kepatuhan Prosedur:** Terapkan langkah administratif sesuai urutan yang diatur dalam ketentuan di atas.
            3. **Dokumentasi Tertulis:** Berikan surat resmi bertanda tangan dan simpan bukti tanda terima karyawan untuk mitigasi risiko perselisihan hubungan industrial.
        """.trimIndent()
    }
}
