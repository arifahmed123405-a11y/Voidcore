package dev.voidcore.coreui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.*

val LocalVisualSettings = staticCompositionLocalOf { VisualSettings() }

/** Static room illumination is cached; it adds no independent animation clock. */
fun Modifier.spatialScene(settings: VisualSettings): Modifier = drawWithCache {
 val solid=settings.reduceTransparency
 val light=Offset(size.width*.72f,size.height*.26f)
 val radius=max(size.width,size.height)*.75f
 val atmosphere=Brush.radialGradient(listOf(Color(0xFF1B3150),Color(0xFF10192A),Color(0xFF080C14),VoidPalette.Background),light,radius)
 val floor=Path().apply {
  moveTo(-size.width*.2f,size.height*.64f)
  cubicTo(size.width*.15f,size.height*.47f,size.width*.68f,size.height*.88f,size.width*1.2f,size.height*.53f)
 }
 onDrawBehind {
  if(solid) drawRect(VoidPalette.Background) else {
   drawRect(atmosphere)
   drawPath(floor,Color(0xFF394D6A).copy(alpha=.20f),style=Stroke(.6.dp.toPx()))
   // Sparse fixed dust: no constant decorative movement, even at High quality.
   if(settings.quality!=VisualQuality.LOW) for(i in 0 until 23) {
    val x=((i* .618034f)%1f)*size.width
    val y=((i* .381966f+.13f)%1f)*size.height
    drawCircle(Color(0xFFA7C6EA).copy(alpha=if(i%4==0) .16f else .06f),.55.dp.toPx(),Offset(x,y))
   }
   drawLine(Color(0xFF8FA9C8).copy(alpha=.07f),Offset(size.width*.09f,0f),Offset(size.width*.09f,size.height),.5.dp.toPx())
  }
 }
}

/** Shared opaque-backed glass treatment. Reduce Transparency removes reflections and shadows. */
@Composable fun Modifier.luxuryPanel(radius: Int = 26, elevated: Boolean = false): Modifier {
 val solid=LocalVisualSettings.current.reduceTransparency
 val economical=LocalVisualSettings.current.quality==VisualQuality.LOW
 val shape=RoundedCornerShape(radius.dp)
 return this
  .then(if(solid || economical) Modifier else Modifier.shadow(if(elevated) 18.dp else 8.dp,shape,clip=false))
  .clip(shape)
  .background(if(solid) Brush.linearGradient(listOf(VoidPalette.Surface,VoidPalette.Surface)) else
   Brush.linearGradient(listOf(Color(0xFF2A3850),Color(0xFF182236),Color(0xFF101723),Color(0xFF0B1018))))
  .border(.75.dp,if(solid) Brush.linearGradient(listOf(VoidPalette.Line,VoidPalette.Line)) else
   Brush.linearGradient(listOf(Color(0xFFA9D1FF).copy(alpha=.52f),Color(0xFF566D8A).copy(alpha=.22f),Color(0xFF9A86FF).copy(alpha=.32f),Color(0xFFF0A1D6).copy(alpha=.18f))),shape)
  .drawWithCache {
   val reflection=Brush.linearGradient(listOf(Color.Transparent,Color(0xFFD9ECFF).copy(alpha=.48f),Color(0xFFA78BFA).copy(alpha=.16f),Color.Transparent))
   onDrawWithContent {
    drawContent()
    if(!solid) drawLine(reflection,Offset(size.width*.14f,1.dp.toPx()),Offset(size.width*.74f,1.dp.toPx()),.65.dp.toPx())
   }
  }
}
