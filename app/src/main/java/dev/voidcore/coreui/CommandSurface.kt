package dev.voidcore.coreui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun CommandSurface(attached: Boolean, onSubmit: (String) -> Unit, onMic: () -> Unit, onAttachment: () -> Unit, model: CoreRenderModel? = null) {
 var text by rememberSaveable { mutableStateOf("") }
 val keyboard=LocalSoftwareKeyboardController.current
 val connection=remember { Path() }
 fun submit() { if(text.isNotBlank()) { onSubmit(text); text=""; keyboard?.hide() } }
 Column {
 if(model!=null) Canvas(Modifier.fillMaxWidth().height(18.dp)) {
  if(!model.settings.reduceTransparency) {
   val strength=model.energy.value*(1f-model.dismissal.value)
   val path=connection.apply { reset(); moveTo(size.width*.5f,0f); cubicTo(size.width*.5f,size.height*.6f,size.width*.6f,size.height*.8f,size.width*.72f,size.height) }
   drawPath(path,Brush.verticalGradient(listOf(Color.Transparent,model.tint.value.copy(alpha=.32f*strength))),style=Stroke(.8.dp.toPx()))
  }
 }
 Column(Modifier.fillMaxWidth().luxuryPanel(elevated=true).padding(18.dp)) {
  Text("C O M M A N D   /   I N P U T",color=VoidPalette.Accent,fontSize=9.sp)
  Spacer(Modifier.height(16.dp))
  BasicTextField(value=text,onValueChange={ text=it },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics { contentDescription="Type a command for a visual response preview" },
   textStyle=TextStyle(color=VoidPalette.Ink,fontSize=16.sp,lineHeight=23.sp),cursorBrush=SolidColor(VoidPalette.Accent),maxLines=4,
   keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={ submit() }),
   decorationBox={ inner -> Box { if(text.isEmpty()) Text("What’s on your mind?",color=VoidPalette.Muted,fontSize=16.sp); inner() } })
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
   TextButton(onClick=onAttachment,modifier=Modifier.semantics { contentDescription="Toggle mock attachment; does not access files" }) { Text("＋ Attach",fontSize=12.sp) }
   TextButton(onClick=onMic,modifier=Modifier.semantics { contentDescription="Preview listening; microphone stays off" }) { Text("◉  Mic",fontSize=12.sp) }
   Spacer(Modifier.weight(1f))
   TextButton(onClick={ submit() },enabled=text.isNotBlank(),modifier=Modifier.semantics { contentDescription="Submit visual demo command" }) { Text("Send  ↗",fontSize=12.sp) }
  }
  HorizontalDivider(color=VoidPalette.Line,modifier=Modifier.padding(bottom=12.dp))
  Text(if(attached) "sample.pdf · placeholder context" else "Context ready for a future phase · demo only",color=VoidPalette.Muted,fontSize=10.sp)
 }
}
}
