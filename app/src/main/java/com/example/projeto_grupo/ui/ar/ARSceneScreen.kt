package com.example.projeto_grupo.ui.ar

import android.content.Context
import android.graphics.Color as AndroidColor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.projeto_grupo.data.model.EquationModel
import com.example.projeto_grupo.data.model.PointType
import com.example.projeto_grupo.ui.EquationViewModel
import com.example.projeto_grupo.ui.MathExpressionEvaluator
import com.example.projeto_grupo.ui.MathFormulaView
import com.example.projeto_grupo.ui.components.PlacementEquationDialog
import com.example.projeto_grupo.ui.components.ScientificCalculatorKeyboard
import com.example.projeto_grupo.ui.components.backspaceAtSelection
import com.example.projeto_grupo.ui.components.insertTokenAtSelection
import com.example.projeto_grupo.ui.components.moveSelectionCursor
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.VertexBuffer
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val PREFS_NAME = "ar_equation_markerless_offline"
private const val KEY_HAS_MODEL = "has_saved_model"
private const val KEY_SCALE = "saved_scale"
private const val KEY_QX = "saved_qx"
private const val KEY_QY = "saved_qy"
private const val KEY_QZ = "saved_qz"
private const val KEY_QW = "saved_qw"

// Saved real-world placement signature (markerless & offline)
private const val KEY_HAS_LOCATION = "has_saved_location"
private const val KEY_NORTH_OFFSET = "saved_north_offset"
private const val KEY_EAST_OFFSET = "saved_east_offset"
private const val KEY_CAMERA_DROP_Y = "saved_camera_drop_y"
private const val KEY_PLACED_AZIMUTH = "saved_placed_azimuth"

// Shared 3D graph dimensions (Filament Y-up: mathX -> worldX, mathY -> worldZ, mathZ = f(x, y) -> worldY)
private const val GRAPH_BASE_ELEVATION = 0.14f
private const val GRAPH_HALF_SPAN = 0.18f
private const val GRAPH_VERTICAL_SCALE = 0.075f

private enum class AnnotationKind {
    HEADER, AXIS, TICK_X, TICK_Y, TICK_Z, MAXIMA, MINIMA, ZERO
}

private data class PendingArPlacement(
    val anchor: Anchor,
    val hitPose: Pose,
    val cameraPose: Pose
)

private data class ProjectedAnnotation(
    val id: String,
    val text: String,
    val subText: String? = null,
    val screenX: Float,
    val screenY: Float,
    val kind: AnnotationKind
)

