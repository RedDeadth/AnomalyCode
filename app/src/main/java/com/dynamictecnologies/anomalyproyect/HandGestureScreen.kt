package com.dynamictecnologies.anomalyproyect

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HandGestureScreen(
    modifier: Modifier = Modifier,
    viewModel: HandGestureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    val gestureState by viewModel.gestureState.collectAsState()
    val landmarksCount by viewModel.landmarksCount.collectAsState()
    val confidence by viewModel.confidence.collectAsState()

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        if (cameraPermissionState.status.isGranted) {
            // Vista de la cámara
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CameraPreview(
                    onImageAnalysis = { imageProxy ->
                        viewModel.analyzeImage(imageProxy)
                    }
                )

                // Overlay con información
                GestureInfoOverlay(
                    gestureState = gestureState,
                    landmarksCount = landmarksCount,
                    confidence = confidence,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
        } else {
            // Pantalla de permisos
            PermissionScreen(
                onRequestPermission = {
                    cameraPermissionState.launchPermissionRequest()
                }
            )
        }
    }
}

@Composable
fun PermissionScreen(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Se necesita acceso a la cámara para detectar gestos",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Conceder permiso")
        }
    }
}

@Composable
fun GestureInfoOverlay(
    gestureState: String,
    landmarksCount: Int,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Estado: $gestureState",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Puntos detectados: $landmarksCount",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Confianza: ${String.format("%.2f", confidence)}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}