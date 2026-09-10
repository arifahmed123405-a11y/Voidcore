package dev.voidcore.coreui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.imageResource
import dev.voidcore.R
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import kotlin.math.*

/** Replacement seam: future GPU/shader renderers consume this same timeline and presentation. */
interface CoreRenderer {
 @Composable fun Render(model: CoreRenderModel, modifier: Modifier, form: CoreForm = CoreForm())
}
data class CoreForm(val capsule: Float = 0f, val edge: Float = 0f)
private fun smooth(a: Float, b: Float, x: Float): Float {
 val t = ((x-a)/(b-a)).coerceIn(0f,1f); return t*t*(3f-2f*t)
}
/** Reusable scratch geometry. No new Path is allocated for each strand or frame. */
private class VeilGeometry(val count: Int) {
 val angle = FloatArray(count) { it * 2f * PI.toFloat() / count }
 val cosine = FloatArray(count) { cos(angle[it]) }
 val sine = FloatArray(count) { sin(angle[it]) }
 val x = FloatArray(count)
 val y = FloatArray(count)
 val path = Path()
 fun contour(center: Offset, radius: Float, scale: Float, phase: Float, deform: Float, symmetry: Float, stretch: Float, squash: Float): Path {
  for(i in 0 until count) {
   val a = angle[i]
   // Even harmonics provide the stable completion moment; the void never becomes circular.
   val r = radius*scale*(1f + .13f*cos(a*2f-.5f) + (1f-symmetry*.82f)*(.10f*sin(a*3f+phase)+.045f*cos(a*5f-phase)) + deform*.08f*sin(a*4f+phase))
   x[i] = center.x + cosine[i]*r*stretch
   y[i] = center.y + sine[i]*r*1.13f*squash
  }
  path.reset(); path.moveTo(x[0], y[0])
  // Closed Catmull-Rom spline produces a liquid boundary from cached sample points.
  for(i in 0 until count) {
   val prev=(i+count-1)%count; val next=(i+1)%count; val after=(i+2)%count
   path.cubicTo(x[i]+(x[next]-x[prev])/6f, y[i]+(y[next]-y[prev])/6f,
    x[next]-(x[after]-x[i])/6f, y[next]-(y[after]-y[i])/6f, x[next], y[next])
  }
  path.close(); return path
 }
}
object EnergyVeilRenderer : CoreRenderer {
 @Composable override fun Render(model: CoreRenderModel, modifier: Modifier, form: CoreForm) {
  val settings = model.settings
  val geometry = remember(settings.quality) { VeilGeometry(settings.quality.points) }
  val strand = remember { Path() }
  val field = remember(settings.quality) { DimensionalField(settings.quality) }
  // Phase 5G+: luminous energy veil derived from the target concept frames
  // (ethereal multi-hue energy orb). Dark/solid center is suppressed in the
  // asset so the texture can rotate as atmospheric energy around the soft core.
  val luminousVeil = ImageBitmap.imageResource(R.drawable.voidcore_luminous_veil)

  Canvas(modifier.semantics {
   contentDescription = "Void Core. ${model.snapshot.state.name.lowercase().replace('_',' ')}."
  }) {
   if (size.minDimension <= 0f) return@Canvas

   val reduced = settings.reduceMotion
   val solid = settings.reduceTransparency
   val phase = if (reduced) .7f else model.phase.value
   val time = if (reduced) .7f else model.clock.value
   // Independent rates keep the plasma, membrane and particle field from moving as one loop.
   val motion = if (reduced) .7f else time * (.62f + model.activity.value * .78f)
   val slowMotion = if (reduced) .4f else time * (.23f + model.activity.value * .18f)
   val counterMotion = if (reduced) .2f else -time * (.34f + model.activity.value * .27f)
   val listen = model.listeningEnvelope()
   val speak = model.speakingEnvelope()
   val p = model.progress.value
   val assembly = model.assembly.value
   val dismiss = model.dismissal.value
   val contract = if (reduced) 0f else smooth(.12f, .72f, dismiss)
   val filament = if (reduced) 0f else smooth(.52f, .84f, dismiss)
   val retract = if (reduced) 0f else smooth(.80f, 1f, dismiss)
   val alive = if (reduced) 1f - dismiss else 1f - smooth(.93f, 1f, dismiss)
   if (alive <= 0f) return@Canvas

   val tint = model.tint.value
   val energy = model.energy.value
   val electric = Color(0xFF70C9FF)
   val ice = Color(0xFFE7F5FF)
   val deepBlue = Color(0xFF2D67E8)
   val violet = Color(0xFF8676FF)
   val magenta = Color(0xFFE88FD5)
   val peach = Color(0xFFFFBE91)
   val ember = Color(0xFFFF8E67)

   val symmetry = model.symmetry.value * (if (reduced) .6f else 1f - smooth(.72f, 1f, model.completion.value))
   val completionPulse = if (reduced) 0f else sin(model.completion.value * PI.toFloat()).coerceAtLeast(0f) * symmetry
   val reform = if (reduced) .2f else sin(p * PI.toFloat()).coerceAtLeast(0f) * model.recovery.value
   val reactive = if (reduced) 0f else listen * .055f + speak * .085f
   val breathing = if (reduced) 0f else (
    sin(time * .78f) * (.018f + model.attentive.value * .012f) +
     sin(time * .31f + 1.2f) * .007f
   )
   // Phase 5G: semantic states never resize the physical sphere. Only invocation and
   // dismissal are allowed to scale the whole presence in/out. Listening, thinking,
   // speaking, success, etc. change light, speed and particle activity instead.
   val transitionScale = if (reduced) {
    1f - dismiss
   } else {
    (.06f + .94f * smooth(.06f, .86f, assembly)) * (1f - contract * .90f)
   }
   val radius = size.minDimension * (.334f + .046f * form.edge) * transitionScale
   val direction = if (settings.edge == DockEdge.RIGHT) 1f else -1f
   // Keep the solid sphere spatially anchored. Motion belongs to the particle veil,
   // not to the sphere itself.
   val drift = Offset.Zero
   val center = Offset(
    size.width * (.5f - .32f * form.capsule) + direction *
     (retract * .58f + (if (reduced) 0f else 1f - smooth(0f, .65f, assembly)) * .48f) * size.width,
    size.height * .49f
   ) + drift
   val shellAlpha = alive * (if (reduced) 1f else smooth(.12f, .72f, assembly)) * (1f - filament)
   // Never let normal semantic states make the presence look dead. State changes
   // alter hue, particle speed and activity; invocation/dismissal control visibility.
   val luminousEnergy = (.72f + energy * .42f).coerceIn(.72f, 1.10f) * shellAlpha
   val stretch = 1f + form.capsule * .22f
   val squash = 1f - filament * .86f

   // Shared physical-sphere geometry. Keep these in the Canvas scope because both
   // the rotated glass body and the later close-orbit particle layer use them.
   val voidCenter = center
   val sphereRadius = radius * .690f

   // Draw the concept-derived luminous veil as a moving transparent energy layer.
   // The sphere is never scaled by this function; only the surrounding veil rotates.
   fun DrawScope.drawLuminousVeil(rotation: Float, scale: Float, alpha: Float) {
    if (solid || alpha <= .002f) return
    val px = (radius * scale).roundToInt().coerceAtLeast(2)
    val dst = androidx.compose.ui.unit.IntOffset(
     (voidCenter.x - px * .5f).roundToInt(),
     (voidCenter.y - px * .5f).roundToInt()
    )
    rotate(rotation, voidCenter) {
     drawImage(
      image = luminousVeil,
      srcOffset = androidx.compose.ui.unit.IntOffset(0, 0),
      srcSize = androidx.compose.ui.unit.IntSize(luminousVeil.width, luminousVeil.height),
      dstOffset = dst,
      dstSize = androidx.compose.ui.unit.IntSize(px, px),
      alpha = alpha.coerceIn(0f, 1f),
      blendMode = BlendMode.Screen
     )
    }
   }

   fun contour(
    scale: Float,
    localPhase: Float = .35f + sin(motion * .36f) * .18f + sin(counterMotion * .19f) * .06f,
    localCenter: Offset = center,
    stable: Float = symmetry
   ): Path = geometry.contour(
    localCenter, radius, scale, localPhase,
    reactive * 1.55f + reform * .7f, stable, stretch, squash
   )

   // The new visual language is built from luminous masses and membranes first.
   // No thin wireframe should dominate the read of the sphere.
   if (!solid) {
    drawCircle(
     Brush.radialGradient(
      listOf(
       electric.copy(alpha = .18f * energy * shellAlpha),
       violet.copy(alpha = .085f * energy * shellAlpha),
       Color.Transparent
      ), center, radius * 2.35f
     ), radius * 2.35f, center
    )
    drawCircle(
     Brush.radialGradient(
      listOf(peach.copy(alpha = .075f * energy * alive), magenta.copy(alpha = .025f * alive), Color.Transparent),
      center + Offset(radius * (.54f + .06f * sin(slowMotion)), -radius * (.24f + .05f * cos(motion * .31f))), radius * 1.28f
     ), radius * 1.28f, center + Offset(radius * (.54f + .06f * sin(slowMotion)), -radius * (.24f + .05f * cos(motion * .31f)))
    )
    drawCircle(
     Brush.radialGradient(
      listOf(deepBlue.copy(alpha = .085f * energy * alive), Color.Transparent),
      center + Offset(-radius * (.58f + .05f * cos(counterMotion)), radius * (.20f + .05f * sin(slowMotion * .73f))), radius * 1.48f
     ), radius * 1.48f, center + Offset(-radius * (.58f + .05f * cos(counterMotion)), radius * (.20f + .05f * sin(slowMotion * .73f)))
    )
   }

   // Invocation remains recognizable, but even the entry line is a soft light filament.
   if (!reduced && assembly < .82f) {
    val arrival = (1f - smooth(.48f, .82f, assembly)) * alive
    val length = size.minDimension * (.025f + .13f * sin(assembly * PI.toFloat()))
    if (!solid) drawLine(
     electric.copy(alpha = .10f * arrival), center - Offset(length * 1.9f, 0f), center + Offset(length * 1.9f, 0f),
     12.dp.toPx(), cap = StrokeCap.Round
    )
    drawLine(
     ice.copy(alpha = .42f * arrival), center - Offset(length, 0f), center + Offset(length, 0f),
     1.1.dp.toPx(), cap = StrokeCap.Round
    )
   }

   field.update(
    center, radius, motion, listen + speak, model.branch.value, model.project.value,
    reform, symmetry, if (reduced) 1f else assembly, squash, model.listening.value, model.speaking.value
   )

   // Back field plus a concept-derived particle/ribbon veil. The texture rotates as
   // one atmospheric layer while the procedural particles use different speeds.
   field.draw(this, false, tint, luminousEnergy, shellAlpha, radius, solid, 0f)
   if (!solid) {
    // Stronger concept veil contribution so the energy form dominates the read
    // (closer to the pure luminous presence of the target concept frames).
    drawLuminousVeil(
     rotation = if (reduced) -7f else -7f + time * (2.6f + model.activity.value * 1.4f),
     scale = 3.22f,
     alpha = .68f * luminousEnergy
    )
   }

   rotate(-14f + if (reduced) 0f else sin(slowMotion * .72f) * 4.2f + sin(counterMotion * .19f) * 1.3f, center) {
    // Filled membrane layers establish one coherent glass body.
    val shellCount = when (settings.quality) {
     VisualQuality.LOW -> 2
     VisualQuality.STANDARD -> 3
     VisualQuality.HIGH -> 4
    }
    for (layer in shellCount downTo 1) {
     val f = layer.toFloat() / shellCount
     val scale = 1.02f + layer * .035f
     val localCenter = center + Offset(
      sin(slowMotion * (.72f + layer * .04f) + layer) * radius * (.016f + layer * .002f),
      cos(counterMotion * (.45f + layer * .03f) + layer) * radius * (.014f + layer * .002f)
     )
     val fillAlpha = (.025f + .026f * f) * shellAlpha
     val brush = if (solid) {
      Brush.radialGradient(listOf(Color(0xFF16222D), Color(0xFF0A1018)), localCenter, radius * 1.35f)
     } else {
      Brush.linearGradient(
       listOf(
        electric.copy(alpha = fillAlpha * .88f),
        Color(0xFF0B1422).copy(alpha = fillAlpha * .56f),
        violet.copy(alpha = fillAlpha),
        magenta.copy(alpha = fillAlpha * .48f),
        Color.Transparent
       ),
       localCenter - Offset(radius * 1.1f, radius * .85f),
       localCenter + Offset(radius * 1.0f, radius * .92f)
      )
     }
     drawPath(contour(scale, motion * (.24f + layer * .025f) + counterMotion * .035f + layer * .37f, localCenter), brush)
    }

    // Soft internal plasma. Multiple broad glow masses merge into a living volume.
    // Keep plasmaStrength in the surrounding Canvas scope because the later aurora pass
    // intentionally reuses the same state-scaled intensity.
    // Elevated for the more luminous energy-orb target look.
    val plasmaStrength = shellAlpha * (.42f + energy * .72f)
    if (!solid) {
     val blobs = when (settings.quality) {
      VisualQuality.LOW -> 4
      VisualQuality.STANDARD -> 6
      VisualQuality.HIGH -> 9
     }
     for (i in 0 until blobs) {
      val directionSign = if (i % 2 == 0) 1f else -1f
      val localRate = .34f + (i % 4) * .075f
      val a = motion * localRate * directionSign + i * 2.399963f + sin(slowMotion * .7f + i) * .14f
      val orbit = radius * (.20f + (i % 4) * .095f + .035f * sin(counterMotion * .56f + i))
      val pt = center + Offset(cos(a) * orbit, sin(a * .93f + i * .11f) * orbit * (.68f + (i % 3) * .07f))
      val r = radius * (.31f + (i % 3) * .075f) * (1f + .08f * sin(time * (.53f + (i % 4) * .07f) + i))
      val color = when (i % 5) {
       0, 1 -> electric
       2 -> ice
       3 -> violet
       else -> magenta
      }
      val alpha = when (i % 4) {
       0 -> .16f
       1 -> .12f
       2 -> .09f
       else -> .075f
      } * plasmaStrength
      drawCircle(
       Brush.radialGradient(
        listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * .32f), Color.Transparent),
        pt, r
       ), r, pt
      )
     }

     // Warm living accent: tiny peach heat pockets, not a purple wash.
     val warmA = center + Offset(cos(motion * .31f + .9f) * radius * .50f, sin(counterMotion * .27f + .9f) * radius * .36f)
     drawCircle(
      Brush.radialGradient(listOf(peach.copy(alpha = .13f * plasmaStrength), ember.copy(alpha = .035f * plasmaStrength), Color.Transparent), warmA, radius * .36f),
      radius * .36f, warmA
     )
    }

    // Moving interior aurora: broad counter-rotating light volumes make the sphere
    // feel continuously alive even when the semantic state is otherwise calm.
    if (!solid) {
     val auroraCount = when (settings.quality) {
      VisualQuality.LOW -> 3
      VisualQuality.STANDARD -> 5
      VisualQuality.HIGH -> 7
     }
     for (i in 0 until auroraCount) {
      val sign = if (i % 2 == 0) 1f else -1f
      val a = motion * (.20f + i * .025f) * sign + i * 1.618f
      val pt = center + Offset(cos(a) * radius * (.30f + (i % 3) * .09f), sin(a * .87f) * radius * (.22f + (i % 4) * .055f))
      val glowRadius = radius * (.40f + (i % 3) * .065f)
      val auroraColor = when (i % 5) {
       0, 1 -> electric
       2 -> ice
       3 -> violet
       else -> peach
      }
      drawCircle(
       Brush.radialGradient(
        listOf(auroraColor.copy(alpha = .075f * plasmaStrength), auroraColor.copy(alpha = .025f * plasmaStrength), Color.Transparent),
        pt, glowRadius
       ), glowRadius, pt
      )
     }
    }

    // Phase 5D: the intelligence core is a deliberately ROUND, solid 3D sphere.
    // The previous irregular void shape made the object read like a hole; the target is
    // a dense physical sphere with luminous energy wrapping around it.
    // Phase 5E: stronger luminous separation around the physical sphere.
    if (!solid) {
     drawCircle(
      Brush.radialGradient(
       listOf(
        ice.copy(alpha = .12f * shellAlpha * energy),
        electric.copy(alpha = .24f * shellAlpha * energy),
        violet.copy(alpha = .12f * shellAlpha * energy),
        magenta.copy(alpha = .045f * shellAlpha * energy),
        Color.Transparent
       ),
       voidCenter, sphereRadius * 1.72f
      ),
      sphereRadius * 1.72f, voidCenter
     )
    }

    // Soft luminous core (Phase 5G+ evolution toward pure energy presence).
    // Reduced solid black mass so the form reads closer to the ethereal
    // energy-orb language of the target concept art while still anchoring
    // a round volumetric center for the veil and particles to orbit.
    drawCircle(
     Brush.radialGradient(
      listOf(
       Color(0xFF3A6EB8).copy(alpha = .55f * shellAlpha),
       Color(0xFF1A3A6E).copy(alpha = .70f * shellAlpha),
       Color(0xFF0A1A30).copy(alpha = .82f * shellAlpha),
       Color(0xFF040A14).copy(alpha = .88f * shellAlpha),
       Color(0xFF010308).copy(alpha = .92f * shellAlpha)
      ),
      voidCenter + Offset(-sphereRadius * .38f, -sphereRadius * .42f),
      sphereRadius * 1.42f
     ),
     sphereRadius, voidCenter
    )

    if (!solid) {
     // Large cool surface sheen: the sphere itself receives the surrounding light.
     val sheen = voidCenter + Offset(-sphereRadius * .33f, -sphereRadius * .31f)
     drawCircle(
      Brush.radialGradient(
       listOf(
        ice.copy(alpha = .38f * luminousEnergy),
        electric.copy(alpha = .24f * luminousEnergy),
        deepBlue.copy(alpha = .10f * luminousEnergy),
        Color.Transparent
       ),
       sheen, sphereRadius * .72f
      ),
      sphereRadius * .72f, sheen
     )

     // Secondary blue caustic across the lower-left hemisphere adds curved glass depth.
     val blueCaustic = voidCenter + Offset(-sphereRadius * .18f, sphereRadius * .22f)
     drawCircle(
      Brush.radialGradient(
       listOf(
        electric.copy(alpha = .24f * luminousEnergy),
        violet.copy(alpha = .10f * luminousEnergy),
        Color.Transparent
       ),
       blueCaustic, sphereRadius * .58f
      ),
      sphereRadius * .58f, blueCaustic
     )

     // Soft annular rim LIGHT, not a stroked circle. This creates a bright glass edge
     // while keeping the target free from visibly hard geometry.
     val rimBrush = Brush.radialGradient(
      colorStops = arrayOf(
       0.00f to Color.Transparent,
       0.68f to Color.Transparent,
       0.82f to deepBlue.copy(alpha = .08f * luminousEnergy),
       0.90f to electric.copy(alpha = .30f * luminousEnergy),
       0.955f to ice.copy(alpha = .38f * luminousEnergy),
       1.00f to Color.Transparent
      ),
      center = voidCenter,
      radius = sphereRadius * 1.17f
     )
     drawCircle(rimBrush, sphereRadius * 1.17f, voidCenter)

     // Warm reflected light on the right hemisphere mirrors the concept's orange/peach glow.
     val warmSurface = voidCenter + Offset(sphereRadius * .44f, sphereRadius * .20f)
     drawCircle(
      Brush.radialGradient(
       listOf(
        peach.copy(alpha = .34f * luminousEnergy),
        ember.copy(alpha = .17f * luminousEnergy),
        magenta.copy(alpha = .055f * luminousEnergy),
        Color.Transparent
       ),
       warmSurface, sphereRadius * .49f
      ),
      sphereRadius * .49f, warmSurface
     )

     // Particle ribbons: many tiny moving points form the orbital strands. There are no
     // continuous arc strokes here, so even close to the screen the trails remain granular,
     // luminous and alive instead of reading as blurred hard lines.
     val ribbonBands = when (settings.quality) {
      VisualQuality.LOW -> 5
      VisualQuality.STANDARD -> 8
      VisualQuality.HIGH -> 11
     }
     val pointsPerRibbon = when (settings.quality) {
      VisualQuality.LOW -> 52
      VisualQuality.STANDARD -> 82
      VisualQuality.HIGH -> 112
     }
     for (band in 0 until ribbonBands) {
      val tilt = (-42f + band * (84f / max(1, ribbonBands - 1))) * PI.toFloat() / 180f
      val ct = cos(tilt); val st = sin(tilt)
      val directionSign = if (band % 2 == 0) 1f else -1f
      val speed = .17f + (band % 4) * .035f
      val bandPhase = motion * speed * directionSign + band * .71f
      val rx = sphereRadius * (1.06f + (band % 4) * .072f)
      val ry = sphereRadius * (.66f + (band % 3) * .105f)
      val ribbonColor = when (band % 6) {
       0 -> ice
       1 -> electric
       2 -> deepBlue
       3 -> violet
       4 -> magenta
       else -> peach
      }

      for (j in 0 until pointsPerRibbon) {
       val a = j * 2f * PI.toFloat() / pointsPerRibbon + bandPhase
       val localX = cos(a) * rx
       val localY = sin(a) * ry
       val warpedX = localX + sin(a * 3f + counterMotion * .18f + band) * sphereRadius * .022f
       val warpedY = localY + cos(a * 2f - slowMotion * .21f + band) * sphereRadius * .018f
       val px = warpedX * ct - warpedY * st
       val py = warpedX * st + warpedY * ct
       val pt = voidCenter + Offset(px, py)

       val depth = ((sin(a + band * .53f) + 1f) * .5f)
       val twinkle = .55f + .45f * sin(time * (.66f + (j % 5) * .04f) + j * .43f + band).absoluteValue
       val frontBoost = .35f + depth * .75f
       val alpha = (.34f * luminousEnergy * twinkle * frontBoost).coerceIn(0f, .94f)
       val warmPoint = ribbonColor == peach || (j + band * 7) % 29 == 0
       val pointColor = if (warmPoint && ribbonColor != peach) peach else ribbonColor
       val pointRadius = when {
        (j + band) % 31 == 0 -> .62.dp.toPx()
        (j + band) % 11 == 0 -> .40.dp.toPx()
        else -> .22.dp.toPx()
       }
       drawCircle(pointColor.copy(alpha = alpha), pointRadius, pt)

       // Sparse luminous kernels provide glow without reconnecting the dots into lines.
       if ((j + band * 5) % 19 == 0) {
        val glow = sphereRadius * if (warmPoint) .055f else .040f
        drawCircle(
         Brush.radialGradient(
          listOf(pointColor.copy(alpha = alpha * .56f), pointColor.copy(alpha = alpha * .12f), Color.Transparent),
          pt,
          glow
         ),
         glow,
         pt
        )
       }
      }
     }
    }

    // A second counter-rotating veil pass provides foreground sparkle and depth.
    // Its center remains transparent, so the physical sphere stays fixed and readable.
    if (!solid) {
     drawLuminousVeil(
      rotation = if (reduced) 11f else 11f - time * (1.7f + model.activity.value * .9f),
      scale = 3.02f,
      alpha = .30f * luminousEnergy
     )
    }

    // State-specific internal intelligence stays soft: glows and nodes instead of line webs.
    if (model.branch.value > .01f && !solid) {
     val branchAlpha = model.branch.value * shellAlpha
     for (i in 0 until 5) {
      val a = motion * (.23f + (i % 2) * .07f) + i * 2.399963f
      val pt = center + Offset(cos(a) * radius * (.40f + (i % 2) * .16f), sin(a) * radius * (.32f + (i % 3) * .08f))
      drawCircle(
       Brush.radialGradient(listOf(ice.copy(alpha = .17f * branchAlpha), violet.copy(alpha = .075f * branchAlpha), Color.Transparent), pt, radius * .10f),
       radius * .10f, pt
      )
     }
    }
   }

