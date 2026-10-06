package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.local.AiConfigEntity
import com.example.ui.viewmodel.ApiTestState

val SUPPORTED_AI_MODELS = listOf(
    Pair("gemini-3.5-flash", "Gemini 3.5 Flash (Direkomendasikan - Sangat Cepat & Akurat)"),
    Pair("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview (Penalaran Kompleks)"),
    Pair("gemini-flash-latest", "Gemini Flash Latest")
)

val CS_TONES = listOf(
    "Legal & Berorientasi Kepatuhan (Compliance)",
    "Profesional, Objektif & Solutif",
    "Konservatif & Mitigasi Risiko Maksimal",
    "Persuasif & Mediasi Hubungan Industrial"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    aiConfig: AiConfigEntity,
    apiTestState: ApiTestState,
    onSaveConfig: (apiKey: String, model: String, businessName: String, tone: String, prompt: String, fallback: Boolean) -> Unit,
    onTestConnection: (apiKey: String, model: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var apiKey by remember(aiConfig) { mutableStateOf(aiConfig.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var selectedModel by remember(aiConfig) { mutableStateOf(aiConfig.modelName) }
    var businessName by remember(aiConfig) { mutableStateOf(aiConfig.businessName) }
    var selectedTone by remember(aiConfig) { mutableStateOf(aiConfig.serviceTone) }
    var customPrompt by remember(aiConfig) { mutableStateOf(aiConfig.customSystemPrompt) }
    var useOfflineFallback by remember(aiConfig) { mutableStateOf(aiConfig.useOfflineFallback) }

    var toneExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Konfigurasi API & AI",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Pengaturan kunci API, model, dan persona CS",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: AI API Key
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kunci API AI (Google Gemini)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Masukkan kunci API Gemini Anda di bawah ini. Kunci ini disimpan secara aman di basis data lokal perangkat Anda untuk memproses permintaan CS.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiKey) "Sembunyikan Kunci" else "Tampilkan Kunci"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input")
                    )

                    // Helper button to autofill from buildconfig if present
                    if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
                        Spacer(modifier = Modifier.height(6.dp))
                        FilledTonalButton(
                            onClick = { apiKey = BuildConfig.GEMINI_API_KEY },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Gunakan Kunci dari Environment (.env)")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Connection Button & Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onTestConnection(apiKey, selectedModel) },
                            enabled = apiKey.isNotBlank() && apiTestState !is ApiTestState.Testing,
                            modifier = Modifier.testTag("test_api_connection_btn")
                        ) {
                            if (apiTestState is ApiTestState.Testing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Menguji...")
                            } else {
                                Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Uji Koneksi AI")
                            }
                        }

                        // Connection badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (apiTestState) {
                                is ApiTestState.Success -> MaterialTheme.colorScheme.primaryContainer
                                is ApiTestState.Error -> MaterialTheme.colorScheme.errorContainer
                                is ApiTestState.Testing -> MaterialTheme.colorScheme.surfaceVariant
                                is ApiTestState.Idle -> if (apiKey.isNotBlank()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                when (apiTestState) {
                                    is ApiTestState.Success -> {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Koneksi Valid", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
                                    }
                                    is ApiTestState.Error -> {
                                        Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Gagal", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold))
                                    }
                                    else -> {
                                        Text(
                                            text = if (apiKey.isNotBlank()) "Kunci Terisi" else "Belum Dikonfigurasi",
                                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Test result detail banner
                    AnimatedVisibility(visible = apiTestState is ApiTestState.Success || apiTestState is ApiTestState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (apiTestState is ApiTestState.Success) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = when (apiTestState) {
                                    is ApiTestState.Success -> apiTestState.message
                                    is ApiTestState.Error -> "Kendala: ${apiTestState.error}"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (apiTestState is ApiTestState.Success) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Section 2: AI Model Selection
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pilihan Model AI",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pilih arsitektur model Gemini yang akan memproses pertanyaan pelanggan.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SUPPORTED_AI_MODELS.forEach { (modelId, desc) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedModel == modelId,
                                onClick = { selectedModel = modelId }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = modelId,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Organization Profile & HR Advisor Persona
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Profil Organisasi & Persona HR Advisor",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text("Nama Perusahaan / Divisi HR") },
                        placeholder = { Text("Contoh: PT Sumber Sejahtera / Divisi Human Capital") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Tone of Voice Dropdown
                    ExposedDropdownMenuBox(
                        expanded = toneExpanded,
                        onExpandedChange = { toneExpanded = !toneExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedTone,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Karakter Pendekatan Hukum HR") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toneExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = toneExpanded,
                            onDismissRequest = { toneExpanded = false }
                        ) {
                            CS_TONES.forEach { tone ->
                                DropdownMenuItem(
                                    text = { Text(tone) },
                                    onClick = {
                                        selectedTone = tone
                                        toneExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        label = { Text("Instruksi Khusus Analisis Kasus") },
                        placeholder = { Text("Contoh: Selalu sertakan tahapan pemanggilan tertulis dan mitigasi risiko perselisihan hubungan industrial.") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Fallback Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fallback Ketentuan Lokal (Room Database)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Jika internet terputus atau kuota API habis, Advisor tetap menganalisis kasus secara langsung dari ketentuan PP/PKB yang tersimpan di basis data lokal.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = useOfflineFallback,
                            onCheckedChange = { useOfflineFallback = it }
                        )
                    }
                }
            }

            // Save Settings Button
            Button(
                onClick = {
                    onSaveConfig(
                        apiKey,
                        selectedModel,
                        businessName,
                        selectedTone,
                        customPrompt,
                        useOfflineFallback
                    )
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_ai_settings_btn")
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Simpan Semua Pengaturan",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