@Composable
fun ARSceneScreen(
    viewModel: EquationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val uiState by viewModel.uiState.collectAsState()
    val latestUiState by rememberUpdatedState(uiState)

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberARCameraNode(engine)
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 100_000f
        lightDirection = Direction(0.35f, -0.85f, -0.35f)
    }
    val childNodes = rememberNodes()

    // Track rear-camera horizontal compass azimuth (radians) with tilt compensation
    var currentAzimuthRad by remember { mutableFloatStateOf(0f) }
    var hasAzimuthReading by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        remappedMatrix
                    )
                    SensorManager.getOrientation(remappedMatrix, orientationAngles)
                    currentAzimuthRad = orientationAngles[0]
                    hasAzimuthReading = true
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Saved state loaded from device storage
    var hasStoredModel by remember { mutableStateOf(prefs.getBoolean(KEY_HAS_MODEL, false)) }
    var hasStoredLocation by remember { mutableStateOf(prefs.getBoolean(KEY_HAS_LOCATION, false)) }

    var targetScale by remember { mutableFloatStateOf(prefs.getFloat(KEY_SCALE, 1.0f)) }
    var smoothScale by remember { mutableFloatStateOf(targetScale) }

    var targetQuaternion by remember {
        mutableStateOf(
            Quaternion(
                x = prefs.getFloat(KEY_QX, 0f),
                y = prefs.getFloat(KEY_QY, 0f),
                z = prefs.getFloat(KEY_QZ, 0f),
                w = prefs.getFloat(KEY_QW, 1f)
            )
        )
    }
    var smoothQuaternion by remember { mutableStateOf(targetQuaternion) }

    var frame by remember { mutableStateOf<Frame?>(null) }
    var modelPlaced by remember { mutableStateOf(false) }
    var isTrackingLost by remember { mutableStateOf(false) }
    var anchorNodeRef by remember { mutableStateOf<AnchorNode?>(null) }
    var equationNodeRef by remember { mutableStateOf<Node?>(null) }
    var activeZHalfRange by remember { mutableDoubleStateOf(2.0) }
    val latestZHalfRange by rememberUpdatedState(activeZHalfRange)

    // Live 2D screen projections of 3D equation pins, axes, and formula banner
    var projectedAnnotations by remember { mutableStateOf<List<ProjectedAnnotation>>(emptyList()) }
    val viewMatrix = remember { FloatArray(16) }
    val projMatrix = remember { FloatArray(16) }

    // Auto-restore timing & manual override state
    var trackingStartedMs by remember { mutableLongStateOf(0L) }
    var autoRestoreStopped by remember { mutableStateOf(false) }

    var isScaling by remember { mutableStateOf(false) }
    var lastPinchSpan by remember { mutableFloatStateOf(0f) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var pendingPlacement by remember { mutableStateOf<PendingArPlacement?>(null) }

    fun saveTransformOnly() {
        prefs.edit()
            .putBoolean(KEY_HAS_MODEL, true)
            .putFloat(KEY_SCALE, targetScale)
            .putFloat(KEY_QX, targetQuaternion.x)
            .putFloat(KEY_QY, targetQuaternion.y)
            .putFloat(KEY_QZ, targetQuaternion.z)
            .putFloat(KEY_QW, targetQuaternion.w)
            .apply()
        hasStoredModel = true
    }

    fun savePlacementSignature(hitPose: Pose, camPose: Pose) {
        val dx = hitPose.tx() - camPose.tx()
        val dz = hitPose.tz() - camPose.tz()
        val cameraDropY = camPose.ty() - hitPose.ty()

        val zAxis = camPose.zAxis
        val xAxis = camPose.xAxis
        val fwdX = -zAxis[0]
        val fwdZ = -zAxis[2]
        val fwdLen = sqrt(fwdX * fwdX + fwdZ * fwdZ).coerceAtLeast(0.001f)
        val normFwdX = fwdX / fwdLen
        val normFwdZ = fwdZ / fwdLen

        val rightX = xAxis[0]
        val rightZ = xAxis[2]
        val rightLen = sqrt(rightX * rightX + rightZ * rightZ).coerceAtLeast(0.001f)
        val normRightX = rightX / rightLen
        val normRightZ = rightZ / rightLen

        val relForward = dx * normFwdX + dz * normFwdZ
        val relRight = dx * normRightX + dz * normRightZ

        val azimuth = currentAzimuthRad
        val northOffset = relForward * cos(azimuth) - relRight * sin(azimuth)
        val eastOffset = relForward * sin(azimuth) + relRight * cos(azimuth)

        prefs.edit()
            .putBoolean(KEY_HAS_MODEL, true)
            .putBoolean(KEY_HAS_LOCATION, true)
            .putFloat(KEY_NORTH_OFFSET, northOffset)
            .putFloat(KEY_EAST_OFFSET, eastOffset)
            .putFloat(KEY_CAMERA_DROP_Y, cameraDropY)
            .putFloat(KEY_PLACED_AZIMUTH, azimuth)
            .putFloat(KEY_SCALE, targetScale)
            .putFloat(KEY_QX, targetQuaternion.x)
            .putFloat(KEY_QY, targetQuaternion.y)
            .putFloat(KEY_QZ, targetQuaternion.z)
            .putFloat(KEY_QW, targetQuaternion.w)
            .apply()
        hasStoredModel = true
        hasStoredLocation = true
    }

    fun attachModelToAnchor(
        anchor: Anchor,
        yawCompensationRad: Float = 0f,
        equation: EquationModel = uiState.equation
    ) {
        anchorNodeRef?.detachAnchor()
        childNodes.clear()

        val adjustedQuat = if (abs(yawCompensationRad) > 0.01f) {
            val yawFix = quaternionFromAxisRadians(Float3(0f, 1f, 0f), yawCompensationRad)
            normalizeQuaternion(yawFix * targetQuaternion)
        } else {
            targetQuaternion
        }
        targetQuaternion = adjustedQuat

        val gridData = computeSurfaceGridData(equation, uiState.xDomain, uiState.yDomain)
        activeZHalfRange = gridData.zHalfRange

        val anchorNode = AnchorNode(engine = engine, anchor = anchor).apply {
            isPositionEditable = false
            isRotationEditable = false
            isScaleEditable = false
        }
        val equationNode = createEquation3DNode(
            engine = engine,
            materialLoader = materialLoader,
            equation = equation,
            gridData = gridData,
            showMaxima = uiState.showMaxima,
            showMinima = uiState.showMinima,
            showZeros = uiState.showZeros,
            xDomain = uiState.xDomain,
            yDomain = uiState.yDomain
        ).apply {
            scale = Scale(targetScale)
            quaternion = adjustedQuat
        }

        anchorNode.addChildNode(equationNode)
        childNodes += anchorNode

        anchorNodeRef = anchorNode
        equationNodeRef = equationNode
        smoothScale = targetScale
        smoothQuaternion = adjustedQuat
        isTrackingLost = false
        autoRestoreStopped = true
        modelPlaced = true
        saveTransformOnly()
    }

    // Live-rebuild the fluid 3D surface & POI pins whenever equation, domain, or AR UI toggles change
    LaunchedEffect(
        modelPlaced,
        uiState.equation,
        uiState.showMaxima,
        uiState.showMinima,
        uiState.showZeros,
        uiState.xDomain,
        uiState.yDomain
    ) {
        val currentAnchorNode = anchorNodeRef
        if (modelPlaced && currentAnchorNode != null) {
            val gridData = withContext(Dispatchers.Default) {
                computeSurfaceGridData(uiState.equation, uiState.xDomain, uiState.yDomain)
            }
            activeZHalfRange = gridData.zHalfRange

            equationNodeRef?.let { oldNode ->
                currentAnchorNode.removeChildNode(oldNode)
                oldNode.destroy()
            }
            val updatedNode = createEquation3DNode(
                engine = engine,
                materialLoader = materialLoader,
                equation = uiState.equation,
                gridData = gridData,
                showMaxima = uiState.showMaxima,
                showMinima = uiState.showMinima,
                showZeros = uiState.showZeros,
                xDomain = uiState.xDomain,
                yDomain = uiState.yDomain
            ).apply {
                scale = Scale(smoothScale)
                quaternion = smoothQuaternion
            }
            currentAnchorNode.addChildNode(updatedNode)
            equationNodeRef = updatedNode
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val screenHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            cameraNode = cameraNode,
            mainLightNode = mainLightNode,
            planeRenderer = !modelPlaced || isTrackingLost,
            childNodes = childNodes,
            sessionConfiguration = { _, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.focusMode = Config.FocusMode.AUTO
                config.depthMode = Config.DepthMode.DISABLED
            },
            onSessionUpdated = { session, updatedFrame ->
                frame = updatedFrame

                if (updatedFrame.camera.trackingState == TrackingState.TRACKING && trackingStartedMs == 0L) {
                    trackingStartedMs = System.currentTimeMillis()
                }

                // 1. Offline markerless restore: match compass direction + detected surface height
                if (hasStoredModel && hasStoredLocation && !modelPlaced && !autoRestoreStopped &&
                    updatedFrame.camera.trackingState == TrackingState.TRACKING &&
                    hasAzimuthReading
                ) {
                    val elapsedMs = System.currentTimeMillis() - trackingStartedMs
                    if (elapsedMs > 5000L) {
                        autoRestoreStopped = true
                    } else if (elapsedMs >= 400L) {
                        val placedAzimuth = prefs.getFloat(KEY_PLACED_AZIMUTH, currentAzimuthRad)
                        val headingDiff = normalizeAngleRad(currentAzimuthRad - placedAzimuth)

                        if (abs(headingDiff) <= 0.78f) {
                            val northOffset = prefs.getFloat(KEY_NORTH_OFFSET, 0f)
                            val eastOffset = prefs.getFloat(KEY_EAST_OFFSET, 0f)
                            val savedDropY = prefs.getFloat(KEY_CAMERA_DROP_Y, 0.5f)

                            val azimuth = currentAzimuthRad
                            val newForward = northOffset * cos(azimuth) + eastOffset * sin(azimuth)
                            val newRight = -northOffset * sin(azimuth) + eastOffset * cos(azimuth)

                            val camPose = updatedFrame.camera.pose
                            val zAxis = camPose.zAxis
                            val xAxis = camPose.xAxis

                            val fwdX = -zAxis[0]
                            val fwdZ = -zAxis[2]
                            val fwdLen = sqrt(fwdX * fwdX + fwdZ * fwdZ).coerceAtLeast(0.001f)

                            val rightX = xAxis[0]
                            val rightZ = xAxis[2]
                            val rightLen = sqrt(rightX * rightX + rightZ * rightZ).coerceAtLeast(0.001f)

                            val candidateX = camPose.tx() + (fwdX / fwdLen) * newForward + (rightX / rightLen) * newRight
                            val candidateZ = camPose.tz() + (fwdZ / fwdLen) * newForward + (rightZ / rightLen) * newRight

                            val matchingPlane = session.getAllTrackables(Plane::class.java)
                                .firstOrNull { plane ->
                                    if (plane.trackingState != TrackingState.TRACKING || plane.subsumedBy != null) {
                                        return@firstOrNull false
                                    }
                                    val actualDropY = camPose.ty() - plane.centerPose.ty()
                                    val dropDiff = abs(actualDropY - savedDropY)

                                    val dxPlane = plane.centerPose.tx() - candidateX
                                    val dzPlane = plane.centerPose.tz() - candidateZ
                                    val horizDist = sqrt(dxPlane * dxPlane + dzPlane * dzPlane)
                                    val maxReach = (maxOf(plane.extentX, plane.extentZ) * 0.5f) + 0.35f

                                    dropDiff <= 0.18f && horizDist <= maxReach
                                }

                            if (matchingPlane != null) {
                                val snappedPose = Pose.makeTranslation(
                                    candidateX,
                                    matchingPlane.centerPose.ty(),
                                    candidateZ
                                )
                                val anchor = runCatching {
                                    matchingPlane.createAnchor(snappedPose)
                                }.getOrNull()

                                if (anchor != null) {
                                    attachModelToAnchor(anchor, yawCompensationRad = headingDiff)
                                }
                            }
                        }
                    }
                }

                // 2. Monitor tracking state if the phone is placed down or covered
                if (modelPlaced) {
                    when (anchorNodeRef?.anchor?.trackingState) {
                        TrackingState.TRACKING -> isTrackingLost = false
                        TrackingState.PAUSED -> isTrackingLost = true
                        TrackingState.STOPPED, null -> {
                            anchorNodeRef?.detachAnchor()
                            childNodes.clear()
                            anchorNodeRef = null
                            equationNodeRef = null
                            modelPlaced = false
                            isTrackingLost = false
                            autoRestoreStopped = true
                            projectedAnnotations = emptyList()
                        }
                    }
                }

                // 3. Smoothly interpolate scale and 3D orientation every frame
                val eqNode = equationNodeRef
                if (eqNode != null) {
                    if (abs(targetScale - smoothScale) > 0.0005f) {
                        smoothScale += (targetScale - smoothScale) * 0.25f
                        eqNode.scale = Scale(smoothScale)
                    }
                    smoothQuaternion = nlerpQuaternion(smoothQuaternion, targetQuaternion, 0.25f)
                    eqNode.quaternion = smoothQuaternion
                }

                // 4. Project 3D equation header, axes (+X, +Y, +Z), and POI pins onto screen coordinates
                if (modelPlaced && !isTrackingLost && eqNode != null &&
                    updatedFrame.camera.trackingState == TrackingState.TRACKING
                ) {
                    updatedFrame.camera.getViewMatrix(viewMatrix, 0)
                    updatedFrame.camera.getProjectionMatrix(projMatrix, 0, 0.05f, 100f)

                    val eqWorldPos = eqNode.worldPosition
                    val eqWorldQuat = eqNode.worldQuaternion

                    fun projectLocal(localPt: Float3): Pair<Float, Float>? {
                        val scaled = Float3(
                            localPt.x * smoothScale,
                            localPt.y * smoothScale,
                            localPt.z * smoothScale
                        )
                        val rotated = rotateVectorByQuaternion(scaled, eqWorldQuat)
                        val worldPt = Float3(
                            eqWorldPos.x + rotated.x,
                            eqWorldPos.y + rotated.y,
                            eqWorldPos.z + rotated.z
                        )
                        return projectWorldToScreenExact(
                            worldPoint = worldPt,
                            viewMatrix = viewMatrix,
                            projMatrix = projMatrix,
                            screenWidth = screenWidthPx,
                            screenHeight = screenHeightPx
                        )
                    }

                    val currentUi = latestUiState
                    val nextAnnotations = ArrayList<ProjectedAnnotation>(48)

                    // Floating mathematical equation banner anchored directly above the vertical +Z axis
                    projectLocal(Float3(0f, GRAPH_BASE_ELEVATION + 0.195f, 0f))?.let { (sx, sy) ->
                        nextAnnotations += ProjectedAnnotation(
                            id = "eq_header",
                            text = currentUi.equation.expression,
                            screenX = sx,
                            screenY = sy,
                            kind = AnnotationKind.HEADER
                        )
                    }

                    // 3D Axis tip labels (math X -> Filament X, math Y -> Filament Z, math Z -> Filament Y)
                    projectLocal(Float3(0.23f, GRAPH_BASE_ELEVATION, 0f))?.let { (sx, sy) ->
                        nextAnnotations += ProjectedAnnotation("axis_x", "+X", null, sx, sy, AnnotationKind.AXIS)
                    }
                    projectLocal(Float3(0f, GRAPH_BASE_ELEVATION, 0.23f))?.let { (sx, sy) ->
                        nextAnnotations += ProjectedAnnotation("axis_y", "+Y", null, sx, sy, AnnotationKind.AXIS)
                    }
                    projectLocal(Float3(0f, GRAPH_BASE_ELEVATION + 0.165f, 0f))?.let { (sx, sy) ->
                        nextAnnotations += ProjectedAnnotation("axis_z", "+Z", null, sx, sy, AnnotationKind.AXIS)
                    }

                    // Dynamic axis tick numbers for X, Y (controlled by sliders) and Z
                    axisTickValues(currentUi.xDomain).forEach { value ->
                        val localX = ((value / currentUi.xDomain) * GRAPH_HALF_SPAN).toFloat()
                        projectLocal(Float3(localX, GRAPH_BASE_ELEVATION - 0.014f, 0f))?.let { (sx, sy) ->
                            nextAnnotations += ProjectedAnnotation(
                                id = "tick_x_$value",
                                text = formatAxisNumber(value),
                                screenX = sx,
                                screenY = sy,
                                kind = AnnotationKind.TICK_X
                            )
                        }
                    }

                    axisTickValues(currentUi.yDomain).forEach { value ->
                        val localZ = ((value / currentUi.yDomain) * GRAPH_HALF_SPAN).toFloat()
                        projectLocal(Float3(0f, GRAPH_BASE_ELEVATION - 0.014f, localZ))?.let { (sx, sy) ->
                            nextAnnotations += ProjectedAnnotation(
                                id = "tick_y_$value",
                                text = formatAxisNumber(value),
                                screenX = sx,
                                screenY = sy,
                                kind = AnnotationKind.TICK_Y
                            )
                        }
                    }

                    val zHalfRange = latestZHalfRange
                    listOf(-zHalfRange, -zHalfRange / 2.0, zHalfRange / 2.0, zHalfRange).forEach { value ->
                        val localY = GRAPH_BASE_ELEVATION + (value / zHalfRange).toFloat() * (2.0f * GRAPH_VERTICAL_SCALE)
                        projectLocal(Float3(0.016f, localY, 0f))?.let { (sx, sy) ->
                            nextAnnotations += ProjectedAnnotation(
                                id = "tick_z_$value",
                                text = formatAxisNumber(value),
                                screenX = sx,
                                screenY = sy,
                                kind = AnnotationKind.TICK_Z
                            )
                        }
                    }

                    // 3D POI pin callouts (poi.x = mathX, poi.y = mathY, poi.z = f(x, y))
                    var zeroCount = 0
                    currentUi.equation.pointsOfInterest.forEach { poi ->
                        val kind = when (poi.type) {
                            PointType.MAXIMA -> if (currentUi.showMaxima) AnnotationKind.MAXIMA else null
                            PointType.MINIMA -> if (currentUi.showMinima) AnnotationKind.MINIMA else null
                            PointType.ZERO -> if (currentUi.showZeros && zeroCount < 8) {
                                zeroCount++
                                AnnotationKind.ZERO
                            } else {
                                null
                            }
                            PointType.INFLECTION -> null
                        } ?: return@forEach

                        val localX = ((poi.x / currentUi.xDomain) * GRAPH_HALF_SPAN)
                            .toFloat().coerceIn(-GRAPH_HALF_SPAN, GRAPH_HALF_SPAN)
                        val localZ = ((poi.y / currentUi.yDomain) * GRAPH_HALF_SPAN)
                            .toFloat().coerceIn(-GRAPH_HALF_SPAN, GRAPH_HALF_SPAN)
                        val clampedMathZ = poi.z.coerceIn(-zHalfRange, zHalfRange).toFloat()
                        val pinHeight = if (kind == AnnotationKind.ZERO) 0.030f else 0.044f
                        val pinTopY = GRAPH_BASE_ELEVATION +
                            (clampedMathZ / zHalfRange.toFloat()) * (2.0f * GRAPH_VERTICAL_SCALE) +
                            pinHeight + 0.008f

                        projectLocal(Float3(localX, pinTopY, localZ))?.let { (sx, sy) ->
                            val labelText = when (kind) {
                                AnnotationKind.MAXIMA -> "Máx ${poi.z} (${poi.x}, ${poi.y})"
                                AnnotationKind.MINIMA -> "Mín ${poi.z} (${poi.x}, ${poi.y})"
                                AnnotationKind.ZERO -> "Zero (${poi.x}, ${poi.y})"
                                else -> poi.label
                            }
                            nextAnnotations += ProjectedAnnotation(
                                id = poi.id,
                                text = labelText,
                                screenX = sx,
                                screenY = sy,
                                kind = kind
                            )
                        }
                    }

                    projectedAnnotations = nextAnnotations
                } else if (projectedAnnotations.isNotEmpty()) {
                    projectedAnnotations = emptyList()
                }
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, _ ->
                    if (!modelPlaced && pendingPlacement == null) {
                        val currentFrame = frame ?: return@rememberOnGestureListener
                        val hitResult = currentFrame.hitTest(motionEvent.x, motionEvent.y)
                            .firstOrNull { hit ->
                                val plane = hit.trackable as? Plane
                                plane != null &&
                                        plane.trackingState == TrackingState.TRACKING &&
                                        plane.isPoseInPolygon(hit.hitPose) &&
                                        plane.extentX >= 0.20f &&
                                        plane.extentZ >= 0.20f
                            }

                        if (hitResult != null) {
                            val anchor = runCatching { hitResult.createAnchor() }.getOrNull()
                            if (anchor != null) {
                                pendingPlacement = PendingArPlacement(
                                    anchor = anchor,
                                    hitPose = hitResult.hitPose,
                                    cameraPose = currentFrame.camera.pose
                                )
                            }
                        }
                    }
                },
                onScaleBegin = { detector, _, _ ->
                    isScaling = true
                    lastPinchSpan = detector.currentSpan
                },
                onScale = { detector, _, _ ->
                    if (modelPlaced && !isTrackingLost && lastPinchSpan > 0f && detector.currentSpan > 0f) {
                        val spanRatio = detector.currentSpan / lastPinchSpan
                        lastPinchSpan = detector.currentSpan

                        val dampedRatio = 1f + (spanRatio - 1f) * 0.75f
                        targetScale = (targetScale * dampedRatio).coerceIn(0.3f, 3.0f)
                    }
                },
                onScaleEnd = { _, _, _ ->
                    isScaling = false
                    lastPinchSpan = 0f
                    if (modelPlaced) saveTransformOnly()
                },
                onScroll = { _, currentEvent, _, distance ->
                    if (modelPlaced && !isTrackingLost && !isScaling) {
                        val eqNode = equationNodeRef ?: return@rememberOnGestureListener
                        val anchorQuat = anchorNodeRef?.worldQuaternion ?: Quaternion()
                        val camQuat = cameraNode.worldQuaternion
                        val camPos = cameraNode.worldPosition

                        val modelCenter = Float3(
                            eqNode.worldPosition.x,
                            eqNode.worldPosition.y + 0.12f * smoothScale,
                            eqNode.worldPosition.z
                        )
                        val (centerX, centerY) = projectWorldToScreenFallback(
                            worldPoint = modelCenter,
                            camPos = camPos,
                            camQuat = camQuat,
                            screenWidth = screenWidthPx,
                            screenHeight = screenHeightPx
                        )

                        val currX = currentEvent.x - centerX
                        val currY = currentEvent.y - centerY
                        val prevX = currX + distance.x
                        val prevY = currY + distance.y

                        val trackballRadius = (screenWidthPx * 0.35f * smoothScale).coerceIn(140f, 600f)

                        val v1 = projectToTrackball(prevX, prevY, trackballRadius)
                        val v2 = projectToTrackball(currX, currY, trackballRadius)

                        val camAxis = crossProduct(v1, v2)
                        val axisLen = length(camAxis)
                        if (axisLen > 0.0001f) {
                            val dot = (v1.x * v2.x + v1.y * v2.y + v1.z * v2.z).coerceIn(-1f, 1f)
                            val angleRad = acos(dot) * 0.85f

                            val normCamAxis = Float3(
                                camAxis.x / axisLen,
                                camAxis.y / axisLen,
                                camAxis.z / axisLen
                            )

                            val worldAxis = rotateVectorByQuaternion(normCamAxis, camQuat)
                            val localAxis = rotateVectorByInverseQuaternion(worldAxis, anchorQuat)

                            val deltaQuat = quaternionFromAxisRadians(localAxis, angleRad)
                            targetQuaternion = normalizeQuaternion(deltaQuat * targetQuaternion)
                            saveTransformOnly()
                        }
                    }
                }
            )
        )

        // Overlay labels anchored directly onto the 3D equation points in space
        projectedAnnotations.forEach { ann ->
            key(ann.id) {
                when (ann.kind) {
                    AnnotationKind.HEADER -> {
                        var cardWidthPx by remember { mutableIntStateOf(0) }
                        var cardHeightPx by remember { mutableIntStateOf(0) }
                        val gapPx = with(density) { 14.dp.toPx() }

                        Surface(
                            modifier = Modifier
                                .onSizeChanged { size ->
                                    cardWidthPx = size.width
                                    cardHeightPx = size.height
                                }
                                .offset {
                                    IntOffset(
                                        x = (ann.screenX - cardWidthPx / 2f).roundToInt(),
                                        y = (ann.screenY - cardHeightPx - gapPx).roundToInt()
                                    )
                                }
                                .widthIn(min = 140.dp, max = 340.dp)
                                .clickable {
                                    viewModel.setSelectedTab(1)
                                    viewModel.setBottomSheetVisible(true)
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
                            tonalElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                MathFormulaView(
                                    expression = uiState.equation.expression,
                                    prefix = "z = f(x, y) = ",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val usedParams = remember(uiState.equation.expression) {
                                    MathExpressionEvaluator.extractParameters(uiState.equation.expression)
                                }
                                val effectiveParams = uiState.equation.getEffectiveParams()
                                if (usedParams.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        usedParams.forEach { param ->
                                            val displayVal = uiState.paramsInput[param]?.ifBlank { null }
                                                ?: (effectiveParams[param]?.toString() ?: "1.0")
                                            MathFormulaView(
                                                expression = displayVal,
                                                prefix = "$param = ",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnnotationKind.AXIS -> {
                        val halfW = with(density) { 14.dp.toPx() }
                        val halfH = with(density) { 10.dp.toPx() }
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = (ann.screenX - halfW).roundToInt(),
                                        y = (ann.screenY - halfH).roundToInt()
                                    )
                                }
                                .background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = ann.text,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    AnnotationKind.TICK_X, AnnotationKind.TICK_Y, AnnotationKind.TICK_Z -> {
                        val tickColor = when (ann.kind) {
                            AnnotationKind.TICK_X -> Color(0xFFFF8A80)
                            AnnotationKind.TICK_Y -> Color(0xFF82B1FF)
                            else -> Color(0xFF69F0AE)
                        }
                        val halfW = with(density) { 10.dp.toPx() }
                        val halfH = with(density) { 8.dp.toPx() }
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = (ann.screenX - halfW).roundToInt(),
                                        y = (ann.screenY - halfH).roundToInt()
                                    )
                                }
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = ann.text,
                                color = tickColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    AnnotationKind.MAXIMA, AnnotationKind.MINIMA, AnnotationKind.ZERO -> {
                        val accent = when (ann.kind) {
                            AnnotationKind.MAXIMA -> Color(0xFFFFD600)
                            AnnotationKind.MINIMA -> Color(0xFF00E676)
                            AnnotationKind.ZERO -> Color(0xFFFF5252)
                            else -> Color.White
                        }
                        val halfW = with(density) { 54.dp.toPx() }
                        val liftY = with(density) { 26.dp.toPx() }
                        Row(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = (ann.screenX - halfW).roundToInt(),
                                        y = (ann.screenY - liftY).roundToInt()
                                    )
                                }
                                .background(Color(0xD912141A), RoundedCornerShape(8.dp))
                                .border(1.dp, accent, RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setSelectedTab(0)
                                    viewModel.setBottomSheetVisible(true)
                                }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = ann.text,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Direct AR HUD domain sliders & toggles for Máximos, Mínimos & Zeros
        if (modelPlaced && !isTrackingLost) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 24.dp, end = 92.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArDomainSlidersCard(
                    xDomain = uiState.xDomain,
                    yDomain = uiState.yDomain,
                    onXDomainChange = viewModel::updateXDomain,
                    onYDomainChange = viewModel::updateYDomain
                )
                ArPoiFilterBar(
                    showMaxima = uiState.showMaxima,
                    showMinima = uiState.showMinima,
                    showZeros = uiState.showZeros,
                    onToggleMaxima = { viewModel.toggleMaxima(!uiState.showMaxima) },
                    onToggleMinima = { viewModel.toggleMinima(!uiState.showMinima) },
                    onToggleZeros = { viewModel.toggleZeros(!uiState.showZeros) }
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallFloatingActionButton(
                onClick = { showSaveDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f)
            ) {
                Icon(Icons.Default.Save, contentDescription = "Guardar equação")
            }

            if (modelPlaced) {
                SmallFloatingActionButton(
                    onClick = {
                        anchorNodeRef?.detachAnchor()
                        childNodes.clear()
                        anchorNodeRef = null
                        equationNodeRef = null
                        isTrackingLost = false
                        autoRestoreStopped = true
                        modelPlaced = false
                        projectedAnnotations = emptyList()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reposicionar modelo")
                }
            }
        }

        FloatingActionButton(
            onClick = { viewModel.setBottomSheetVisible(true) },
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = "Detalhes da Equação (Abas 1 e 2)")
        }

        if (!modelPlaced) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 92.dp, bottom = 24.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
                tonalElevation = 0.dp
            ) {
                val isSearchingSaved = hasStoredModel && hasStoredLocation && !autoRestoreStopped
                Column(modifier = Modifier.padding(16.dp)) {
                    val prompt = when {
                        isSearchingSaved ->
                            "A procurar a superfície original... Aponte para o mesmo local ou toque numa grelha."
                        hasStoredModel ->
                            "Toque na superfície onde pretende colocar o modelo."
                        else ->
                            "Mova a câmara lentamente sobre uma superfície com textura até formar uma grelha e toque para ancorar."
                    }
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (isSearchingSaved) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { autoRestoreStopped = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Colocar num novo local")
                        }
                    }
                }
            }
        } else if (isTrackingLost) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 92.dp, bottom = 24.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.60f),
                tonalElevation = 0.dp
            ) {
                Text(
                    text = "A relocalizar... Mova a câmara lentamente sobre a superfície ou toque no botão de atualizar acima para reposicionar.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        pendingPlacement?.let { placement ->
            val matchingSavedId = uiState.savedEquations.firstOrNull { saved ->
                saved.expression == uiState.equation.expression &&
                    saved.getUsedParams() == uiState.equation.getEffectiveParams()
            }?.id
            val matchingCloudId = uiState.serverEquations.firstOrNull { apiEq ->
                apiEq.id == uiState.equation.id ||
                    (apiEq.expression == uiState.equation.expression &&
                        apiEq.a == uiState.equation.a &&
                        apiEq.b == uiState.equation.b &&
                        apiEq.c == uiState.equation.c)
            }?.id

            PlacementEquationDialog(
                savedEquations = uiState.savedEquations,
                cloudEquations = uiState.serverEquations,
                isSyncingCloud = uiState.isSyncingCloud,
                initialSelectedSavedId = matchingSavedId,
                initialSelectedCloudId = matchingCloudId,
                onRefreshCloud = { viewModel.fetchServerEquations() },
                onDismiss = {
                    placement.anchor.detach()
                    pendingPlacement = null
                },
                onConfirmSaved = { saved ->
                    val updatedEq = viewModel.applySavedEquation(saved)
                    savePlacementSignature(placement.hitPose, placement.cameraPose)
                    attachModelToAnchor(placement.anchor, equation = updatedEq)
                    pendingPlacement = null
                },
                onConfirmCloud = { apiEq ->
                    val updatedEq = viewModel.applyApiEquation(apiEq)
                    savePlacementSignature(placement.hitPose, placement.cameraPose)
                    attachModelToAnchor(placement.anchor, equation = updatedEq)
                    pendingPlacement = null
                },
                onConfirmNew = { expr, params ->
                    val updatedEq = viewModel.applyCustomEquation(expr, params)
                    savePlacementSignature(placement.hitPose, placement.cameraPose)
                    attachModelToAnchor(placement.anchor, equation = updatedEq)
                    pendingPlacement = null
                }
            )
        }

        if (showSaveDialog) {
            SaveEquationDialog(
                initialExpression = uiState.equation.expression,
                initialParams = uiState.paramsInput,
                isLoggedIn = uiState.isLoggedIn,
                onDismiss = { showSaveDialog = false },
                onSaveLocal = { name, expr, params ->
                    val err = viewModel.saveEquationToDevice(
                        name = name,
                        expression = expr,
                        paramsInput = params
                    )
                    if (err == null) {
                        showSaveDialog = false
                        Toast.makeText(context, "Equação guardada no dispositivo!", Toast.LENGTH_SHORT).show()
                    }
                    err
                },
                onSaveCloud = { name, expr, params ->
                    val err = viewModel.saveEquationToCloud(
                        name = name,
                        expression = expr,
                        paramsInput = params
                    ) { result ->
                        result.onSuccess {
                            Toast.makeText(context, "Equação guardada na nuvem!", Toast.LENGTH_SHORT).show()
                        }.onFailure { failure ->
                            Toast.makeText(
                                context,
                                "Erro ao guardar na nuvem: ${failure.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    if (err == null) {
                        showSaveDialog = false
                    }
                    err
                }
            )
        }
    }
}

private const val SAVE_EXPR_FIELD_KEY = "__SAVE_EXPR__"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaveEquationDialog(
    initialExpression: String,
    initialParams: Map<String, String>,
    isLoggedIn: Boolean,
    onDismiss: () -> Unit,
    onSaveLocal: (name: String, expression: String, params: Map<String, String>) -> String?,
    onSaveCloud: (name: String, expression: String, params: Map<String, String>) -> String?
) {
    var name by remember { mutableStateOf("") }
    var expressionValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialExpression,
                selection = TextRange(initialExpression.length)
            )
        )
    }
    val detectedParams = remember(expressionValue.text) {
        MathExpressionEvaluator.extractParameters(expressionValue.text)
    }
    var paramValues by remember {
        mutableStateOf(
            initialParams.mapValues { (_, v) -> TextFieldValue(v, TextRange(v.length)) }
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeCalcField by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(detectedParams) {
        val updated = mutableMapOf<String, TextFieldValue>()
        detectedParams.forEach { param ->
            val defaultStr = initialParams[param] ?: if (param == "c") "0.0" else "1.0"
            updated[param] = paramValues[param] ?: TextFieldValue(defaultStr, TextRange(defaultStr.length))
        }
        paramValues = updated
        if (activeCalcField != null && activeCalcField != SAVE_EXPR_FIELD_KEY && activeCalcField !in detectedParams) {
            activeCalcField = SAVE_EXPR_FIELD_KEY
        }
    }

    LaunchedEffect(activeCalcField) {
        if (activeCalcField != null) {
            keyboardController?.hide()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.94f),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
        tonalElevation = 0.dp,
        title = {
            val dialogView = LocalView.current
            SideEffect {
                (dialogView.parent as? DialogWindowProvider)?.window?.setDimAmount(0.08f)
            }
            Text("Guardar Equação")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome (opcional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                activeCalcField = null
                            }
                        }
                )

                val exprInteraction = remember { MutableInteractionSource() }
                LaunchedEffect(exprInteraction) {
                    exprInteraction.interactions.collect { interaction ->
                        if (interaction is PressInteraction.Release) {
                            activeCalcField = SAVE_EXPR_FIELD_KEY
                            keyboardController?.hide()
                        }
                    }
                }

                OutlinedTextField(
                    value = expressionValue,
                    onValueChange = {
                        expressionValue = it
                        errorMessage = null
                    },
                    readOnly = true,
                    interactionSource = exprInteraction,
                    label = { Text("Expressão z = f(x, y)") },
                    trailingIcon = {
                        IconButton(onClick = { activeCalcField = SAVE_EXPR_FIELD_KEY }) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "Abrir teclado científico"
                            )
                        }
                    },
                    isError = errorMessage != null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                activeCalcField = SAVE_EXPR_FIELD_KEY
                                keyboardController?.hide()
                            }
                        }
                )

                if (detectedParams.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        detectedParams.forEach { param ->
                            val paramInteraction = remember(param) { MutableInteractionSource() }
                            LaunchedEffect(paramInteraction, param) {
                                paramInteraction.interactions.collect { interaction ->
                                    if (interaction is PressInteraction.Release) {
                                        activeCalcField = param
                                        keyboardController?.hide()
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = paramValues[param] ?: TextFieldValue("1.0"),
                                onValueChange = { updated ->
                                    paramValues = paramValues.toMutableMap().apply { put(param, updated) }
                                    errorMessage = null
                                },
                                readOnly = true,
                                interactionSource = paramInteraction,
                                label = { Text(param) },
                                singleLine = true,
                                modifier = Modifier
                                    .width(95.dp)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            activeCalcField = param
                                            keyboardController?.hide()
                                        }
                                    }
                            )
                        }
                    }
                }

                activeCalcField?.let { targetField ->
                    val isExpr = targetField == SAVE_EXPR_FIELD_KEY
                    ScientificCalculatorKeyboard(
                        targetLabel = if (isExpr) "Expressão z = f(x, y)" else "Parâmetro '$targetField'",
                        isNumericOnly = !isExpr,
                        onInsert = { token ->
                            errorMessage = null
                            if (isExpr) {
                                expressionValue = insertTokenAtSelection(expressionValue, token)
                            } else {
                                val curr = paramValues[targetField] ?: TextFieldValue("")
                                paramValues = paramValues.toMutableMap().apply {
                                    put(targetField, insertTokenAtSelection(curr, token))
                                }
                            }
                        },
                        onBackspace = {
                            errorMessage = null
                            if (isExpr) {
                                expressionValue = backspaceAtSelection(expressionValue)
                            } else {
                                val curr = paramValues[targetField] ?: TextFieldValue("")
                                paramValues = paramValues.toMutableMap().apply {
                                    put(targetField, backspaceAtSelection(curr))
                                }
                            }
                        },
                        onClear = {
                            errorMessage = null
                            if (isExpr) {
                                expressionValue = TextFieldValue("")
                            } else {
                                paramValues = paramValues.toMutableMap().apply {
                                    put(targetField, TextFieldValue(""))
                                }
                            }
                        },
                        onMoveCursor = { delta ->
                            if (isExpr) {
                                expressionValue = moveSelectionCursor(expressionValue, delta)
                            } else {
                                val curr = paramValues[targetField] ?: TextFieldValue("")
                                paramValues = paramValues.toMutableMap().apply {
                                    put(targetField, moveSelectionCursor(curr, delta))
                                }
                            }
                        },
                        onDismiss = { activeCalcField = null }
                    )
                }

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (!isLoggedIn) {
                    Text(
                        text = "Inicie sessão no separador Perfil para guardar na nuvem.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val stringParams = detectedParams.associateWith { param ->
                            paramValues[param]?.text ?: if (param == "c") "0.0" else "1.0"
                        }
                        val err = onSaveLocal(name, expressionValue.text, stringParams)
                        if (err != null) {
                            errorMessage = err
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Local")
                }

                Button(
                    enabled = isLoggedIn,
                    onClick = {
                        val stringParams = detectedParams.associateWith { param ->
                            paramValues[param]?.text ?: if (param == "c") "0.0" else "1.0"
                        }
                        val err = onSaveCloud(name, expressionValue.text, stringParams)
                        if (err != null) {
                            errorMessage = err
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nuvem")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun ArDomainSlidersCard(
    xDomain: Double,
    yDomain: Double,
    onXDomainChange: (Double) -> Unit,
    onYDomainChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0x9914171F),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "X [±$xDomain]",
                    color = Color(0xFFFF8A80),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(68.dp)
                )
                Slider(
                    value = xDomain.toFloat(),
                    onValueChange = { onXDomainChange(it.toDouble()) },
                    valueRange = 0.5f..10.0f,
                    steps = 18,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Y [±$yDomain]",
                    color = Color(0xFF82B1FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(68.dp)
                )
                Slider(
                    value = yDomain.toFloat(),
                    onValueChange = { onYDomainChange(it.toDouble()) },
                    valueRange = 0.5f..10.0f,
                    steps = 18,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                )
            }
        }
    }
}