   // Foreground ribbons complete the 3D read, but remain blurred and translucent.
   field.draw(
    this, true, tint, luminousEnergy, shellAlpha, radius, solid,
    if (reduced) 0f else (model.particles.value + model.project.value * .3f).coerceIn(0f, 1f)
   )

   // No stroked outer membrane in Phase 5G. The shell edge is formed entirely by
   // light masses, the animated veil texture and micro-particle density.
   if (!solid && shellAlpha > .01f) {
    // Lens highlights are luminous masses rather than hard streaks.
    val hi = center + Offset(-radius * (.43f + .04f * sin(slowMotion * .9f)), -radius * (.62f + .05f * cos(motion * .26f)))
    drawCircle(
     Brush.radialGradient(listOf(ice.copy(alpha = .27f * energy * shellAlpha), electric.copy(alpha = .07f * shellAlpha), Color.Transparent), hi, radius * .32f),
     radius * .32f, hi
    )
    val warmHi = center + Offset(radius * (.45f + .05f * cos(counterMotion * .7f)), radius * (.39f + .05f * sin(slowMotion * .8f)))
    drawCircle(
     Brush.radialGradient(listOf(peach.copy(alpha = .12f * energy * shellAlpha), magenta.copy(alpha = .04f * shellAlpha), Color.Transparent), warmHi, radius * .28f),
     radius * .28f, warmHi
    )
   }

