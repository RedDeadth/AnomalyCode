package com.dynamictecnologies.anomalyproyect

import android.app.Application
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import kotlin.math.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import java.nio.ByteBuffer

@HiltViewModel
class HandGestureViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _gestureState = MutableStateFlow("Inicializando...")
    val gestureState: StateFlow<String> = _gestureState

    private val _landmarksCount = MutableStateFlow(0)
    val landmarksCount: StateFlow<Int> = _landmarksCount

    private val _confidence = MutableStateFlow(0f)
    val confidence: StateFlow<Float> = _confidence

    private val _debugInfo = MutableStateFlow("")
    val debugInfo: StateFlow<String> = _debugInfo

    private var handLandmarker: HandLandmarker? = null

    init {
        initializeHandLandmarker()
    }

    private fun initializeHandLandmarker() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .build()

                val options = HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinHandDetectionConfidence(0.7f)
                    .setMinHandPresenceConfidence(0.7f)
                    .setMinTrackingConfidence(0.5f)
                    .setNumHands(1)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result: HandLandmarkerResult, inputImage: MPImage ->
                        handleResults(result, inputImage)
                    }
                    .setErrorListener { error: RuntimeException ->
                        handleError(error)
                    }
                    .build()

                handLandmarker = HandLandmarker.createFromOptions(getApplication(), options)
                _gestureState.value = "Listo para detectar"
                Timber.d("HandLandmarker inicializado correctamente")

            } catch (e: Exception) {
                _gestureState.value = "Error al inicializar: ${e.message}"
                Timber.e(e, "Error inicializando HandLandmarker")
            }
        }
    }

    fun analyzeImage(imageProxy: ImageProxy) {
        val frameTime = imageProxy.imageInfo.timestamp / 1_000_000

        try {

            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()

            handLandmarker?.detectAsync(mpImage, frameTime)

        } catch (e: Exception) {
            _gestureState.value = "Error en análisis: ${e.message}"
            Timber.e(e, "Error analizando imagen")
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer // Y
        val vuBuffer = imageProxy.planes[2].buffer // V
        val uBuffer = imageProxy.planes[1].buffer // U

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()
        val uSize = uBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize + uSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)
        uBuffer.get(nv21, ySize + vuSize, uSize)

        val yuvImage = android.graphics.YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    // Callback para los resultados de MediaPipe
    private fun handleResults(result: HandLandmarkerResult, inputImage: MPImage) {
        viewModelScope.launch(Dispatchers.Main) {
            if (result.landmarks().isNotEmpty()) {
                // Obtener la primera mano
                val handLandmarks: List<NormalizedLandmark> = result.landmarks()[0]
                val correctedLandmarks = handLandmarks.map { landmark ->
                    NormalizedLandmark.create(1f - landmark.x(), landmark.y(), landmark.z())
                }

                val landmarks = handLandmarks

                _landmarksCount.value = correctedLandmarks.size

                // Obtener la confianza
                val handedness = result.handedness()
                _confidence.value = if (handedness.isNotEmpty()) {
                    handedness[0][0].score()
                } else {
                    0f
                }

                debugKeyPoints(correctedLandmarks)

                val gesture = detectImprovedGesture(correctedLandmarks)
                _gestureState.value = gesture.first
                _debugInfo.value = gesture.second

                Timber.d("Detectados ${correctedLandmarks.size} puntos, gesto: ${gesture.first}")
            } else {
                _gestureState.value = "No se detectó mano"
                _landmarksCount.value = 0
                _confidence.value = 0f
                _debugInfo.value = ""
            }
        }
    }

    // Callback para errores de MediaPipe
    private fun handleError(error: RuntimeException) {
        viewModelScope.launch(Dispatchers.Main) {
            _gestureState.value = "Error del detector: ${error.message}"
            Timber.e(error, "Error del detector HandLandmarker")
        }
    }

    private fun debugKeyPoints(landmarks: List<NormalizedLandmark>) {
        val wrist = landmarks[0]
        val thumbTip = landmarks[4]
        val indexTip = landmarks[8]
        val indexMcp = landmarks[5]

        Timber.d("=== DEBUG COORDENADAS ===")
        Timber.d("Muñeca: (${String.format(Locale.US, "%.3f", wrist.x())}, ${String.format(Locale.US, "%.3f", wrist.y())})")
        Timber.d("Pulgar tip: (${String.format(Locale.US, "%.3f", thumbTip.x())}, ${String.format(Locale.US, "%.3f", thumbTip.y())})")
        Timber.d("Índice tip: (${String.format(Locale.US, "%.3f", indexTip.x())}, ${String.format(Locale.US, "%.3f", indexTip.y())})")
        Timber.d("Índice MCP: (${String.format(Locale.US, "%.3f", indexMcp.x())}, ${String.format(Locale.US, "%.3f", indexMcp.y())})")
        Timber.d("Índice extendido (Lógica corregida): ${indexTip.y() < indexMcp.y()}")
        Timber.d("========================")
    }

    private fun detectImprovedGesture(landmarks: List<NormalizedLandmark>): Pair<String, String> {
        if (landmarks.size < 21) return Pair("Datos insuficientes", "")

        // Puntos clave según MediaPipe Hand Landmarks
        val wrist = landmarks[0]

        // Pulgar
        val thumbCmc = landmarks[1]
        val thumbMcp = landmarks[2]
        val thumbIp = landmarks[3]
        val thumbTip = landmarks[4]

        // Índice
        val indexMcp = landmarks[5]
        val indexPip = landmarks[6]
        val indexDip = landmarks[7]
        val indexTip = landmarks[8]

        // Medio
        val middleMcp = landmarks[9]
        val middlePip = landmarks[10]
        val middleDip = landmarks[11]
        val middleTip = landmarks[12]

        // Anular
        val ringMcp = landmarks[13]
        val ringPip = landmarks[14]
        val ringDip = landmarks[15]
        val ringTip = landmarks[16]

        // Meñique
        val pinkyMcp = landmarks[17]
        val pinkyPip = landmarks[18]
        val pinkyDip = landmarks[19]
        val pinkyTip = landmarks[20]

        // Detectar dedos extendidos con lógica mejorada
        val fingersExtended = detectFingersExtended(landmarks)
        val fingersUpCount = fingersExtended.count { it }

        // Info de debug más detallada
        val fingerNames = listOf("Pulgar", "Índice", "Medio", "Anular", "Meñique")
        val debugInfo = buildString {
            appendLine("=== DEBUG GESTOS ===")
            fingersExtended.forEachIndexed { index, extended ->
                appendLine("${fingerNames[index]}: ${if (extended) "↑" else "↓"}")
            }
            appendLine("Total extendidos: $fingersUpCount")
            appendLine("================")
        }

        // Detección de gestos específicos con mayor precisión
        val gesture = when {
            // Puño cerrado - todos los dedos doblados
            fingersUpCount == 0 -> "Puño cerrado"

            // Un dedo
            fingersUpCount == 1 -> {
                when {
                    fingersExtended[1] -> "Apuntando (índice)"
                    fingersExtended[0] -> "Pulgar arriba"
                    fingersExtended[2] -> "Dedo medio"
                    fingersExtended[3] -> "Dedo anular"
                    fingersExtended[4] -> "Meñique"
                    else -> "Un dedo"
                }
            }

            // Dos dedos
            fingersUpCount == 2 -> {
                when {
                    fingersExtended[1] && fingersExtended[2] -> detectVictoryOrPeace(indexTip, middleTip)
                    fingersExtended[0] && fingersExtended[1] -> "Pistola/L"
                    fingersExtended[0] && fingersExtended[4] -> "Shaka/Hang loose"
                    fingersExtended[1] && fingersExtended[4] -> "Rock on"
                    else -> "Dos dedos"
                }
            }

            // Tres dedos
            fingersUpCount == 3 -> {
                when {
                    fingersExtended[1] && fingersExtended[2] && fingersExtended[3] -> "Tres dedos (medio)"
                    fingersExtended[0] && fingersExtended[1] && fingersExtended[2] -> "Tres dedos (pulgar)"
                    else -> "Tres dedos"
                }
            }

            // Cuatro dedos
            fingersUpCount == 4 -> {
                if (!fingersExtended[0]) "Cuatro dedos (sin pulgar)"
                else "Cuatro dedos"
            }

            // Cinco dedos
            fingersUpCount == 5 -> detectOpenHandGesture(landmarks)

            else -> "Gesto desconocido"
        }

        return Pair(gesture, debugInfo)
    }

    private fun detectFingersExtended(landmarks: List<NormalizedLandmark>): List<Boolean> {
        val fingersExtended = mutableListOf<Boolean>()

        // Pulgar - usar distancia desde muñeca
        val thumbExtended = distance(landmarks[4], landmarks[0]) > distance(landmarks[3], landmarks[0])
        fingersExtended.add(thumbExtended)

        // Otros dedos - usar articulaciones intermedias, no solo Y
        val fingerData = listOf(
            Triple(8, 6, 5),   // Índice: tip, pip, mcp
            Triple(12, 10, 9), // Medio: tip, pip, mcp
            Triple(16, 14, 13),// Anular: tip, pip, mcp
            Triple(20, 18, 17) // Meñique: tip, pip, mcp
        )

        fingerData.forEach { (tip, pip, mcp) ->
            val tipToMcp = distance(landmarks[tip], landmarks[mcp])
            val pipToMcp = distance(landmarks[pip], landmarks[mcp])
            fingersExtended.add(tipToMcp > pipToMcp * 1.1f)
        }

        return fingersExtended
    }

    private fun isThumbExtended(landmarks: List<NormalizedLandmark>): Boolean {
        val thumbTip = landmarks[4]   // Punta del pulgar
        val thumbIp = landmarks[3]    // Articulación del pulgar
        val thumbMcp = landmarks[2]   // Base del pulgar
        val wrist = landmarks[0]      // Muñeca

        // Método 1: Comparar distancias desde la muñeca
        val tipToWristDist = distance(thumbTip, wrist)
        val mcpToWristDist = distance(thumbMcp, wrist)

        // Si el tip está más lejos de la muñeca que el MCP, está extendido
        val method1 = tipToWristDist > mcpToWristDist * 1.1

        // Método 2: Usar coordenada X (el pulgar se mueve horizontalmente)
        // Asumiendo mano derecha: pulgar extendido tiene X mayor que el MCP
        val method2 = abs(thumbTip.x() - thumbMcp.x()) > 0.04

        // Combinamos ambos métodos para mayor precisión
        return method1 && method2
    }

    private fun distance(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
        val dx = p1.x() - p2.x()
        val dy = p1.y() - p2.y()
        return sqrt(dx * dx + dy * dy)
    }

    private fun detectVictoryOrPeace(indexTip: NormalizedLandmark, middleTip: NormalizedLandmark): String {
        val distance = distance(indexTip, middleTip)
        return if (distance > 0.08f) "Victoria/Paz (V)" else "Dos dedos juntos"
    }

    private fun detectOpenHandGesture(landmarks: List<NormalizedLandmark>): String {
        // Verificar si los dedos están separados (mano abierta) o juntos
        val indexTip = landmarks[8]
        val middleTip = landmarks[12]
        val ringTip = landmarks[16]
        val pinkyTip = landmarks[20]

        val avgSpread = (
                distance(indexTip, middleTip) +
                        distance(middleTip, ringTip) +
                        distance(ringTip, pinkyTip)
                ) / 3

        return if (avgSpread > 0.06f) "Mano abierta" else "Cinco dedos juntos"
    }

    override fun onCleared() {
        super.onCleared()
        handLandmarker?.close()
    }
}