@Composable
private fun ArPoiFilterBar(
    showMaxima: Boolean,
    showMinima: Boolean,
    showZeros: Boolean,
    onToggleMaxima: () -> Unit,
    onToggleMinima: () -> Unit,
    onToggleZeros: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArPoiToggleChip(
            label = "Máximos",
            accentColor = Color(0xFFFFD600),
            checked = showMaxima,
            onClick = onToggleMaxima
        )
        ArPoiToggleChip(
            label = "Mínimos",
            accentColor = Color(0xFF00E676),
            checked = showMinima,
            onClick = onToggleMinima
        )
        ArPoiToggleChip(
            label = "Zeros",
            accentColor = Color(0xFFFF5252),
            checked = showZeros,
            onClick = onToggleZeros
        )
    }
}

@Composable
private fun ArPoiToggleChip(
    label: String,
    accentColor: Color,
    checked: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (checked) Color(0xE614171F) else Color(0x9914171F)
    val borderColor = if (checked) accentColor else Color.White.copy(alpha = 0.25f)
    val textColor = if (checked) Color.White else Color.White.copy(alpha = 0.65f)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(1.2.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) accentColor else Color.Transparent)
                .border(1.2.dp, accentColor, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF101318),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun projectWorldToScreenExact(
    worldPoint: Float3,
    viewMatrix: FloatArray,
    projMatrix: FloatArray,
    screenWidth: Float,
    screenHeight: Float
): Pair<Float, Float>? {
    val worldVec = floatArrayOf(worldPoint.x, worldPoint.y, worldPoint.z, 1f)
    val camVec = FloatArray(4)
    val clipVec = FloatArray(4)

    Matrix.multiplyMV(camVec, 0, viewMatrix, 0, worldVec, 0)
    if (camVec[2] > -0.05f) return null

    Matrix.multiplyMV(clipVec, 0, projMatrix, 0, camVec, 0)
    val w = clipVec[3]
    if (w <= 0.001f) return null

    val ndcX = clipVec[0] / w
    val ndcY = clipVec[1] / w

    if (ndcX < -1.3f || ndcX > 1.3f || ndcY < -1.3f || ndcY > 1.3f) return null

    val screenX = (ndcX * 0.5f + 0.5f) * screenWidth
    val screenY = (0.5f - ndcY * 0.5f) * screenHeight
    return Pair(screenX, screenY)
}