   // WAITING is poised attention expressed as two soft side glows, not orbit strokes.
   val attention = model.attentive.value * shellAlpha
   if (attention > .01f && !solid) {
    for (side in listOf(-1f, 1f)) {
     val pt = center + Offset(side * radius * .83f, 0f)
     drawCircle(
      Brush.radialGradient(listOf(electric.copy(alpha = .12f * attention), violet.copy(alpha = .045f * attention), Color.Transparent), pt, radius * .42f),
      radius * .42f, pt
     )
    }
   }

   // ACTING projects a moving particle beam rather than a continuous hard rail.
   if (model.project.value > .01f && !solid) {
    val project = model.project.value * shellAlpha
    val start = center + Offset(radius * .72f, 0f)
    val length = radius * (1.0f + .24f * sin(p * PI.toFloat()))
    for (i in 0 until 28) {
     val t = i / 27f
     val wobble = sin(time * .9f + i * .73f) * radius * .012f
     val pt = start + Offset(length * t, wobble)
     val fade = (1f - t) * project
     drawCircle(electric.copy(alpha = .30f * fade), .42.dp.toPx(), pt)
     if (i % 9 == 0) {
      val glow = radius * .035f
      drawCircle(Brush.radialGradient(listOf(ice.copy(alpha = .18f * fade), Color.Transparent), pt, glow), glow, pt)
     }
    }
   }

