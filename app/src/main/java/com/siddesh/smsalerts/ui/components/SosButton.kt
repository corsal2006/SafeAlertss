package com.siddesh.smsalerts.ui.components

import android.view.MotionEvent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.EmergencyRedDark
import com.siddesh.smsalerts.ui.theme.EmergencyRedLight
import com.siddesh.smsalerts.utils.HapticUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SosButton(
    modifier: Modifier = Modifier,
    size: Dp = 210.dp,
    holdDurationMs: Long = 2000L,
    requireHold: Boolean = true,
    onSosTriggered: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isPressed by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    fun startHolding() {
        if (!requireHold) {
            HapticUtils.vibrateSosHoldCompleted(context)
            onSosTriggered()
            return
        }

        isPressed = true
        HapticUtils.vibrateTick(context)

        holdJob?.cancel()
        holdJob = coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val fraction = (elapsed.toFloat() / holdDurationMs).coerceIn(0f, 1f)
                holdProgress = fraction

                if (fraction >= 1.0f) {
                    HapticUtils.vibrateSosHoldCompleted(context)
                    isPressed = false
                    holdProgress = 0f
                    onSosTriggered()
                    break
                }
                delay(16)
            }
        }
    }

    fun releaseHolding() {
        if (requireHold) {
            isPressed = false
            holdJob?.cancel()
            holdProgress = 0f
        }
    }

    Box(
        modifier = modifier
            .size(size + 40.dp)
            .semantics {
                contentDescription = "Emergency SOS Button. Press and hold for two seconds to initiate emergency alert."
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size + 36.dp)
                .scale(pulseScale)
        ) {
            drawCircle(
                color = EmergencyRed.copy(alpha = glowAlpha),
                radius = size.toPx() / 2 + 16.dp.toPx()
            )
        }

        Canvas(
            modifier = Modifier.size(size + 14.dp)
        ) {
            if (holdProgress > 0f) {
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = holdProgress * 360f,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .shadow(
                    elevation = if (isPressed) 8.dp else 20.dp,
                    shape = CircleShape,
                    ambientColor = EmergencyRed,
                    spotColor = EmergencyRedDark
                )
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            EmergencyRedLight,
                            EmergencyRed,
                            EmergencyRedDark
                        )
                    )
                )
                .border(
                    width = 3.dp,
                    color = Color.White.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .pointerInteropFilter { motionEvent ->
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startHolding()
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            releaseHolding()
                            true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "SEND EMERGENCY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = Color.White.copy(alpha = 0.95f)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "ALERT",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isPressed && holdProgress > 0f) {
                        "HOLDING ${(holdProgress * 100).toInt()}%"
                    } else if (requireHold) {
                        "HOLD 2S TO SEND"
                    } else {
                        "TAP TO SEND"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
