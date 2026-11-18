package com.example.projeto_grupo.ui.ar

import android.graphics.Color
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.projeto_grupo.ui.EquationViewModel
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
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
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ARSceneScreen(
    viewModel: EquationViewModel,
    modifier: Modifier = Modifier
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberARCameraNode(engine)
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 100_000f
    }
    val childNodes = rememberNodes()

    var frame by remember { mutableStateOf<Frame?>(null) }
    var modelPlaced by remember { mutableStateOf(false) }
    var isTrackingLost by remember { mutableStateOf(false) }
    var anchorNodeRef by remember { mutableStateOf<AnchorNode?>(null) }
    var equationNodeRef by remember { mutableStateOf<Node?>(null) }

    // Target vs. smoothed scale and quaternion orientation
    var targetScale by remember { mutableFloatStateOf(1.0f) }
    var smoothScale by remember { mutableFloatStateOf(1.0f) }

    var targetQuaternion by remember { mutableStateOf(Quaternion()) }
    var smoothQuaternion by remember { mutableStateOf(Quaternion()) }

    var isScaling by remember { mutableStateOf(false) }
    var lastPinchSpan by remember { mutableFloatStateOf(0f) }

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
            // Show surface grid before placement OR while ARCore is trying to relocalize
            planeRenderer = !modelPlaced || isTrackingLost,
            childNodes = childNodes,
            sessionConfiguration = { _, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.focusMode = Config.FocusMode.AUTO
                config.depthMode = Config.DepthMode.DISABLED
            },
            onSessionUpdated = { _, updatedFrame ->
                frame = updatedFrame

                // Check if the anchor lost tracking (e.g., phone was placed face-down on table)
                if (modelPlaced) {
                    val anchorState = anchorNodeRef?.anchor?.trackingState
                    when (anchorState) {
                        TrackingState.TRACKING -> {
                            isTrackingLost = false
                        }
                        TrackingState.PAUSED -> {
                            isTrackingLost = true
                        }
                        TrackingState.STOPPED, null -> {
                            // Anchor permanently invalidated by ARCore -> reset cleanly
                            anchorNodeRef?.detachAnchor()
                            childNodes.clear()
                            anchorNodeRef = null
                            equationNodeRef = null
                            modelPlaced = false
                            isTrackingLost = false
                        }
                    }
                }

                // Smoothly interpolate scale and 3D orientation every frame
                equationNodeRef?.let { node ->
                    if (abs(targetScale - smoothScale) > 0.0005f) {
                        smoothScale += (targetScale - smoothScale) * 0.25f
                        node.scale = Scale(smoothScale)
                    }
                    smoothQuaternion = nlerpQuaternion(smoothQuaternion, targetQuaternion, 0.25f)
                    node.quaternion = smoothQuaternion
                }
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, _ ->
                    if (!modelPlaced) {
                        val hitResult = frame?.hitTest(motionEvent.x, motionEvent.y)
                            ?.firstOrNull { hit ->
                                val plane = hit.trackable as? Plane
                                plane != null &&
                                        plane.trackingState == TrackingState.TRACKING &&
                                        plane.isPoseInPolygon(hit.hitPose) &&
                                        plane.extentX >= 0.25f &&
                                        plane.extentZ >= 0.25f
                            }

                        val anchor = runCatching { hitResult?.createAnchor() }.getOrNull()

                        if (anchor != null) {
                            val anchorNode = AnchorNode(engine = engine, anchor = anchor).apply {
                                isPositionEditable = false
                                isRotationEditable = false
                                isScaleEditable = false
                            }
                            val equationNode = createEquation3DNode(engine, materialLoader)

                            anchorNode.addChildNode(equationNode)
                            childNodes += anchorNode

                            anchorNodeRef = anchorNode
                            equationNodeRef = equationNode
                            targetScale = 1.0f
                            smoothScale = 1.0f
                            targetQuaternion = Quaternion()
                            smoothQuaternion = Quaternion()
                            isTrackingLost = false
                            modelPlaced = true
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
                },
                onScroll = { _, currentEvent, _, distance ->
                    if (modelPlaced && !isTrackingLost && !isScaling) {
                        val eqNode = equationNodeRef ?: return@rememberOnGestureListener
                        val anchorQuat = anchorNodeRef?.worldQuaternion ?: Quaternion()
                        val camQuat = cameraNode.worldQuaternion
                        val camPos = cameraNode.worldPosition

                        val modelCenter = Float3(
                            eqNode.worldPosition.x,
                            eqNode.worldPosition.y + 0.08f * smoothScale,
                            eqNode.worldPosition.z
                        )
                        val (centerX, centerY) = projectWorldToScreen(
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

                        val trackballRadius = (screenWidthPx * 0.35f * smoothScale).coerceIn(140f, 3000f)

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
                        }
                    }
                }
            )
        )

        if (modelPlaced) {
            SmallFloatingActionButton(
                onClick = {
                    anchorNodeRef?.detachAnchor()
                    childNodes.clear()
                    anchorNodeRef = null
                    equationNodeRef = null
                    isTrackingLost = false
                    modelPlaced = false
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reposicionar modelo")
            }
        }

        FloatingActionButton(
            onClick = { viewModel.setBottomSheetVisible(true) },
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
                    .padding(24.dp),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp
            ) {
                Text(
                    text = "Mova a câmara lentamente sobre uma superfície com textura até formar uma grelha e toque para ancorar.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (isTrackingLost) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 6.dp
            ) {
                Text(
                    text = "A relocalizar... Mova a câmara lentamente sobre a superfície ou toque no botão de atualizar acima para reposicionar.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun projectWorldToScreen(
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

private fun createEquation3DNode(
    engine: com.google.android.filament.Engine,
    materialLoader: io.github.sceneview.loaders.MaterialLoader
): Node {
    val rootNode = Node(engine = engine).apply {
        isEditable = false
    }

    val axisMaterial = materialLoader.createColorInstance(Color.parseColor("#FFFFFF"))
    val peakMaterial = materialLoader.createColorInstance(Color.parseColor("#3F51B5"))
    val valleyMaterial = materialLoader.createColorInstance(Color.parseColor("#00BCD4"))

    // X, Y, Z coordinate axes
    rootNode.addChildNode(
        CubeNode(engine, size = Size(0.42f, 0.004f, 0.004f), materialInstance = axisMaterial).apply {
            position = Position(0f, 0.002f, 0f)
        }
    )
    rootNode.addChildNode(
        CubeNode(engine, size = Size(0.004f, 0.004f, 0.42f), materialInstance = axisMaterial).apply {
            position = Position(0f, 0.002f, 0f)
        }
    )
    rootNode.addChildNode(
        CubeNode(engine, size = Size(0.004f, 0.22f, 0.004f), materialInstance = axisMaterial).apply {
            position = Position(0f, 0.11f, 0f)
        }
    )

    // 3D surface points for y = 0.09 + 0.06 * sin(14x) * cos(14z)
    val range = -0.18f..0.18f
    val step = 0.03f
    var x = range.start
    while (x <= range.endInclusive) {
        var z = range.start
        while (z <= range.endInclusive) {
            val wave = sin(x * 14f) * cos(z * 14f)
            val y = 0.09f + 0.06f * wave

            rootNode.addChildNode(
                CubeNode(
                    engine = engine,
                    size = Size(0.018f, 0.018f, 0.018f),
                    materialInstance = if (wave >= 0f) peakMaterial else valleyMaterial
                ).apply {
                    position = Position(x, y, z)
                }
            )
            z += step
        }
        x += step
    }

    return rootNode
}