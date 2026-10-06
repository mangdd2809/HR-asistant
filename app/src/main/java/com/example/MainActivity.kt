package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.AiSettingsScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.KnowledgeBaseScreen
import com.example.ui.screens.SessionsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.CustomerServiceViewModel
import com.example.ui.viewmodel.CustomerServiceViewModelFactory
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: CustomerServiceViewModel by viewModels {
        CustomerServiceViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: CustomerServiceViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()

    val knowledgeList by viewModel.knowledgeList.collectAsStateWithLifecycle()
    val knowledgeSearchQuery by viewModel.knowledgeSearchQuery.collectAsStateWithLifecycle()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()

    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()
    val apiTestState by viewModel.apiTestState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // BackHandler: if not on CHAT tab, navigate back to CHAT
    BackHandler(enabled = currentTab != AppTab.CHAT) {
        viewModel.selectTab(AppTab.CHAT)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            // Adaptive Tablet / Landscape layout with NavigationRail
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationRailItem(
                        selected = currentTab == AppTab.CHAT,
                        onClick = { viewModel.selectTab(AppTab.CHAT) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == AppTab.CHAT) Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Outlined.Chat,
                                contentDescription = "Kasus HR"
                            )
                        },
                        label = { Text("Kasus HR") },
                        modifier = Modifier.testTag("tab_chat")
                    )

                    NavigationRailItem(
                        selected = currentTab == AppTab.KNOWLEDGE,
                        onClick = { viewModel.selectTab(AppTab.KNOWLEDGE) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == AppTab.KNOWLEDGE) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = "Regulasi HR"
                            )
                        },
                        label = { Text("Regulasi HR") },
                        modifier = Modifier.testTag("tab_knowledge")
                    )

                    NavigationRailItem(
                        selected = currentTab == AppTab.AI_CONFIG,
                        onClick = { viewModel.selectTab(AppTab.AI_CONFIG) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == AppTab.AI_CONFIG) Icons.Filled.Key else Icons.Outlined.Key,
                                contentDescription = "API Advisor"
                            )
                        },
                        label = { Text("API Advisor") },
                        modifier = Modifier.testTag("tab_ai_config")
                    )

                    NavigationRailItem(
                        selected = currentTab == AppTab.SESSIONS,
                        onClick = { viewModel.selectTab(AppTab.SESSIONS) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == AppTab.SESSIONS) Icons.Filled.Forum else Icons.Outlined.Forum,
                                contentDescription = "Log Kasus"
                            )
                        },
                        label = { Text("Log Kasus") },
                        modifier = Modifier.testTag("tab_sessions")
                    )
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.navigationBars,
                    modifier = Modifier.weight(1f)
                ) { innerPadding ->
                    TabContent(
                        currentTab = currentTab,
                        viewModel = viewModel,
                        activeSession = activeSession,
                        chatMessages = chatMessages,
                        isGenerating = isGenerating,
                        allSessions = allSessions,
                        knowledgeList = knowledgeList,
                        knowledgeSearchQuery = knowledgeSearchQuery,
                        selectedCategoryFilter = selectedCategoryFilter,
                        aiConfig = aiConfig,
                        apiTestState = apiTestState,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        } else {
            // Mobile layout with NavigationBar
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        NavigationBarItem(
                            selected = currentTab == AppTab.CHAT,
                            onClick = { viewModel.selectTab(AppTab.CHAT) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.CHAT) Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Outlined.Chat,
                                    contentDescription = "Kasus HR"
                                )
                            },
                            label = { Text("Kasus HR") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("tab_chat")
                        )

                        NavigationBarItem(
                            selected = currentTab == AppTab.KNOWLEDGE,
                            onClick = { viewModel.selectTab(AppTab.KNOWLEDGE) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.KNOWLEDGE) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Outlined.MenuBook,
                                    contentDescription = "Regulasi HR"
                                )
                            },
                            label = { Text("Regulasi HR") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("tab_knowledge")
                        )

                        NavigationBarItem(
                            selected = currentTab == AppTab.AI_CONFIG,
                            onClick = { viewModel.selectTab(AppTab.AI_CONFIG) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.AI_CONFIG) Icons.Filled.Key else Icons.Outlined.Key,
                                    contentDescription = "API Advisor"
                                )
                            },
                            label = { Text("API Advisor") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("tab_ai_config")
                        )

                        NavigationBarItem(
                            selected = currentTab == AppTab.SESSIONS,
                            onClick = { viewModel.selectTab(AppTab.SESSIONS) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.SESSIONS) Icons.Filled.Forum else Icons.Outlined.Forum,
                                    contentDescription = "Log Kasus"
                                )
                            },
                            label = { Text("Log Kasus") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("tab_sessions")
                        )
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.navigationBars,
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                TabContent(
                    currentTab = currentTab,
                    viewModel = viewModel,
                    activeSession = activeSession,
                    chatMessages = chatMessages,
                    isGenerating = isGenerating,
                    allSessions = allSessions,
                    knowledgeList = knowledgeList,
                    knowledgeSearchQuery = knowledgeSearchQuery,
                    selectedCategoryFilter = selectedCategoryFilter,
                    aiConfig = aiConfig,
                    apiTestState = apiTestState,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
fun TabContent(
    currentTab: AppTab,
    viewModel: CustomerServiceViewModel,
    activeSession: com.example.data.local.ChatSession?,
    chatMessages: List<com.example.data.local.ChatMessage>,
    isGenerating: Boolean,
    allSessions: List<com.example.data.local.ChatSession>,
    knowledgeList: List<com.example.data.local.KnowledgeItem>,
    knowledgeSearchQuery: String,
    selectedCategoryFilter: String?,
    aiConfig: com.example.data.local.AiConfigEntity,
    apiTestState: com.example.ui.viewmodel.ApiTestState,
    modifier: Modifier = Modifier
) {
    when (currentTab) {
        AppTab.CHAT -> {
            ChatScreen(
                session = activeSession,
                messages = chatMessages,
                isGenerating = isGenerating,
                onSendMessage = { query -> viewModel.sendCustomerMessage(query) },
                onNewSession = { viewModel.startNewSession() },
                onRateMessage = { id, helpful -> viewModel.rateMessage(id, helpful) },
                modifier = modifier
            )
        }
        AppTab.KNOWLEDGE -> {
            KnowledgeBaseScreen(
                items = knowledgeList,
                searchQuery = knowledgeSearchQuery,
                selectedCategory = selectedCategoryFilter,
                onSearchChange = { q -> viewModel.setKnowledgeSearchQuery(q) },
                onCategoryChange = { cat -> viewModel.setCategoryFilter(cat) },
                onAddKnowledge = { title, cat, content, keywords ->
                    viewModel.addKnowledgeItem(title, cat, content, keywords)
                },
                onImportFile = { uri, name, cat ->
                    viewModel.importFileContent(uri, name, cat)
                },
                onUpdateKnowledge = { item -> viewModel.updateKnowledgeItem(item) },
                onDeleteKnowledge = { item -> viewModel.deleteKnowledgeItem(item) },
                onToggleActive = { item -> viewModel.toggleKnowledgeActive(item) },
                modifier = modifier
            )
        }
        AppTab.AI_CONFIG -> {
            AiSettingsScreen(
                aiConfig = aiConfig,
                apiTestState = apiTestState,
                onSaveConfig = { key, model, name, tone, prompt, fallback ->
                    viewModel.updateAiConfig(key, model, name, tone, prompt, fallback)
                },
                onTestConnection = { key, model ->
                    viewModel.testAiConnection(key, model)
                },
                modifier = modifier
            )
        }
        AppTab.SESSIONS -> {
            SessionsScreen(
                sessions = allSessions,
                activeSessionId = activeSession?.id,
                onSelectSession = { sess -> viewModel.selectSession(sess) },
                onNewSession = { name, title -> viewModel.startNewSession(name, title) },
                onDeleteSession = { id -> viewModel.deleteSession(id) },
                totalKnowledgeCount = knowledgeList.size,
                modifier = modifier
            )
        }
    }
}
