#!/usr/bin/env python3
"""Source/configuration audit only. Does not resolve dependencies, compile Kotlin or run Android."""
from pathlib import Path
import re
import sqlite3
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/dev/voidcore'

def source(name):
    return (SRC / name).read_text()

def check(condition, description):
    if not condition:
        raise AssertionError(description)
    print('PASS:', description)

settings = (ROOT / 'settings.gradle.kts').read_text()
root_build = (ROOT / 'build.gradle.kts').read_text()
app_build = (ROOT / 'app/build.gradle.kts').read_text()
properties = (ROOT / 'gradle.properties').read_text()
check('include(":app")' in settings and all(x in settings for x in ['google()', 'mavenCentral()', 'gradlePluginPortal()']), 'Gradle module and plugin/dependency repositories')
plugins = dict(re.findall(r'id\("([^"]+)"\) version "([^"]+)"', root_build))
check(plugins['com.android.application'] == '8.7.3', 'AGP pin retained')
check(all(plugins[x] == '2.4.20' for x in ['org.jetbrains.kotlin.android', 'org.jetbrains.kotlin.plugin.compose', 'org.jetbrains.kotlin.plugin.serialization']), 'Kotlin, Compose compiler and serialization plugin versions aligned')
check(all('id("' + name + '")' in app_build for name in plugins), 'All required app plugins applied')
check('buildFeatures { compose = true }' in app_build and 'kotlinCompilerExtensionVersion' not in app_build, 'Kotlin 2 Compose plugin setup without conflicting legacy compiler pin')
check('compileSdk = 35' in app_build and 'targetSdk = 35' in app_build and 'minSdk = 26' in app_build and 'buildToolsVersion = "35.0.0"' in app_build, 'SDK and Build Tools pins')
check(app_build.count('JavaVersion.VERSION_17') == 2 and 'JvmTarget.JVM_17' in app_build, 'Java and Kotlin bytecode target JDK 17')
check('androidx.room:' not in app_build and 'kapt(' not in app_build and 'com.google.devtools.ksp' not in root_build + app_build, 'No Room/KAPT/KSP annotation-processor build path')
check(all(x in app_build for x in ['androidx.navigation:navigation-compose:2.8.5', 'androidx.compose:compose-bom:2024.12.01', 'androidx.compose.animation:animation', 'junit:junit:4.13.2', 'kotlinx-coroutines-test:1.9.0', 'androidx.test:runner:1.6.2']), 'Navigation, animation and test dependencies declared')
for file in ['scripts/gradle-bootstrap.sh', 'scripts/gradle-bootstrap.ps1', '.github/workflows/android.yml']:
    check('8.9' in (ROOT / file).read_text(), file + ' uses Gradle 8.9')

check('namespace = "dev.voidcore"' in app_build and 'applicationId = "dev.voidcore"' in app_build, 'Namespace/application ID alignment')
files = list(SRC.rglob('*.kt'))
for file in files:
    expected = 'dev.voidcore.' + '.'.join(file.relative_to(SRC).parts[:-1])
    check(re.search(r'^package\s+' + re.escape(expected) + r'\s*$', file.read_text(), re.M) is not None, 'Package path: ' + str(file.relative_to(SRC)))
android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
application = manifest.find('application')
check(application is not None and application.get(android + 'allowBackup') == 'false', 'App backup disabled')
for item in [application, *application.findall('activity')]:
    name = item.get(android + 'name')
    check((SRC / (name.lstrip('.').replace('.', '/') + '.kt')).is_file(), 'Manifest class resolves: ' + name)
check(application.find('activity').get(android + 'exported') == 'true', 'Launcher activity exported explicitly')
permissions = {x.get(android + 'name') for x in manifest.findall('uses-permission')}
check({'android.permission.INTERNET','android.permission.RECORD_AUDIO','android.permission.CAMERA','android.permission.WRITE_SETTINGS'}.issubset(permissions) and any(x.get(android + 'name') == '.accessibilityengine.VoidAccessibilityService' for x in application.findall('service')), 'Phase 3 permissions and opt-in AccessibilityService declared')
styles = ET.parse(ROOT / 'app/src/main/res/values/styles.xml').getroot()
check(application.get(android + 'theme').removeprefix('@style/') in [x.get('name') for x in styles.findall('style')], 'Manifest theme reference resolves')

