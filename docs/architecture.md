# Architecture — Meshify

توثيق لبنية المشروع الفعلية المستخرجة من `settings.gradle.kts`، ملفات `build.gradle.kts`، وشجرة المصدر. لا يوجد أي كود وهمي أو افتراضي هنا.

## الطبقات (Module Layering)

الاعتمادية تصاعدية نحو الأسفل؛ لا يُسمح بكسر الاتجاه:

```
:app
  └─> :feature:*                    (home, chat, discovery, settings, onboarding, help, real-device-testing)
        └─> :core:domain           (pure Kotlin/JVM — صفر اعتماديات Android)
        └─> :core:common
        └─> :core:data
        └─> :core:network
        └─> :core:ui
```

- **`:core:domain` نقية تماماً (Kotlin/JVM)** — لا تستورد أي `android.*`. هذه أعمق طبقة ولا يعتمد عليها إلا ما هو أعلى منها.
- **لا يُسمح باعتماد متبادل بين `:feature:*`** — مثلاً `:feature:chat` لا تستورد `:feature:home`. كل وحدة feature تعتمد فقط على `:core:*`.
- المصدر يوضع تحت `src/main/java/` (وليس `src/main/kotlin/`).

## الوحدات وقوائم الحزم (namespaces)

| الوحدة | namespace | النوع |
|---|---|---|
| `:app` | `com.p2p.meshify` | Application |
| `:core:common` | `com.p2p.meshify.core.common` | Android Library |
| `:core:domain` | `com.p2p.meshify.domain.*` و `com.p2p.meshify.core.domain.*` | pure Kotlin/JVM |
| `:core:data` | `com.p2p.meshify.core.data` | Android Library |
| `:core:network` | `com.p2p.meshify.core.network` | Android Library |
| `:core:ui` | `com.p2p.meshify.core.ui` | Android Library |
| `:feature:home` | `com.p2p.meshify.feature.home` | Android Library |
| `:feature:chat` | `com.p2p.meshify.feature.chat` | Android Library |
| `:feature:discovery` | `com.p2p.meshify.feature.discovery` | Android Library |
| `:feature:settings` | `com.p2p.meshify.feature.settings` | Android Library |
| `:feature:onboarding` | `com.p2p.meshify.feature.onboarding` | Android Library |
| `:feature:help` | `com.p2p.meshify.feature.help` | Android Library (كود ميت — غير موصول) |
| `:feature:real-device-testing` | `com.p2p.meshify.feature.realdevicetesting` | Android Library |

## قرارات بنيوية بارزة

- **بادئتا تسمية في `:core:domain`** (ثغرة تاريخية موثّقة في `QWEN.md`):
  - `com.p2p.meshify.core.domain.*` — الأقدم (مثل `WifiStateChecker`).
  - `com.p2p.meshify.domain.*` — الأحدث (النماذج وواجهات المستودعات).
- **مُحسّنات المترجم (opt-ins)** مُعرّفة في `app/build.gradle.kts`:
  - `androidx.compose.material3.ExperimentalMaterial3Api`
  - `androidx.compose.material3.ExperimentalMaterial3ExpressiveApi`
- **`abiFilters = arm64-v8a` فقط** — لا توجد بناءات x86/32-bit.
- **مجلد مخطط Room:** `$projectDir/schemas` (يُصدَّر من `app/` و `core/data/`).
- **`lint.abortOnError = false`** و **`lint.checkReleaseBuilds = false`**.
- **`org.gradle.configuration-cache = false`** في `gradle.properties`.
- **الترجمة:** عربي (`values-ar`) وإنجليزي (`values`) مع دعم RTL. تُحفظ لغة الواجهة في DataStore (`appLanguage` flow)، وتغييرها يستدعي `activity.recreate()`.
- **التنقل:** عبر `MeshifyNavHost` في `:core:ui`؛ المسارات معرّفة في `sealed class Screen` باستخدام `@Serializable` (type-safe Navigation).
- **النقل (transport):** BLE اختياري (يُتحكّم به عبر الإعدادات)؛ الافتراضي LAN TCP + mDNS.
- **الأمان:** بعد Phase 3 كل الرسائل تُرسل بالنص العادي (plaintext) — راجع `MessageEnvelope` في `:core:domain`.

## جدول الاعتماديات بين الوحدات

| الوحدة | domain | common | data | network | ui | feature أخرى |
|---|---|---|---|---|---|---|
| `:core:domain` | — | — | — | — | — | — |
| `:core:common` | ✓ | — | — | — | — | — |
| `:core:data` | ✓ | ✓ | — | ✓ | — | — |
| `:core:network` | ✓ | ✓ | — | — | — | — |
| `:core:ui` | ✓ | ✓ | — | — | — | — |
| `:feature:home` | ✓ | ✓ | ✓ | — | ✓ | — |
| `:feature:chat` | ✓ | ✓ | ✓ | — | ✓ | — |
| `:feature:discovery` | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| `:feature:settings` | ✓ | ✓ | ✓ | — | ✓ | — |
| `:feature:onboarding` | ✓ | ✓ | — | — | ✓ | — |
| `:feature:help` | — | ✓ | — | — | ✓ | — |
| `:feature:real-device-testing` | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| `:app` | ✓ | ✓ | ✓ | ✓ | ✓ | كل الـ features ما عدا `help` |

**ملاحظة:** `:feature:help` مُعرّفة كوحدة لكنها **غير موصولة** بأي مسار تنقل (لا `Screen.Help` ولا `Screen.About`، ولا استدعاء من `MainActivity` أو `MeshifyNavHost`) — كود ميت حالياً.