private fun normalizeAngleRad(angle: Float): Float {
    var a = angle
    val twoPi = (2.0 * PI).toFloat()
    while (a > PI) a -= twoPi
    while (a < -PI) a += twoPi
    return a
}

private fun projectWorldToScreenFallback(
    worldPoint: Float3,
    camPos: Float3,
    camQuat: Quaternion,
    screenWidth: Float,
    screenHeight: Float
): Pair<Float, Float> {
    val rel = Float3(
        worldPoint.x - camPos.x,
        worldPoint.y - camPos.y,
        worldPoint.z - camPos.z
    )
    val camSpace = rotateVectorByInverseQuaternion(rel, camQuat)
    val depth = (-camSpace.z).coerceAtLeast(0.15f)
    val tanHalfFov = 0.577f
    val aspect = screenWidth / screenHeight

    val ndcX = (camSpace.x / depth) / (tanHalfFov * aspect)
    val ndcY = (camSpace.y / depth) / tanHalfFov

    val screenX = (ndcX * 0.5f + 0.5f) * screenWidth
    val screenY = (0.5f - ndcY * 0.5f) * screenHeight
    return Pair(screenX, screenY)
}

private fun projectToTrackball(dx: Float, dy: Float, radius: Float): Float3 {
    val u = dx / radius
    val v = -dy / radius
    val d2 = u * u + v * v
    return if (d2 <= 0.5f) {
        Float3(u, v, sqrt(1f - d2))
    } else {
        val z = 0.5f / sqrt(d2)
        val len = sqrt(u * u + v * v + z * z)
        Float3(u / len, v / len, z / len)
    }
}

