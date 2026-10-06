package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AiConfigEntity
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessage
import com.example.data.local.ChatSession
import com.example.data.local.KnowledgeItem
import com.example.data.remote.GeminiApiClient
import com.example.data.repository.ChatRepository
import com.example.data.repository.KnowledgeRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

enum class AppTab {
    CHAT,
    KNOWLEDGE,
    SESSIONS,
    AI_CONFIG
}

sealed class ApiTestState {
    object Idle : ApiTestState()
    object Testing : ApiTestState()
    data class Success(val message: String) : ApiTestState()
    data class Error(val error: String) : ApiTestState()
}

class CustomerServiceViewModel(
    application: Application,
    private val knowledgeRepository: KnowledgeRepository,
    private val chatRepository: ChatRepository
) : AndroidViewModel(application) {

    private val _currentTab = MutableStateFlow(AppTab.CHAT)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _activeSession = MutableStateFlow<ChatSession?>(null)
    val activeSession: StateFlow<ChatSession?> = _activeSession.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    val allSessions: StateFlow<List<ChatSession>> = chatRepository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _knowledgeSearchQuery = MutableStateFlow("")
    val knowledgeSearchQuery: StateFlow<String> = _knowledgeSearchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    // Filtered Knowledge Base
    val knowledgeList: StateFlow<List<KnowledgeItem>> = combine(
        knowledgeRepository.allKnowledge,
        _knowledgeSearchQuery,
        _selectedCategoryFilter
    ) { allItems, query, categoryFilter ->
        allItems.filter { item ->
            val matchesQuery = if (query.isBlank()) true else {
                item.title.contains(query, ignoreCase = true) ||
                    item.content.contains(query, ignoreCase = true) ||
                    item.keywords.contains(query, ignoreCase = true)
            }
            val matchesCategory = if (categoryFilter == null || categoryFilter == "Semua") true else {
                item.category.equals(categoryFilter, ignoreCase = true)
            }
            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _aiConfig = MutableStateFlow(
        AiConfigEntity(
            id = 1,
            apiKey = "",
            modelName = "gemini-3.5-flash",
            businessName = "Mitra Layanan Pelanggan",
            serviceTone = "Ramah & Profesional",
            customSystemPrompt = "Anda adalah Customer Service AI yang sopan dan ramah.",
            useOfflineFallback = true
        )
    )
    val aiConfig: StateFlow<AiConfigEntity> = _aiConfig.asStateFlow()

    private val _apiTestState = MutableStateFlow<ApiTestState>(ApiTestState.Idle)
    val apiTestState: StateFlow<ApiTestState> = _apiTestState.asStateFlow()

    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    // UI Dialog State
    val isAddKnowledgeDialogOpen = MutableStateFlow(false)
    val isImportFileDialogOpen = MutableStateFlow(false)
    val selectedKnowledgeDetail = MutableStateFlow<KnowledgeItem?>(null)

    init {
        viewModelScope.launch {
            // Seed DB if empty
            knowledgeRepository.seedInitialDataIfEmpty()

            // Observe AI Config
            chatRepository.aiConfigFlow.collect { config ->
                if (config != null) {
                    _aiConfig.value = config
                }
            }
        }

        // Initialize default or first session
        viewModelScope.launch {
            val session = chatRepository.getOrCreateLatestSession()
            _activeSession.value = session
            listenMessagesForSession(session.id)
        }
    }

    private fun listenMessagesForSession(sessionId: Long) {
        viewModelScope.launch {
            chatRepository.getMessagesForSession(sessionId).collect { messages ->
                _chatMessages.value = messages
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun selectSession(session: ChatSession) {
        _activeSession.value = session
        listenMessagesForSession(session.id)
        _currentTab.value = AppTab.CHAT
    }

    fun startNewSession(customerName: String = "Pelanggan", title: String? = null) {
        viewModelScope.launch {
            val newId = chatRepository.createNewSession(customerName, title)
            val session = ChatSession(
                id = newId,
                title = title ?: "Percakapan #${newId}",
                customerName = customerName,
                lastMessage = "Sesi baru dimulai"
            )
            _activeSession.value = session
            listenMessagesForSession(newId)
            _snackbarEvent.emit("Sesi baru dimulai")
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (_activeSession.value?.id == sessionId) {
                val nextSession = allSessions.value.firstOrNull { it.id != sessionId }
                if (nextSession != null) {
                    selectSession(nextSession)
                } else {
                    startNewSession()
                }
            }
            _snackbarEvent.emit("Sesi dihapus")
        }
    }

    fun sendCustomerMessage(messageText: String) {
        val text = messageText.trim()
        if (text.isBlank() || _isGenerating.value) return

        val currentSess = _activeSession.value ?: return

        viewModelScope.launch {
            _isGenerating.value = true
            try {
                chatRepository.processCustomerMessage(
                    sessionId = currentSess.id,
                    userQuery = text,
                    recentMessages = _chatMessages.value
                )
            } catch (e: Exception) {
                _snackbarEvent.emit("Kendala saat memproses: ${e.message}")
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun rateMessage(messageId: Long, isHelpful: Boolean) {
        viewModelScope.launch {
            chatRepository.updateFeedback(messageId, isHelpful)
            _snackbarEvent.emit(if (isHelpful) "Terima kasih atas tanggapan positif!" else "Terima kasih atas masukannya, sistem akan berbenah.")
        }
    }

    // --- Knowledge Base Actions ---
    fun setKnowledgeSearchQuery(query: String) {
        _knowledgeSearchQuery.value = query
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun addKnowledgeItem(
        title: String,
        category: String,
        content: String,
        keywords: String,
        sourceType: String = "MANUAL",
        fileName: String? = null
    ) {
        if (title.isBlank() || content.isBlank()) {
            viewModelScope.launch { _snackbarEvent.emit("Judul dan isi konten tidak boleh kosong") }
            return
        }

        viewModelScope.launch {
            val newItem = KnowledgeItem(
                title = title.trim(),
                category = category.ifBlank { "Umum" },
                content = content.trim(),
                keywords = keywords.trim(),
                sourceType = sourceType,
                fileName = fileName,
                updatedAt = System.currentTimeMillis(),
                isActive = true
            )
            knowledgeRepository.insert(newItem)
            isAddKnowledgeDialogOpen.value = false
            _snackbarEvent.emit("Data basis pengetahuan berhasil ditambahkan")
        }
    }

    fun updateKnowledgeItem(item: KnowledgeItem) {
        viewModelScope.launch {
            knowledgeRepository.update(item.copy(updatedAt = System.currentTimeMillis()))
            selectedKnowledgeDetail.value = null
            _snackbarEvent.emit("Data berhasil diperbarui")
        }
    }

    fun deleteKnowledgeItem(item: KnowledgeItem) {
        viewModelScope.launch {
            knowledgeRepository.delete(item)
            selectedKnowledgeDetail.value = null
            _snackbarEvent.emit("Data basis pengetahuan dihapus")
        }
    }

    fun toggleKnowledgeActive(item: KnowledgeItem) {
        viewModelScope.launch {
            knowledgeRepository.toggleActive(item)
            _snackbarEvent.emit(if (item.isActive) "Data dinonaktifkan dari RAG" else "Data diaktifkan kembali")
        }
    }

    /**
     * Read content from imported text Uri
     */
    fun importFileContent(uri: Uri, fileName: String, category: String) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _snackbarEvent.emit("Gagal membuka file")
                    return@launch
                }
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.use { it.readText() }

                if (content.isBlank()) {
                    _snackbarEvent.emit("File kosong")
                    return@launch
                }

                // Clean title from fileName
                val title = fileName.substringBeforeLast(".").replace("_", " ").replace("-", " ")
                    .replaceFirstChar { it.uppercase() }

                addKnowledgeItem(
                    title = title,
                    category = category.ifBlank { "Dokumen Impor" },
                    content = content,
                    keywords = "$title, $category, dokumen impor",
                    sourceType = "FILE_IMPORT",
                    fileName = fileName
                )
                isImportFileDialogOpen.value = false
            } catch (e: Exception) {
                _snackbarEvent.emit("Gagal membaca file: ${e.message}")
            }
        }
    }

    // --- AI Config & Test ---
    fun updateAiConfig(
        apiKey: String,
        modelName: String,
        businessName: String,
        serviceTone: String,
        customPrompt: String,
        useOfflineFallback: Boolean
    ) {
        viewModelScope.launch {
            val updated = _aiConfig.value.copy(
                apiKey = apiKey.trim(),
                modelName = modelName,
                businessName = businessName.trim(),
                serviceTone = serviceTone,
                customSystemPrompt = customPrompt.trim(),
                useOfflineFallback = useOfflineFallback
            )
            chatRepository.saveAiConfig(updated)
            _aiConfig.value = updated
            _snackbarEvent.emit("Pengaturan API AI berhasil disimpan")
        }
    }

    fun testAiConnection(testApiKey: String, modelName: String) {
        if (testApiKey.isBlank()) {
            _apiTestState.value = ApiTestState.Error("Kunci API AI belum diisi")
            return
        }

        viewModelScope.launch {
            _apiTestState.value = ApiTestState.Testing
            val result = GeminiApiClient.testConnection(testApiKey.trim(), modelName)
            if (result.isSuccess) {
                _apiTestState.value = ApiTestState.Success("Koneksi Sukses! Model $modelName aktif.")
            } else {
                val errMsg = result.exceptionOrNull()?.message ?: "Gagal terhubung"
                _apiTestState.value = ApiTestState.Error(errMsg)
            }
        }
    }

    fun resetApiTestState() {
        _apiTestState.value = ApiTestState.Idle
    }
}

class CustomerServiceViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = AppDatabase.getInstance(application)
        val knowledgeRepo = KnowledgeRepository(database.knowledgeDao())
        val chatRepo = ChatRepository(database.chatDao(), knowledgeRepo)
        return CustomerServiceViewModel(application, knowledgeRepo, chatRepo) as T
    }
}
