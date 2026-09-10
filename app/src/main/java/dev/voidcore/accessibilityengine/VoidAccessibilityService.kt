package dev.voidcore.accessibilityengine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class VoidAccessibilityService : AccessibilityService() {
 companion object { @Volatile var current: VoidAccessibilityService? = null; private set }
 override fun onServiceConnected() { current=this }
 override fun onDestroy() { if(current===this) current=null; super.onDestroy() }
 override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
 override fun onInterrupt() = Unit
 fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)
 fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)
 fun isVoidCoreForeground(): Boolean = rootInActiveWindow?.packageName?.toString() == packageName
 fun tapText(text: String): Boolean {
  if(text.isBlank()) return false
  val nodes=rootInActiveWindow?.findAccessibilityNodeInfosByText(text).orEmpty()
  val target=nodes.firstOrNull { it.isVisibleToUser } ?: return false
  var node: AccessibilityNodeInfo?=target
  while(node!=null) { if(node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true; node=node.parent }
  return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
 }
 private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
  if(node==null) return null
  if(node.isVisibleToUser && node.isEditable && (node.isFocused || node.isAccessibilityFocused)) return node
  for(i in 0 until node.childCount) findEditable(node.getChild(i))?.let { return it }
  if(node.isVisibleToUser && node.isEditable) return node
  return null
 }
 fun typeIntoFocused(text: String): Boolean {
  if(text.isBlank()) return false
  val root=rootInActiveWindow ?: return false
  val node=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
   ?.takeIf { it.isEditable }
   ?: findEditable(root)
   ?: return false
  val args=Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text) }
  if(node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) && node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)) return true
  if(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)) return true
  val clipboard=getSystemService(ClipboardManager::class.java)
  clipboard.setPrimaryClip(ClipData.newPlainText("Void Core",text))
  node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
  return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
 }
 private fun scrollableNodes(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
  if(node==null) return
  if(node.isVisibleToUser && node.isScrollable) out += node
  for(i in 0 until node.childCount) scrollableNodes(node.getChild(i),out)
 }
 private fun gestureScrollDown(): Boolean {
  val dm=resources.displayMetrics
  val x=dm.widthPixels*0.5f
  val path=Path().apply { moveTo(x,dm.heightPixels*0.72f); lineTo(x,dm.heightPixels*0.28f) }
  val stroke=GestureDescription.StrokeDescription(path,0,350)
  return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(),null,null)
 }
 fun scrollForward(): Boolean {
  val nodes=mutableListOf<AccessibilityNodeInfo>()
  scrollableNodes(rootInActiveWindow,nodes)
  for(node in nodes) {
   if(node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
   if(node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)) return true
  }
  return gestureScrollDown()
 }
}