private fun crossProduct(a: Float3, b: Float3): Float3 = Float3(
    a.y * b.z - a.z * b.y,
    a.z * b.x - a.x * b.z,
    a.x * b.y - a.y * b.x
)

private fun length(v: Float3): Float = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)

private fun quaternionFromAxisRadians(axis: Float3, angleRadians: Float): Quaternion {
    val half = angleRadians * 0.5f
    val s = sin(half)
    return Quaternion(axis.x * s, axis.y * s, axis.z * s, cos(half))
}

private fun normalizeQuaternion(q: Quaternion): Quaternion {
    val len = sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w)
    return if (len > 0.0001f) Quaternion(q.x / len, q.y / len, q.z / len, q.w / len) else Quaternion()
}

private fun nlerpQuaternion(a: Quaternion, b: Quaternion, t: Float): Quaternion {
    val dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w
    val sign = if (dot < 0f) -1f else 1f
    return normalizeQuaternion(
        Quaternion(
            x = a.x + (b.x * sign - a.x) * t,
            y = a.y + (b.y * sign - a.y) * t,
            z = a.z + (b.z * sign - a.z) * t,
            w = a.w + (b.w * sign - a.w) * t
        )
    )
}

private fun rotateVectorByQuaternion(v: Float3, q: Quaternion): Float3 {
    val inv = Quaternion(-q.x, -q.y, -q.z, q.w)
    val vecQuat = Quaternion(v.x, v.y, v.z, 0f)
    val res = q * vecQuat * inv
    return Float3(res.x, res.y, res.z)
}

