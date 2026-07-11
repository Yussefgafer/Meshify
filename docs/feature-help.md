# `:feature:help` — (كود ميت / غير موصول)

> ⚠️ **هذه الوحدة غير موصولة بأي مسار تنقل ولا يمكن الوصول إليها وقت التشغيل.** توثيقها هنا لأغراض الاكتمال فقط.

**الغرض الظاهري:** شاشتا مساعدة (Help / About).

**الاعتماديات (`build.gradle.kts`):** `:core:ui`, `:core:common`. Compose BOM + material3 + icons-extended. **لا Hilt ولا ViewModel.**

## الملفات

جميع المسارات نسبة إلى `feature/help/src/main/java/com/p2p/meshify/feature/help/`:

| الملف | المحتوى |
|---|---|
| `HelpScreen.kt` | `@Composable fun HelpScreen(onNavigateBack, onNavigateToAbout)` — 4 أقسام قابلة للطي (Getting Started, Connectivity, Privacy & Security, Troubleshooting) + بطاقة About تنتقل إليها. Composable صرف بلا ViewModel. |
| `AboutScreen.kt` | `@Composable fun AboutScreen(appVersion, onNavigateBack)` — اسم التطبيق، شارة الإصدار، الوصف، أقسام Team/Features/Tech Stack، ورابط GitHub عبر `LocalUriHandler`. |

## حالة الوصول

- `Screen` sealed class (`core/ui/.../navigation/Screen.kt`) لا تعرّف `Screen.Help` أو `Screen.About`.
- `grep` لـ `Screen.Help` / `HelpScreen` / `Screen.About` / `AboutScreen` عبر المستودع (عدا التعريفات) = **صفر نتائج** في `app/` و `core/ui/`.
- `MeshifyNavHost` و `MainActivity.kt` لا يستدعيانها.
- `:app` **لا يعتمد** على `:feature:help` (غائبة من `build.gradle.kts`).

**النتيجة:** الشاشتان قابلتان للترجمة لكنهما غير قابلتين للوصول — كود ميت حتى يتم توصيل مسار لهما.