states = ['SLEEPING', 'INVOKING', 'LISTENING', 'THINKING', 'PLANNING', 'ACTING', 'WAITING_FOR_USER', 'SPEAKING', 'SUCCESS', 'ERROR_RECOVERY', 'DISMISSING']
state_source = source('assistantstate/AssistantState.kt')
actual = re.search(r'enum class AssistantState\s*\{([^}]+)\}', state_source).group(1)
check([x.strip() for x in actual.split(',')] == states, 'Exactly 11 canonical assistant states')
all_source = '\n'.join(f.read_text() for f in files)
check(all_source.count('enum class AssistantState ') == 1 and len(re.findall(r'=\s*AssistantStateEngine\(\)', all_source)) == 1, 'One semantic enum and one production engine construction')
for name in ['voiceengine/VoiceEngine.kt', 'voiceengine/AudioProfiles.kt', 'workflowengine/Workflow.kt', 'overlayservice/Presence.kt']:
    check('StateSynchronizedAdapter' in source(name), name + ' references shared state contract')
check('PresencePreviewController(assistantState)' in source('app/VoidApplication.kt'), 'Presence controller receives the application state instance')
ui = source('app/MainActivity.kt')
check('app.assistantState.snapshot.collectAsStateWithLifecycle()' in ui and 'app.presence.layout.collectAsStateWithLifecycle()' in ui, 'UI and diagnostics observe app-owned state/layout')
renderer = source('coreui/AiCore.kt')
poses = source('coreui/VisualModels.kt')
runtime = source('coreui/CoreRuntime.kt')
stage = source('coreui/PresenceStage.kt')
screens = source('app/VisualScreens.kt')
controller = source('app/VisualDemoViewModel.kt')
motions = re.findall(r'AssistantState\.(\w+) -> CorePose\((.*)\)', poses)
check([x[0] for x in motions] == states and len(set(x[1] for x in motions)) == 11, 'All 11 states have distinct visual choreography targets')
check(all(x in renderer for x in ['interface CoreRenderer', 'class VeilGeometry', 'path.cubicTo', 'voidCenter', 'Soft internal plasma', 'Dense micro-particle atmosphere', 'Close-orbit particle skin']), 'Replaceable soft-glass renderer with stable sphere and dense particle-lattice veil')
check(all(x in runtime for x in ['updateTransition(snapshot.state', 'transition.animateFloat', 'transition.animateColor', 'repeatOnLifecycle(Lifecycle.State.STARTED)', 'assembly.animateTo', 'dismissal.animateTo']), 'Canonical state drives smoothly animated properties and lifecycle-gated clock')
presence = source('overlayservice/Presence.kt')
check(re.findall(r'\b(?:FULL_PRESENCE|COMPACT|CAPSULE|EDGE_AGENT)\("([^"]+)"\)', presence) == ['Full Presence', 'Compact', 'Capsule', 'Edge Agent'], 'Four corrected presence modes')
check('renderer.Render(model,Modifier.fillMaxSize(),CoreForm(capsule,edge))' in stage and stage.count('renderer.Render(') == 1, 'One persistent renderer node supports all presence forms')

