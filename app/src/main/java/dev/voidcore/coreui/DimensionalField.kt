package dev.voidcore.coreui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import kotlin.math.*

/**
 * Particle-based volumetric orbital field.
 * Phase 5G intensifies the concept-derived veil while avoids continuous stroked paths so the energy veil reads
 * like thousands of luminous micro-particles flowing around a solid sphere rather
 * than hard neon lines drawn over it.
 */
internal class DimensionalField(private val quality: VisualQuality) {
 private val samples = when (quality) {
  VisualQuality.LOW -> 44
  VisualQuality.STANDARD -> 72
  VisualQuality.HIGH -> 104
 }
 private val bands = when (quality) {
  VisualQuality.LOW -> 4
  VisualQuality.STANDARD -> 6
  VisualQuality.HIGH -> 8
 }
 private val points = Array(bands) { Array(samples) { Offset.Zero } }
 private val depth = Array(bands) { FloatArray(samples) }
 private val shimmer = Array(bands) { FloatArray(samples) }

 fun update(
  center: Offset, radius: Float, time: Float, amplitude: Float, branch: Float,
  projection: Float, recovery: Float, symmetry: Float, assembly: Float, squash: Float,
  listening: Float, speaking: Float
 ) {
  for (band in 0 until bands) {
   val layer = band.toFloat()
   val rate = .18f + (band % 4) * .045f
   val counter = if (band % 2 == 0) 1f else -1f
   val localTime = time * rate * counter
   val tilt = .42f + band * .40f * (1f - symmetry * .28f) + .08f * sin(time * .24f + layer)
   val azimuth = layer * 2.39996f + localTime + .10f * sin(time * .13f + layer)
   val ct = cos(tilt); val st = sin(tilt); val ca = cos(azimuth); val sa = sin(azimuth)

   for (i in 0 until samples) {
    val a = i * 2f * PI.toFloat() / samples
    val breathe = amplitude * .012f * sin(a * 2f + time * .72f + layer)
    val curl = .035f * sin(a * 3f + time * (.31f + (band % 3) * .04f) + layer * .67f)
    val ripple = .018f * cos(a * 5f - time * (.28f + (band % 2) * .05f) + layer)
    val r = radius * (.86f + (band % 4) * .085f + curl + ripple + breathe)

    // Semantic states affect intensity/speed, not the physical sphere scale.
    val x = cos(a + .035f * sin(time * .29f + layer)) * r
    val y = sin(a) * r
    val z = y * st + sin(a * 2f + time * .47f + layer) * r * (.055f + branch * .025f)
    val yy = y * ct
    val perspective = 1f + z / max(radius, 1f) * .10f
    val spread = (1f - assembly) * (band % 3 + 1) * .13f

    points[band][i] = center + Offset(
     (x * ca - yy * sa) * perspective * (1f + projection * .10f + spread) + recovery * r * .06f * sin(layer),
     ((x * sa + yy * ca) * perspective * (1f + spread) + recovery * r * .05f * cos(layer)) * squash
    )
    depth[band][i] = z
    shimmer[band][i] = .45f + .55f * abs(sin(time * (.45f + (i % 7) * .035f) + i * .71f + layer))
   }
  }
 }

 fun draw(
  scope: DrawScope, foreground: Boolean, tint: Color, energy: Float, alpha: Float,
  radius: Float, solid: Boolean, fragments: Float
 ) = with(scope) {
  if (solid || alpha <= .001f) return@with

  val strength = (if (foreground) .88f else .56f) * alpha * (.58f + energy * .54f)
  val micro = when (quality) {
   VisualQuality.LOW -> .44f
   VisualQuality.STANDARD -> .36f
   VisualQuality.HIGH -> .30f
  }

  for (band in 0 until bands) {
   val baseColor = when (band % 7) {
    0 -> Color(0xFFF4FBFF)
    1 -> Color(0xFF8AD8FF)
    2 -> Color(0xFF4B92FF)
    3 -> Color(0xFF6D6CF4)
    4 -> Color(0xFFA58BFF)
    5 -> tint
    else -> Color(0xFFF2A8D8)
   }

   for (i in 0 until samples) {
    val isFront = depth[band][i] > radius * .025f
    if (isFront != foreground) continue

    val point = points[band][i]
    val z = (depth[band][i] / max(radius, 1f)).coerceIn(-1f, 1f)
    val depthLight = if (foreground) .65f + .35f * ((z + 1f) * .5f) else .36f + .20f * ((z + 1f) * .5f)
    val sparkle = shimmer[band][i]
    val warm = (i + band * 5) % 19 == 0
    val color = if (warm) Color(0xFFFFBC8D) else baseColor
    val particleAlpha = (strength * depthLight * (.48f + .52f * sparkle)).coerceIn(0f, .92f)

    // Most particles are deliberately sub-pixel/small; the visual trail emerges from density.
    val dotRadius = when {
     (i + band) % 23 == 0 -> micro * 1.55f
     (i + band) % 9 == 0 -> micro * 1.10f
     else -> micro
    }
    drawCircle(color.copy(alpha = particleAlpha), dotRadius, point)

    // Only a sparse subset gets bloom so the track stays particle-made, not line-made.
    if ((i + band * 3) % 17 == 0) {
     val glow = radius * if (warm) .038f else .026f
     drawCircle(
      Brush.radialGradient(
       listOf(color.copy(alpha = particleAlpha * .40f), color.copy(alpha = particleAlpha * .10f), Color.Transparent),
       point,
       glow
      ),
      glow,
      point
     )
    }

    // Activity fragments are extra luminous nodes, still point-based.
    if (foreground && fragments > .01f && (i + band * 11) % 31 == 0) {
     val glow = radius * .050f
     drawCircle(
      Brush.radialGradient(
       listOf(color.copy(alpha = strength * .32f * fragments), color.copy(alpha = strength * .08f * fragments), Color.Transparent),
       point,
       glow
      ),
      glow,
      point
     )
    }
   }
  }
 }
}
