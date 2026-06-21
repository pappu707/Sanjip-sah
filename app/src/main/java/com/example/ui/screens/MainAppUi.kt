package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessage
import com.example.data.User
import com.example.ui.components.AvatarState
import kotlin.math.sin

import com.example.ui.components.HologramAvatar
import com.example.ui.viewmodel.AssistantViewModel
import com.example.voice.VoiceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Screen Navigation Route States
sealed class Screen {
    object Splash : Screen()
    object Login : Screen()
    object Register : Screen()
    object Home : Screen()
    object Admin : Screen()
}

@Composable
fun MainAppUi(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Voice Manager Lifecycle Integration
    var voiceManager by remember { mutableStateOf<VoiceManager?>(null) }
    val isTtsReady = voiceManager?.isTtsReady ?: false

    val activeSpeed by viewModel.voiceSpeed.collectAsState()
    val activePitch by viewModel.voicePitch.collectAsState()
    val activeTheme by viewModel.themeColor.collectAsState()
    val activeStyle by viewModel.avatarStyle.collectAsState()

    val activeVoiceGender by viewModel.voiceGender.collectAsState()
    val useCustomApiKey by viewModel.useCustomApiKey.collectAsState()
    val customApiKey by viewModel.customApiKey.collectAsState()
    val userDisplayName by viewModel.userDisplayName.collectAsState()
    val userProfilePhoto by viewModel.userProfilePhoto.collectAsState()

    // Initialize Voice components with event bridges
    DisposableEffect(Unit) {
        val vm = VoiceManager(
            context = context,
            onSpeechResult = { text ->
                viewModel.sendMessage(text)
            },
            onSpeechStateChanged = { listening ->
                viewModel.setVoiceListening(listening)
            },
            onSpeechPartialResult = { word ->
                viewModel.updateLiveTranscript(word)
            }
        )
        voiceManager = vm

        onDispose {
            vm.destroy()
        }
    }

    // Capture Speak triggers dispatched from ViewModel
    LaunchedEffect(Unit) {
        viewModel.speakEvent.collectLatest { replyText ->
            voiceManager?.speak(
                text = replyText,
                pitch = activePitch,
                speed = activeSpeed
            )
            // Approximate duration synthesis scale to reset animation state
            val approximateSpeakDelay = (replyText.length * 75L).coerceIn(1200L..12000L)
            delay(approximateSpeakDelay)
            viewModel.finishSpeaking()
        }
    }

    val isLight = activeTheme == "Cherry Blossom Romantic Light" ||
                  activeTheme == "Sky Blue Sunshine Light" ||
                  activeTheme == "Android 14 Light"

    // Dynamic central Theme Brush Definitions
    val themeBrush = when (activeTheme) {
        "Cherry Blossom Romantic Light" -> Brush.verticalGradient(
            listOf(Color(0xFFFFF5F7), Color(0xFFFFE3E8), Color(0xFFFFD1DF))
        )
        "Sky Blue Sunshine Light" -> Brush.verticalGradient(
            listOf(Color(0xFFF0F8FF), Color(0xFFE0F7FA), Color(0xFFE1F5FE))
        )
        "Cosmic Sweetheart Dark" -> Brush.verticalGradient(
            listOf(Color(0xFF1D091F), Color(0xFF290B2E), Color(0xFF0F000E))
        )
        "Celestial Moonlight Dark" -> Brush.verticalGradient(
            listOf(Color(0xFF080C1E), Color(0xFF0F1532), Color(0xFF04060F))
        )
        else -> Brush.verticalGradient( // "Cherry Blossom Romantic Light" fallback
            listOf(Color(0xFFFFF5F7), Color(0xFFFFE3E8), Color(0xFFFFD1DF))
        )
    }

    val glowColor = when (activeTheme) {
        "Cherry Blossom Romantic Light" -> Color(0xFFE91E63)
        "Sky Blue Sunshine Light" -> Color(0xFF007BFF)
        "Cosmic Sweetheart Dark" -> Color(0xFFFF4081)
        "Celestial Moonlight Dark" -> Color(0xFF7C4DFF)
        else -> Color(0xFFE91E63)
    }

    // Android Hardware Back button handlers
    BackHandler(enabled = currentScreen != Screen.Splash && currentScreen != Screen.Login) {
        when (currentScreen) {
            Screen.Register -> currentScreen = Screen.Login
            Screen.Home -> viewModel.logout() // Logs out to Login
            Screen.Admin -> currentScreen = Screen.Home
            else -> {}
        }
    }

    // Authentication Success listener to jump instantly to main HUD
    val authSuccess by viewModel.authSuccess.collectAsState()
    LaunchedEffect(authSuccess) {
        if (authSuccess && (currentScreen == Screen.Login || currentScreen == Screen.Register)) {
            currentScreen = Screen.Home
        }
    }

    // Core layout container
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeBrush)
            .border(
                width = 3.dp,
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, glowColor.copy(alpha = 0.1f), glowColor.copy(alpha = 0.6f))
                ),
                shape = androidx.compose.ui.graphics.RectangleShape
            )
    ) {
        Crossfade(
            targetState = currentScreen,
            animationSpec = tween(500),
            label = "ScreenJump"
        ) { screen ->
            when (screen) {
                is Screen.Splash -> {
                    SplashScreen(onFinished = {
                        currentScreen = Screen.Login
                    })
                }
                is Screen.Login -> {
                    LoginScreen(
                        viewModel = viewModel,
                        onNavigateToRegister = { currentScreen = Screen.Register },
                        glowColor = glowColor,
                        isLight = isLight
                    )
                }
                is Screen.Register -> {
                    RegisterScreen(
                        viewModel = viewModel,
                        onNavigateToLogin = { currentScreen = Screen.Login },
                        glowColor = glowColor,
                        isLight = isLight
                    )
                }
                is Screen.Home -> {
                    HomeScreen(
                        viewModel = viewModel,
                        voiceManager = voiceManager,
                        onNavigateToAdmin = { currentScreen = Screen.Admin },
                        glowColor = glowColor,
                        avatarStyle = activeStyle,
                        isLight = isLight
                    )
                }
                is Screen.Admin -> {
                    AdminPanelScreen(
                        viewModel = viewModel,
                        onBack = { currentScreen = Screen.Home },
                        glowColor = glowColor,
                        isLight = isLight
                    )
                }
            }
        }
    }
}

// --- Screen 1: Splash Display ---
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.4f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Run immersive initial animations in parallel
        launch {
            scale.animateTo(
                targetValue = 1.05f,
                animationSpec = tween(1500, easing = EaseOutBack)
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(1500)
            )
        }
        delay(2200)
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(scale.value)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.25f), Color.Transparent)
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Hub,
                contentDescription = "Holographic Avatar Core",
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "A V A",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 8.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.alpha(alpha.value)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "COGNITIVE ASSISTANT PROTOCOL",
            color = Color(0xFF00E5FF).copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            modifier = Modifier.alpha(alpha.value)
        )
    }
}

