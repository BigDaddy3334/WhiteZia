package shop.whitezia.client.ui

import android.app.Activity
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import shop.whitezia.client.R
import shop.whitezia.client.model.WhiteZiaLanguage
import shop.whitezia.client.model.WhiteZiaThemeMode

object WhiteZiaSpacing {
    val xs = 4.dp       // Extra small spacing
    val sm = 8.dp       // Small spacing
    val md = 12.dp      // Medium spacing
    val lg = 16.dp      // Large spacing
    val xl = 20.dp      // Extra large spacing
    val xxl = 24.dp     // Double extra large spacing
    val xxxl = 32.dp    // Triple extra large spacing

    // Component-specific spacing
    val cardPadding = 16.dp
    val sectionSpacing = 24.dp
    val inputSpacing = 10.dp
    val listItemSpacing = 8.dp
    val iconSpacing = 6.dp
}

object WhiteZiaAnimations {
    // Duration constants (in milliseconds)
    const val DURATION_INSTANT = 120
    const val DURATION_FAST = 180
    const val DURATION_NORMAL = 300
    const val DURATION_SLOW = 400
    const val DURATION_VERY_SLOW = 600

    // Easing functions for smooth animations
    val easingStandard = FastOutSlowInEasing
    val easingEmphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val easingDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    // Reusable animation specs
    fun <T> standardTween(duration: Int = DURATION_NORMAL) =
        tween<T>(durationMillis = duration, easing = easingStandard)

    fun <T> fastFade() =
        tween<T>(durationMillis = DURATION_FAST, easing = easingStandard)

    fun <T> slowTransition() =
        tween<T>(durationMillis = DURATION_SLOW, easing = easingEmphasized)
}

interface WhiteZiaPaletteColors {
    val Background: Color
    val Surface: Color
    val SurfaceAlt: Color
    val DropdownSurface: Color
    val Input: Color
    val Border: Color
    val Divider: Color
    val ControlBorder: Color
    val Accent: Color
    val AccentPressed: Color
    val AccentText: Color
    val AccentDim: Color
    val OnAccent: Color
    val Success: Color
    val Error: Color
    val Warning: Color
    val WarningText: Color
    val Ink: Color
    val Muted: Color
    val Pale: Color
    val SectionTitle: Color
    val FieldLabel: Color
    val Description: Color
    val Placeholder: Color
    val Disabled: Color
    val SurfaceHover: Color
    val AccentSurface: Color
    val SuccessSurface: Color
    val WarningSurface: Color
    val ErrorSurface: Color
}

object WhiteZiaPaletteDark : WhiteZiaPaletteColors {
    // Core backgrounds - deeper, richer blacks with subtle blue undertones
    override val Background = Color(0xFF0A0C10)           // Deeper pure dark
    override val Surface = Color(0xFF13161D)              // Elevated surface with better contrast
    override val SurfaceAlt = Color(0xFF0E1117)           // Subtle variation
    override val DropdownSurface = Color(0xFF1A1E28)      // Clearer dropdown distinction
    override val Input = Color(0xFF0E1117)                // Matches SurfaceAlt for consistency

    // Borders and dividers - improved hierarchy
    override val Border = Color(0xFF1C2028)               // Subtle border
    override val Divider = Color(0xFF22273A)              // More visible dividers
    override val ControlBorder = Color(0xFF2D3448)        // Clearer control borders

    // Brand colors - vibrant and modern
    override val Accent = Color(0xFF7C6FEA)               // Brighter, more vibrant purple
    override val AccentPressed = Color(0xFF6456D6)        // Deeper pressed state
    override val AccentText = Color(0xFF8A7FED)           // Lighter for text
    override val AccentDim = Color(0xFF5547C2)            // Dimmed accent
    override val OnAccent = Color(0xFFFFFFFF)             // Pure white on accent

    // Status colors - more vibrant and clear
    override val Success = Color(0xFF10D98E)              // Brighter, more energetic green
    override val Error = Color(0xFFFF5757)                // Vivid red with better visibility
    override val Warning = Color(0xFFFFC043)              // Warmer, more noticeable amber
    override val WarningText = Color(0xFFFFC043)          // Consistent warning text

    // Text colors - optimized contrast and hierarchy
    override val Ink = Color(0xFFF2F3F7)                  // Brighter white for primary text
    override val Muted = Color(0xFFB8BED6)                // Softer muted text
    override val Pale = Color(0xFF9BA3C4)                 // Subtle pale text
    override val SectionTitle = Color(0xFFD4D7E3)         // Clearer section headers
    override val FieldLabel = Color(0xFFBFC4D8)           // Better field label visibility
    override val Description = Color(0xFF9FA6C0)          // Optimized description text
    override val Placeholder = Color(0xFF7A8299)          // Subtle placeholders
    override val Disabled = Color(0xFF5A6178)             // Clear disabled state

    // Interactive states
    override val SurfaceHover = Color(0xFF181D27)         // Subtle hover effect

    // Surface tints - refined for better visual feedback
    override val AccentSurface = Color(0xFF1A1640)        // Deeper purple tint
    override val SuccessSurface = Color(0xFF0A2D20)       // Richer green tint
    override val WarningSurface = Color(0xFF2A2310)       // Warmer amber tint
    override val ErrorSurface = Color(0xFF331818)         // Deeper red tint
}

object WhiteZiaPaletteLight : WhiteZiaPaletteColors {
    override val Background = Color(0xFFF5F6FA)
    override val Surface = Color(0xFFFFFFFF)
    override val SurfaceAlt = Color(0xFFF8F9FC)
    override val DropdownSurface = Color(0xFFFAFBFD)
    override val Border = Color(0xFFE5E8F0)
    override val Divider = Color(0xFFDCE0EB)
    override val ControlBorder = Color(0xFFD1D6E4)
    override val Accent = Color(0xFF6C5CE7)
    override val AccentPressed = Color(0xFF5A4BD1)
    override val AccentText = Color(0xFF5546C8)
    override val OnAccent = Color(0xFFFFFFFF)
    override val Success = Color(0xFF00B87C)
    override val Error = Color(0xFFE63946)
    override val Warning = Color(0xFFF59E0B)
    override val WarningText = Color(0xFFD97706)
    override val Ink = Color(0xFF0F1419)
    override val Muted = Color(0xFF4B5563)
    override val Pale = Color(0xFF6B7280)
    override val SectionTitle = Color(0xFF374151)
    override val FieldLabel = Color(0xFF4B5563)
    override val Description = Color(0xFF6B7280)
    override val Placeholder = Color(0xFF9CA3AF)
    override val Disabled = Color(0xFFD1D5DB)
    override val Input = Color(0xFFFAFBFC)
    override val AccentDim = Color(0xFF9F93E8)
    override val SurfaceHover = Color(0xFFF3F4F8)
    override val AccentSurface = Color(0xFFF0EDFC)
    override val SuccessSurface = Color(0xFFE6F7F1)
    override val WarningSurface = Color(0xFFFEF3E2)
    override val ErrorSurface = Color(0xFFFEE8E9)
}

private val LocalWhiteZiaPalette = staticCompositionLocalOf<WhiteZiaPaletteColors> { WhiteZiaPaletteDark }

internal val LocalWhiteZiaStrings = staticCompositionLocalOf<WhiteZiaStrings> { EnglishStrings }

