package com.taverndream.yedu

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taverndream.yedu.ui.GalgameTheme
import com.taverndream.yedu.ui.LibraryScreen
import com.taverndream.yedu.ui.PlayerScreen
import com.taverndream.yedu.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            GalgameTheme {
                GalgameApp()
            }
        }
    }
}

private enum class Screen { Library, Player, Settings }

@Composable
private fun GalgameApp(vm: GalgameViewModel = viewModel()) {
    var screen by remember { mutableStateOf(Screen.Library) }
    val snackbar = remember { SnackbarHostState() }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importScript(uri) }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.importSerial) {
        if (vm.importSerial > 0) screen = Screen.Player
    }
    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            snackbar.showSnackbar(it)
            vm.toast = null
        }
    }
    LaunchedEffect(vm.error) {
        if (vm.error != null && screen == Screen.Player) screen = Screen.Library
    }

    BackHandler(enabled = screen != Screen.Library) {
        screen = Screen.Library
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
            },
            label = "galgame-screen",
        ) { target ->
            when (target) {
                Screen.Library -> LibraryScreen(
                    vm = vm,
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onContinue = { screen = Screen.Player },
                    onSettings = { screen = Screen.Settings },
                )
                Screen.Player -> PlayerScreen(
                    vm = vm,
                    onBack = { screen = Screen.Library },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onSettings = { screen = Screen.Settings },
                )
                Screen.Settings -> SettingsScreen(
                    vm = vm,
                    onBack = { screen = Screen.Library },
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}