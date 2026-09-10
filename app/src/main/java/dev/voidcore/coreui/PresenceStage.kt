package dev.voidcore.coreui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import dev.voidcore.overlayservice.PresenceMode
import kotlin.math.abs

/** One persistent renderer node changes bounds/position; there is no crossfade between four widgets. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable fun PresenceStage(model: CoreRenderModel, mode: PresenceMode, visible: Boolean, onSwipeDismiss: () -> Unit, renderer: CoreRenderer = EnergyVeilRenderer, scene: PreviewScene = PreviewScene.NORMAL) {
 val reduced = model.settings.reduceMotion
 val targetPath = remember { Path() }
 val spec: FiniteAnimationSpec<Float> = if(reduced) snap() else spring(dampingRatio = 1f, stiffness = 150f)
 val w by animateFloatAsState(when(mode) { PresenceMode.FULL_PRESENCE -> .96f; PresenceMode.COMPACT -> .66f; PresenceMode.CAPSULE -> .92f; PresenceMode.EDGE_AGENT -> .19f }, spec, label = "Presence width")
 val modeHeight=when(mode) { PresenceMode.FULL_PRESENCE -> .65f; PresenceMode.COMPACT -> .44f; PresenceMode.CAPSULE -> .20f; PresenceMode.EDGE_AGENT -> .20f }
 val h by animateFloatAsState(if(scene==PreviewScene.KEYBOARD) minOf(modeHeight,.44f) else if(scene==PreviewScene.LOCK_SCREEN) minOf(modeHeight,.34f) else modeHeight, spec, label = "Presence height")
 val centerY by animateFloatAsState(if(scene == PreviewScene.KEYBOARD) .40f else if(scene == PreviewScene.LOCK_SCREEN) .72f else if(mode == PresenceMode.EDGE_AGENT) .48f else .66f, spec, label = "Presence depth")
 val edge by animateFloatAsState(if(mode == PresenceMode.EDGE_AGENT) 1f else 0f, spec, label = "Edge docking")
 val capsule by animateFloatAsState(if(mode == PresenceMode.CAPSULE) 1f else 0f, spec, label = "Capsule compression")
 val direction by animateFloatAsState(if(model.settings.edge == DockEdge.RIGHT) 1f else -1f, spec, label = "Dock side")
 val visibility by animateFloatAsState(if(visible) 1f else 0f, tween(if(reduced) 100 else 160), label = "Surface entry")
 BoxWithConstraints(Modifier.fillMaxWidth().height(470.dp).clip(RoundedCornerShape(32.dp)).border(1.dp,VoidPalette.Line,RoundedCornerShape(32.dp)).spatialScene(model.settings)) {
  val stageWidth=maxWidth; val stageHeight=maxHeight
  if(scene != PreviewScene.LOCK_SCREEN && scene != PreviewScene.FULLSCREEN && scene != PreviewScene.ACTION_TARGET) Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement=Arrangement.spacedBy(21.dp)) {
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("9:41",color=VoidPalette.Ink,fontSize=12.sp); Text("PHONE SURFACE · MOCK",color=VoidPalette.Muted,fontSize=9.sp) }
   Spacer(Modifier.height(12.dp))
   Text("A little room\nto think.",color=Color(0xFFB6C2CE),fontSize=28.sp,lineHeight=33.sp)
   HorizontalDivider(color=VoidPalette.Line)
   Text("TODAY",color=VoidPalette.Muted,fontSize=10.sp,letterSpacing=2.sp)
   Text("Design notes",color=Color(0xFF99A7B5),fontSize=16.sp)
   Text("Latest document · 2 pages",color=VoidPalette.Muted,fontSize=12.sp)
   HorizontalDivider(color=VoidPalette.Line)
   Text("Afternoon review",color=Color(0xFF99A7B5),fontSize=16.sp)
  }
  if(scene == PreviewScene.LOCK_SCREEN) Column(Modifier.align(Alignment.TopCenter).padding(top=60.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text("9:41",color=VoidPalette.Ink,fontSize=52.sp)
   Text("MOCK LOCK SCREEN",color=VoidPalette.Muted,fontSize=9.sp,letterSpacing=2.sp)
   Text("Unlock to continue",color=VoidPalette.Muted,fontSize=12.sp)
   Text("Biometric / permission wait · visual only",color=VoidPalette.Muted,fontSize=9.sp)
  }
  if(scene == PreviewScene.FULLSCREEN) Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
   Text("FULLSCREEN",color=VoidPalette.Muted,fontSize=22.sp,letterSpacing=3.sp)
   Text("Mock content · presence stays at the edge",color=VoidPalette.Muted,fontSize=10.sp)
  }
  if(scene == PreviewScene.KEYBOARD) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp).background(Color(0xFF141C29)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Text("MOCK KEYBOARD · NOT AN INPUT METHOD",color=VoidPalette.Muted,fontSize=8.sp)
   for(row in listOf("Q W E R T Y U I O P","A S D F G H J K L","Z X C V B N M")) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
     row.split(" ").forEach { Text(it,color=VoidPalette.Muted,fontSize=11.sp) }
    }
   }
   Box(Modifier.align(Alignment.CenterHorizontally).width(120.dp).height(15.dp).background(VoidPalette.Line,RoundedCornerShape(4.dp)))
  }
  if(scene == PreviewScene.ACTION_TARGET) Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text("ACTION TARGET · MOCK",color=VoidPalette.Muted,fontSize=10.sp,letterSpacing=2.sp)
   Text("A visual intention.",color=VoidPalette.Ink,fontSize=24.sp)
   Text("Highlight only. Nothing is activated.",color=VoidPalette.Muted,fontSize=11.sp)
  }
  if(scene == PreviewScene.ACTION_TARGET) Text("Preview control",Modifier.align(Alignment.TopCenter).padding(top=164.dp).background(VoidPalette.Raised,RoundedCornerShape(12.dp)).padding(12.dp),color=VoidPalette.Ink,fontSize=12.sp)
  Canvas(Modifier.matchParentSize()) {
   if(visibility > 0f) {
    val location=Offset(size.width*(.5f+direction*edge*((1f-w)*.5f-.018f)),size.height*centerY)
    if(scene == PreviewScene.ACTION_TARGET) {
     val target=Offset(size.width*.5f,188.dp.toPx())
     val action=model.project.value*visibility*(1f-model.dismissal.value)
     val pulse=if(reduced) .35f else kotlin.math.sin(model.progress.value*kotlin.math.PI.toFloat()).coerceAtLeast(0f)
     val path=targetPath.apply { reset(); moveTo(location.x,location.y); cubicTo(location.x+size.width*.16f,location.y-size.height*.14f,target.x-size.width*.1f,target.y+size.height*.10f,target.x,target.y) }
     drawPath(path,model.tint.value.copy(alpha=action*(.23f+.35f*pulse)),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
     drawRoundRect(model.tint.value.copy(alpha=action*(.16f+.25f*pulse)),topLeft=target-Offset(66.dp.toPx(),22.dp.toPx()),size=androidx.compose.ui.geometry.Size(132.dp.toPx(),44.dp.toPx()),cornerRadius=androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),style=androidx.compose.ui.graphics.drawscope.Stroke(.8.dp.toPx()))
    }
    if(!model.settings.reduceTransparency) {
     drawRect(Brush.radialGradient(listOf(Color.Black.copy(alpha=.78f*visibility),Color.Transparent),location,size.width*.8f))
     val arrival=(1f-model.assembly.value)*visibility
     val x=if(direction>0f) size.width else 0f
     drawCircle(Brush.radialGradient(listOf(model.tint.value.copy(alpha=.24f*arrival),Color.Transparent),Offset(x,size.height*.65f),size.width*.5f),size.width*.5f,Offset(x,size.height*.65f))
     drawLine(model.tint.value.copy(alpha=arrival*.8f),Offset(x,size.height*.48f),Offset(x,size.height*.77f),2.dp.toPx())
    }
   }
  }
  val xFraction=(1f-w)*.5f + direction*edge*((1f-w)*.5f-.018f)
  val yFraction=centerY-h*.5f
  Box(Modifier.offset(x=stageWidth*xFraction,y=stageHeight*yFraction).width(stageWidth*w).height(stageHeight*h)
   .alpha(visibility)
   .background(if(model.settings.reduceTransparency) Color(0xFF10161D) else VoidPalette.Surface.copy(alpha=.78f*capsule+.70f*edge),RoundedCornerShape(28.dp))
   .border(.75.dp,Brush.linearGradient(listOf(Color(0xFFADCDEC).copy(alpha=(capsule+edge).coerceIn(0f,1f)*.32f),Color.Transparent,Color(0xFF657DA5).copy(alpha=(capsule+edge).coerceIn(0f,1f)*.16f))),RoundedCornerShape(28.dp))
   .semantics { if(!visible) invisibleToUser() else contentDescription = "${mode.title} visual preview. Swipe horizontally for fast dismissal." }
   .pointerInput(visible) {
    var travelled=0f
    detectHorizontalDragGestures(onDragStart={ travelled=0f },onDragEnd={ if(visible && abs(travelled)>48.dp.toPx()) onSwipeDismiss() },onHorizontalDrag={ change, amount -> change.consume(); travelled+=amount })
   }) {
   renderer.Render(model,Modifier.fillMaxSize(),CoreForm(capsule,edge))
   if(capsule>.05f) Column(Modifier.align(Alignment.CenterStart).padding(start=76.dp,end=12.dp).alpha(capsule),verticalArrangement=Arrangement.spacedBy(3.dp)) {
    Text(when(model.snapshot.state) {
     dev.voidcore.assistantstate.AssistantState.WAITING_FOR_USER -> "Waiting for confirmation…"
     dev.voidcore.assistantstate.AssistantState.PLANNING -> "Searching files…"
     else -> "Opening Spotify…"
    },color=VoidPalette.Ink,fontSize=13.sp,maxLines=2)
    Text("VISUAL DEMO ONLY",color=VoidPalette.Muted,fontSize=8.sp,letterSpacing=1.sp)
   }
  }
  if(visible && capsule<.2f && edge<.2f && scene != PreviewScene.KEYBOARD && scene != PreviewScene.LOCK_SCREEN) Text(if(model.snapshot.state == dev.voidcore.assistantstate.AssistantState.LISTENING) "Listening…" else model.snapshot.state.name.lowercase().replace('_',' '),
   Modifier.align(Alignment.BottomCenter).padding(bottom=24.dp),color=VoidPalette.Accent,fontSize=12.sp)
  if(!visible) Text("Ready to invoke",Modifier.align(Alignment.BottomCenter).padding(24.dp),color=VoidPalette.Muted,fontSize=12.sp)
 }
}