object WhiteZiaL10n {
    val tabProfiles: String @Composable get() = LocalWhiteZiaStrings.current.tabProfiles
    val tabConnect: String @Composable get() = LocalWhiteZiaStrings.current.tabConnect
    val tabScan: String @Composable get() = LocalWhiteZiaStrings.current.tabScan
    val tabLogs: String @Composable get() = LocalWhiteZiaStrings.current.tabLogs
    val btnConnect: String @Composable get() = LocalWhiteZiaStrings.current.btnConnect
    val btnConnecting: String @Composable get() = LocalWhiteZiaStrings.current.btnConnecting
    val btnStop: String @Composable get() = LocalWhiteZiaStrings.current.btnStop
    val btnClose: String @Composable get() = LocalWhiteZiaStrings.current.btnClose
    val btnSave: String @Composable get() = LocalWhiteZiaStrings.current.btnSave
    val btnCancel: String @Composable get() = LocalWhiteZiaStrings.current.btnCancel
    val btnCreate: String @Composable get() = LocalWhiteZiaStrings.current.btnCreate
    val btnImport: String @Composable get() = LocalWhiteZiaStrings.current.btnImport
    val btnDelete: String @Composable get() = LocalWhiteZiaStrings.current.btnDelete
    val btnCopy: String @Composable get() = LocalWhiteZiaStrings.current.btnCopy
    val btnShare: String @Composable get() = LocalWhiteZiaStrings.current.btnShare
    val appSettingsTitle: String @Composable get() = LocalWhiteZiaStrings.current.appSettingsTitle
    val fieldTheme: String @Composable get() = LocalWhiteZiaStrings.current.fieldTheme
    val fieldLanguage: String @Composable get() = LocalWhiteZiaStrings.current.fieldLanguage
    val themeModeAuto: String @Composable get() = LocalWhiteZiaStrings.current.themeModeAuto
    val themeModeLight: String @Composable get() = LocalWhiteZiaStrings.current.themeModeLight
    val themeModeDark: String @Composable get() = LocalWhiteZiaStrings.current.themeModeDark
    val fieldMode: String @Composable get() = LocalWhiteZiaStrings.current.fieldMode
    val connectionModeProxy: String @Composable get() = LocalWhiteZiaStrings.current.connectionModeProxy
    val connectionModeVpn: String @Composable get() = LocalWhiteZiaStrings.current.connectionModeVpn
    val sectionConnection: String @Composable get() = LocalWhiteZiaStrings.current.sectionConnection
    val sectionResolver: String @Composable get() = LocalWhiteZiaStrings.current.sectionResolver
    val bannerBatteryTitle: String @Composable get() = LocalWhiteZiaStrings.current.bannerBatteryTitle
    val bannerBatteryBody: String @Composable get() = LocalWhiteZiaStrings.current.bannerBatteryBody
    val bannerAllowBackground: String @Composable get() = LocalWhiteZiaStrings.current.bannerAllowBackground
    val bannerNotificationTitle: String @Composable get() = LocalWhiteZiaStrings.current.bannerNotificationTitle
    val bannerNotificationBody: String @Composable get() = LocalWhiteZiaStrings.current.bannerNotificationBody
    val bannerEnableNotification: String @Composable get() = LocalWhiteZiaStrings.current.bannerEnableNotification
    val bannerVpnWarningTitle: String @Composable get() = LocalWhiteZiaStrings.current.bannerVpnWarningTitle
    val bannerVpnWarningBody: String @Composable get() = LocalWhiteZiaStrings.current.bannerVpnWarningBody
    val parallelTest: String @Composable get() = LocalWhiteZiaStrings.current.parallelTest
    val profileTabConnection: String @Composable get() = LocalWhiteZiaStrings.current.profileTabConnection
    val profileTabResolver: String @Composable get() = LocalWhiteZiaStrings.current.profileTabResolver
    val profileTabSetting: String @Composable get() = LocalWhiteZiaStrings.current.profileTabSetting
    val settingGuideTitle: String @Composable get() = LocalWhiteZiaStrings.current.settingGuideTitle
    val settingGuideIntro: String @Composable get() = LocalWhiteZiaStrings.current.settingGuideIntro
    val settingGuideSource: String @Composable get() = LocalWhiteZiaStrings.current.settingGuideSource
    val settingGuideEffectLabel: String @Composable get() = LocalWhiteZiaStrings.current.settingGuideEffectLabel
    val settingGuideSections: List<SettingsGuideSection> @Composable get() = LocalWhiteZiaStrings.current.settingGuideSections
    val cdSettingGuide: String @Composable get() = LocalWhiteZiaStrings.current.cdSettingGuide
    val menuAppSettings: String @Composable get() = LocalWhiteZiaStrings.current.menuAppSettings
    val menuDonate: String @Composable get() = LocalWhiteZiaStrings.current.menuDonate
    val logsTitle: String @Composable get() = LocalWhiteZiaStrings.current.logsTitle
    val logsClear: String @Composable get() = LocalWhiteZiaStrings.current.logsClear
    val logsCopy: String @Composable get() = LocalWhiteZiaStrings.current.logsCopy
    val scanBtnStart: String @Composable get() = LocalWhiteZiaStrings.current.scanBtnStart
    val scanBtnStop: String @Composable get() = LocalWhiteZiaStrings.current.scanBtnStop
    val scanBtnSaveAs: String @Composable get() = LocalWhiteZiaStrings.current.scanBtnSaveAs
    val scanBtnResume: String @Composable get() = LocalWhiteZiaStrings.current.scanBtnResume
    val scanStatusTitle: String @Composable get() = LocalWhiteZiaStrings.current.scanStatusTitle
    val scanLabelTotal: String @Composable get() = LocalWhiteZiaStrings.current.scanLabelTotal
    val scanLabelValid: String @Composable get() = LocalWhiteZiaStrings.current.scanLabelValid
    val scanLabelRejected: String @Composable get() = LocalWhiteZiaStrings.current.scanLabelRejected
    val scanLabelStatus: String @Composable get() = LocalWhiteZiaStrings.current.scanLabelStatus
    val scanLabelSource: String @Composable get() = LocalWhiteZiaStrings.current.scanLabelSource
    val scanLabelWorkers: String @Composable get() = LocalWhiteZiaStrings.current.scanLabelWorkers
    val scanLabelProgress: String @Composable get() = LocalWhiteZiaStrings.current.scanLabelProgress
    val scanAutoSave: String @Composable get() = LocalWhiteZiaStrings.current.scanAutoSave
    val supportTitle: String @Composable get() = LocalWhiteZiaStrings.current.supportTitle
    val supportBody: String @Composable get() = LocalWhiteZiaStrings.current.supportBody
    val resolverRequired: String @Composable get() = LocalWhiteZiaStrings.current.resolverRequired
    val serverRouteMissing: String @Composable get() = LocalWhiteZiaStrings.current.serverRouteMissing
    val selectorConnectionProfiles: String @Composable get() = LocalWhiteZiaStrings.current.selectorConnectionProfiles
    val selectorResolverProfiles: String @Composable get() = LocalWhiteZiaStrings.current.selectorResolverProfiles
    val selectorSettingProfiles: String @Composable get() = LocalWhiteZiaStrings.current.selectorSettingProfiles
    val languageEn: String @Composable get() = LocalWhiteZiaStrings.current.languageEn
    val languageFa: String @Composable get() = LocalWhiteZiaStrings.current.languageFa
    val serverTestTitle: String @Composable get() = LocalWhiteZiaStrings.current.serverTestTitle
    val serverTestButton: String @Composable get() = LocalWhiteZiaStrings.current.serverTestButton
    val serverTestSingleButton: String @Composable get() = LocalWhiteZiaStrings.current.serverTestSingleButton
    val serverTestRunning: String @Composable get() = LocalWhiteZiaStrings.current.serverTestRunning
    val serverTestIdle: String @Composable get() = LocalWhiteZiaStrings.current.serverTestIdle
    val serverTestReady: String @Composable get() = LocalWhiteZiaStrings.current.serverTestReady
    val serverTestFailed: String @Composable get() = LocalWhiteZiaStrings.current.serverTestFailed
    val serverTestMeasuring: String @Composable get() = LocalWhiteZiaStrings.current.serverTestMeasuring
    val serverTestStarting: String @Composable get() = LocalWhiteZiaStrings.current.serverTestStarting
    val serverTestPending: String @Composable get() = LocalWhiteZiaStrings.current.serverTestPending
    val serverTestProgressTemplate: String @Composable get() = LocalWhiteZiaStrings.current.serverTestProgressTemplate
    val serverTestSummaryTemplate: String @Composable get() = LocalWhiteZiaStrings.current.serverTestSummaryTemplate
    val serverTestNoSavedServers: String @Composable get() = LocalWhiteZiaStrings.current.serverTestNoSavedServers
    val serverTestNoConnectedResolvers: String @Composable get() = LocalWhiteZiaStrings.current.serverTestNoConnectedResolvers
    val serverTestFailedTemplate: String @Composable get() = LocalWhiteZiaStrings.current.serverTestFailedTemplate
    val serverTestConnectionRequired: String @Composable get() = LocalWhiteZiaStrings.current.serverTestConnectionRequired
    val serverTestServiceConnected: String @Composable get() = LocalWhiteZiaStrings.current.serverTestServiceConnected
    val serverTestServiceTesting: String @Composable get() = LocalWhiteZiaStrings.current.serverTestServiceTesting
    val serverTestServiceGood: String @Composable get() = LocalWhiteZiaStrings.current.serverTestServiceGood
    val serverTestServiceFair: String @Composable get() = LocalWhiteZiaStrings.current.serverTestServiceFair
    val serverTestServicePoor: String @Composable get() = LocalWhiteZiaStrings.current.serverTestServicePoor
    val serverTestScoreGood: String @Composable get() = LocalWhiteZiaStrings.current.serverTestScoreGood
    val serverTestScoreFair: String @Composable get() = LocalWhiteZiaStrings.current.serverTestScoreFair
    val serverTestScorePoor: String @Composable get() = LocalWhiteZiaStrings.current.serverTestScorePoor
    val serverTestScoreUnavailable: String @Composable get() = LocalWhiteZiaStrings.current.serverTestScoreUnavailable