   // Dense micro-particle atmosphere. The particles are intentionally tiny and numerous;
   // only a small minority receive bloom. Motion and luminance react to state, size does not.
   if (!reduced && !solid) {
    val count = when (settings.quality) {
     VisualQuality.LOW -> 120
     VisualQuality.STANDARD -> 240
     VisualQuality.HIGH -> 420
    }
    val stateBoost = (.52f + model.particles.value * .58f + energy * .30f).coerceIn(.38f, 1.22f)
    for (i in 0 until count) {
     val base = i * 2.399963f
     val speed = .105f + (i % 7) * .021f
     val orbitDirection = if (i % 4 == 0) -1f else 1f
     val orbit = base + motion * speed * orbitDirection + sin(counterMotion * .23f + i * .37f) * .12f
     val distance = radius * (.78f + (i % 11) * .050f + .025f * sin(time * (.31f + (i % 5) * .035f) + i)) +
      (1f - assembly) * size.minDimension * .18f
     val elliptic = .76f + (i % 5) * .035f
     val pt = center + Offset(cos(orbit) * distance, sin(orbit) * distance * elliptic * (1f - filament))
     val warmParticle = i % 11 == 0 || i % 23 == 0
     val color = if (warmParticle) if (i % 2 == 0) peach else ember else when (i % 5) {
      0 -> ice
      1 -> electric
      2 -> deepBlue
      3 -> violet
      else -> tint
     }
     val twinkle = .48f + .52f * sin(time * (.58f + (i % 5) * .11f) + i * .61f).absoluteValue
     val alpha = (.16f + (i % 5) * .032f) * stateBoost * luminousEnergy * (1f - retract) * twinkle
     val dot = when {
      warmParticle && i % 3 == 0 -> .52.dp.toPx()
      i % 17 == 0 -> .42.dp.toPx()
      i % 7 == 0 -> .32.dp.toPx()
      else -> .20.dp.toPx()
     }
     drawCircle(color.copy(alpha = alpha.coerceIn(0f, .86f)), dot, pt)

     // Sparse bloom points create the luminous "cooking" effect without turning the
     // particle cloud back into thick glowing dots or hard trails.
     if (i % 21 == 0 || warmParticle && i % 4 == 0) {
      val halo = radius * if (warmParticle) .050f else .034f
      drawCircle(
       Brush.radialGradient(
        listOf(color.copy(alpha = alpha * .52f), color.copy(alpha = alpha * .12f), Color.Transparent),
        pt,
        halo
       ),
       halo,
       pt
      )
     }
    }
   }

