# `:core:ui` — طبقة الواجهة الأساسية (Android Library)

**الغرض:** نظام التصميم (theme, colors, typography)، مكونات Compose القابلة لإعادة الاستخدام، التنقل (navigation)، ونماذج UI (DTOs) للطبقة العرضية.

**البناء (`build.gradle.kts`):** `namespace = "com.p2p.meshify.core.ui"`. تعتمد على `:core:common`, `:core:domain`. تستخدم Compose BOM 2026.03.01، `material3` (1.5.0-alpha17)، `material.icons.extended`، `androidx.graphics.shapes` (MD3E)، Coil 3 (compose + network)، ExoPlayer (media3)، Navigation Compose 2.9.7، `kotlinx.serialization.json`، lifecycle، Accompanist Permissions. تملك `res/` خاصة (`com.p2p.meshify.core.ui.R`) + موارد من `core:common`.

## الملفات الرئيسية

جميع المسارات نسبة إلى `core/ui/src/main/java/com/p2p/meshify/core/ui/`:

| الملف | المحتوى |
|---|---|
| `theme/Color.kt` | لوحة الألوان (Teal/Violet للفاتح، Teal للداكن) + 8 ألوان مسبقة لاختيار seed color. |
| `theme/Type.kt` | `Typography` كامل M3 (أحجام خطوط و `FontWeight.ExtraBold` للعناوين). |
| `theme/Theme.kt` | `MeshifyTheme()` — يدعم dynamic color (Android 12+) والأنماط الفاتح/الداكن/النظامي. |
| `theme/MeshifyDesignSystem.kt` | `object MeshifyDesignSystem` — ثوابت: `Spacing` (4–48dp)، `Shapes` (8–24dp)، `IconSizes` (18–40dp)، `Elevation` (0–4dp). |
| `navigation/Screen.kt` | `sealed class Screen` بـ `@Serializable`: Onboarding, Home, Discovery, Chat(peerId, peerName), Settings, Developer, RealDeviceTesting. |
| `navigation/MeshifyNavigation.kt` | `MeshifyNavHost()` — `NavHost` مع `composable<T>` لكل مسار. يأخذ lambdas للشاشات الفعلية (Inversion of Control) من `:app`. |
| `components/MeshifyKit.kt` | المكونات الأساسية: `MeshifyAvatar`, `MeshifyAvatarWithOnline`, `MeshifyCard`, `MeshifyListItem`, `MeshifySectionHeader`, `MeshifyPill`. |
| `components/MeshifyKitDialogs.kt` | `DeleteConfirmationDialog`, `FullImageViewer`, `MeshifyTextInputDialog`, `MeshifySelectionDialog<T>`, `ThemeSelectionBottomSheet`, `SeedColorPickerGrid`. |
| `components/AlbumMediaGrid.kt` | شبكة وسائط ألبوم (3 أعمدة) عبر Coil. |
| `components/ForwardMessageDialog.kt` | حوار إعادة توجيه كامل مع بحث وأقسام واختيار متعدد وشريط تقدم (`ForwardDialogState`). |
| `components/MediaStagingChatInput.kt` | شريط إدخال محادثة مع أزرار صور/فيديو/ملف + `BasicTextField` + زر إرسال دائري متحرك. |
| `components/PhysicsSwipeToDelete.kt` | سحب للحذف بفيزياء (تأثير مغناطيسي) مع احتكاك وعتبة فتح. |
| `components/QrCodeDisplay.kt` | عرض رمز QR للتحقق OOB (يستخدم Icon placeholder حالياً). |
| `components/SettingsGroup.kt` | `MeshifySettingsGroup` و `MeshifySettingsItem`. |
| `components/StagedMediaRow.kt` | صف أفقي للمرفقات المؤقتة مع صور مصغرة. |
| `components/VideoPlayer.kt` | مشغل فيديو عبر `ExoPlayer` (`PlayerView` في `AndroidView`)، `playWhenReady = false`. |
| `hooks/PremiumHaptics.kt` | `PremiumHaptics` بـ 12 نمطاً (`Tick, Pop, Thud, Buildup, Success, Error, Send...`) + `LocalPremiumHaptics` CompositionLocal. |
| `model/AttachmentUiModel.kt` | `data class AttachmentUiModel(id, type: MessageType, filePath)`. |
| `model/ChatUiModel.kt` | `data class ChatUiModel(peerId, peerName, lastMessage, timestamp, unreadCount)`. |
| `model/MessageUiModel.kt` | `data class MessageUiModel(id, text, type: MessageType, timestamp)`. |
| `model/StagedAttachment.kt` | `data class StagedAttachment(uri: Uri, bytes: ByteArray, type: MessageType)`. |

## قرارات تقنية ظاهرة

- **الألوان:** M3 Material You مع dynamic color (Android 12+) أو تبديل يدوي بين `DarkColorScheme`/`LightColorScheme`. 8 ألوان seed مسبقة في `Color.kt`.
- **التنقل:** Jetpack Navigation Compose 2.9.7 مع `Screen` sealed class `@Serializable` (type-safe). `MeshifyNavHost` هو وعاء IoC لا يعرف تفاصيل الشاشات.
- **MD3E:** `androidx.graphics.shapes` لتشكيل morphing + `material3` alpha17. `@OptIn(ExperimentalMaterial3Api::class)`.
- **Coil 3:** `AsyncImage` مع `crossfade(true)`؛ الصور المحلية تُحمّل عبر `File(mediaPath)` لا URI.
- **الاهتزاز:** `PremiumHaptics` يستخدم `VibrationEffect.createPredefined` (API 29+) أو `createWaveform`، متاح عبر `LocalPremiumHaptics`.
- **نماذج UI:** 4 نماذج تفصل `:core:ui` عن `:core:data` (لا تعتمد على Room entities).
- **RTL:** موارد عربية/إنجليزية مع دعم RTL.