    // Scan tab additional
    val scanDefaultList: String @Composable get() = LocalWhiteZiaStrings.current.scanDefaultList
    val scanSelectFile: String @Composable get() = LocalWhiteZiaStrings.current.scanSelectFile
    val scanProfileLabel: String @Composable get() = LocalWhiteZiaStrings.current.scanProfileLabel
    val scanAutoSaveTitle: String @Composable get() = LocalWhiteZiaStrings.current.scanAutoSaveTitle
    val scanSaveAsTitle: String @Composable get() = LocalWhiteZiaStrings.current.scanSaveAsTitle
    val scanSaveAsName: String @Composable get() = LocalWhiteZiaStrings.current.scanSaveAsName
    val scanSaveAsPlaceholder: String @Composable get() = LocalWhiteZiaStrings.current.scanSaveAsPlaceholder
    val scanNoFileSelected: String @Composable get() = LocalWhiteZiaStrings.current.scanNoFileSelected
    val scanMessageLabel: String @Composable get() = LocalWhiteZiaStrings.current.scanMessageLabel

    // Setup card
    val setupResolversLabel: String @Composable get() = LocalWhiteZiaStrings.current.setupResolversLabel
    val setupAddConnectionSupportingText: String @Composable get() = LocalWhiteZiaStrings.current.setupAddConnectionSupportingText
    val setupAddResolverSupportingText: String @Composable get() = LocalWhiteZiaStrings.current.setupAddResolverSupportingText
    val setupManualResolvers: String @Composable get() = LocalWhiteZiaStrings.current.setupManualResolvers
    val setupSectionSetup: String @Composable get() = LocalWhiteZiaStrings.current.setupSectionSetup

    // Connection info card
    val infoCardConnection: String @Composable get() = LocalWhiteZiaStrings.current.infoCardConnection
    val infoLabelMode: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelMode
    val infoLabelSocks5Proxy: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelSocks5Proxy
    val infoLabelHttpProxy: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelHttpProxy
    val infoLabelAuth: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelAuth
    val infoLabelUser: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelUser
    val infoLabelPass: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelPass
    val infoLabelSplitTunnel: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelSplitTunnel
    val infoLabelApps: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelApps
    val infoLabelConnectionProfile: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelConnectionProfile
    val infoLabelResolverProfile: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelResolverProfile
    val infoLabelSettingProfile: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelSettingProfile
    val infoLabelProtocol: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelProtocol
    val infoLabelAuthOn: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelAuthOn
    val infoLabelAuthOff: String @Composable get() = LocalWhiteZiaStrings.current.infoLabelAuthOff

    // Speed indicators
    val speedDown: String @Composable get() = LocalWhiteZiaStrings.current.speedDown
    val speedUp: String @Composable get() = LocalWhiteZiaStrings.current.speedUp
    val speedTotalUsage: String @Composable get() = LocalWhiteZiaStrings.current.speedTotalUsage

    // Resolver runtime
    val resolverActiveResolvers: String @Composable get() = LocalWhiteZiaStrings.current.resolverActiveResolvers
    val resolverValidResolvers: String @Composable get() = LocalWhiteZiaStrings.current.resolverValidResolvers
    val resolverPending: String @Composable get() = LocalWhiteZiaStrings.current.resolverPending
    val resolverNoResolvers: String @Composable get() = LocalWhiteZiaStrings.current.resolverNoResolvers
    val backgroundScanningInProgress: String @Composable get() = LocalWhiteZiaStrings.current.backgroundScanningInProgress

    // Profile dialogs
    val profileDialogCreateSetting: String @Composable get() = LocalWhiteZiaStrings.current.profileDialogCreateSetting
    val profileDialogEditSetting: String @Composable get() = LocalWhiteZiaStrings.current.profileDialogEditSetting
    val profileDialogCreateResolver: String @Composable get() = LocalWhiteZiaStrings.current.profileDialogCreateResolver
    val profileDialogEditResolver: String @Composable get() = LocalWhiteZiaStrings.current.profileDialogEditResolver
    val profileDialogCreateConnection: String @Composable get() = LocalWhiteZiaStrings.current.profileDialogCreateConnection
    val profileDialogEditConnection: String @Composable get() = LocalWhiteZiaStrings.current.profileDialogEditConnection
    val profileDialogImportSettings: String @Composable get() = LocalWhiteZiaStrings.current.profileDialogImportSettings
    val profileDialogImportConnection: String @Composable get() = LocalWhiteZiaStrings.current.profileDialogImportConnection
    val profileDialogExportConnection: String @Composable get() = LocalWhiteZiaStrings.current.profileDialogExportConnection
    val profileDialogExportAllConnections: String @Composable get() = LocalWhiteZiaStrings.current.profileDialogExportAllConnections
    val profileDialogExportAllResolvers: String @Composable get() = LocalWhiteZiaStrings.current.profileDialogExportAllResolvers
    val profileDialogExportSettings: String @Composable get() = LocalWhiteZiaStrings.current.profileDialogExportSettings
    val profileExportResolverTotalTemplate: String @Composable get() = LocalWhiteZiaStrings.current.profileExportResolverTotalTemplate
    val profileExportSavingFile: String @Composable get() = LocalWhiteZiaStrings.current.profileExportSavingFile
    val profileExportSavedToTemplate: String @Composable get() = LocalWhiteZiaStrings.current.profileExportSavedToTemplate
    val profileFieldName: String @Composable get() = LocalWhiteZiaStrings.current.profileFieldName
    val profileFieldResolvers: String @Composable get() = LocalWhiteZiaStrings.current.profileFieldResolvers
    val profileFieldProfileLinks: String @Composable get() = LocalWhiteZiaStrings.current.profileFieldProfileLinks
    val profileFieldToml: String @Composable get() = LocalWhiteZiaStrings.current.profileFieldToml
    val profileNamePlaceholderFastTunnel: String @Composable get() = LocalWhiteZiaStrings.current.profileNamePlaceholderFastTunnel
    val profileNamePlaceholderHomeResolvers: String @Composable get() = LocalWhiteZiaStrings.current.profileNamePlaceholderHomeResolvers
    val profileNamePlaceholderImportedSettings: String @Composable get() = LocalWhiteZiaStrings.current.profileNamePlaceholderImportedSettings
    val profileNamePlaceholderConnection: String @Composable get() = LocalWhiteZiaStrings.current.profileNamePlaceholderConnection
    val profileResolverPlaceholder: String @Composable get() = LocalWhiteZiaStrings.current.profileResolverPlaceholder

    // Profile menu actions
    val profileMenuExport: String @Composable get() = LocalWhiteZiaStrings.current.profileMenuExport
    val profileMenuEdit: String @Composable get() = LocalWhiteZiaStrings.current.profileMenuEdit
    val profileMenuDelete: String @Composable get() = LocalWhiteZiaStrings.current.profileMenuDelete
    val profileMenuUse: String @Composable get() = LocalWhiteZiaStrings.current.profileMenuUse
    val profileMenuUseSelected: String @Composable get() = LocalWhiteZiaStrings.current.profileMenuUseSelected

    // Profile list empty states
    val profileNoResolverLists: String @Composable get() = LocalWhiteZiaStrings.current.profileNoResolverLists
    val profileNoSettingProfiles: String @Composable get() = LocalWhiteZiaStrings.current.profileNoSettingProfiles
    val profileQrUnavailable: String @Composable get() = LocalWhiteZiaStrings.current.profileQrUnavailable

    // Profile action buttons
    val profileBtnCreate: String @Composable get() = LocalWhiteZiaStrings.current.profileBtnCreate
    val profileBtnImport: String @Composable get() = LocalWhiteZiaStrings.current.profileBtnImport
    val profileBtnDeleteDups: String @Composable get() = LocalWhiteZiaStrings.current.profileBtnDeleteDups
    val profileBtnExportAll: String @Composable get() = LocalWhiteZiaStrings.current.profileBtnExportAll
    val profileBtnSaveCurrent: String @Composable get() = LocalWhiteZiaStrings.current.profileBtnSaveCurrent
    val profileBtnImportFile: String @Composable get() = LocalWhiteZiaStrings.current.profileBtnImportFile
    val profileBtnClear: String @Composable get() = LocalWhiteZiaStrings.current.profileBtnClear

    // Delete confirmation dialogs
    val deleteConnectionTitle: String @Composable get() = LocalWhiteZiaStrings.current.deleteConnectionTitle
    val deleteResolverTitle: String @Composable get() = LocalWhiteZiaStrings.current.deleteResolverTitle
    val deleteSettingTitle: String @Composable get() = LocalWhiteZiaStrings.current.deleteSettingTitle
    val deleteDupsTitle: String @Composable get() = LocalWhiteZiaStrings.current.deleteDupsTitle

