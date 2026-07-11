# `:feature:settings` — الإعدادات + شاشة المطورين

**الغرض:** إعدادات التطبيق الشاملة (الهوية، المظهر، الخصوصية، الشبكة، التطبيق، معلومات، وشاشة المطورين المخفية).

**الاعتماديات (`build.gradle.kts`):** `:core:domain`, `:core:data`, `:core:ui`, `:core:common` + Compose، material3، icons-extended، Coil 3، lifecycle، Hilt.

## الملفات

جميع المسارات نسبة إلى `feature/settings/src/main/java/com/p2p/meshify/feature/settings/`:

| الملف | المحتوى |
|---|---|
| `SettingsScreen.kt` | الشاشة الرئيسية (~807 سطر): avatar، الهوية، المظهر، الخصوصية، الشبكة، إعدادات التطبيق، about، كل الحوارات/الأوراق السفلية. |
| `SettingsViewModel.kt` | يقرأ 15+ Flow من `ISettingsRepository` ويكتبها. |
| `DeveloperScreen.kt` | شاشة المطور (مخفية): `DeveloperViewModel` داخلي + Composables لإدراج/مسح بيانات وهمية. |

## الشاشات والمكونات

- **`SettingsScreen`** (المسار `Screen.Settings`): `LargeTopAppBar` + `Scaffold` + `Column` (verticalScroll) + `MeshifySettingsGroup`/`MeshifySettingsItem`.
  - الحوارات المحلية: `MeshifyTextInputDialog` (الاسم)، `ThemeSelectionBottomSheet` (الثيم)، `MeshifySelectionDialog` (اللغة، حجم الخط)، `AlertDialog` (النسخ الاحتياطي، الاعتمادات)، `BleStatusBottomSheet` (حالة BLE + Transport Mode)، `SeedColorPickerGrid` (لون البذرة).
- **`DeveloperScreen`** (المسار `Screen.Developer`): تُفتح بـ 7 نقرات على رقم الإصدار (مهلة 2 ثانية). `MeshifySettingsGroup` (Mock Data / Testing / Cleanup) + `AlertDialog` تأكيد + Snackbar.
- **`BleStatusBottomSheet`**: `FilterChip` لـ `TransportMode` (AUTO/LAN_ONLY/BLE_ONLY/MULTI_PATH).

## `SettingsViewModel`

- `settingsUiState: StateFlow<SettingsUiState>` (~25 حقلاً): `displayName`, `themeMode`, `dynamicColorEnabled`, `hapticFeedbackEnabled`, `isNetworkVisible`, `avatarHash`, `deviceId`, `motionPreset`, `motionScale`, `fontFamilyPreset`, `customFontUri`, `bubbleStyle`, `visualDensity`, `seedColor`, `appLanguage`, `fontSizeScale`, `notificationsEnabled`, `notificationSound`, `notificationVibrate`, `bleEnabled`, `transportMode`, `displayNameError`.
- `errorMessage: StateFlow<String?>`، `deviceId: StateFlow<String>`، `appVersion: String`.
- أفعال: `updateDisplayName`, `setThemeMode`, `setHapticFeedback`, `setDynamicColor`, `setNetworkVisibility`, `updateAvatar`، `setSeedColor`, `setAppLanguage`, `setFontSizeScale`, `setNotificationsEnabled/Sound/Vibrate`, `setBleEnabled`, `setTransportMode`, `setMotionPreset`, `setShapeStyle`, `setMotionScale`, `setFontFamilyPreset`, `setCustomFontUri`, `setBubbleStyle`, `setVisualDensity`, `clearCache`, `exportBackup`, `clearError`.

## `DeveloperViewModel`

- بلا StateFlows عامة (نمط callback `onComplete: (String) -> Unit`).
- `clearAllData()`، `insertMockConversations()` (7 محادثات × 4 رسائل)، `insertMockMediaMessages()`، `insertMockChatWithReactions()`، `insertMockChatWithReplies()`، `insertMockLongConversation()` (50 رسالة)، `clearMockData()`.

## قرارات تقنية

- **7 نقرات** على رقم الإصدار لفتح شاشة المطورين (easter egg).
- **تبديل اللغة** يعيد إنشاء Activity (`activity.recreate()`).
- **اختيار avatar** عبر `GetContent()` لـ `image/*`؛ نسخ Device ID عبر `ClipboardManager`.
- كل الإعدادات عبر `ISettingsRepository` (DataStore). `SettingsViewModel` يُنشأ **يدوياً** في `MainActivity` (وليس Hilt).
- زر "Clear All Data" يتطلب تأكيداً.
