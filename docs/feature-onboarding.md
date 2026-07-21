# `:feature:onboarding` — شاشة الترحيب الأولى

**الغرض:** شاشة الترحيب للمستخدم الجديد — 3 صفحات (ترحيب → كيف يعمل → الأذونات) مع اختيار اللغة، طلب الأذونات واحدة تلو الأخرى، وحوار ملخص.

**الاعتماديات (`build.gradle.kts`):** `:core:domain`, `:core:ui`, `:core:common` (لا تعتمد على `:core:data` ولا `:core:network`) + Compose، material3، lifecycle، Hilt، Kotlin Serialization.

## الملفات

جميع المسارات نسبة إلى `feature/onboarding/src/main/java/com/p2p/meshify/feature/onboarding/`:

| الملف | المحتوى |
|---|---|
| `WelcomeScreen.kt` | الـ Composable الرئيسي: `TopAppBar`، `HorizontalPager` (3 صفحات)، `BottomNav`، `PermissionRequestCard`، `PermissionResultCard`، `PermissionSummaryDialog`، `PermissionDefinitions`. |
| `WelcomeViewModel.kt` | ViewModel لحالة التنقل بين الصفحات والقائمة المنسدلة للغة. |
| `WelcomeUiState.kt` | `WelcomeUiState`, `PermissionInfo`, `PermissionStatus` (enum), `PermissionIconType` (enum), `PermissionRequestResult` (sealed) + `toPermissionStatus()`. |
| `OnboardingPage.kt` | الصفحات الثلاث: `WelcomePage`, `HowItWorksPage` (+`StepCard`), `PermissionsOverviewPage` (+`PermissionRow`, `StatusBadge`). |
| `SkipConfirmationDialog.kt` | حوار تأكيد تخطي سير الأذونات. |

## الشاشات

- **`WelcomeScreen`** (المسار `Screen.Onboarding`): `TopBar` (لغة + Skip) + `HorizontalPager` (3 صفحات) + `BottomNav` (مؤشر + Next/Get Started).

## `WelcomeViewModel`

- `uiState` يحوي `currentPage` (0–2)، `totalPages = 3`، `isAnimating` (يمنع التفاعل 300ms)، `isLangMenuOpen`.
- أفعال: `nextPage()`، `goToPage(pageIndex)`، `toggleLangMenu()`.

## قرارات تقنية

- **سير الأذونات** يُدار محلياً في `OnboardingRoute` داخل `MainActivity.kt` (وليس بالـ ViewModel) — `remember` + `DisposableEffect`.
- **3 أذونات** (`PermissionDefinitions.getPermissions()`): Nearby Wi-Fi (أو Location لـ <Android 13) **إجباري**، Bluetooth (SCAN+CONNECT+ADVERTISE) **اختياري**، Notifications (API 33+) **اختياري**.
- **تبديل اللغة** يعيد إنشاء Activity (`activity.recreate()`) عبر `onLangChange`.
- أنواع `PermissionRequestResult`: `Granted, Denied, DeniedPermanently, Skipped, AlreadyGranted` (أُضيفت لتغطية حالات الأمان المفقودة).
- الـ ViewModel بلا تبعيات (`@Inject constructor()` فارغ). لا توجد ملفات اختبارية.
