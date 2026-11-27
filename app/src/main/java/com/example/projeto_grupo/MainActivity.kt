package com.example.projeto_grupo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.projeto_grupo.ui.EquationViewModel
import com.example.projeto_grupo.ui.ar.ARSceneScreen
import com.example.projeto_grupo.ui.EquationBottomSheet
import com.example.projeto_grupo.ui.home.HomeScreen
import com.example.projeto_grupo.ui.theme.Projeto_grupoTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Handle camera permission result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            Projeto_grupoTheme {
                Projeto_grupoApp()
            }
        }
    }
}

@Composable
fun Projeto_grupoApp() {
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
                        ARSceneScreen(viewModel = viewModel)
                        EquationBottomSheet(viewModel = viewModel)
                    }
                    AppDestinations.FAVORITES -> {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToAR = { currentDestination = AppDestinations.AR_VIEWER }
                        )
                    }
                    AppDestinations.PROFILE -> {
                        Greeting(name = "Perfil")
                    }
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
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
