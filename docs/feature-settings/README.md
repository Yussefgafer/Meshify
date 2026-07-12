# `:feature:settings` — الإعدادات + شاشة المطورين

**الغرض:** إعدادات التطبيق الشاملة (الهوية، المظهر، الخصوصية، الشبكة، التطبيق، معلومات، وشاشة المطورين المخفية).

**الاعتماديات (`build.gradle.kts`):** `:core:domain`, `:core:data`, `:core:ui`, `:core:common` + Compose، material3، icons-extended، Coil 3، lifecycle، Hilt.

## الملفات

جميع المسارات نسبة إلى `feature/settings/src/main/java/com/p2p/meshify/feature/settings/`:

| الملف | المحتوى |
|---|---|
| `SettingsScreen.kt` | الشاشة الرئيسية: `LargeTopAppBar` + `Scaffold` + `Column` (verticalScroll) + header (avatar/name/deviceId). يستضيف حالات ظهور الحوارات/الأوراق السفلية والـ snackbars، وينادي المقاطع والأغلفة أدناه. |
| `SettingsSections.kt` | مقاطع الشاشة كـ composables مستقلة: `IdentitySection`, `AppearanceSection`, `PrivacySection`, `NetworkSection`, `AppSettingsSection`, `AboutSection` (مع منطق الـ easter-egg للنقر 7 مرات). تستخدم مكوّنات MD3E المحلية من `SettingsComponents.kt`. |
| `SettingsComponents.kt` | مكوّنات MD3E معبّرة محلية (مستوحاة من لغة PixelPlayer): `SettingsSection` (ترويسة بأيقونة بارزة ملوّنة)، `SettingsItem` (صف بطاقة `surfaceContainer`)، `SwitchSettingItem` (مفتاح متحرك بأيقونة Check/Close عبر `AnimatedContent`). |
| `SettingsViewModel.kt` | يقرأ الـ Flows من `ISettingsRepository` ويكتبها في `SettingsUiState`. |
| `BleStatusBottomSheet.kt` | `BleStatusBottomSheet`: حالة BLE + `FilterChip` لـ `TransportMode` (AUTO/LAN_ONLY/BLE_ONLY/MULTI_PATH)، بأسلوب MD3E. |
| `SettingsNameDialog.kt` | غلاف حول `MeshifyTextInputDialog` لتعديل الاسم. |
| `SettingsThemeSheet.kt` | غلاف حول `ThemeSelectionBottomSheet` لاختيار الثيم. |
| `SettingsLanguageDialog.kt` | غلاف حول `MeshifySelectionDialog` (en/ar) — يُعيد إنشاء الـ Activity عند التغيير. |
| `SettingsFontSizeDialog.kt` | غلاف حول `MeshifySelectionDialog` لمقياس الخط (0.8/1.0/1.2/1.5). |
| `SettingsBackupDialog.kt` | `AlertDialog` النسخ الاحتياطي/الاستعادة (MD3E: FilledTonalButton للتأكيد). |
| `SettingsCreditsDialog.kt` | `AlertDialog` الاعتمادات (MD3E). |
| `DeveloperScreen.kt` | شاشة المطور (مخفية): composable فقط. |
| `DeveloperViewModel.kt` | `DeveloperViewModel` (منفصل عن الشاشة): إدراج/مسح بيانات وهمية. |

## الشاشات والمكونات

- **`SettingsScreen`** (المسار `Screen.Settings`): `LargeTopAppBar` + `Scaffold` + `Column` (verticalScroll) + `MeshifySettingsGroup`/`MeshifySettingsItem`.
  - الحوارات المحلية: `MeshifyTextInputDialog` (الاسم)، `ThemeSelectionBottomSheet` (الثيم)، `MeshifySelectionDialog` (اللغة، حجم الخط)، `AlertDialog` (النسخ الاحتياطي، الاعتمادات)، `BleStatusBottomSheet` (حالة BLE + Transport Mode)، `SeedColorPickerGrid` (لون البذرة).
- **`DeveloperScreen`** (المسار `Screen.Developer`): تُفتح بـ 7 نقرات على رقم الإصدار (مهلة 2 ثانية). `MeshifySettingsGroup` (Mock Data / Testing / Cleanup) + `AlertDialog` تأكيد + Snackbar.
- **`BleStatusBottomSheet`**: `FilterChip` لـ `TransportMode` (AUTO/LAN_ONLY/BLE_ONLY/MULTI_PATH).

## `SettingsViewModel`

- `settingsUiState: StateFlow<SettingsUiState>`: `displayName`, `themeMode`, `dynamicColorEnabled`, `hapticFeedbackEnabled`, `isNetworkVisible`, `avatarHash`, `deviceId`, `deviceIdLoaded`, `seedColor`, `appLanguage`, `fontSizeScale`, `notificationsEnabled`, `notificationSound`, `notificationVibrate`, `bleEnabled`, `transportMode`, `displayNameError`.
- `errorMessage: StateFlow<String?>`، `deviceId: StateFlow<String>`، `appVersion: String`.
- أفعال: `updateDisplayName`, `setThemeMode`, `setHapticFeedback`, `setDynamicColor`, `setNetworkVisibility`, `updateAvatar`، `setSeedColor`, `setAppLanguage`, `setFontSizeScale`, `setNotificationsEnabled/Sound/Vibrate`, `setBleEnabled`, `setTransportMode`, `clearCache`, `exportBackup`, `clearError`.

## `DeveloperViewModel`

- بلا StateFlows عامة (نمط callback `onComplete: (String) -> Unit`).
- `clearAllData()`، `insertMockConversations()` (7 محادثات × 4 رسائل)، `insertMockMediaMessages()`، `insertMockChatWithReactions()`، `insertMockChatWithReplies()`، `insertMockLongConversation()` (50 رسالة)، `clearMockData()`.

## قرارات تقنية

- **7 نقرات** على رقم الإصدار لفتح شاشة المطورين (easter egg).
- **تبديل اللغة** يعيد إنشاء Activity (`activity.recreate()`).
- **اختيار avatar** عبر `GetContent()` لـ `image/*`؛ نسخ Device ID عبر `ClipboardManager`.
- كل الإعدادات عبر `ISettingsRepository` (DataStore). `SettingsViewModel` يُنشأ **يدوياً** في `MainActivity` (وليس Hilt).
- زر "Clear All Data" يتطلب تأكيداً.