voice = source('voiceengine/VoiceProfiles.kt')
check(re.findall(r'VoiceProfile\("[^"]+", "([^"]+)"', voice) == ['Neutral Core', 'Void', 'Architect', 'Spectral', 'Titan', 'Omega'], 'Six required voice presets')
voice_fields = ['name','pitch','speakingSpeed','cadence','pauseLength','energy','warmth','metallicResonance','syntheticDepth','harmonicLayer','bassPresence','brightness','clarity','breathiness','reverb','stereoWidth','digitalTexture','glitchAmount','sentenceEndModulation','emotionIntensity','questionInflection','warningIntensity','confirmationEmphasis']
check(all(re.search(r'val\s+' + x + r'\s*:', voice) for x in voice_fields), 'Every required voice profile property')
check('interface VoiceProfileCatalog' in voice and 'findByName(name: String)' in voice and 'data class Ambiguous' in voice, 'Custom names and unambiguous future name-resolution contract')
audio = source('voiceengine/AudioProfiles.kt')
check(all(re.search(r'val\s+' + x + r'\s*:', audio) for x in ['name','wakeSound','listeningCue','thinkingAmbience','actionCue','confirmationCue','successTone','warningTone','errorTone','notificationSoundFamily','dismissalSound','hapticPairing']), 'All audio slots and custom profile names')
provider = source('providerrouter/AiProvider.kt')
check(all(x in provider for x in ['fun generate(', 'fun stream(', 'fun supportsVision()', 'fun supportsTools()', 'fun healthCheck()', 'val available:', 'val priority:', 'allowFailover']), 'Provider capabilities and routing contracts')
# Current source review guard: only the executor may invoke a tool adapter.
execute_files = [str(f.relative_to(SRC)) for f in files if re.search(r'\btool\.execute\(', f.read_text())]
check(sorted(execute_files) == ['securityengine/Security.kt'], 'Tool dispatch occurs only in the gated executor')
planner = source('agentbrain/AgentPlanner.kt')
check(not re.search(r'^import\s+(android\.|dev\.voidcore\.securityengine|dev\.voidcore\.androidtools)', planner, re.M), 'Planner has no Android, executor or security implementation import')
security = source('securityengine/Security.kt')
check(all('interface ' + x in security for x in ['PermissionChecker','TaskScopeValidator','RiskClassifier','ConfirmationPolicy','SecureAppRestrictions','ProtectedActionChecker','ActionAuditLog']), 'All security contracts exist')
check('Phase3SecurityGate' in source('securityengine/Phase3Security.kt') and 'nativeToolRegistry(this)' in source('app/VoidApplication.kt'), 'Phase 3 low-risk native registry is gated; sensitive categories remain outside the allowlist')
check(security.index('gate.evaluate(frozen)') < security.index('if (!check.allowed)') < security.index('tool.execute(frozen)'), 'Validation and denial precede dispatch')
check('Collections.unmodifiableMap(HashMap(request.arguments))' in security, 'Validated arguments isolated from caller mutation')
workflow = source('workflowengine/Workflow.kt')
check(all(x in workflow for x in ['val order:', 'val dependencies:', 'fun pause(', 'fun resume(', 'RetryPolicy', 'FAILED', 'confirmationRequired', 'StepVerifier', 'PARTIALLY_COMPLETED', 'CheckpointStore']), 'Workflow lifecycle and checkpoint contracts')

# Validate the processor-free app-private foundation store.
store = source('memory/LocalDatabase.kt')
entity_names = ['AssistantPreferenceEntity','VoiceProfileEntity','AudioProfileEntity','TrustRuleEntity','PeopleAliasEntity','RecentContextEntity','TaskWorkflowEntity','AutomationEntity','ActivityEntity']
check(all('@Serializable data class ' + name in store for name in entity_names), 'All nine local foundation record groups are serializable')
check(all(x in store for x in ['class FoundationDao(context: Context)', 'MutableStateFlow', 'getSharedPreferences', 'class LocalDatabase private constructor', 'class LocalAuditLog']), 'Processor-free app-private persistence and audit log')
check('androidx.room' not in store and '@Dao' not in store and '@Database' not in store, 'Memory layer has no Room/KAPT/KSP dependency')
check(not re.search(r'api.?key|secret|password|token', '\n'.join(re.findall(r'@Serializable data class [^\n]+', store)), re.I), 'No credential field in local foundation records')