    // GroupLabels
    val groupMtu: String @Composable get() = LocalWhiteZiaStrings.current.groupMtu
    val groupRuntimeWorkers: String @Composable get() = LocalWhiteZiaStrings.current.groupRuntimeWorkers
    val groupLocalProxy: String @Composable get() = LocalWhiteZiaStrings.current.groupLocalProxy
    val groupNetworkTuning: String @Composable get() = LocalWhiteZiaStrings.current.groupNetworkTuning
    val groupReliability: String @Composable get() = LocalWhiteZiaStrings.current.groupReliability
    val groupDefault: String @Composable get() = LocalWhiteZiaStrings.current.groupDefault
    val groupCustomSettings: String @Composable get() = LocalWhiteZiaStrings.current.groupCustomSettings
    val groupParallelTestResults: String @Composable get() = LocalWhiteZiaStrings.current.groupParallelTestResults

    // Advanced settings field labels
    val settingListenIp: String @Composable get() = LocalWhiteZiaStrings.current.settingListenIp
    val settingListenPort: String @Composable get() = LocalWhiteZiaStrings.current.settingListenPort
    val settingHttpProxy: String @Composable get() = LocalWhiteZiaStrings.current.settingHttpProxy
    val settingHttpPort: String @Composable get() = LocalWhiteZiaStrings.current.settingHttpPort
    val settingSocks5Auth: String @Composable get() = LocalWhiteZiaStrings.current.settingSocks5Auth
    val settingUsername: String @Composable get() = LocalWhiteZiaStrings.current.settingUsername
    val settingPassword: String @Composable get() = LocalWhiteZiaStrings.current.settingPassword
    val settingBalancingStrategy: String @Composable get() = LocalWhiteZiaStrings.current.settingBalancingStrategy
    val settingUploadDup: String @Composable get() = LocalWhiteZiaStrings.current.settingUploadDup
    val settingDownloadDup: String @Composable get() = LocalWhiteZiaStrings.current.settingDownloadDup
    val settingUploadCompress: String @Composable get() = LocalWhiteZiaStrings.current.settingUploadCompress
    val settingDownloadCompress: String @Composable get() = LocalWhiteZiaStrings.current.settingDownloadCompress
    val settingBaseEncodeData: String @Composable get() = LocalWhiteZiaStrings.current.settingBaseEncodeData
    val settingPingWatchdog: String @Composable get() = LocalWhiteZiaStrings.current.settingPingWatchdog
    val settingTrafficWarmup: String @Composable get() = LocalWhiteZiaStrings.current.settingTrafficWarmup
    val settingWarmupProbes: String @Composable get() = LocalWhiteZiaStrings.current.settingWarmupProbes
    val settingKeepalive: String @Composable get() = LocalWhiteZiaStrings.current.settingKeepalive
    val settingLogLevel: String @Composable get() = LocalWhiteZiaStrings.current.settingLogLevel
    val settingSearch: String @Composable get() = LocalWhiteZiaStrings.current.settingSearch
    val settingMinUpload: String @Composable get() = LocalWhiteZiaStrings.current.settingMinUpload
    val settingMinDownload: String @Composable get() = LocalWhiteZiaStrings.current.settingMinDownload
    val settingMaxUpload: String @Composable get() = LocalWhiteZiaStrings.current.settingMaxUpload
    val settingMaxDownload: String @Composable get() = LocalWhiteZiaStrings.current.settingMaxDownload
    val settingResolverRetries: String @Composable get() = LocalWhiteZiaStrings.current.settingResolverRetries
    val settingResolverTimeout: String @Composable get() = LocalWhiteZiaStrings.current.settingResolverTimeout
    val settingResolverParallel: String @Composable get() = LocalWhiteZiaStrings.current.settingResolverParallel
    val settingResolverParallelNote: String @Composable get() = LocalWhiteZiaStrings.current.settingResolverParallelNote
    val settingLogsRetries: String @Composable get() = LocalWhiteZiaStrings.current.settingLogsRetries
    val settingLogsTimeout: String @Composable get() = LocalWhiteZiaStrings.current.settingLogsTimeout
    val settingLogsParallel: String @Composable get() = LocalWhiteZiaStrings.current.settingLogsParallel
    val settingRxTxWorkers: String @Composable get() = LocalWhiteZiaStrings.current.settingRxTxWorkers
    val settingProcessWorkers: String @Composable get() = LocalWhiteZiaStrings.current.settingProcessWorkers
    val settingTunnelPacketTimeout: String @Composable get() = LocalWhiteZiaStrings.current.settingTunnelPacketTimeout
    val settingIdlePoll: String @Composable get() = LocalWhiteZiaStrings.current.settingIdlePoll
    val settingTxChannel: String @Composable get() = LocalWhiteZiaStrings.current.settingTxChannel
    val settingRxChannel: String @Composable get() = LocalWhiteZiaStrings.current.settingRxChannel
    val settingUdpPool: String @Composable get() = LocalWhiteZiaStrings.current.settingUdpPool
    val settingStreamQueue: String @Composable get() = LocalWhiteZiaStrings.current.settingStreamQueue
    val settingOrphanQueue: String @Composable get() = LocalWhiteZiaStrings.current.settingOrphanQueue
    val settingDnsFragments: String @Composable get() = LocalWhiteZiaStrings.current.settingDnsFragments
    val settingSocksUdpTimeout: String @Composable get() = LocalWhiteZiaStrings.current.settingSocksUdpTimeout
    val settingTerminalRetain: String @Composable get() = LocalWhiteZiaStrings.current.settingTerminalRetain
    val settingCancelledRetain: String @Composable get() = LocalWhiteZiaStrings.current.settingCancelledRetain
    val settingRetryBase: String @Composable get() = LocalWhiteZiaStrings.current.settingRetryBase
    val settingRetryStep: String @Composable get() = LocalWhiteZiaStrings.current.settingRetryStep
    val settingRetryLinear: String @Composable get() = LocalWhiteZiaStrings.current.settingRetryLinear
    val settingRetryMax: String @Composable get() = LocalWhiteZiaStrings.current.settingRetryMax
    val settingBusyRetry: String @Composable get() = LocalWhiteZiaStrings.current.settingBusyRetry
    val settingSettingLabel: String @Composable get() = LocalWhiteZiaStrings.current.settingSettingLabel

    // Split Tunnel
    val splitTunnelTitle: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelTitle
    val splitTunnelAppRouting: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelAppRouting
    val splitTunnelSelected: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelSelected
    val splitTunnelSelectApps: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelSelectApps
    val splitTunnelNoAppsFound: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelNoAppsFound
    val splitTunnelSearchPlaceholder: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelSearchPlaceholder
    val splitTunnelDialogTitle: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelDialogTitle
    val splitTunnelSearchLabel: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelSearchLabel

    // Connection logs (inline panel)
    val logsInlineTitle: String @Composable get() = LocalWhiteZiaStrings.current.logsInlineTitle
    val logsDiagnostics: String @Composable get() = LocalWhiteZiaStrings.current.logsDiagnostics

    // Notification / VPN banners
    val bannerNotificationBlockedTitle: String @Composable get() = LocalWhiteZiaStrings.current.bannerNotificationBlockedTitle
    val bannerNotificationBlockedBody: String @Composable get() = LocalWhiteZiaStrings.current.bannerNotificationBlockedBody
    val bannerFullVpnWarningTitle: String @Composable get() = LocalWhiteZiaStrings.current.bannerFullVpnWarningTitle
    val bannerFullVpnWarningBody: String @Composable get() = LocalWhiteZiaStrings.current.bannerFullVpnWarningBody

    // Parallel test UI
    val parallelTestOpenLabel: String @Composable get() = LocalWhiteZiaStrings.current.parallelTestOpenLabel
    val parallelTestClosedLabel: String @Composable get() = LocalWhiteZiaStrings.current.parallelTestClosedLabel
    val parallelTestDescription: String @Composable get() = LocalWhiteZiaStrings.current.parallelTestDescription
    val parallelTestYourConfigs: String @Composable get() = LocalWhiteZiaStrings.current.parallelTestYourConfigs

    // Connect tab messages
    val connectNeedResolvers: String @Composable get() = LocalWhiteZiaStrings.current.connectNeedResolvers
    val connectSelectedCount: String @Composable get() = LocalWhiteZiaStrings.current.connectSelectedCount