private fun rotateVectorByInverseQuaternion(v: Float3, q: Quaternion): Float3 {
    val inv = Quaternion(-q.x, -q.y, -q.z, q.w)
    val vecQuat = Quaternion(v.x, v.y, v.z, 0f)
    val res = inv * vecQuat * q
    return Float3(res.x, res.y, res.z)
}

private class SurfaceGridData(
    val gridSteps: Int,
    val worldStep: Float,
    val mathHeights: Array<FloatArray>,
    val worldPositions: Array<FloatArray>,
    val valid: Array<BooleanArray>,
    val zHalfRange: Double
)

private fun computeSurfaceGridData(
    equation: EquationModel,
    xDomain: Double,
    yDomain: Double
): SurfaceGridData {
    val gridSteps = 72
    val cols = gridSteps + 1
    val worldStep = (GRAPH_HALF_SPAN * 2f) / gridSteps

    val rawHeights = Array(cols) { DoubleArray(cols) { Double.NaN } }
    val compiled = runCatching { MathExpressionEvaluator.compile(equation.expression) }.getOrNull()
    val normalizedParams = equation.getEffectiveParams().mapKeys { it.key.lowercase() }
    var maxAbsZ = 0.0

    for (ix in 0..gridSteps) {
        val worldX = -GRAPH_HALF_SPAN + ix * worldStep
        val mathX = (worldX / GRAPH_HALF_SPAN) * xDomain

        for (iy in 0..gridSteps) {
            val worldZ = -GRAPH_HALF_SPAN + iy * worldStep
            val mathY = (worldZ / GRAPH_HALF_SPAN) * yDomain

            val rawMathZ = if (compiled == null) {
                Double.NaN
            } else {
                runCatching {
                    compiled.evaluate(x = mathX, y = mathY, params = normalizedParams)
                }.getOrDefault(Double.NaN)
            }

            if (rawMathZ.isFinite()) {
                rawHeights[ix][iy] = rawMathZ
                val absZ = abs(rawMathZ)
                if (absZ > maxAbsZ) {
                    maxAbsZ = absZ
                }
            }
        }
    }

    val zHalfRange = if (maxAbsZ <= 2.0) 2.0 else ceil(maxAbsZ).coerceAtMost(50.0)
    val zHalfRangeF = zHalfRange.toFloat()

    val mathHeights = Array(cols) { FloatArray(cols) }
    val worldPositions = Array(cols) { FloatArray(cols * 3) }
    val valid = Array(cols) { BooleanArray(cols) }

    for (ix in 0..gridSteps) {
        val worldX = -GRAPH_HALF_SPAN + ix * worldStep
        for (iy in 0..gridSteps) {
            val worldZ = -GRAPH_HALF_SPAN + iy * worldStep
            val rawMathZ = rawHeights[ix][iy]
            if (rawMathZ.isFinite()) {
                val clampedMathZ = rawMathZ.coerceIn(-zHalfRange, zHalfRange).toFloat()
                val worldY = GRAPH_BASE_ELEVATION + (clampedMathZ / zHalfRangeF) * (2.0f * GRAPH_VERTICAL_SCALE)
                mathHeights[ix][iy] = clampedMathZ
                worldPositions[ix][iy * 3] = worldX
                worldPositions[ix][iy * 3 + 1] = worldY
                worldPositions[ix][iy * 3 + 2] = worldZ
                valid[ix][iy] = true
            }
        }
    }

    return SurfaceGridData(
        gridSteps = gridSteps,
        worldStep = worldStep,
        mathHeights = mathHeights,
        worldPositions = worldPositions,
        valid = valid,
        zHalfRange = zHalfRange
    )
}

/**
 * Custom Filament node holding a high-density double-sided continuous triangle mesh
 * in Filament's Y-up coordinate space (mathX -> worldX, mathY -> worldZ, mathZ = f(x, y) -> worldY).
 */
private class SmoothSurfaceMeshNode(
    engine: Engine,
    entity: Int,
    private val vertexBuffer: VertexBuffer,
    private val indexBuffers: List<IndexBuffer>,
    private val materials: List<MaterialInstance>
) : Node(engine = engine, entity = entity) {
    override fun destroy() {
        super.destroy()
        runCatching { engine.destroyVertexBuffer(vertexBuffer) }
        indexBuffers.forEach { ib ->
            runCatching { engine.destroyIndexBuffer(ib) }
        }
        materials.forEach { mat ->
            runCatching { engine.destroyMaterialInstance(mat) }
        }
    }
}

private class DisposableEquationRootNode(
    engine: Engine,
    private val materials: List<MaterialInstance>
) : Node(engine = engine) {
    override fun destroy() {
        super.destroy()
        materials.forEach { mat ->
            runCatching { engine.destroyMaterialInstance(mat) }
        }
    }
}

