package com.example.projeto_grupo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.projeto_grupo.ui.EquationViewModel
import com.example.projeto_grupo.ui.ar.ARSceneScreen
import com.example.projeto_grupo.ui.auth.AuthScreen
import com.example.projeto_grupo.ui.EquationBottomSheet
import com.example.projeto_grupo.ui.home.HomeScreen
import com.example.projeto_grupo.ui.theme.Projeto_grupoTheme

class MainActivity : ComponentActivity() {
    private var hasCameraPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            Projeto_grupoTheme {
                Projeto_grupoApp(
                    hasCameraPermission = hasCameraPermission,
                    onRequestCameraPermission = {
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onOpenAppSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun Projeto_grupoApp(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.AR_VIEWER) }
    val viewModel: EquationViewModel = viewModel()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (currentDestination) {
                    AppDestinations.AR_VIEWER -> {
                        if (hasCameraPermission) {
                            ARSceneScreen(viewModel = viewModel)
                            EquationBottomSheet(viewModel = viewModel)
                        } else {
                            CameraPermissionRequiredCard(
                                onRequestPermission = onRequestCameraPermission,
                                onOpenSettings = onOpenAppSettings
                            )
                        }
                    }
                    AppDestinations.FAVORITES -> {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToAR = { currentDestination = AppDestinations.AR_VIEWER },
                            onNavigateToProfile = { currentDestination = AppDestinations.PROFILE }
                        )
                    }
                    AppDestinations.PROFILE -> {
                        AuthScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionRequiredCard(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Permissão de Câmara Necessária",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Para visualizar equações em Realidade Aumentada, a aplicação precisa de aceder à câmara do dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Permitir Câmara")
                }
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Abrir Definições da App")
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    AR_VIEWER("RA Equação", R.drawable.ic_home),
    FAVORITES("Biblioteca", R.drawable.ic_favorite),
    PROFILE("Perfil", R.drawable.ic_account_box),
}
