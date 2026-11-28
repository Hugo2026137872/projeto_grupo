package com.example.projeto_grupo.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projeto_grupo.ui.EquationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: EquationViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedAuthTab by remember { mutableIntStateOf(0) } // 0 = Login, 1 = Register

    var serverUrlText by remember(uiState.baseUrlInput) { mutableStateOf(uiState.baseUrlInput) }
    var loginIdentifier by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }

    var regUsername by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Header ---
        Text(
            text = "Perfil & Servidor API",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // --- Server Configuration Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Servidor FastApi (3D AR)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    val (statusColor, statusText) = when (uiState.isServerConnected) {
                        true -> Color(0xFF4CAF50) to "Conectado"
                        false -> MaterialTheme.colorScheme.error to "Sem Conexão"
                        null -> Color.Gray to "A verificar..."
                    }

                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.15f),
                        contentColor = statusColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = serverUrlText,
                        onValueChange = { serverUrlText = it },
                        label = { Text("URL do Servidor") },
                        placeholder = { Text("http://10.0.2.2:8000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.updateBaseUrl(serverUrlText) }
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.updateBaseUrl(serverUrlText) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Testar ligação"
                        )
                    }
                }
            }
        }

        // --- Feedback Banners ---
        AnimatedVisibility(visible = uiState.authErrorMessage != null) {
            uiState.authErrorMessage?.let { errMsg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = errMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearAuthFeedback() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar mensagem",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = uiState.authSuccessMessage != null) {
            uiState.authSuccessMessage?.let { succMsg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32)
                        )
                        Text(
                            text = succMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF1B5E20),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearAuthFeedback() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = Color(0xFF1B5E20)
                            )
                        }
                    }
                }
            }
        }

        // --- Logged In View OR Auth Forms ---
        if (uiState.isLoggedIn && uiState.currentUser != null) {
            val user = uiState.currentUser!!

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.username.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = user.username,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    // Personal API Key Section
                    val context = LocalContext.current
                    var apiKeyVisible by remember { mutableStateOf(false) }
                    val currentApiKey = uiState.userApiKey

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "API Key Pessoal (Controlo Externo)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!currentApiKey.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (apiKeyVisible) currentApiKey else "${currentApiKey.take(8)}••••••••••••••••",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                        Icon(
                                            imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (apiKeyVisible) "Ocultar API Key" else "Mostrar API Key"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("API Key", currentApiKey))
                                            Toast.makeText(context, "API Key copiada!", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copiar API Key"
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Gere uma API Key para alterar variáveis da nuvem via API.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.rotateApiKey() },
                            enabled = !uiState.authLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (currentApiKey.isNullOrBlank()) "Gerar API Key" else "Rodar API Key")
                        }
                    }

                    HorizontalDivider()

                    Button(
                        onClick = { showLogoutDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Terminar Sessão")
                    }
                }
            }

        } else {
            // --- Register & Login Form Tabs ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TabRow(
                        selectedTabIndex = selectedAuthTab,
                        containerColor = Color.Transparent
                    ) {
                        Tab(
                            selected = selectedAuthTab == 0,
                            onClick = {
                                selectedAuthTab = 0
                                viewModel.clearAuthFeedback()
                            },
                            text = { Text("Iniciar Sessão") }
                        )
                        Tab(
                            selected = selectedAuthTab == 1,
                            onClick = {
                                selectedAuthTab = 1
                                viewModel.clearAuthFeedback()
                            },
                            text = { Text("Registar Conta") }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (selectedAuthTab == 0) {
                            // --- LOGIN FORM ---
                            Text(
                                text = "Bem-vindo de volta!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            OutlinedTextField(
                                value = loginIdentifier,
                                onValueChange = { loginIdentifier = it },
                                label = { Text("Utilizador ou Email") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = loginPassword,
                                onValueChange = { loginPassword = it },
                                label = { Text("Palavra-passe") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (passwordVisible) "Ocultar" else "Mostrar"
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    viewModel.loginUser(loginIdentifier, loginPassword)
                                },
                                enabled = !uiState.authLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                if (uiState.authLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Login,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Entrar")
                                }
                            }

                        } else {
                            // --- REGISTER FORM ---
                            Text(
                                text = "Criar Nova Conta",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            OutlinedTextField(
                                value = regUsername,
                                onValueChange = { regUsername = it },
                                label = { Text("Nome de Utilizador") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Badge,
                                        contentDescription = null
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = regEmail,
                                onValueChange = { regEmail = it },
                                label = { Text("Email") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = regPassword,
                                onValueChange = { regPassword = it },
                                label = { Text("Palavra-passe (min 6 carateres)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (passwordVisible) "Ocultar" else "Mostrar"
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    viewModel.registerUser(regUsername, regEmail, regPassword)
                                },
                                enabled = !uiState.authLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                if (uiState.authLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Criar Conta")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Terminar Sessão") },
            text = { Text("Tem a certeza de que pretende terminar a sessão?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logoutUser()
                    }
                ) {
                    Text("Sair", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