routes = source('app/Routes.kt')
check(re.findall(r'\w+\("([^"]+)", true\)', routes) == ['Home','Automations','Activity','You'], 'Four main navigation destinations')
future = ['Conversation','Agent Workspace','Projects','Universal Search','Memory','Connections','Trust & Safety','Voice Lab','Audio Lab','AI Providers','Privacy','Offline','Performance','Camera/Vision','Notifications','Calls','Diagnostics']
check(re.findall(r'\w+\("([^"]+)"\)', routes) == future, 'All 17 future routes retained')
diag = source('app/Diagnostics.kt')
check(all(x in diag for x in ['IMPLEMENTED("IMPLEMENTED")', 'MOCKED("MOCKED")', 'NOT_AVAILABLE("NOT AVAILABLE")']), 'Explicit diagnostics availability labels')
check(all('ModuleDiagnostic("' + name + '"' in diag for name in ['voice-engine','provider-router','accessibility-engine','vision-engine','overlay-service','notification-engine','workflow-engine','security-engine','memory / database']), 'All requested diagnostics modules represented')
tiers = re.findall(r'(LOW|STANDARD|HIGH)\("\w+", (\d+), (\d+), (\d+), (\d+)\)', poses)
check([t[0] for t in tiers] == ['LOW', 'STANDARD', 'HIGH'] and
      all(0 < int(t[i]) <= limit for t in tiers for i, limit in enumerate([160, 16, 64, 8], start=1)) and
      all(x in poses for x in ['reduceMotion', 'reduceTransparency']), 'Visual accessibility settings and bounded quality tiers')
check('val transitionScale' in renderer and 'model.open.value' not in renderer and 'val sphereRadius = radius * .690f' in renderer and 'if (!reduced && !solid)' in renderer and 'if(active && !settings.reduceMotion)' in runtime, 'Physical sphere scale is state-invariant; Reduce Motion stops continuous particle ticking')
check(all(x in stage for x in ['spring(dampingRatio = 1f', 'detectHorizontalDragGestures', 'PHONE SURFACE', 'if(!visible) invisibleToUser()']), 'Morphing, fake phone, swipe dismissal and hidden-surface semantics')
check(all(x in controller for x in ['fun fullSequence()', 'fun dismiss(', 'fun morphModes()', 'fun submit(', 'fun select(', 'sequence?.cancel()', 'if(token == generation)']), 'Cancelable mock demo controller and stale-job guard')
sequence = re.search(r'val sequence = listOf\((.*?)\n  \)', controller, re.S).group(1)
check(re.findall(r'AssistantState\.(\w+) to', sequence) == [x for x in states if x != 'ERROR_RECOVERY'], 'Full sequence follows requested order (recovery separately selectable)')
check('surfaceVisible = false' in controller and 'state(AssistantState.SLEEPING)' in controller, 'Demo finishes by hiding surface and returning to canonical sleep')
check(not re.search(r'^import (android\.|dev\.voidcore\.(androidtools|providerrouter|securityengine|workflowengine))', controller, re.M), 'Visual demo controller has no Android, provider or tool/execution dependency')
check(all(x in screens for x in ['PresenceHome', 'ConversationPreview', 'AgentWorkspacePreview', 'VisualDiagnostics', 'Reduce Motion', 'Reduce Transparency', 'Listening amplitude:', 'Speaking amplitude:', 'Phase:', 'Progress:', 'Renderer:']), 'Requested visual screens, controls and diagnostics metrics')
check(all(x in source('voiceengine/VisualFeedback.kt') for x in ['WAKE', 'LISTENING', 'ACTION', 'CONFIRMATION', 'SUCCESS', 'WARNING', 'DISMISSAL', 'HapticIntent', 'VisualFeedbackSink']), 'Audio/haptic event hooks only')
print('\nSTATIC SOURCE/CONFIGURATION AUDIT PASSED. Android sync, Kotlin/LiteRT compilation, tests and device rendering remain UNVERIFIED.')