    // Auto-tune / parallel test results
    val autoTuneSaveSettingAs: String @Composable get() = LocalWhiteZiaStrings.current.autoTuneSaveSettingAs
    val autoTuneMtuFail: String @Composable get() = LocalWhiteZiaStrings.current.autoTuneMtuFail
    val autoTuneMtuPass: String @Composable get() = LocalWhiteZiaStrings.current.autoTuneMtuPass
    val autoTuneMtuTest: String @Composable get() = LocalWhiteZiaStrings.current.autoTuneMtuTest
    val autoTuneSpeedLabel: String @Composable get() = LocalWhiteZiaStrings.current.autoTuneSpeedLabel
    val autoTunePingLabel: String @Composable get() = LocalWhiteZiaStrings.current.autoTunePingLabel
    val autoTuneStatusStarting: String @Composable get() = LocalWhiteZiaStrings.current.autoTuneStatusStarting
    val autoTuneMeasuringSpeed: String @Composable get() = LocalWhiteZiaStrings.current.autoTuneMeasuringSpeed
    val cdParallelTestSpeed: String @Composable get() = LocalWhiteZiaStrings.current.cdParallelTestSpeed
    val cdParallelTestPing: String @Composable get() = LocalWhiteZiaStrings.current.cdParallelTestPing
    val cdConnectButtonDisconnected: String @Composable get() = LocalWhiteZiaStrings.current.cdConnectButtonDisconnected
    val cdConnectButtonConnecting: String @Composable get() = LocalWhiteZiaStrings.current.cdConnectButtonConnecting
    val cdConnectButtonConnected: String @Composable get() = LocalWhiteZiaStrings.current.cdConnectButtonConnected
    val cdAutoTuneMtuFailed: String @Composable get() = LocalWhiteZiaStrings.current.cdAutoTuneMtuFailed
    val cdAutoTuneMtuPassed: String @Composable get() = LocalWhiteZiaStrings.current.cdAutoTuneMtuPassed
    val cdAutoTuneMtuTesting: String @Composable get() = LocalWhiteZiaStrings.current.cdAutoTuneMtuTesting

    val shareSubjectProfile: String @Composable get() = LocalWhiteZiaStrings.current.shareSubjectProfile
    val shareChooserProfile: String @Composable get() = LocalWhiteZiaStrings.current.shareChooserProfile
    val shareChooserClientConfig: String @Composable get() = LocalWhiteZiaStrings.current.shareChooserClientConfig
    val shareChooserAdvancedSettings: String @Composable get() = LocalWhiteZiaStrings.current.shareChooserAdvancedSettings
    val shareChooserResolvers: String @Composable get() = LocalWhiteZiaStrings.current.shareChooserResolvers

    val errorUnableToOpenResolverFile: String @Composable get() = LocalWhiteZiaStrings.current.errorUnableToOpenResolverFile
    val errorInvalidResolverIpTemplate: String @Composable get() = LocalWhiteZiaStrings.current.errorInvalidResolverIpTemplate
    val errorNoResolverEntries: String @Composable get() = LocalWhiteZiaStrings.current.errorNoResolverEntries
    val errorEnterValidResolverIp: String @Composable get() = LocalWhiteZiaStrings.current.errorEnterValidResolverIp
    val errorEnterProfileNameToSave: String @Composable get() = LocalWhiteZiaStrings.current.errorEnterProfileNameToSave
    val resolverValidSingularTemplate: String @Composable get() = LocalWhiteZiaStrings.current.resolverValidSingularTemplate
    val resolverValidPluralTemplate: String @Composable get() = LocalWhiteZiaStrings.current.resolverValidPluralTemplate
    val advancedProfileModifiedSuffix: String @Composable get() = LocalWhiteZiaStrings.current.advancedProfileModifiedSuffix
    val cdEditPrefix: String @Composable get() = LocalWhiteZiaStrings.current.cdEditPrefix
    val resolverFieldPlaceholder: String @Composable get() = LocalWhiteZiaStrings.current.resolverFieldPlaceholder
    val dropdownPlaceholderSelect: String @Composable get() = LocalWhiteZiaStrings.current.dropdownPlaceholderSelect
    val setupDefaultResolver: String @Composable get() = LocalWhiteZiaStrings.current.setupDefaultResolver
    val setupDefaultConnection: String @Composable get() = LocalWhiteZiaStrings.current.setupDefaultConnection
    val setupDefaultAdvanced: String @Composable get() = LocalWhiteZiaStrings.current.setupDefaultAdvanced

    val balancingStrategyRandom: String @Composable get() = LocalWhiteZiaStrings.current.balancingStrategyRandom
    val balancingStrategyRoundRobin: String @Composable get() = LocalWhiteZiaStrings.current.balancingStrategyRoundRobin
    val balancingStrategyLeastLoss: String @Composable get() = LocalWhiteZiaStrings.current.balancingStrategyLeastLoss
    val balancingStrategyLowestLatency: String @Composable get() = LocalWhiteZiaStrings.current.balancingStrategyLowestLatency
    val compressionOff: String @Composable get() = LocalWhiteZiaStrings.current.compressionOff
    val compressionZstd: String @Composable get() = LocalWhiteZiaStrings.current.compressionZstd
    val compressionLz4: String @Composable get() = LocalWhiteZiaStrings.current.compressionLz4
    val compressionZlib: String @Composable get() = LocalWhiteZiaStrings.current.compressionZlib
    val splitTunnelAllAppsChoice: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelAllAppsChoice
    val splitTunnelOnlySelectedChoice: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelOnlySelectedChoice
    val splitTunnelBypassSelectedChoice: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelBypassSelectedChoice
    val encryptionMethodNone: String @Composable get() = LocalWhiteZiaStrings.current.encryptionMethodNone
    val encryptionMethodXor: String @Composable get() = LocalWhiteZiaStrings.current.encryptionMethodXor
    val encryptionMethodChacha20: String @Composable get() = LocalWhiteZiaStrings.current.encryptionMethodChacha20
    val encryptionMethodAes128: String @Composable get() = LocalWhiteZiaStrings.current.encryptionMethodAes128
    val encryptionMethodAes192: String @Composable get() = LocalWhiteZiaStrings.current.encryptionMethodAes192
    val encryptionMethodAes256: String @Composable get() = LocalWhiteZiaStrings.current.encryptionMethodAes256

    // Download TOML dialog
    val downloadTomlTitle: String @Composable get() = LocalWhiteZiaStrings.current.downloadTomlTitle
    val downloadTomlBtn: String @Composable get() = LocalWhiteZiaStrings.current.downloadTomlBtn

    // Save setting as button
    val saveSettingAs: String @Composable get() = LocalWhiteZiaStrings.current.saveSettingAs

    // Validation messages
    val validationEnterResolverIp: String @Composable get() = LocalWhiteZiaStrings.current.validationEnterResolverIp
    val validationEnterProfileName: String @Composable get() = LocalWhiteZiaStrings.current.validationEnterProfileName

    // HomeSelectorCard
    val homeSelectorSettingLabel: String @Composable get() = LocalWhiteZiaStrings.current.homeSelectorSettingLabel
    val homeSelectorUnsavedChanges: String @Composable get() = LocalWhiteZiaStrings.current.homeSelectorUnsavedChanges

    // Existing (menu items kept for completeness)
    val menuVersion: String @Composable get() = LocalWhiteZiaStrings.current.menuVersion
    val setupTitle: String @Composable get() = LocalWhiteZiaStrings.current.setupTitle
    val setupAddConnection: String @Composable get() = LocalWhiteZiaStrings.current.setupAddConnection
    val setupAddResolver: String @Composable get() = LocalWhiteZiaStrings.current.setupAddResolver
    val resolverNotSelected: String @Composable get() = LocalWhiteZiaStrings.current.resolverNotSelected
    val connectProgressConnected: String @Composable get() = LocalWhiteZiaStrings.current.connectProgressConnected
    val scanWorkerWarning: String @Composable get() = LocalWhiteZiaStrings.current.scanWorkerWarning

    // Newly added getters for remaining English strings translation
    val homeSelectorNoSavedLists: String @Composable get() = LocalWhiteZiaStrings.current.homeSelectorNoSavedLists
    val homeSelectorNotSelected: String @Composable get() = LocalWhiteZiaStrings.current.homeSelectorNotSelected
    val homeSelectorResolverProfileFallback: String @Composable get() = LocalWhiteZiaStrings.current.homeSelectorResolverProfileFallback
    val homeSelectorSearchConnections: String @Composable get() = LocalWhiteZiaStrings.current.homeSelectorSearchConnections
    val homeSelectorSearchResolvers: String @Composable get() = LocalWhiteZiaStrings.current.homeSelectorSearchResolvers
    val homeSelectorSearchSettings: String @Composable get() = LocalWhiteZiaStrings.current.homeSelectorSearchSettings
    val homeSelectorCustomAdvanced: String @Composable get() = LocalWhiteZiaStrings.current.homeSelectorCustomAdvanced
    val profileNameCopySuffix: String @Composable get() = LocalWhiteZiaStrings.current.profileNameCopySuffix
    val settingProfileFastTunnelPlaceholder: String @Composable get() = LocalWhiteZiaStrings.current.settingProfileFastTunnelPlaceholder
    val resolverProfileHomeResolversPlaceholder: String @Composable get() = LocalWhiteZiaStrings.current.resolverProfileHomeResolversPlaceholder
    val settingProfileImportedSettingsPlaceholder: String @Composable get() = LocalWhiteZiaStrings.current.settingProfileImportedSettingsPlaceholder
    val homeSelectorNoConnectionProfiles: String @Composable get() = LocalWhiteZiaStrings.current.homeSelectorNoConnectionProfiles
    val homeSelectorNoResolverProfiles: String @Composable get() = LocalWhiteZiaStrings.current.homeSelectorNoResolverProfiles
    val homeSelectorNoSettingProfiles: String @Composable get() = LocalWhiteZiaStrings.current.homeSelectorNoSettingProfiles