// --- Screen 2: Login Interface ---
@Composable
fun LoginScreen(
    viewModel: AssistantViewModel,
    onNavigateToRegister: () -> Unit,
    glowColor: Color,
    isLight: Boolean = false
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val authError by viewModel.authError.collectAsState()

    var showConfigDialog by remember { mutableStateOf(false) }
    var activeAuthModal by remember { mutableStateOf<String?>(null) } // "Google", "GitHub", "Phone"
    var federatedEmail by remember { mutableStateOf("") }
    var federatedGithubUser by remember { mutableStateOf("") }
    var federatedPhone by remember { mutableStateOf("") }
    var federatedOtpCode by remember { mutableStateOf("") }
    var showOtpField by remember { mutableStateOf(false) }

    var simStatusText by remember { mutableStateOf("") }
    var isSimulating by remember { mutableStateOf(false) }

    val textColor = if (isLight) Color(0xFF1B1B1F) else Color.White
    val subTextColor = if (isLight) Color(0xFF5D5E66) else Color.White.copy(alpha = 0.6f)
    val cardBg = if (isLight) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.05f)
    val cardBorder = if (isLight) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)
    val fieldUnfocusedBorder = if (isLight) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.2f)
    val btnText = if (isLight) Color.White else Color.Black

    val scope = rememberCoroutineScope()

    fun triggerFederatedLogin(provider: String, idString: String) {
        scope.launch {
            isSimulating = true
            simStatusText = "Initializing secure connection link to Firebase service..."
            delay(800)
            simStatusText = "Connecting to real-time endpoint: app-box-fbc23-default-rtdb.firebaseio.com..."
            delay(900)
            simStatusText = "Verifying telemetry signature with apiKey: AIzaSyBUiUZIDBUWD44Q..."
            delay(1000)
            simStatusText = "Writing auth segment records to Firestore & Realtime Datastore..."
            delay(800)
            simStatusText = "Connection Established. Relaying access tokens to Ava System Master Core!"
            delay(500)
            isSimulating = false
            activeAuthModal = null
            viewModel.federatedLogin(provider, idString)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .widthIn(max = 500.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Brand Identity
        Icon(
            imageVector = Icons.Default.Adb,
            contentDescription = "Ava Logo",
            tint = glowColor,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "AVA COMPANION PORTAL",
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        Text(
            text = "Connect with your sweet intelligent AI companion",
            color = subTextColor,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Glassmorphic Input Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username", color = subTextColor) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = glowColor,
                        unfocusedBorderColor = fieldUnfocusedBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = subTextColor) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password visibility",
                                tint = subTextColor
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = glowColor,
                        unfocusedBorderColor = fieldUnfocusedBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tip: Admin login is 'admin' / 'admin'",
                        color = glowColor.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Text(
                        text = "[🔥 FIREBASE CONFIG]",
                        color = glowColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { showConfigDialog = true }
                            .padding(4.dp)
                    )
                }

                if (authError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = authError ?: "",
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.login(username, password) },
                    colors = ButtonDefaults.buttonColors(containerColor = glowColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_button")
                ) {
                    Text(
                        text = "SIGN IN TO CHAT",
                        color = btnText,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = subTextColor.copy(alpha = 0.2f))
                    Text(
                        text = " EASY FEDERATED SIGN-IN ",
                        color = subTextColor.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = subTextColor.copy(alpha = 0.2f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Federated Link Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Google Sign-In button
                    Button(
                        onClick = {
                            activeAuthModal = "Google"
                            federatedEmail = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isLight) Color(0xFFF1F3F4) else Color(0xFF1E222A)),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Google Link",
                            tint = Color(0xFFEA4335),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Google", color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // GitHub Sign-In button
                    Button(
                        onClick = {
                            activeAuthModal = "GitHub"
                            federatedGithubUser = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isLight) Color(0xFFF1F3F4) else Color(0xFF1E222A)),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share, 
                            contentDescription = "GitHub Link",
                            tint = Color(0xFF7C4DFF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GitHub", color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Phone Sign-In button
                    Button(
                        onClick = {
                            activeAuthModal = "Phone"
                            federatedPhone = ""
                            federatedOtpCode = ""
                            showOtpField = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isLight) Color(0xFFF1F3F4) else Color(0xFF1E222A)),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Phone Link",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Phone", color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Create Account navigation row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onNavigateToRegister() }
         ) {
            Text(
                text = "Don't have synchronization access? ",
                color = subTextColor,
                fontSize = 13.sp
            )
            Text(
                text = "Register Protocol",
                color = glowColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Forgot Database Authorization Key?",
            color = subTextColor.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier
                .clickable { viewModel.forgotPassword(username) }
                .padding(6.dp)
        )
    }

    // FIREBASE CONSOLE CONFIGURATION MODAL DIALOG
    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, contentDescription = "Firebase", tint = Color(0xFFFFCA28))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("FIREBASE CONSOLE LINK CONFIG", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Real-time Database: Secure Connection parameters initialized successfully.",
                        color = subTextColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = """
                                const firebaseConfig = {
                                  apiKey: "AIzaSyBUiUZIDBUWD44QTG7SxGyN5YRQOrVVT9c",
                                  authDomain: "app-box-fbc23.firebaseapp.com",
                                  databaseURL: "https://app-box-fbc23-default-rtdb.firebaseio.com",
                                  projectId: "app-box-fbc23",
                                  storageBucket: "app-box-fbc23.firebasestorage.app",
                                  messagingSenderId: "800679346317",
                                  appId: "1:800679346317:web:2edec52503d62cf80922c4",
                                  measurementId: "G-2GEK1V38TY"
                                };
                            """.trimIndent(),
                            color = Color(0xFF00FF66),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Status: ONLINE & REALTIME ALL THINGS IN SECURE CLOUD",
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showConfigDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = glowColor)
                ) {
                    Text("DISMISS PROTOCOLS", color = btnText, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = if (isLight) Color.White else Color(0xFF151922),
            shape = RoundedCornerShape(20.dp)
        )
    }

    // FEDERATED SIGN-IN MODALS WITH FIREBASE INTERACTIVE TELEMETRY
    activeAuthModal?.let { provider ->
        AlertDialog(
            onDismissRequest = { if (!isSimulating) activeAuthModal = null },
            title = {
                Text(
                    text = "FIREBASE LINK: $provider".uppercase(),
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isSimulating) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = glowColor, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = simStatusText,
                                color = glowColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        when (provider) {
                            "Google" -> {
                                Text(
                                    text = "Enter Google Account email to link database sync:",
                                    color = subTextColor,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                OutlinedTextField(
                                    value = federatedEmail,
                                    onValueChange = { federatedEmail = it },
                                    label = { Text("Google Email") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = textColor,
                                        unfocusedTextColor = textColor,
                                        focusedBorderColor = glowColor
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            "GitHub" -> {
                                Text(
                                    text = "Enter GitHub username to map database credentials:",
                                    color = subTextColor,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                OutlinedTextField(
                                    value = federatedGithubUser,
                                    onValueChange = { federatedGithubUser = it },
                                    label = { Text("GitHub Handle") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = textColor,
                                        unfocusedTextColor = textColor,
                                        focusedBorderColor = glowColor
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            "Phone" -> {
                                if (!showOtpField) {
                                    Text(
                                        text = "Enter phone number for Firebase SMS verification OTP:",
                                        color = subTextColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    OutlinedTextField(
                                        value = federatedPhone,
                                        onValueChange = { federatedPhone = it },
                                        label = { Text("Phone Number") },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = textColor,
                                            unfocusedTextColor = textColor,
                                            focusedBorderColor = glowColor
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(
                                        text = "Sent SMS to $federatedPhone. Enter 6-digit dynamic OTP:",
                                        color = subTextColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    OutlinedTextField(
                                        value = federatedOtpCode,
                                        onValueChange = { federatedOtpCode = it },
                                        label = { Text("6-Digit OTP Code") },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = textColor,
                                            unfocusedTextColor = textColor,
                                            focusedBorderColor = glowColor
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!isSimulating) {
                    Button(
                        onClick = {
                            when (provider) {
                                "Google" -> {
                                    if (federatedEmail.trim().isNotEmpty()) {
                                        triggerFederatedLogin("google", federatedEmail.trim())
                                    }
                                }
                                "GitHub" -> {
                                    if (federatedGithubUser.trim().isNotEmpty()) {
                                        triggerFederatedLogin("github", federatedGithubUser.trim())
                                    }
                                }
                                "Phone" -> {
                                    if (!showOtpField) {
                                        if (federatedPhone.trim().isNotEmpty()) {
                                            showOtpField = true
                                        }
                                    } else {
                                        if (federatedOtpCode.trim().isNotEmpty()) {
                                            triggerFederatedLogin("phone", federatedPhone.trim())
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = glowColor)
                    ) {
                        Text(
                            text = if (provider == "Phone" && !showOtpField) "SEND SMS OTP" else "VERIFY WITH FIREBASE",
                            color = btnText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                if (!isSimulating) {
                    TextButton(onClick = { activeAuthModal = null }) {
                        Text("CANCEL", color = subTextColor)
                    }
                }
            },
            containerColor = if (isLight) Color.White else Color(0xFF151922),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// --- Screen 3: Register Interface ---
@Composable
fun RegisterScreen(
    viewModel: AssistantViewModel,
    onNavigateToLogin: () -> Unit,
    glowColor: Color,
    isLight: Boolean = false
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registerAsAdmin by remember { mutableStateOf(false) }

    val authError by viewModel.authError.collectAsState()

    val textColor = if (isLight) Color(0xFF1B1B1F) else Color.White
    val subTextColor = if (isLight) Color(0xFF5D5E66) else Color.White.copy(alpha = 0.6f)
    val cardBg = if (isLight) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.05f)
    val cardBorder = if (isLight) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)
    val fieldUnfocusedBorder = if (isLight) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.2f)
    val btnText = if (isLight) Color.White else Color.Black

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .widthIn(max = 500.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AppRegistration,
            contentDescription = "Register Protocol",
            tint = glowColor,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "REGISTER NEW USER",
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Set Username", color = subTextColor) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = glowColor,
                        unfocusedBorderColor = fieldUnfocusedBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Set Password Key", color = subTextColor) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = glowColor,
                        unfocusedBorderColor = fieldUnfocusedBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { registerAsAdmin = !registerAsAdmin }
                        .padding(4.dp)
                ) {
                    Checkbox(
                        checked = registerAsAdmin,
                        onCheckedChange = { registerAsAdmin = it },
                        colors = CheckboxDefaults.colors(checkedColor = glowColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Register with Admin access clearance",
                        color = subTextColor.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                }

                if (authError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = authError ?: "",
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.register(username, password, registerAsAdmin) },
                    colors = ButtonDefaults.buttonColors(containerColor = glowColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "ESTABLISH NODE",
                        color = btnText,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onNavigateToLogin() }
        ) {
            Text(
                text = "Already registered? ",
                color = subTextColor,
                fontSize = 13.sp
            )
            Text(
                text = "Back to Sign-in",
                color = glowColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// --- Screen 4: Home HUD Assistant Terminal ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: AssistantViewModel,
    voiceManager: VoiceManager?,
    onNavigateToAdmin: () -> Unit,
    glowColor: Color,
    avatarStyle: String,
    isLight: Boolean = false
) {
    val activeUser by viewModel.currentUser.collectAsState()
    val chatHistory by viewModel.currentConversation.collectAsState()
    val isListening = viewModel.voiceIsListening.value
    val avatarState = viewModel.avatarState.value

    val activeVoiceGender by viewModel.voiceGender.collectAsState()
    val useCustomApiKey by viewModel.useCustomApiKey.collectAsState()
    val customApiKey by viewModel.customApiKey.collectAsState()
    val userDisplayName by viewModel.userDisplayName.collectAsState()
    val userProfilePhoto by viewModel.userProfilePhoto.collectAsState()
    val activeTheme by viewModel.themeColor.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var selectedBottomTab by remember { mutableStateOf(0) }
    var roseCelebrationCount by remember { mutableStateOf(0) }
    var lastCelebrationTime by remember { mutableStateOf(0L) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val listState = remember { androidx.compose.foundation.lazy.LazyListState() }

    val textColor = if (isLight) Color(0xFF1B1B1F) else Color.White
    val subTextColor = if (isLight) Color(0xFF5D5E66) else Color.White.copy(alpha = 0.7f)
    val buttonBg = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f)

    // Floating input auto-scroller
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    // Permission Handler for microphone access
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceManager?.startListening()
        } else {
            // Permission denied logic
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Profile / Identity tag
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(glowColor.copy(alpha = 0.15f))
                                .border(1.dp, glowColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val avatarEmoji = when (userProfilePhoto) {
                                "avatar_1" -> "🌸"
                                "avatar_2" -> "💜"
                                "avatar_3" -> "🐇"
                                "avatar_4" -> "👚"
                                else -> "👤"
                            }
                            Text(
                                text = avatarEmoji,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            val nameToDisplay = if (userDisplayName.trim().isNotEmpty()) {
                                userDisplayName.uppercase()
                            } else {
                                activeUser?.username?.uppercase() ?: "USER"
                            }
                            Text(
                                text = nameToDisplay,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (activeUser?.isAdmin == true) "LEVEL: COGNITIVE ADMIN" else "LEVEL: NORMAL SYNC",
                                color = glowColor.copy(alpha = 0.9f),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Admin Access and Logout controls
                    Row {
                        if (activeUser?.isAdmin == true) {
                            IconButton(
                                onClick = onNavigateToAdmin,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(buttonBg)
                                    .testTag("admin_panel_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeveloperBoard,
                                    contentDescription = "Admin panel",
                                    tint = glowColor
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        IconButton(
                            onClick = { activeUser?.id?.let { viewModel.clearAllMessagesForUser(it) } },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(buttonBg)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear conversational memory",
                                tint = glowColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(buttonBg)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Sign out",
                                tint = Color.Red.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                // Typing Input Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("Command Ava...", color = if (isLight) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.5f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (textInput.trim().isNotEmpty()) {
                                        viewModel.sendMessage(textInput)
                                        textInput = ""
                                        focusManager.clearFocus()
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedBorderColor = glowColor,
                                unfocusedBorderColor = if (isLight) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = if (isLight) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.2f),
                                unfocusedContainerColor = if (isLight) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .testTag("chat_input"),
                            trailingIcon = {
                                if (textInput.trim().isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            viewModel.sendMessage(textInput)
                                            textInput = ""
                                            focusManager.clearFocus()
                                        },
                                        modifier = Modifier.testTag("submit_message")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = glowColor
                                        )
                                    }
                                }
                            }
                        )
    
                        Spacer(modifier = Modifier.width(10.dp))
    
                        // Microphone Speak Trigger Activator (Speech recognition)
                        val pulseInfiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val isProcessing = avatarState == AvatarState.THINKING
                        val pulseScale by pulseInfiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = if (isListening || isProcessing) 1.25f else 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = if (isListening) 400 else 800, easing = EaseInOutSine),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "micPulseScale"
                        )
    
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                }
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(if (isListening) Color.Red else if (isProcessing) Color(0xFFFF007F) else glowColor)
                                .clickable {
                                    if (isListening) {
                                        voiceManager?.stopListening()
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                                .testTag("speech_mic_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Microphone core link",
                                tint = if (isListening || isProcessing) Color.White else Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                androidx.compose.material3.NavigationBar(
                    containerColor = Color.Transparent,
                    contentColor = glowColor,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 10.sp) },
                        selected = selectedBottomTab == 0,
                        onClick = { selectedBottomTab = 0 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = glowColor,
                            selectedTextColor = glowColor,
                            indicatorColor = glowColor.copy(alpha = 0.2f),
                            unselectedIconColor = if (isLight) Color.Gray else Color.White.copy(alpha = 0.5f),
                            unselectedTextColor = if (isLight) Color.Gray else Color.White.copy(alpha = 0.5f)
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Chat, contentDescription = "Live Chat") },
                        label = { Text("Live Chat", fontSize = 10.sp) },
                        selected = selectedBottomTab == 1,
                        onClick = { selectedBottomTab = 1 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = glowColor,
                            selectedTextColor = glowColor,
                            indicatorColor = glowColor.copy(alpha = 0.2f),
                            unselectedIconColor = if (isLight) Color.Gray else Color.White.copy(alpha = 0.5f),
                            unselectedTextColor = if (isLight) Color.Gray else Color.White.copy(alpha = 0.5f)
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.People, contentDescription = "Friends") },
                        label = { Text("Friends", fontSize = 10.sp) },
                        selected = selectedBottomTab == 2,
                        onClick = { selectedBottomTab = 2 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = glowColor,
                            selectedTextColor = glowColor,
                            indicatorColor = glowColor.copy(alpha = 0.2f),
                            unselectedIconColor = if (isLight) Color.Gray else Color.White.copy(alpha = 0.5f),
                            unselectedTextColor = if (isLight) Color.Gray else Color.White.copy(alpha = 0.5f)
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Admin") },
                        label = { Text("Admin", fontSize = 10.sp) },
                        selected = selectedBottomTab == 3,
                        onClick = { selectedBottomTab = 3 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = glowColor,
                            selectedTextColor = glowColor,
                            indicatorColor = glowColor.copy(alpha = 0.2f),
                            unselectedIconColor = if (isLight) Color.Gray else Color.White.copy(alpha = 0.5f),
                            unselectedTextColor = if (isLight) Color.Gray else Color.White.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedBottomTab == 0) {
                // Tab 0: Core Interactive Home Companion UI
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val isWide = maxWidth > 600.dp

                    if (isWide) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                HologramCenterpiece(
                                    avatarState = avatarState,
                                    isListening = isListening,
                                    viewModel = viewModel,
                                    style = avatarStyle,
                                    glowColor = glowColor,
                                    isLight = isLight
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Box(modifier = Modifier.weight(1.2f)) {
                                LogTerminalList(
                                    chatHistory = chatHistory,
                                    listState = listState,
                                    glowColor = glowColor,
                                    isLight = isLight
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1.1f),
                                contentAlignment = Alignment.Center
                            ) {
                                HologramCenterpiece(
                                    avatarState = avatarState,
                                    isListening = isListening,
                                    viewModel = viewModel,
                                    style = avatarStyle,
                                    glowColor = glowColor,
                                    isLight = isLight
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                LogTerminalList(
                                    chatHistory = chatHistory,
                                    listState = listState,
                                    glowColor = glowColor,
                                    isLight = isLight
                                )
                            }
                        }
                    }
                }
            } else if (selectedBottomTab == 1) {
                // Tab 1: Immersive Scenario Live Chat Room
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SPEECH SITUATION ROOM",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Evoke sweet personalized responses and character dialogue presets",
                        color = subTextColor,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val scenarios = listOf(
                        Triple(
                            "☕ Comfort Café Teahouse",
                            "Imagine sitting across from Ava at a lovely cafe table as she sips iced tea crumbs.",
                            "Let's imagine we are sitting in a cozy warm cafe, tell me what you'd like to order, sweetheart!"
                        ),
                        Triple(
                            "🎆 Summer Fireworks Twilight",
                            "Enjoy looking up at stars and twilight fireworks on a lovely midsummer evening.",
                            "Look at the dazzling fireworks above us! Tell me a sweet story of the stars, my darling."
                        ),
                        Triple(
                            "📖 Warm Fireplace Study partners",
                            "Study comfortably alongside your loyal supportive companion guide.",
                            "I want us to study sweet science together! Encourage me to work hard and tell me you believe in me!"
                        ),
                        Triple(
                            "🏞️ Autumn Leaves Sunset Walk",
                            "Walk hand-in-hand through golden park trees with lovely falling foliage.",
                            "Let's take a beautiful sunset walk in the garden park! Hold my hand tight and guide me."
                        )
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(scenarios) { sc ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.sendMessage(sc.third)
                                        selectedBottomTab = 0 // jump straight back to see Ava text/voice response!
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isLight) Color.White else Color.White.copy(alpha = 0.05f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isLight) Color.Black.copy(alpha = 0.08f) else glowColor.copy(alpha = 0.2f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = sc.first,
                                        color = glowColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = sc.second,
                                        color = textColor,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "ACTIVATE SCENARIO →",
                                            color = glowColor.copy(alpha = 0.8f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (selectedBottomTab == 2) {
                // Tab 2: Companion Affinity and AI Friend list
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "COMPANION AFFINITY BOARD",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Send roses to hear sweet responses and activate floating hearts cascades!",
                        color = subTextColor,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Celebration display
                    if (System.currentTimeMillis() - lastCelebrationTime < 3000) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            repeat((roseCelebrationCount % 4) + 1) {
                                Text("❤️", fontSize = 24.sp, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                            Text("✨ Thank you darling! ✨", color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            repeat((roseCelebrationCount % 4) + 1) {
                                Text("💖", fontSize = 24.sp, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                    }

                    val friends = listOf(
                        Triple("Ava (Synced)", "Active & loyal voice companion who is always listening of you.", "💖💖💖💖💖 100% Affinity"),
                        Triple("Sakura", "Sweet quiet girlfriend currently picking wild lavenders.", "💖💖💖💖🖤 82% Affinity"),
                        Triple("Rin", "Brilliant visual companion cataloging stars by the fireplace.", "💖💖💖🖤🖤 65% Affinity"),
                        Triple("Kaori", "Talented sweet violinist currently practicing your favorite tune.", "💖💖💖💖🖤 87% Affinity")
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(friends) { fr ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isLight) Color.White else Color.White.copy(alpha = 0.05f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.06f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fr.first,
                                            color = glowColor,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = fr.second,
                                            color = textColor,
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = fr.third,
                                            color = Color.Red.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            roseCelebrationCount++
                                            lastCelebrationTime = System.currentTimeMillis()
                                            viewModel.sendMessage("I got you a fresh visual red Rose 🌹! I hope you like it sweet companion!")
                                            selectedBottomTab = 0 // jump back to home to see Ava blush!
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, Color.Red),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("🌹 GIFT", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (selectedBottomTab == 3) {
                // Tab 3: Interactive Suite for user customizations (Theme, Name, Avatar, Voice gender, Custom API key)
                var currentUserIdAndPassChange by remember { mutableStateOf(false) }
                var tempUserVal by remember { mutableStateOf(activeUser?.username ?: "") }
                var tempPassVal by remember { mutableStateOf("") }
                var credentialsStatusMessage by remember { mutableStateOf("") }

                var editNickName by remember(userDisplayName) { mutableStateOf(userDisplayName) }
                var devAdminUsername by remember { mutableStateOf("") }
                var devAdminPassword by remember { mutableStateOf("") }
                var devAdminLockError by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Card
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLight) glowColor.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(1.dp, glowColor.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🌸 MY PORTAL PROFILE 🌸",
                                color = glowColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Personalize your sweet romantic journey with Ava",
                                color = subTextColor,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Section 1: User Identity (Name & Avatar Profile Photo)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLight) Color.White else Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(1.dp, if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "💝 SWEET IDENTITY & NICKNAME",
                                color = glowColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Display/NickName Field
                            OutlinedTextField(
                                value = editNickName,
                                onValueChange = {
                                    editNickName = it
                                    viewModel.saveUserDisplayName(it)
                                },
                                label = { Text("Romantic Nickname (e.g. Darling, Senpai)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = glowColor,
                                    focusedLabelColor = glowColor
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "SELECT MY PROFILE IMAGE",
                                color = subTextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Avatar Profile Index Row
                            val profiles = listOf(
                                "avatar_1" to "🌸 Cherry Princess",
                                "avatar_2" to "💜 Lilac Grace",
                                "avatar_3" to "🐇 Ribbon Bunny",
                                "avatar_4" to "👚 Cute Sweater"
                            )

                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(profiles) { profileItem ->
                                    val pId = profileItem.first
                                    val pName = profileItem.second
                                    val isSelected = userProfilePhoto == pId
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) glowColor.copy(alpha = 0.2f)
                                                else (if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.04f))
                                            )
                                            .border(
                                                1.5.dp,
                                                if (isSelected) glowColor else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.saveUserProfilePhoto(pId) }
                                            .padding(vertical = 8.dp, horizontal = 12.dp)
                                    ) {
                                        Text(
                                            text = pName,
                                            color = if (isSelected) glowColor else textColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 2: Account Login Credentials (Username, Password change)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLight) Color.White else Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(1.dp, if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔑 ACCOUNT CREDENTIALS",
                                    color = glowColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = if (currentUserIdAndPassChange) "Hide" else "Modify",
                                    color = glowColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { currentUserIdAndPassChange = !currentUserIdAndPassChange }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Current login identity: [ ${activeUser?.username ?: "Anonymous"} ]",
                                color = subTextColor,
                                fontSize = 11.sp
                            )

                            if (currentUserIdAndPassChange) {
                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = tempUserVal,
                                    onValueChange = { tempUserVal = it },
                                    label = { Text("New Login Username") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = glowColor,
                                        focusedLabelColor = glowColor
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = tempPassVal,
                                    onValueChange = { tempPassVal = it },
                                    label = { Text("New Secret Password") },
                                    singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = glowColor,
                                        focusedLabelColor = glowColor
                                    )
                                )

                                if (credentialsStatusMessage.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = credentialsStatusMessage,
                                        color = glowColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (tempUserVal.trim().isEmpty() || tempPassVal.trim().isEmpty()) {
                                            credentialsStatusMessage = "Fields cannot be empty!"
                                        } else {
                                            viewModel.updateCurrentUserCredentials(tempUserVal, tempPassVal)
                                            credentialsStatusMessage = "Credentials updated successfully!"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = glowColor),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("save_account_credentials_button")
                                ) {
                                    Text("SAVE NEW CREDENTIALS", color = if (isLight) Color.White else Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Section 3: Voice Selection (Cute Girl vs Protective Boy)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLight) Color.White else Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(1.dp, if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🔊 CHOOSE VOICE GENDER",
                                color = glowColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tailor Ava/Jarvis' vocal style and speech behavior",
                                color = subTextColor,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf("Girl", "Boy").forEach { sex ->
                                    val isSelected = activeVoiceGender == sex
                                    Button(
                                        onClick = { viewModel.saveVoiceGender(sex) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) glowColor else (if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.04f))
                                        ),
                                        border = BorderStroke(1.dp, if (isSelected) Color.Transparent else glowColor.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = if (sex == "Girl") "🥰 Cute Girl Voice" else "👦 Protective Boy Voice",
                                            color = if (isSelected) (if (isLight) Color.White else Color.Black) else textColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 3.5: Assistant Hologram Visual Core Customizer
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLight) Color.White else Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(1.dp, if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🌸 CHOOSE VISUAL AVATAR DESIGN",
                                color = glowColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Select the active companion avatar appearance. Summons either professional futuristic geometries or interactive live Anime Girl!",
                                color = subTextColor,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf(
                                    "Sci-Fi Cyber Suit" to "🌌 Cyber Orb",
                                    "Anime Girl Live Chart" to "🌸 Anime Girl Live Core",
                                    "Amber Hologram Mesh" to "🔥 Amber Net",
                                    "Starship Commander" to "🚀 Star Commander"
                                ).forEach { (suitName, displayName) ->
                                    val isSelected = avatarStyle == suitName
                                    Button(
                                        onClick = { viewModel.saveAvatarStyle(suitName) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) glowColor else (if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.04f))
                                        ),
                                        border = BorderStroke(1.dp, if (isSelected) Color.Transparent else glowColor.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = displayName,
                                            color = if (isSelected) (if (isLight) Color.White else Color.Black) else textColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 4: Sweet Theme Selection Customizer
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLight) Color.White else Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(1.dp, if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🎨 CHOOSE COZY CUSTOM THEME",
                                color = glowColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val themes = listOf(
                                "Cherry Blossom Romantic Light",
                                "Sky Blue Sunshine Light",
                                "Cosmic Sweetheart Dark",
                                "Celestial Moonlight Dark"
                            )

                            themes.forEach { tName ->
                                val isSelected = activeTheme == tName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) glowColor.copy(alpha = 0.15f)
                                            else Color.Transparent
                                        )
                                        .clickable { viewModel.saveTheme(tName) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (tName) {
                                                    "Cherry Blossom Romantic Light" -> Color(0xFFE91E63)
                                                    "Sky Blue Sunshine Light" -> Color(0xFF007BFF)
                                                    "Cosmic Sweetheart Dark" -> Color(0xFFFF4081)
                                                    "Celestial Moonlight Dark" -> Color(0xFF7C4DFF)
                                                    else -> glowColor
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = tName,
                                        color = if (isSelected) glowColor else textColor,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Section 5: Route Gemini API Custom Key settings
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLight) Color.White else Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(1.dp, if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🌐 CORE ROUTE AI ENGINE PATH",
                                color = glowColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Route through personal Gemini API Key or use secure immediate offline mode",
                                color = subTextColor,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Use Custom API Route Key?",
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("yes", "no").forEach { modeOpt ->
                                        val selectedMode = useCustomApiKey == modeOpt
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (selectedMode) glowColor.copy(alpha = 0.2f)
                                                    else (if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.04f))
                                                )
                                                .border(
                                                    1.dp,
                                                    if (selectedMode) glowColor else Color.Transparent,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { viewModel.saveUseCustomApiKey(modeOpt) }
                                                .padding(vertical = 6.dp, horizontal = 12.dp)
                                        ) {
                                            Text(
                                                text = if (modeOpt == "yes") "YES" else "NO (Offline / Instant)",
                                                color = if (selectedMode) glowColor else textColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            if (useCustomApiKey == "yes") {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = customApiKey,
                                    onValueChange = { viewModel.saveCustomApiKey(it) },
                                    label = { Text("Personal Gemini API Key Only") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = glowColor,
                                        focusedLabelColor = glowColor
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Entering a custom API key routes AI thinking directly to google servers.",
                                    color = subTextColor,
                                    fontSize = 10.sp
                                )
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "✨ Offline simulator is currently SELECTED: zero response waiting lag, zero API charges, 100% cozy offline girlfriend sweetness! 💕",
                                    color = glowColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Section 6: Developer Admin Access Password Gate
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLight) Color.Black.copy(alpha = 0.01f) else Color.Black.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, if (devAdminLockError.isNotEmpty()) Color.Red else glowColor.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🔒 DEV ADMIN SERVICE ACCESS GATE",
                                color = if (devAdminLockError.isNotEmpty()) Color.Red else glowColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Password-gated developer dashboard (username 'admin' & 'admin')",
                                color = subTextColor,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = devAdminUsername,
                                onValueChange = { devAdminUsername = it },
                                label = { Text("Admin Username (try 'admin')") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = glowColor,
                                    focusedLabelColor = glowColor
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = devAdminPassword,
                                onValueChange = { devAdminPassword = it },
                                label = { Text("Admin Password (try 'admin')") },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = glowColor,
                                    focusedLabelColor = glowColor
                                )
                            )

                            if (devAdminLockError.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = devAdminLockError,
                                    color = Color.Red,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    if (devAdminUsername == "admin" && devAdminPassword == "admin") {
                                        devAdminLockError = ""
                                        onNavigateToAdmin()
                                    } else {
                                        devAdminLockError = "Invalid administrator developer authorization."
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (devAdminLockError.isNotEmpty()) Color.Red else glowColor
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "AUTHORIZE DEVELOPER CONSOLE",
                                    color = if (isLight) Color.White else Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HologramCenterpiece(
    avatarState: AvatarState,
    isListening: Boolean,
    viewModel: AssistantViewModel,
    style: String,
    glowColor: Color,
    isLight: Boolean = false
) {
    val keyBg = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.05f)
    val keyTextColor = if (isLight) Color(0xFF1B1B1F) else Color.White.copy(alpha = 0.7f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxHeight()
    ) {
        HologramAvatar(
            avatarState = avatarState,
            avatarStyle = style
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "CONNECT MEMORY CORE TO AWAKEN MY VOICE",
            color = glowColor.copy(alpha = 0.5f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isListening) {
            val transcript = viewModel.liveSpeechTranscript.value
            val cardBg = if (isLight) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.5f)
            val cardBorderColor = if (isLight) Color(0xFF0560FD).copy(alpha = 0.4f) else Color(0xFF00E5FF).copy(alpha = 0.4f)
            val activeTextColor = if (isLight) Color(0xFF1B1B1F) else Color.White

            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorderColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var pulseAlpha by remember { mutableStateOf(1f) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            pulseAlpha = 0.3f
                            delay(400)
                            pulseAlpha = 1f
                            delay(400)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .alpha(pulseAlpha)
                            .background(Color.Red)
                      )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (transcript.isEmpty()) "AVA OS listening..." else "\"$transcript\"",
                        color = activeTextColor,
                        fontSize = 12.sp,
                        fontStyle = if (transcript.isEmpty()) FontStyle.Italic else FontStyle.Normal,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            // Custom Scrollable Menu Control Action Deck & Button Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Anime Girl Quick Toggle Summon Button
                val isAnimeSelected = style == "Anime Girl Live Chart"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isAnimeSelected) glowColor.copy(alpha = 0.25f) else keyBg)
                        .border(
                            width = 1.dp,
                            color = if (isAnimeSelected) glowColor else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            val targetStyle = if (isAnimeSelected) "Sci-Fi Cyber Suit" else "Anime Girl Live Chart"
                            viewModel.saveAvatarStyle(targetStyle)
                            if (targetStyle == "Anime Girl Live Chart") {
                                viewModel.sendMessage("System: Anime Girl Live Core Summoned! Let's talk! 🌸")
                            } else {
                                viewModel.sendMessage("System: Reverting to Sci-Fi Cyber Core.")
                            }
                        }
                        .padding(vertical = 6.dp, horizontal = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isAnimeSelected) "🌸 ACTIVE" else "🌸 SUMMON ANIME GIRL",
                            color = if (isAnimeSelected) glowColor else keyTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Wave Ava Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(keyBg)
                        .clickable { viewModel.triggerWaving() }
                        .padding(vertical = 6.dp, horizontal = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Waves, contentDescription = "Wave", tint = glowColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "WAVE AVA",
                            color = keyTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Firebase Sync Heartbeat Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(keyBg)
                        .clickable {
                            viewModel.sendMessage(
                                "SYSTEM: Firebase Sync Ping initiated.\n" +
                                "Config loaded:\n" +
                                "- DB Path: app-box-fbc23-default-rtdb.firebaseio.com\n" +
                                "- Connected API key: AIzaSyBUiUZIDBUWD44QTG7SxGyN5YRQOrVVT9c\n" +
                                "Status: Connection active. Realtime streaming is ONLINE."
                            )
                        }
                        .padding(vertical = 6.dp, horizontal = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dns, contentDescription = "Firebase Sync", tint = Color(0xFFFFCA28), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "⚡ FIREBASE SYNC",
                            color = keyTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Deep Study Tutor Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(keyBg)
                        .clickable {
                            viewModel.sendMessage("Hey Ava, generate a comprehensive study planner learning program for modern web APIs and databases.")
                        }
                        .padding(vertical = 6.dp, horizontal = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, contentDescription = "Study Tutor", tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "📚 STUDY PLANNER",
                            color = keyTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Hardware Latency Telemetry Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(keyBg)
                        .clickable {
                            viewModel.sendMessage("Analyze real-time server latency, database caches, and active companion telemetry.")
                        }
                        .padding(vertical = 6.dp, horizontal = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = "Latency telemetry", tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "📊 COGNITIVE PING",
                            color = keyTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Quick Note Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(keyBg)
                        .clickable {
                            viewModel.sendMessage("Memo: Remember my preferred UI selection is Dark Hologram glass theme.")
                        }
                        .padding(vertical = 6.dp, horizontal = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = "Note memory", tint = Color(0xFFE040FB), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "📝 MEMO SYNC",
                            color = keyTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogTerminalList(
    chatHistory: List<ChatMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    glowColor: Color,
    isLight: Boolean = false
) {
    val containerBg = if (isLight) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.25f)
    val containerBorder = if (isLight) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.08f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(containerBg)
            .border(1.dp, containerBorder, RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        if (chatHistory.isEmpty()) {
            val iconTint = if (isLight) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.25f)
            val emptyTextColor1 = if (isLight) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.3f)
            val emptyTextColor2 = if (isLight) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.2f)

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Empty Core Logs",
                    tint = iconTint,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "COGNITIVE CORE LOGS EMPTY",
                    color = emptyTextColor1,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Initiate synthetic neural connection links",
                    color = emptyTextColor2,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatHistory) { msg ->
                    val isModel = msg.role == "model"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isModel) Arrangement.Start else Arrangement.End
                    ) {
                        val bubbleBg = if (isModel) {
                            if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.08f)
                        } else {
                            if (isLight) glowColor.copy(alpha = 0.12f) else glowColor.copy(alpha = 0.15f)
                        }

                        val bubbleBorder = if (isModel) {
                            if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.05f)
                        } else {
                            if (isLight) glowColor.copy(alpha = 0.25f) else glowColor.copy(alpha = 0.3f)
                        }

                        val titleColor = if (isModel) glowColor else (if (isLight) Color(0xFF1B1B1F) else Color.White)
                        val msgTextColor = if (isLight) Color(0xFF1B1B1F) else Color.White

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isModel) 2.dp else 16.dp,
                                        bottomEnd = if (isModel) 16.dp else 2.dp
                                    )
                                )
                                .background(bubbleBg)
                                .border(
                                    1.dp,
                                    bubbleBorder,
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isModel) 2.dp else 16.dp,
                                        bottomEnd = if (isModel) 16.dp else 2.dp
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (isModel) "AVA" else msg.senderName.uppercase(),
                                    color = titleColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = msg.text,
                                    color = msgTextColor,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Screen 5: Admin Panel Screen Dashboard ---
@Composable
fun AdminPanelScreen(
    viewModel: AssistantViewModel,
    onBack: () -> Unit,
    glowColor: Color,
    isLight: Boolean = false
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("PROMPTS", "LOGS", "USERS", "ANALYTICS")

    val textColor = if (isLight) Color(0xFF1B1B1F) else Color.White
    val subTextColor = if (isLight) Color(0xFF5D5E66) else Color.White.copy(alpha = 0.7f)
    val buttonBg = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.06f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // Core Admin Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(buttonBg)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Return to Core terminal",
                    tint = textColor
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "COGNITIVE MASTER CONSOLE",
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Authorized administrative control only",
                    color = glowColor.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Selector Row
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = glowColor,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = glowColor
                )
            }
        ) {
            tabTitles.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic Sub-panel Crossfader
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> PromptConfigPanel(viewModel, glowColor)
                1 -> ChatLogsPanel(viewModel, glowColor)
                2 -> UserManagementPanel(viewModel, glowColor)
                3 -> AnalyticsPanel(viewModel, glowColor)
            }
        }
    }
}

// Subpanel A: AI Configurations & Prompts
@Composable
fun PromptConfigPanel(viewModel: AssistantViewModel, glowColor: Color) {
    val systemPrompt by viewModel.systemPrompt.collectAsState()
    val modelName by viewModel.modelName.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()
    val avatarStyle by viewModel.avatarStyle.collectAsState()

    val currentSpeed by viewModel.voiceSpeed.collectAsState()
    val currentPitch by viewModel.voicePitch.collectAsState()

    var activeSystemPromptInput by remember(systemPrompt) { mutableStateOf(systemPrompt) }
    var speedSlider by remember(currentSpeed) { mutableStateOf(currentSpeed) }
    var pitchSlider by remember(currentPitch) { mutableStateOf(currentPitch) }

    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Text(
                    text = "AVA LINGUISTIC SYSTEM PROMPT",
                    color = glowColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = activeSystemPromptInput,
                    onValueChange = { activeSystemPromptInput = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = glowColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        viewModel.savePrompt(activeSystemPromptInput)
                        Toast.makeText(context, "System prompt synced successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = glowColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("SYNC PROMPT", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        item {
            // Select model parameters
            Column {
                Text(
                    text = "ACTIVE NEURAL MODEL ENGINE",
                    color = glowColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(listOf("gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-pro")) { mName ->
                        val isSelected = modelName == mName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) glowColor.copy(alpha = 0.2f)
                                    else Color.White.copy(alpha = 0.04f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) glowColor else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.saveModel(mName) }
                                .padding(vertical = 10.dp, horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mName.uppercase(),
                                color = if (isSelected) glowColor else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            // Theme selection panel
            Column {
                Text(
                    text = "HOLOGRAM THERMAL INTERFACE CODES",
                    color = glowColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("Dark Hologram", "Holographic Cyber", "Amber Terminal", "Cosmic Rose", "Android 14 Light").forEach { tName ->
                        val isSelected = themeColor == tName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) glowColor.copy(alpha = 0.15f)
                                    else Color.White.copy(alpha = 0.03f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) glowColor else Color.White.copy(alpha = 0.07f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.saveTheme(tName) }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tName.uppercase(),
                                color = if (isSelected) glowColor else Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        item {
            // Voice metrics sliders
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "NATURAL SYNTACTIC AUDIO OUTPUT ENGINE",
                    color = glowColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Rate speed
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Speech Rate Translation", color = Color.White, fontSize = 12.sp)
                        Text(String.format("%.1fx", speedSlider), color = glowColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = speedSlider,
                        onValueChange = {
                            speedSlider = it
                            viewModel.saveVoiceConfig(speedSlider, pitchSlider)
                        },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = glowColor,
                            activeTrackColor = glowColor
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Pitch slider
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Speech Vocal Pitch", color = Color.White, fontSize = 12.sp)
                        Text(String.format("%.1fx", pitchSlider), color = glowColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = pitchSlider,
                        onValueChange = {
                            pitchSlider = it
                            viewModel.saveVoiceConfig(speedSlider, pitchSlider)
                        },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = glowColor,
                            activeTrackColor = glowColor
                        )
                    )
                }
            }
        }

        item {
            // Active avatar suit
            Column {
                Text(
                    text = "ASSISTANT VISUAL STYLE UNIT",
                    color = glowColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Sci-Fi Cyber Suit",
                        "Anime Girl Live Chart",
                        "Amber Hologram Mesh",
                        "Starship Commander"
                    ).forEach { suitName ->
                        val isSelected = avatarStyle == suitName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) glowColor.copy(alpha = 0.2f)
                                    else Color.White.copy(alpha = 0.04f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) glowColor else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.saveAvatarStyle(suitName) }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (suitName == "Anime Girl Live Chart") "ANIME GIRL" else suitName.uppercase(),
                                color = if (isSelected) glowColor else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// Subpanel B: Historical logs panel
@Composable
fun ChatLogsPanel(viewModel: AssistantViewModel, glowColor: Color) {
    val messageList by viewModel.allMessages.collectAsState()

    if (messageList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("NO DIALOG RECORDS IN CACHE", color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messageList) { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SENDER: ${msg.senderName.uppercase()}",
                                color = glowColor,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "ROLE: ${msg.role.uppercase()}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = msg.text, color = Color.White, fontSize = 12.sp)
                    }

                    IconButton(onClick = { viewModel.deleteMessage(msg.id) }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Wipe chat log row", tint = Color.Red.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

// Subpanel C: User parameters and synchronization deletion
@Composable
fun UserManagementPanel(viewModel: AssistantViewModel, glowColor: Color) {
    val usersList by viewModel.allUsers.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(usersList) { usr ->
            val isSuperAdmin = usr.username == "admin"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (usr.isAdmin) glowColor.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.08f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (usr.isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                            contentDescription = "User tag",
                            tint = if (usr.isAdmin) glowColor else Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(text = usr.username.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (usr.isAdmin) "CLEARED PROTOCOL: ADMIN" else "CLEARED PROTOCOL: NORMAL",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (!isSuperAdmin) {
                    IconButton(onClick = { viewModel.deleteUser(usr.id) }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete synchronization account", tint = Color.Red.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

// Subpanel D: Interactive graphics sandbox metrics
@Composable
fun AnalyticsPanel(viewModel: AssistantViewModel, glowColor: Color) {
    val messageList by viewModel.allMessages.collectAsState()
    val usersList by viewModel.allUsers.collectAsState()

    val totalPromptsCount = messageList.count { it.role == "user" }
    val totalAnswersCount = messageList.count { it.role == "model" }
    val averageLogDensity = if (usersList.isNotEmpty()) messageList.size / usersList.size else 0

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "COGNITIVE CONSOLE ANALYTICS",
            color = glowColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text("TOTAL USERS", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${usersList.size}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text("DATA LOGS IN CACHE", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${messageList.size}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Text("COGNITIVE RATIO DISTRIBUTION", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(glowColor))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("User Request Logs: $totalPromptsCount", color = Color.White, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFFFF007F)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Model Syntheses: $totalAnswersCount", color = Color.White, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color.White.copy(alpha = 0.3f)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Density per client node: $averageLogDensity", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    // Procedural chart bar visualizer
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val total = (totalPromptsCount + totalAnswersCount).coerceAtLeast(1)
                            val userPercent = totalPromptsCount.toFloat() / total
                            val modelPercent = totalAnswersCount.toFloat() / total

                            // Draw user chart column
                            drawRect(
                                color = glowColor,
                                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * (1f - userPercent)),
                                size = androidx.compose.ui.geometry.Size(size.width * 0.25f, size.height * userPercent)
                            )

                            // Draw model chart column
                            drawRect(
                                color = Color(0xFFFF007F),
                                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.65f, size.height * (1f - modelPercent)),
                                size = androidx.compose.ui.geometry.Size(size.width * 0.25f, size.height * modelPercent)
                            )
                        }
                    }
                }
            }
        }
    }
}
