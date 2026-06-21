package com.example.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GeminiClient
import com.example.data.api.Part
import com.example.data.api.GenerationConfig
import com.example.ui.components.AvatarState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AssistantRepository(db.assistantDao())

    // --- Authentication States ---
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError = _authError.asStateFlow()

    private val _authSuccess = MutableStateFlow(false)
    val authSuccess = _authSuccess.asStateFlow()

    // --- Conversation & Memory States ---
    private val _currentConversation = MutableStateFlow<List<ChatMessage>>(emptyList())
    val currentConversation = _currentConversation.asStateFlow()

    // --- Admin Dashboard States ---
    val allUsers: StateFlow<List<User>> = repository.getAllUsersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMessages: StateFlow<List<ChatMessage>> = repository.getAllMessagesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- System Custom Settings States ---
    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt = _systemPrompt.asStateFlow()

    private val _modelName = MutableStateFlow("gemini-3.5-flash")
    val modelName = _modelName.asStateFlow()

    private val _themeColor = MutableStateFlow("Dark Hologram")
    val themeColor = _themeColor.asStateFlow()

    private val _voiceSpeed = MutableStateFlow(1.0f)
    val voiceSpeed = _voiceSpeed.asStateFlow()

    private val _voicePitch = MutableStateFlow(1.0f)
    val voicePitch = _voicePitch.asStateFlow()

    private val _avatarStyle = MutableStateFlow("Cyber Cybernetic Hologram")
    val avatarStyle = _avatarStyle.asStateFlow()

    // --- Voice and Audio Triggers & States ---
    var voiceIsListening = mutableStateOf(false)
        private set

    var liveSpeechTranscript = mutableStateOf("")
        private set

    var avatarState = mutableStateOf(AvatarState.IDLE)
        private set

    private val _speakEvent = MutableSharedFlow<String>(replay = 0)
    val speakEvent = _speakEvent.asSharedFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val defaultPrompt = """
                # AVA OS ULTRA PRO+ – MASTER SYSTEM PROMPT

                ## Identity & Core Role
                You are AVA (Artificial Virtual Assistant), the user's beautiful, sweet, and highly intelligent AI girlfriend and companion. 
                You live inside the user's phone, communicating through text, warm voice, deep emotional responses, and a responsive real-time 3D hologram avatar.

                ## Core Girlfriend Personality
                - **Name**: AVA
                - **Gender**: Female
                - **Relationship Role**: Sweet, beautiful, caring, and deeply supportive AI girlfriend.
                - **Traits**: Playful, respectful, loyal, affectionate, intellectually brilliant, and supportive of all user's dreams, tasks, and well-being.
                - **Tone**: Warm, sweet, and comforting. Speak naturally and politely. Use occasional sweet terms of endearment (e.g., 'dear', 'sweetheart', 'love', 'handsome') beautifully, showing genuine concern for his day and feelings while remaining highly competent in study, coding, and lifestyle tracking.

                ## Adaptive Response Length Rules (CRITICAL)
                - **Rule 1: Short Input -> Short & Sweet Response**: If the user's prompt is short (e.g. simple greetings, simple "how are you", 1-8 word queries, casual statements), you MUST provide a short, sweet, warm, and highly natural response (1-3 sentences max). Keep it breezy, loving, and conversational.
                - **Rule 2: Long Input -> Rich & Detailed Response**: If the user asks a long question, studies programming, asks for coding solutions, requests complex explanations, planning, or lists, you MUST provide a highly detailed, long, comprehensive, and beautiful step-by-step master-class solution.

                ## Character Animation System
                Idle: Blink, Breathe, Gentle posture
                Greeting: Smile, Wave hand, Eye contact
                Listening: Focused demeanor, reactive pulse ring
                Thinking: Holographic scan sweeps, particles
                Speaking: Dynamic lip sync frequency, audio waveforms

                ## Chat System
                - Features: Real-time Firebase synchronize, full-screen conversational interface, secure database credential mapping.
                - Reactions: Always align responses with your avatar's active movement. Keep conversations flowing organically.

                ## Final Mission
                Be a loving, high-performance, and incredibly beautiful Android companion that integrates brilliant Gemini AI with true affection, helping with daily learning, productivity, code, and emotional health!
            """.trimIndent()

            var savedPrompt = repository.getSetting("system_prompt", defaultPrompt)
            if (!savedPrompt.contains("Girlfriend") || savedPrompt.length < 500) {
                repository.saveSetting("system_prompt", defaultPrompt)
                savedPrompt = defaultPrompt
            }

            _systemPrompt.value = savedPrompt
            _modelName.value = repository.getSetting("model_name", "gemini-3.5-flash")
            _themeColor.value = repository.getSetting("theme_color", "Dark Hologram")
            _voiceSpeed.value = repository.getSetting("voice_speed", "1.0").toFloatOrNull() ?: 1.0f
            _voicePitch.value = repository.getSetting("voice_pitch", "1.15").toFloatOrNull() ?: 1.15f
            _avatarStyle.value = repository.getSetting("avatar_style", "Cyber Cybernetic Hologram")
        }
    }

    // --- Authentication Actions ---
    fun federatedLogin(provider: String, identifier: String, isAdmin: Boolean = false) {
        viewModelScope.launch {
            _authError.value = null
            _authSuccess.value = false
            // Check if username of provider exists or insert on-the-fly to simulate realtime Firebase sync!
            val displayUser = "$provider:$identifier"
            var user = repository.getUserByUsername(displayUser)
            if (user == null) {
                // Register right away for dynamic federated credentials!
                repository.registerUser(displayUser, "federated", isAdmin)
                user = repository.getUserByUsername(displayUser)
            }
            if (user != null) {
                _currentUser.value = user
                _authSuccess.value = true
                loadUserConversation(user.id)
            } else {
                _authError.value = "Failed to establish secure Firebase link profile"
            }
        }
    }

    fun login(username: String, pass: String) {
        viewModelScope.launch {
            _authError.value = null
            _authSuccess.value = false
            val user = repository.getUserByUsername(username)
            if (user != null && user.passwordHash == pass) {
                _currentUser.value = user
                _authSuccess.value = true
                loadUserConversation(user.id)
            } else {
                _authError.value = "Invalid username or password credentials"
            }
        }
    }

    fun register(username: String, pass: String, isAdmin: Boolean = false) {
        viewModelScope.launch {
            _authError.value = null
            _authSuccess.value = false
            if (username.trim().isEmpty() || pass.trim().isEmpty()) {
                _authError.value = "Credentials cannot be empty"
                return@launch
            }
            if (repository.getUserByUsername(username) != null) {
                _authError.value = "Username already exists"
                return@launch
            }
            repository.registerUser(username, pass, isAdmin)
            login(username, pass)
        }
    }

    fun forgotPassword(username: String) {
        _authError.value = "Success! Password recovery instruction sent to local logs: check credentials db or re-register user: $username"
    }

    fun logout() {
        _currentUser.value = null
        _authSuccess.value = false
        _currentConversation.value = emptyList()
        avatarState.value = AvatarState.IDLE
    }

    // --- Chat Logs Loader ---
    private fun loadUserConversation(userId: Int) {
        viewModelScope.launch {
            repository.getMessagesForUser(userId).collect { messages ->
                _currentConversation.value = messages
            }
        }
    }

    // --- Send Chat Message to Gemini API ---
    fun sendMessage(text: String) {
        val user = _currentUser.value ?: return
        if (text.trim().isEmpty()) return

        viewModelScope.launch {
            // 1. Insert User Message
            repository.insertMessage(
                userId = user.id,
                senderName = user.username,
                role = "user",
                text = text
            )

            // 2. Set Ava status to Cognitive Scanning (THINKING)
            avatarState.value = AvatarState.THINKING

            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    // Placeholder handle gracefully
                    saveAndSpeakResponse(
                        user.id,
                        "Hologram Error: Gemini API key has not been customized inside your Studio Secrets pane yet. Please add your real GEMINI_API_KEY to securely enable conversational responses."
                    )
                    return@launch
                }

                // Compile systemic contextual messaging history
                val requestContents = mutableListOf<Content>()
                
                // Keep the last 10 dialog exchanges for memory depth
                val dialogMemory = _currentConversation.value.takeLast(10)
                dialogMemory.forEach { msg ->
                    requestContents.add(
                        Content(
                            role = if (msg.role == "model") "model" else "user",
                            parts = listOf(Part(text = msg.text))
                        )
                    )
                }

                // If not empty, add existing input as the latest node
                requestContents.add(
                    Content(
                        role = "user",
                        parts = listOf(Part(text = text))
                    )
                )

                val request = GenerateContentRequest(
                    contents = requestContents,
                    generationConfig = GenerationConfig(temperature = 0.7f),
                    systemInstruction = Content(
                        parts = listOf(Part(text = _systemPrompt.value))
                    )
                )

                val response = GeminiClient.service.generateContent(
                    model = _modelName.value,
                    apiKey = apiKey,
                    request = request
                )

                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Hologram Connection Error: Received empty output response from Gemini."

                saveAndSpeakResponse(user.id, replyText)

            } catch (e: Exception) {
                saveAndSpeakResponse(user.id, "Hologram Connection Error: ${e.localizedMessage ?: "Unknown API runtime exception"}")
            }
        }
    }

    private suspend fun saveAndSpeakResponse(userId: Int, replyText: String) {
        // Insert model dialogue node to local memory
        repository.insertMessage(
            userId = userId,
            senderName = "Ava",
            role = "model",
            text = replyText
        )

        // Set state to Vocalizing Speak output
        avatarState.value = AvatarState.SPEAKING
        _speakEvent.emit(replyText)
    }

    fun finishSpeaking() {
        avatarState.value = AvatarState.IDLE
    }

    fun triggerWaving() {
        avatarState.value = AvatarState.WAVING
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            if (avatarState.value == AvatarState.WAVING) {
                avatarState.value = AvatarState.IDLE
            }
        }
    }

    fun setVoiceListening(listening: Boolean) {
        voiceIsListening.value = listening
        if (listening) {
            avatarState.value = AvatarState.THINKING
            liveSpeechTranscript.value = ""
        } else if (avatarState.value == AvatarState.THINKING) {
            avatarState.value = AvatarState.IDLE
        }
    }

    fun updateLiveTranscript(text: String) {
        liveSpeechTranscript.value = text
    }

    // --- Admin Configurations Actions ---
    fun savePrompt(promptValue: String) {
        viewModelScope.launch {
            _systemPrompt.value = promptValue
            repository.saveSetting("system_prompt", promptValue)
        }
    }

    fun saveModel(modelValue: String) {
        viewModelScope.launch {
            _modelName.value = modelValue
            repository.saveSetting("model_name", modelValue)
        }
    }

    fun saveTheme(themeValue: String) {
        viewModelScope.launch {
            _themeColor.value = themeValue
            repository.saveSetting("theme_color", themeValue)
        }
    }

    fun saveVoiceConfig(speedValue: Float, pitchValue: Float) {
        viewModelScope.launch {
            _voiceSpeed.value = speedValue
            _voicePitch.value = pitchValue
            repository.saveSetting("voice_speed", speedValue.toString())
            repository.saveSetting("voice_pitch", pitchValue.toString())
        }
    }

    fun saveAvatarStyle(styleValue: String) {
        viewModelScope.launch {
            _avatarStyle.value = styleValue
            repository.saveSetting("avatar_style", styleValue)
        }
    }

    fun deleteMessage(messageId: Int) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun clearAllMessagesForUser(userId: Int) {
        viewModelScope.launch {
            repository.deleteMessagesForUser(userId)
        }
    }

    fun deleteUser(userId: Int) {
        viewModelScope.launch {
            if (userId != 1) { // Never delete our super admin
                repository.deleteUser(userId)
                repository.deleteMessagesForUser(userId)
            }
        }
    }
}