    val setupNoResolversConfigured: String @Composable get() = LocalWhiteZiaStrings.current.setupNoResolversConfigured
    val setupInvalidResolverIp: String @Composable get() = LocalWhiteZiaStrings.current.setupInvalidResolverIp
    val setupServerRouteAndKey: String @Composable get() = LocalWhiteZiaStrings.current.setupServerRouteAndKey
    val setupEncryptionKeyMissing: String @Composable get() = LocalWhiteZiaStrings.current.setupEncryptionKeyMissing

    val scanProfileNeedsServer: String @Composable get() = LocalWhiteZiaStrings.current.scanProfileNeedsServer
    val scanProfileFallback: String @Composable get() = LocalWhiteZiaStrings.current.scanProfileFallback
    val scanResultsTitle: String @Composable get() = LocalWhiteZiaStrings.current.scanResultsTitle
    val scanCurrentScan: String @Composable get() = LocalWhiteZiaStrings.current.scanCurrentScan
    val scanFieldWorkers: String @Composable get() = LocalWhiteZiaStrings.current.scanFieldWorkers
    val scanSaveBodyTemplate: String @Composable get() = LocalWhiteZiaStrings.current.scanSaveBodyTemplate
    val scanStatusReady: String @Composable get() = LocalWhiteZiaStrings.current.scanStatusReady
    val scanStatusStarting: String @Composable get() = LocalWhiteZiaStrings.current.scanStatusStarting
    val scanStatusRunning: String @Composable get() = LocalWhiteZiaStrings.current.scanStatusRunning
    val scanStatusCompleted: String @Composable get() = LocalWhiteZiaStrings.current.scanStatusCompleted
    val scanStatusFailed: String @Composable get() = LocalWhiteZiaStrings.current.scanStatusFailed
    val scanStatusStopped: String @Composable get() = LocalWhiteZiaStrings.current.scanStatusStopped
    val scanStatusIdle: String @Composable get() = LocalWhiteZiaStrings.current.scanStatusIdle

    val groupCustomConnections: String @Composable get() = LocalWhiteZiaStrings.current.groupCustomConnections
    val customConnectionsEmpty: String @Composable get() = LocalWhiteZiaStrings.current.customConnectionsEmpty
    val profileFieldDomain: String @Composable get() = LocalWhiteZiaStrings.current.profileFieldDomain
    val profileFieldEncryptionKey: String @Composable get() = LocalWhiteZiaStrings.current.profileFieldEncryptionKey
    val profileFieldEncryptionMethod: String @Composable get() = LocalWhiteZiaStrings.current.profileFieldEncryptionMethod
    val profileDomainPlaceholder: String @Composable get() = LocalWhiteZiaStrings.current.profileDomainPlaceholder
    val profileEncryptionKeyPlaceholder: String @Composable get() = LocalWhiteZiaStrings.current.profileEncryptionKeyPlaceholder
    val profileMyStormDnsPlaceholder: String @Composable get() = LocalWhiteZiaStrings.current.profileMyStormDnsPlaceholder
    val profileDomainFallback: String @Composable get() = LocalWhiteZiaStrings.current.profileDomainFallback
    val profileStatusActive: String @Composable get() = LocalWhiteZiaStrings.current.profileStatusActive
    val profileStatusSelected: String @Composable get() = LocalWhiteZiaStrings.current.profileStatusSelected
    val profileStatusModified: String @Composable get() = LocalWhiteZiaStrings.current.profileStatusModified
    @Composable fun resolverProfileSummary(count: Int): String = LocalWhiteZiaStrings.current.resolverProfileSummary(count)
    val profileFieldProfileLinkSingle: String @Composable get() = LocalWhiteZiaStrings.current.profileFieldProfileLinkSingle
    val dialogDeleteConfirm: String @Composable get() = LocalWhiteZiaStrings.current.dialogDeleteConfirm
    val deleteConnectionMessageTemplate: String @Composable get() = LocalWhiteZiaStrings.current.deleteConnectionMessageTemplate
    val deleteResolverMessageTemplate: String @Composable get() = LocalWhiteZiaStrings.current.deleteResolverMessageTemplate
    val deleteSettingMessageTemplate: String @Composable get() = LocalWhiteZiaStrings.current.deleteSettingMessageTemplate
    val deleteDupsMessageSingleConnection: String @Composable get() = LocalWhiteZiaStrings.current.deleteDupsMessageSingleConnection
    val deleteDupsMessageManyConnection: String @Composable get() = LocalWhiteZiaStrings.current.deleteDupsMessageManyConnection

    val footerPoweredBy: String @Composable get() = LocalWhiteZiaStrings.current.footerPoweredBy

    val verificationVerifying: String @Composable get() = LocalWhiteZiaStrings.current.verificationVerifying
    val verificationVerified: String @Composable get() = LocalWhiteZiaStrings.current.verificationVerified
    val verificationNeedsAttention: String @Composable get() = LocalWhiteZiaStrings.current.verificationNeedsAttention
    val verificationPending: String @Composable get() = LocalWhiteZiaStrings.current.verificationPending
    val verificationNotRunYet: String @Composable get() = LocalWhiteZiaStrings.current.verificationNotRunYet
    val verificationCheckingRoute: String @Composable get() = LocalWhiteZiaStrings.current.verificationCheckingRoute
    val verificationProxyReachable: String @Composable get() = LocalWhiteZiaStrings.current.verificationProxyReachable
    val verificationVpnReachable: String @Composable get() = LocalWhiteZiaStrings.current.verificationVpnReachable
    val verificationProxyWarming: String @Composable get() = LocalWhiteZiaStrings.current.verificationProxyWarming
    val verificationVpnWarming: String @Composable get() = LocalWhiteZiaStrings.current.verificationVpnWarming
    val verificationModeChanged: String @Composable get() = LocalWhiteZiaStrings.current.verificationModeChanged
    val verificationSocksNotReachable: String @Composable get() = LocalWhiteZiaStrings.current.verificationSocksNotReachable
    val verificationVpnInterfaceInactive: String @Composable get() = LocalWhiteZiaStrings.current.verificationVpnInterfaceInactive

    val noResolversPlaceholder: String @Composable get() = LocalWhiteZiaStrings.current.noResolversPlaceholder
    val whiteZiaResolversLabel: String @Composable get() = LocalWhiteZiaStrings.current.whiteZiaResolversLabel
    val whiteZiaConfigsLabel: String @Composable get() = LocalWhiteZiaStrings.current.whiteZiaConfigsLabel
    val whiteZiaConfigsDescription: String @Composable get() = LocalWhiteZiaStrings.current.whiteZiaConfigsDescription
    val whiteZiaAggressiveConfigsLabel: String @Composable get() = LocalWhiteZiaStrings.current.whiteZiaAggressiveConfigsLabel
    val whiteZiaAggressiveConfigsDescription: String @Composable get() = LocalWhiteZiaStrings.current.whiteZiaAggressiveConfigsDescription
    val whiteZiaLogsLabel: String @Composable get() = LocalWhiteZiaStrings.current.whiteZiaLogsLabel
    val whiteZiaDiagnosticsLabel: String @Composable get() = LocalWhiteZiaStrings.current.whiteZiaDiagnosticsLabel
    val parallelTestCollapseDescription: String @Composable get() = LocalWhiteZiaStrings.current.parallelTestCollapseDescription
    val parallelTestExpandDescription: String @Composable get() = LocalWhiteZiaStrings.current.parallelTestExpandDescription

    val autoTuneStartingTest: String @Composable get() = LocalWhiteZiaStrings.current.autoTuneStartingTest
    val autoTuneFailedFallback: String @Composable get() = LocalWhiteZiaStrings.current.autoTuneFailedFallback
    val autoTuneMeasuredKeyword: String @Composable get() = LocalWhiteZiaStrings.current.autoTuneMeasuredKeyword

    val dropdownSelectFallback: String @Composable get() = LocalWhiteZiaStrings.current.dropdownSelectFallback

    val splitTunnelAllApps: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelAllApps
    val splitTunnelNoApps: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelNoApps
    val splitTunnelOnlyPrefix: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelOnlyPrefix
    val splitTunnelBypassPrefix: String @Composable get() = LocalWhiteZiaStrings.current.splitTunnelBypassPrefix