   // Close-orbit particle skin. These micro-points are what visually "draw" the moving
   // luminous strands around the solid sphere; there are no connecting lines.
   if (!reduced && !solid) {
    val closeCount = when (settings.quality) {
     VisualQuality.LOW -> 96
     VisualQuality.STANDARD -> 180
     VisualQuality.HIGH -> 320
    }
    for (i in 0 until closeCount) {
     val warm = i % 13 == 0
     val directionSign = if (i % 3 == 0) -1f else 1f
     val a = motion * (.18f + (i % 6) * .020f) * directionSign + i * 2.399963f
     val orbitRadius = sphereRadius * (1.025f + (i % 7) * .036f)
     val yScale = .71f + (i % 4) * .055f
     val localX = cos(a) * orbitRadius
     val localY = sin(a) * orbitRadius * yScale
     val tilt = ((i % 5) - 2) * .21f
     val pt = voidCenter + Offset(
      localX * cos(tilt) - localY * sin(tilt),
      localX * sin(tilt) + localY * cos(tilt)
     )
     val color = if (warm) peach else when (i % 5) {
      0 -> ice
      1, 2 -> electric
      3 -> violet
      else -> magenta
     }
     val pulse = .56f + .44f * sin(time * (.72f + (i % 4) * .12f) + i * .47f).absoluteValue
     val depth = ((sin(a) + 1f) * .5f)
     val strength = luminousEnergy * pulse * (.46f + depth * .74f)
     val dot = when {
      warm -> .50.dp.toPx()
      i % 19 == 0 -> .44.dp.toPx()
      i % 8 == 0 -> .32.dp.toPx()
      else -> .20.dp.toPx()
     }
     drawCircle(color.copy(alpha = (.78f * strength).coerceIn(0f, .90f)), dot, pt)

     if (i % 23 == 0 || warm && i % 2 == 0) {
      val halo = sphereRadius * if (warm) .058f else .040f
      drawCircle(
       Brush.radialGradient(
        listOf(color.copy(alpha = .44f * strength), color.copy(alpha = .10f * strength), Color.Transparent),
        pt,
        halo
       ),
       halo,
       pt
      )
     }
    }
   }