private fun buildSmoothEquationSurfaceNode(
    engine: Engine,
    materialLoader: io.github.sceneview.loaders.MaterialLoader,
    gridData: SurfaceGridData
): Node? {
    val gridSteps = gridData.gridSteps
    val cols = gridSteps + 1
    val worldStep = gridData.worldStep
    val mathHeights = gridData.mathHeights
    val worldPositions = gridData.worldPositions
    val valid = gridData.valid
    val zHalfRangeF = gridData.zHalfRange.toFloat().coerceAtLeast(1e-4f)

    val perSideVertexCount = cols * cols
    val totalVertexCount = perSideVertexCount * 2
    val vertexData = ByteBuffer.allocateDirect(totalVertexCount * 28).order(ByteOrder.nativeOrder())
    val floatView = vertexData.asFloatBuffer()

    val normals = Array(cols) { FloatArray(cols * 3) }
    for (ix in 0..gridSteps) {
        for (iy in 0..gridSteps) {
            val py = worldPositions[ix][iy * 3 + 1]

            val ixL = max(0, ix - 1)
            val ixR = min(gridSteps, ix + 1)
            val iyD = max(0, iy - 1)
            val iyU = min(gridSteps, iy + 1)

            val yL = if (valid[ixL][iy]) worldPositions[ixL][iy * 3 + 1] else py
            val yR = if (valid[ixR][iy]) worldPositions[ixR][iy * 3 + 1] else py
            val yD = if (valid[ix][iyD]) worldPositions[ix][iyD * 3 + 1] else py
            val yU = if (valid[ix][iyU]) worldPositions[ix][iyU * 3 + 1] else py

            val spanX = ((ixR - ixL) * worldStep).coerceAtLeast(1e-5f)
            val spanZ = ((iyU - iyD) * worldStep).coerceAtLeast(1e-5f)

            val nx = -(yR - yL) * spanZ
            val ny = spanX * spanZ
            val nz = -(yU - yD) * spanX
            val nLen = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
            normals[ix][iy * 3] = nx / nLen
            normals[ix][iy * 3 + 1] = ny / nLen
            normals[ix][iy * 3 + 2] = nz / nLen
        }
    }

    // 1. Top surface vertices
    for (ix in 0..gridSteps) {
        for (iy in 0..gridSteps) {
            val px = worldPositions[ix][iy * 3]
            val py = worldPositions[ix][iy * 3 + 1]
            val pz = worldPositions[ix][iy * 3 + 2]
            val nx = normals[ix][iy * 3]
            val ny = normals[ix][iy * 3 + 1]
            val nz = normals[ix][iy * 3 + 2]
            val (qx, qy, qz, qw) = normalToTangentQuaternion(nx, ny, nz)

            floatView.put(px)
            floatView.put(py)
            floatView.put(pz)
            floatView.put(qx)
            floatView.put(qy)
            floatView.put(qz)
            floatView.put(qw)
        }
    }

    // 2. Underside surface vertices (0.6 mm shell thickness + outward-facing inverted normal)
    val shellThickness = 0.0006f
    for (ix in 0..gridSteps) {
        for (iy in 0..gridSteps) {
            val px = worldPositions[ix][iy * 3]
            val py = worldPositions[ix][iy * 3 + 1] - shellThickness
            val pz = worldPositions[ix][iy * 3 + 2]
            val nx = -normals[ix][iy * 3]
            val ny = -normals[ix][iy * 3 + 1]
            val nz = -normals[ix][iy * 3 + 2]
            val (qx, qy, qz, qw) = normalToTangentQuaternion(nx, ny, nz)

            floatView.put(px)
            floatView.put(py)
            floatView.put(pz)
            floatView.put(qx)
            floatView.put(qy)
            floatView.put(qz)
            floatView.put(qw)
        }
    }
    vertexData.rewind()

    val vertexBuffer = VertexBuffer.Builder()
        .bufferCount(1)
        .vertexCount(totalVertexCount)
        .attribute(
            VertexBuffer.VertexAttribute.POSITION,
            0,
            VertexBuffer.AttributeType.FLOAT3,
            0,
            28
        )
        .attribute(
            VertexBuffer.VertexAttribute.TANGENTS,
            0,
            VertexBuffer.AttributeType.FLOAT4,
            12,
            28
        )
        .build(engine)
    vertexBuffer.setBufferAt(engine, 0, vertexData)

    val bandIndices = Array(5) { ArrayList<Int>(gridSteps * gridSteps * 4) }

    for (ix in 0 until gridSteps) {
        for (iy in 0 until gridSteps) {
            if (!valid[ix][iy] || !valid[ix + 1][iy] || !valid[ix][iy + 1] || !valid[ix + 1][iy + 1]) {
                continue
            }

            val avgNormZ = (0.25f * (
                    mathHeights[ix][iy] +
                            mathHeights[ix + 1][iy] +
                            mathHeights[ix][iy + 1] +
                            mathHeights[ix + 1][iy + 1]
                    )) / zHalfRangeF

            val bandIdx = when {
                avgNormZ >= 0.325f -> 4
                avgNormZ >= 0.10f -> 3
                avgNormZ >= -0.10f -> 2
                avgNormZ >= -0.325f -> 1
                else -> 0
            }

            val i00 = ix * cols + iy
            val i10 = (ix + 1) * cols + iy
            val i01 = ix * cols + (iy + 1)
            val i11 = (ix + 1) * cols + (iy + 1)

            val list = bandIndices[bandIdx]

            // Top face
            list.add(i00)
            list.add(i01)
            list.add(i10)

            list.add(i10)
            list.add(i01)
            list.add(i11)

            // Underside face
            val u00 = i00 + perSideVertexCount
            val u10 = i10 + perSideVertexCount
            val u01 = i01 + perSideVertexCount
            val u11 = i11 + perSideVertexCount

            list.add(u00)
            list.add(u10)
            list.add(u01)

            list.add(u10)
            list.add(u11)
            list.add(u01)
        }
    }

    val bandColors = listOf(
        "#00ACC1",
        "#26C6DA",
        "#3F51B5",
        "#7E57C2",
        "#EC407A"
    )

    data class BuiltPrimitive(
        val indexBuffer: IndexBuffer,
        val indexCount: Int,
        val material: MaterialInstance
    )

    val activePrimitives = mutableListOf<BuiltPrimitive>()
    for (b in 0..4) {
        val indices = bandIndices[b]
        if (indices.isEmpty()) continue

        val ib = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)

        val idxBytes = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder())
        val intBuf = idxBytes.asIntBuffer()
        for (idx in indices) intBuf.put(idx)
        idxBytes.rewind()
        ib.setBuffer(engine, idxBytes)

        activePrimitives += BuiltPrimitive(
            indexBuffer = ib,
            indexCount = indices.size,
            material = materialLoader.createColorInstance(AndroidColor.parseColor(bandColors[b]))
        )
    }

    if (activePrimitives.isEmpty()) {
        runCatching { engine.destroyVertexBuffer(vertexBuffer) }
        return null
    }

    val meshEntity = EntityManager.get().create()
    val builder = RenderableManager.Builder(activePrimitives.size)
        .boundingBox(
            Box(
                0f,
                GRAPH_BASE_ELEVATION,
                0f,
                GRAPH_HALF_SPAN + 0.05f,
                0.32f,
                GRAPH_HALF_SPAN + 0.05f
            )
        )
        .culling(false)
        .castShadows(false)
        .receiveShadows(false)

    activePrimitives.forEachIndexed { idx, prim ->
        builder
            .geometry(
                idx,
                RenderableManager.PrimitiveType.TRIANGLES,
                vertexBuffer,
                prim.indexBuffer,
                0,
                prim.indexCount
            )
            .material(idx, prim.material)
    }
    builder.build(engine, meshEntity)

    return SmoothSurfaceMeshNode(
        engine = engine,
        entity = meshEntity,
        vertexBuffer = vertexBuffer,
        indexBuffers = activePrimitives.map { it.indexBuffer },
        materials = activePrimitives.map { it.material }
    ).apply {
        isEditable = false
    }
}

private fun normalToTangentQuaternion(nx: Float, ny: Float, nz: Float): FloatArray {
    val refX: Float
    val refY: Float
    val refZ: Float
    if (abs(ny) < 0.99f) {
        refX = 0f; refY = 1f; refZ = 0f
    } else {
        refX = 1f; refY = 0f; refZ = 0f
    }

    var tx = refY * nz - refZ * ny
    var ty = refZ * nx - refX * nz
    var tz = refX * ny - refY * nx
    val tLen = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-6f)
    tx /= tLen; ty /= tLen; tz /= tLen

    val bx = ny * tz - nz * ty
    val by = nz * tx - nx * tz
    val bz = nx * ty - ny * tx

    val m00 = tx; val m01 = bx; val m02 = nx
    val m10 = ty; val m11 = by; val m12 = ny
    val m20 = tz; val m21 = bz; val m22 = nz

    val trace = m00 + m11 + m22
    var qx: Float
    var qy: Float
    var qz: Float
    var qw: Float

    if (trace > 0f) {
        val s = sqrt(trace + 1f) * 2f
        qw = 0.25f * s
        qx = (m21 - m12) / s
        qy = (m02 - m20) / s
        qz = (m10 - m01) / s
    } else if (m00 > m11 && m00 > m22) {
        val s = sqrt(1f + m00 - m11 - m22) * 2f
        qw = (m21 - m12) / s
        qx = 0.25f * s
        qy = (m01 + m10) / s
        qz = (m02 + m20) / s
    } else if (m11 > m22) {
        val s = sqrt(1f + m11 - m00 - m22) * 2f
        qw = (m02 - m20) / s
        qx = (m01 + m10) / s
        qy = 0.25f * s
        qz = (m12 + m21) / s
    } else {
        val s = sqrt(1f + m22 - m00 - m11) * 2f
        qw = (m10 - m01) / s
        qx = (m02 + m20) / s
        qy = (m12 + m21) / s
        qz = 0.25f * s
    }

    val qLen = sqrt(qx * qx + qy * qy + qz * qz + qw * qw).coerceAtLeast(1e-6f)
    return floatArrayOf(qx / qLen, qy / qLen, qz / qLen, qw / qLen)
}