    val tapToCollapse: String @Composable get() = LocalWhiteZiaStrings.current.tapToCollapse
    val tapToConfigure: String @Composable get() = LocalWhiteZiaStrings.current.tapToConfigure
    val parallelTestOpen: String @Composable get() = LocalWhiteZiaStrings.current.parallelTestOpen
    val parallelTestClosed: String @Composable get() = LocalWhiteZiaStrings.current.parallelTestClosed

    val appsSearchPlaceholder: String @Composable get() = LocalWhiteZiaStrings.current.appsSearchPlaceholder

    val profileFieldProfileLinksLabel: String @Composable get() = LocalWhiteZiaStrings.current.profileFieldProfileLinksLabel

    val cdCloseSelector: String @Composable get() = LocalWhiteZiaStrings.current.cdCloseSelector
    val cdSelected: String @Composable get() = LocalWhiteZiaStrings.current.cdSelected
    val cdDismissScannerInfo: String @Composable get() = LocalWhiteZiaStrings.current.cdDismissScannerInfo
    val cdEditField: String @Composable get() = LocalWhiteZiaStrings.current.cdEditField
    val cdDragToReorder: String @Composable get() = LocalWhiteZiaStrings.current.cdDragToReorder
    val cdProfileQrCode: String @Composable get() = LocalWhiteZiaStrings.current.cdProfileQrCode
    val cdAppMenu: String @Composable get() = LocalWhiteZiaStrings.current.cdAppMenu
    val cdAppSettings: String @Composable get() = LocalWhiteZiaStrings.current.cdAppSettings
    val cdDonate: String @Composable get() = LocalWhiteZiaStrings.current.cdDonate
    val cdDismissBatteryWarning: String @Composable get() = LocalWhiteZiaStrings.current.cdDismissBatteryWarning
    val cdDismissVpnWarning: String @Composable get() = LocalWhiteZiaStrings.current.cdDismissVpnWarning

    val resolverCountTemplate: String @Composable get() = LocalWhiteZiaStrings.current.resolverCountTemplate
    val resolverCountOneTemplate: String @Composable get() = LocalWhiteZiaStrings.current.resolverCountOneTemplate

    val genericConnectionFallback: String @Composable get() = LocalWhiteZiaStrings.current.genericConnectionFallback
    val genericResolverFallback: String @Composable get() = LocalWhiteZiaStrings.current.genericResolverFallback
    val genericSettingFallback: String @Composable get() = LocalWhiteZiaStrings.current.genericSettingFallback
    val scanProfileMenuActions: String @Composable get() = LocalWhiteZiaStrings.current.scanProfileMenuActions
    val settingProfileMenuActions: String @Composable get() = LocalWhiteZiaStrings.current.settingProfileMenuActions
    val connectionProfileMenuActions: String @Composable get() = LocalWhiteZiaStrings.current.connectionProfileMenuActions
    val resolverProfileMenuActions: String @Composable get() = LocalWhiteZiaStrings.current.resolverProfileMenuActions
    val useSettingProfile: String @Composable get() = LocalWhiteZiaStrings.current.useSettingProfile
    val useResolverProfile: String @Composable get() = LocalWhiteZiaStrings.current.useResolverProfile
    val exportConnectionProfileAction: String @Composable get() = LocalWhiteZiaStrings.current.exportConnectionProfileAction
    val editConnectionProfileAction: String @Composable get() = LocalWhiteZiaStrings.current.editConnectionProfileAction
    val deleteConnectionProfileAction: String @Composable get() = LocalWhiteZiaStrings.current.deleteConnectionProfileAction
    val deleteConnectionProfileBlockedAction: String @Composable get() = LocalWhiteZiaStrings.current.deleteConnectionProfileBlockedAction
    val exportSettingProfileAction: String @Composable get() = LocalWhiteZiaStrings.current.exportSettingProfileAction
    val editSettingProfileAction: String @Composable get() = LocalWhiteZiaStrings.current.editSettingProfileAction
    val deleteSettingProfileAction: String @Composable get() = LocalWhiteZiaStrings.current.deleteSettingProfileAction
    val editResolverProfileAction: String @Composable get() = LocalWhiteZiaStrings.current.editResolverProfileAction
    val deleteResolverProfileAction: String @Composable get() = LocalWhiteZiaStrings.current.deleteResolverProfileAction
    val brandWhiteZia: String @Composable get() = LocalWhiteZiaStrings.current.brandWhiteZia

    val errorImportSettingsFile: String @Composable get() = LocalWhiteZiaStrings.current.errorImportSettingsFile
    val errorImportSettings: String @Composable get() = LocalWhiteZiaStrings.current.errorImportSettings
    val errorImportResolver: String @Composable get() = LocalWhiteZiaStrings.current.errorImportResolver
    val errorImportProfile: String @Composable get() = LocalWhiteZiaStrings.current.errorImportProfile
    val errorExportProfile: String @Composable get() = LocalWhiteZiaStrings.current.errorExportProfile

    val resolverScanResults: String @Composable get() = LocalWhiteZiaStrings.current.resolverScanResults
    val scanResultsSuffix: String @Composable get() = LocalWhiteZiaStrings.current.scanResultsSuffix
    val noResolverEntriesError: String @Composable get() = LocalWhiteZiaStrings.current.noResolverEntriesError

    val profileImportSuccess: String @Composable get() = LocalWhiteZiaStrings.current.profileImportSuccess
    val profileBtnScanQr: String @Composable get() = LocalWhiteZiaStrings.current.profileBtnScanQr
    val qrScanNoCode: String @Composable get() = LocalWhiteZiaStrings.current.qrScanNoCode
    val qrScanCancelled: String @Composable get() = LocalWhiteZiaStrings.current.qrScanCancelled

}

object WhiteZiaPalette {
    val Background: Color
        @Composable get() = LocalWhiteZiaPalette.current.Background
    val Surface: Color
        @Composable get() = LocalWhiteZiaPalette.current.Surface
    val SurfaceAlt: Color
        @Composable get() = LocalWhiteZiaPalette.current.SurfaceAlt
    val DropdownSurface: Color
        @Composable get() = LocalWhiteZiaPalette.current.DropdownSurface
    val Border: Color
        @Composable get() = LocalWhiteZiaPalette.current.Border
    val Divider: Color
        @Composable get() = LocalWhiteZiaPalette.current.Divider
    val ControlBorder: Color
        @Composable get() = LocalWhiteZiaPalette.current.ControlBorder
    val Accent: Color
        @Composable get() = LocalWhiteZiaPalette.current.Accent
    val AccentPressed: Color
        @Composable get() = LocalWhiteZiaPalette.current.AccentPressed
    val AccentText: Color
        @Composable get() = LocalWhiteZiaPalette.current.AccentText
    val OnAccent: Color
        @Composable get() = LocalWhiteZiaPalette.current.OnAccent
    val Success: Color
        @Composable get() = LocalWhiteZiaPalette.current.Success
    val Error: Color
        @Composable get() = LocalWhiteZiaPalette.current.Error
    val Warning: Color
        @Composable get() = LocalWhiteZiaPalette.current.Warning
    val WarningText: Color
        @Composable get() = LocalWhiteZiaPalette.current.WarningText
    val Ink: Color
        @Composable get() = LocalWhiteZiaPalette.current.Ink
    val Muted: Color
        @Composable get() = LocalWhiteZiaPalette.current.Muted
    val Pale: Color
        @Composable get() = LocalWhiteZiaPalette.current.Pale
    val SectionTitle: Color
        @Composable get() = LocalWhiteZiaPalette.current.SectionTitle
    val FieldLabel: Color
        @Composable get() = LocalWhiteZiaPalette.current.FieldLabel
    val Description: Color
        @Composable get() = LocalWhiteZiaPalette.current.Description
    val Placeholder: Color
        @Composable get() = LocalWhiteZiaPalette.current.Placeholder
    val Disabled: Color
        @Composable get() = LocalWhiteZiaPalette.current.Disabled
    val Input: Color
        @Composable get() = LocalWhiteZiaPalette.current.Input
    val AccentDim: Color
        @Composable get() = LocalWhiteZiaPalette.current.AccentDim
    val SurfaceHover: Color
        @Composable get() = LocalWhiteZiaPalette.current.SurfaceHover
    val AccentSurface: Color
        @Composable get() = LocalWhiteZiaPalette.current.AccentSurface
    val SuccessSurface: Color
        @Composable get() = LocalWhiteZiaPalette.current.SuccessSurface
    val WarningSurface: Color
        @Composable get() = LocalWhiteZiaPalette.current.WarningSurface
    val ErrorSurface: Color
        @Composable get() = LocalWhiteZiaPalette.current.ErrorSurface
}

@Composable
fun currentPalette(themeMode: String = WhiteZiaThemeMode.System): WhiteZiaPaletteColors {
    return if (shouldUseDarkTheme(themeMode)) WhiteZiaPaletteDark else WhiteZiaPaletteLight
}