   // SUCCESS releases a sparse particle halo instead of drawing a completion ring.
   if (completionPulse > .01f && !solid) {
    for (i in 0 until 42) {
     val a = i * 2f * PI.toFloat() / 42f + motion * .10f
     val releaseRadius = radius * (1.10f + model.completion.value * .22f + .025f * sin(i * 1.7f))
     val pt = center + Offset(cos(a) * releaseRadius, sin(a) * releaseRadius * .88f)
     val color = if (i % 8 == 0) peach else if (i % 3 == 0) ice else electric
     val alpha = completionPulse * alive * (.20f + .18f * sin(i.toFloat()).absoluteValue)
     drawCircle(color.copy(alpha = alpha), if (i % 8 == 0) .70.dp.toPx() else .38.dp.toPx(), pt)
    }
   }

   // Dismissal collapses the body into one soft filament.
   if (filament > .01f) {
    val half = radius * (1.8f + filament * 2f)
    if (!solid) drawLine(electric.copy(alpha = .10f * filament * alive), center - Offset(half, 0f), center + Offset(half, 0f), 12.dp.toPx(), cap = StrokeCap.Round)
    drawLine(ice.copy(alpha = .48f * filament * alive), center - Offset(half, 0f), center + Offset(half, 0f), 1.0.dp.toPx(), cap = StrokeCap.Round)
   }
  }
 }
}
@Composable fun AiCoreContainer(model: CoreRenderModel, modifier: Modifier = Modifier, renderer: CoreRenderer = EnergyVeilRenderer, height: Dp = 350.dp) {
 Box(modifier.fillMaxWidth().height(height)) {
  Canvas(Modifier.matchParentSize()) {
   val solid=model.settings.reduceTransparency
   val energy=model.energy.value*(1f-model.dismissal.value)
   val floor=Offset(size.width*.5f,size.height*.88f)
   if(!solid) {
    // A flattened pool of reflected light grounds the hovering object in the scene.
    scale(1f,.12f,floor) {
     drawCircle(Brush.radialGradient(listOf(Color(0xFF79BFFF).copy(alpha=.20f*energy),Color.Transparent),floor,size.width*.4f),size.width*.4f,floor)
    }
    drawLine(Brush.horizontalGradient(listOf(Color.Transparent,VoidPalette.Accent.copy(alpha=.22f*energy),Color.Transparent)),
     Offset(size.width*.18f,size.height*.88f),Offset(size.width*.82f,size.height*.88f),.6.dp.toPx())
   }
   // Sparse instrument framing stays static in every motion mode.
   val c=VoidPalette.Muted.copy(alpha=.23f)
   val inset=8.dp.toPx(); val length=12.dp.toPx()
   for(x in listOf(inset,size.width-inset)) {
    val sign=if(x<size.width*.5f) 1f else -1f
    drawLine(c,Offset(x,size.height*.28f),Offset(x+sign*length,size.height*.28f),.6.dp.toPx())
    drawLine(c,Offset(x,size.height*.28f),Offset(x,size.height*.28f+length),.6.dp.toPx())
   }
  }
  renderer.Render(model,Modifier.fillMaxSize())
  Text("P R E S E N C E   /   0 1",Modifier.align(Alignment.BottomStart).padding(bottom=4.dp),color=VoidPalette.Muted,fontSize=8.sp)
  Text("VISUAL PREVIEW",Modifier.align(Alignment.BottomEnd).padding(bottom=4.dp),color=VoidPalette.Muted,fontSize=8.sp)
 }
}