private fun createEquation3DNode(
    engine: Engine,
    materialLoader: io.github.sceneview.loaders.MaterialLoader,
    equation: EquationModel,
    gridData: SurfaceGridData,
    showMaxima: Boolean,
    showMinima: Boolean,
    showZeros: Boolean,
    xDomain: Double,
    yDomain: Double
): Node {
    val axisMaterial = materialLoader.createColorInstance(AndroidColor.parseColor("#FFFFFF"))
    val xAxisTipMat = materialLoader.createColorInstance(AndroidColor.parseColor("#FF5252"))
    val yAxisTipMat = materialLoader.createColorInstance(AndroidColor.parseColor("#448AFF"))
    val zAxisTipMat = materialLoader.createColorInstance(AndroidColor.parseColor("#69F0AE"))
    val zeroPlaneMaterial = materialLoader.createColorInstance(AndroidColor.parseColor("#546E7A"))

    val maximaPoiMat = materialLoader.createColorInstance(AndroidColor.parseColor("#FFD600"))
    val minimaPoiMat = materialLoader.createColorInstance(AndroidColor.parseColor("#00E676"))
    val zeroPoiMat = materialLoader.createColorInstance(AndroidColor.parseColor("#FF5252"))

    val rootNode = DisposableEquationRootNode(
        engine = engine,
        materials = listOf(
            axisMaterial,
            xAxisTipMat,
            yAxisTipMat,
            zAxisTipMat,
            zeroPlaneMaterial,
            maximaPoiMat,
            minimaPoiMat,
            zeroPoiMat
        )
    ).apply {
        isEditable = false
    }

    // 1. Fluid, double-sided continuous 3D surface mesh
    buildSmoothEquationSurfaceNode(
        engine = engine,
        materialLoader = materialLoader,
        gridData = gridData
    )?.let { smoothSurfaceNode ->
        rootNode.addChildNode(smoothSurfaceNode)
    }

    // 2. Mathematical X, Y (horizontal plane) and Z (vertical height) coordinate axes
    rootNode.addChildNode(
        CubeNode(engine, size = Size(0.42f, 0.0025f, 0.0025f), materialInstance = axisMaterial).apply {
            position = Position(0f, GRAPH_BASE_ELEVATION, 0f)
        }
    )
    rootNode.addChildNode(
        CubeNode(engine, size = Size(0.010f, 0.010f, 0.010f), materialInstance = xAxisTipMat).apply {
            position = Position(0.215f, GRAPH_BASE_ELEVATION, 0f)
        }
    )

    rootNode.addChildNode(
        CubeNode(engine, size = Size(0.0025f, 0.0025f, 0.42f), materialInstance = axisMaterial).apply {
            position = Position(0f, GRAPH_BASE_ELEVATION, 0f)
        }
    )
    rootNode.addChildNode(
        CubeNode(engine, size = Size(0.010f, 0.010f, 0.010f), materialInstance = yAxisTipMat).apply {
            position = Position(0f, GRAPH_BASE_ELEVATION, 0.215f)
        }
    )

    rootNode.addChildNode(
        CubeNode(engine, size = Size(0.0025f, 0.32f, 0.0025f), materialInstance = axisMaterial).apply {
            position = Position(0f, GRAPH_BASE_ELEVATION, 0f)
        }
    )
    rootNode.addChildNode(
        CubeNode(engine, size = Size(0.010f, 0.010f, 0.010f), materialInstance = zAxisTipMat).apply {
            position = Position(0f, GRAPH_BASE_ELEVATION + 0.165f, 0f)
        }
    )

    // 2b. Axis tick marks matching the current X/Y slider domains and Z range
    axisTickValues(xDomain).forEach { value ->
        val localX = ((value / xDomain) * GRAPH_HALF_SPAN).toFloat()
        rootNode.addChildNode(
            CubeNode(
                engine,
                size = Size(0.002f, 0.008f, 0.008f),
                materialInstance = xAxisTipMat
            ).apply {
                position = Position(localX, GRAPH_BASE_ELEVATION, 0f)
            }
        )
    }

    axisTickValues(yDomain).forEach { value ->
        val localZ = ((value / yDomain) * GRAPH_HALF_SPAN).toFloat()
        rootNode.addChildNode(
            CubeNode(
                engine,
                size = Size(0.008f, 0.008f, 0.002f),
                materialInstance = yAxisTipMat
            ).apply {
                position = Position(0f, GRAPH_BASE_ELEVATION, localZ)
            }
        )
    }

    val zHalfRange = gridData.zHalfRange
    listOf(-zHalfRange, -zHalfRange / 2.0, zHalfRange / 2.0, zHalfRange).forEach { value ->
        val localY = GRAPH_BASE_ELEVATION + (value / zHalfRange).toFloat() * (2.0f * GRAPH_VERTICAL_SCALE)
        rootNode.addChildNode(
            CubeNode(
                engine,
                size = Size(0.008f, 0.002f, 0.008f),
                materialInstance = zAxisTipMat
            ).apply {
                position = Position(0f, localY, 0f)
            }
        )
    }

    // 3. Subtle reference frame at z = 0 (zero plane)
    val frameThickness = 0.0012f
    listOf(-GRAPH_HALF_SPAN, GRAPH_HALF_SPAN).forEach { edge ->
        rootNode.addChildNode(
            CubeNode(
                engine,
                size = Size(GRAPH_HALF_SPAN * 2f, frameThickness, frameThickness),
                materialInstance = zeroPlaneMaterial
            ).apply {
                position = Position(0f, GRAPH_BASE_ELEVATION, edge)
            }
        )
        rootNode.addChildNode(
            CubeNode(
                engine,
                size = Size(frameThickness, frameThickness, GRAPH_HALF_SPAN * 2f),
                materialInstance = zeroPlaneMaterial
            ).apply {
                position = Position(edge, GRAPH_BASE_ELEVATION, 0f)
            }
        )
    }

    // 4. Sleek 3D Pins planted directly on the smooth surface (poi.x = mathX, poi.y = mathY, poi.z = mathZ)
    equation.pointsOfInterest.forEach { poi ->
        val shouldShow = when (poi.type) {
            PointType.MAXIMA -> showMaxima
            PointType.MINIMA -> showMinima
            PointType.ZERO -> showZeros
            PointType.INFLECTION -> true
        }
        if (!shouldShow) return@forEach

        val worldX = ((poi.x / xDomain) * GRAPH_HALF_SPAN)
            .toFloat().coerceIn(-GRAPH_HALF_SPAN, GRAPH_HALF_SPAN)
        val worldZ = ((poi.y / yDomain) * GRAPH_HALF_SPAN)
            .toFloat().coerceIn(-GRAPH_HALF_SPAN, GRAPH_HALF_SPAN)
        val clampedMathZ = poi.z.coerceIn(-zHalfRange, zHalfRange).toFloat()
        val surfaceY = GRAPH_BASE_ELEVATION + (clampedMathZ / zHalfRange.toFloat()) * (2.0f * GRAPH_VERTICAL_SCALE)

        val poiMat = when (poi.type) {
            PointType.MAXIMA -> maximaPoiMat
            PointType.MINIMA -> minimaPoiMat
            PointType.ZERO -> zeroPoiMat
            PointType.INFLECTION -> axisMaterial
        }

        val pinHeight = if (poi.type == PointType.ZERO) 0.030f else 0.044f

        rootNode.addChildNode(
            CubeNode(
                engine = engine,
                size = Size(0.014f, 0.003f, 0.014f),
                materialInstance = poiMat
            ).apply {
                position = Position(worldX, surfaceY + 0.002f, worldZ)
            }
        )

        rootNode.addChildNode(
            CubeNode(
                engine = engine,
                size = Size(0.0025f, pinHeight, 0.0025f),
                materialInstance = axisMaterial
            ).apply {
                position = Position(worldX, surfaceY + pinHeight * 0.5f, worldZ)
            }
        )

        rootNode.addChildNode(
            CubeNode(
                engine = engine,
                size = Size(0.012f, 0.012f, 0.012f),
                materialInstance = poiMat
            ).apply {
                position = Position(worldX, surfaceY + pinHeight, worldZ)
            }
        )
    }

    return rootNode
}

private fun axisTickValues(domain: Double): List<Double> {
    val half = domain / 2.0
    return listOf(-domain, -half, 0.0, half, domain)
}

private fun formatAxisNumber(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", value)
    }
}