private val WhiteZiaColorSchemeDark = darkColorScheme(
    primary = WhiteZiaPaletteDark.Accent,
    onPrimary = WhiteZiaPaletteDark.OnAccent,
    primaryContainer = WhiteZiaPaletteDark.AccentPressed,
    onPrimaryContainer = WhiteZiaPaletteDark.OnAccent,
    secondary = WhiteZiaPaletteDark.Pale,
    onSecondary = WhiteZiaPaletteDark.Background,
    secondaryContainer = WhiteZiaPaletteDark.DropdownSurface,
    onSecondaryContainer = WhiteZiaPaletteDark.Ink,
    tertiary = WhiteZiaPaletteDark.Success,
    onTertiary = WhiteZiaPaletteDark.Background,
    tertiaryContainer = WhiteZiaPaletteDark.SuccessSurface,
    onTertiaryContainer = WhiteZiaPaletteDark.Success,
    background = WhiteZiaPaletteDark.Background,
    onBackground = WhiteZiaPaletteDark.Ink,
    surface = WhiteZiaPaletteDark.Surface,
    onSurface = WhiteZiaPaletteDark.Ink,
    surfaceVariant = WhiteZiaPaletteDark.SurfaceAlt,
    onSurfaceVariant = WhiteZiaPaletteDark.Muted,
    surfaceTint = WhiteZiaPaletteDark.Accent,
    outline = WhiteZiaPaletteDark.ControlBorder,
    outlineVariant = WhiteZiaPaletteDark.Border,
    inverseSurface = WhiteZiaPaletteDark.Ink,
    inverseOnSurface = WhiteZiaPaletteDark.Background,
    inversePrimary = WhiteZiaPaletteDark.AccentPressed,
    error = WhiteZiaPaletteDark.Error,
    onError = WhiteZiaPaletteDark.OnAccent,
    errorContainer = WhiteZiaPaletteDark.ErrorSurface,
    onErrorContainer = WhiteZiaPaletteDark.Error,
    scrim = Color(0xFF000000),
    surfaceBright = WhiteZiaPaletteDark.Divider,
    surfaceDim = WhiteZiaPaletteDark.Background,
    surfaceContainerLowest = WhiteZiaPaletteDark.Background,
    surfaceContainerLow = WhiteZiaPaletteDark.SurfaceAlt,
    surfaceContainer = WhiteZiaPaletteDark.Surface,
    surfaceContainerHigh = WhiteZiaPaletteDark.DropdownSurface,
    surfaceContainerHighest = WhiteZiaPaletteDark.Divider,
    primaryFixed = WhiteZiaPaletteDark.AccentSurface,
    primaryFixedDim = WhiteZiaPaletteDark.AccentSurface,
    onPrimaryFixed = WhiteZiaPaletteDark.Ink,
    onPrimaryFixedVariant = WhiteZiaPaletteDark.Muted,
    secondaryFixed = WhiteZiaPaletteDark.DropdownSurface,
    secondaryFixedDim = WhiteZiaPaletteDark.DropdownSurface,
    onSecondaryFixed = WhiteZiaPaletteDark.Ink,
    onSecondaryFixedVariant = WhiteZiaPaletteDark.Muted,
    tertiaryFixed = WhiteZiaPaletteDark.SuccessSurface,
    tertiaryFixedDim = WhiteZiaPaletteDark.SuccessSurface,
    onTertiaryFixed = WhiteZiaPaletteDark.Ink,
    onTertiaryFixedVariant = WhiteZiaPaletteDark.Success,
)

private val WhiteZiaColorSchemeLight = lightColorScheme(
    primary = WhiteZiaPaletteLight.Accent,
    onPrimary = WhiteZiaPaletteLight.OnAccent,
    primaryContainer = WhiteZiaPaletteLight.AccentSurface,
    onPrimaryContainer = WhiteZiaPaletteLight.AccentText,
    secondary = WhiteZiaPaletteLight.Pale,
    onSecondary = WhiteZiaPaletteLight.Surface,
    secondaryContainer = WhiteZiaPaletteLight.SurfaceAlt,
    onSecondaryContainer = WhiteZiaPaletteLight.Ink,
    tertiary = WhiteZiaPaletteLight.Success,
    onTertiary = WhiteZiaPaletteLight.OnAccent,
    tertiaryContainer = WhiteZiaPaletteLight.SuccessSurface,
    onTertiaryContainer = WhiteZiaPaletteLight.Success,
    background = WhiteZiaPaletteLight.Background,
    onBackground = WhiteZiaPaletteLight.Ink,
    surface = WhiteZiaPaletteLight.Surface,
    onSurface = WhiteZiaPaletteLight.Ink,
    surfaceVariant = WhiteZiaPaletteLight.SurfaceAlt,
    onSurfaceVariant = WhiteZiaPaletteLight.Muted,
    surfaceTint = WhiteZiaPaletteLight.Accent,
    outline = WhiteZiaPaletteLight.ControlBorder,
    outlineVariant = WhiteZiaPaletteLight.Border,
    inverseSurface = WhiteZiaPaletteLight.Ink,
    inverseOnSurface = WhiteZiaPaletteLight.Background,
    inversePrimary = WhiteZiaPaletteLight.AccentPressed,
    error = WhiteZiaPaletteLight.Error,
    onError = WhiteZiaPaletteLight.OnAccent,
    errorContainer = WhiteZiaPaletteLight.ErrorSurface,
    onErrorContainer = WhiteZiaPaletteLight.Error,
    scrim = Color(0x77000000),
    surfaceBright = WhiteZiaPaletteLight.Surface,
    surfaceDim = WhiteZiaPaletteLight.SurfaceAlt,
    surfaceContainerLowest = WhiteZiaPaletteLight.Background,
    surfaceContainerLow = WhiteZiaPaletteLight.SurfaceAlt,
    surfaceContainer = WhiteZiaPaletteLight.Surface,
    surfaceContainerHigh = WhiteZiaPaletteLight.DropdownSurface,
    surfaceContainerHighest = WhiteZiaPaletteLight.Surface,
    primaryFixed = WhiteZiaPaletteLight.AccentSurface,
    primaryFixedDim = WhiteZiaPaletteLight.AccentSurface,
    onPrimaryFixed = WhiteZiaPaletteLight.Ink,
    onPrimaryFixedVariant = WhiteZiaPaletteLight.Muted,
    secondaryFixed = WhiteZiaPaletteLight.SurfaceAlt,
    secondaryFixedDim = WhiteZiaPaletteLight.SurfaceAlt,
    onSecondaryFixed = WhiteZiaPaletteLight.Ink,
    onSecondaryFixedVariant = WhiteZiaPaletteLight.Muted,
    tertiaryFixed = WhiteZiaPaletteLight.SuccessSurface,
    tertiaryFixedDim = WhiteZiaPaletteLight.SuccessSurface,
    onTertiaryFixed = WhiteZiaPaletteLight.Ink,
    onTertiaryFixedVariant = WhiteZiaPaletteLight.Success,
)

private val WhiteZiaTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
    ),
)

private val WhiteZiaPersianFontFamily = FontFamily(
    Font(R.font.vazirmatn_ui_fd_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_ui_fd_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_ui_fd_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_ui_fd_bold, FontWeight.Bold),
)

private fun Typography.withFontFamily(fontFamily: FontFamily): Typography {
    return copy(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily),
    )
}

@Suppress("DEPRECATION")
@Composable
fun WhiteZiaTheme(
    themeMode: String = WhiteZiaThemeMode.System,
    languageCode: String = WhiteZiaLanguage.En,
    content: @Composable () -> Unit,
) {
    val darkTheme = shouldUseDarkTheme(themeMode)
    val palette = if (darkTheme) WhiteZiaPaletteDark else WhiteZiaPaletteLight
    val colorScheme = if (darkTheme) WhiteZiaColorSchemeDark else WhiteZiaColorSchemeLight
    val isPersian = languageCode == WhiteZiaLanguage.Fa
    val layoutDirection = if (isPersian) LayoutDirection.Rtl else LayoutDirection.Ltr
    val typography = if (isPersian) {
        WhiteZiaTypography.withFontFamily(WhiteZiaPersianFontFamily)
    } else {
        WhiteZiaTypography
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = palette.Background.toArgb()
            window.navigationBarColor = palette.Surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val strings = if (isPersian) PersianStrings else EnglishStrings
    CompositionLocalProvider(
        LocalWhiteZiaPalette provides palette,
        LocalWhiteZiaStrings provides strings,
        androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

@Composable
private fun shouldUseDarkTheme(themeMode: String): Boolean {
    val systemDarkTheme = isSystemInDarkTheme()
    return when (themeMode) {
        WhiteZiaThemeMode.Light -> false
        WhiteZiaThemeMode.Dark -> true
        else -> systemDarkTheme
    }
}
