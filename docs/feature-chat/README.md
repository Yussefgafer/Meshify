# `:feature:chat` — شاشة المحادثة

**الغرض:** شاشة المحادثة الفردية — عرض وإرسال رسائل نصية ووسائط (صور، فيديو، ملفات) مع الرد، إعادة التوجيه، الحذف، التفاعلات (reactions)، البحث داخل المحادثة، وضع التحديد المتعدد، وعرض تقدم رفع الملفات.

**الاعتماديات (`build.gradle.kts`):** `:core:domain`, `:core:data`, `:core:ui`, `:core:common` + Compose BOM، material3، icons-extended، Coil 3، lifecycle، Hilt، navigation-compose.

> ملاحظة: `build.gradle.kts` يعلن `androidx.paging.compose` لكنه غير مستخدم فعلياً — لا توجد شاشة تستهلك Paging 3. ترقيم المحادثة اليدوي (`getMessagesPaged` بحجم 50) كان موجوداً سابقاً في `ChatViewModel` وحُذف لأنه كان ميتاً (`getMessages` يُرجع المحادثة كاملة).

## الملفات الرئيسية

جميع المسارات نسبة إلى `feature/chat/src/main/java/com/p2p/meshify/feature/chat/`:

| الملف | المحتوى |
|---|---|
| `ChatViewModel.kt` | المنطق الكامل: إرسال، رفع مرفقات، رد، إعادة توجيه، تحديد متعدد، بحث، حذف. |
| `ChatScreen.kt` | الـ Composable الرئيسي — ينسّق TopBar/InputBar/MessageList/الحوارات + تمرير ذكي + Snackbar للأخطاء. |
| `components/MessageList.kt` | `LazyColumn` برسائل متحركة (staggered) + تحميل مرفقات عبر `produceState` + حالة فارغة. |
| `components/MessageBubble.kt` | فقاعة الرسالة: نص/صورة (AsyncImage)/فيديو/ملف + شريط تقدم الرفع + حالة الإرسال + أيقونة النقل (Bluetooth/BOTH) + التفاعلات. |
| `components/ChatInputBar.kt` | شريط الإدخال + أزرار إرفاق (صور/فيديو/ملف عبر `GetContent`) + قراءة URI مع فحص الحجم (`MAX_FILE_SIZE_BYTES=100MB`) و `use {}`. |
| `components/ChatTopBar.kt` | الشريط العلوي: الاسم، avatar، حالة الاتصال، رجوع، بحث. |
| `components/SelectionModeTopBar.kt` | شريط وضع التحديد المتعدد: نسخ/إعادة توجيه/حذف. |
| `components/ChatContextMenu.kt` | Bottom Sheet عند الضغط المطول: رد/إعادة توجيه/نسخ/حذف. |
| `components/ReplyIndicator.kt` | مؤشر الرد فوق شريط الإدخال. |
| `components/ScrollToFAB.kt` | زر عائم للتمرير للأسفل عند الابتعاد. |
| `components/BackConfirmationDialog.kt` | تأكيد الرجوع عند وجود مسودة (>1024 حرف). |
| `components/DeleteConfirmationDialog.kt` | تأكيد حذف رسالة (مع `DELETE_FOR_EVERYONE`). |

## الشاشات

- **`ChatScreen`** (المسار `Screen.Chat(peerId, peerName)`): تركّب `Scaffold` + `snackbarHost` + `WindowInsets.ime` + TopBar (عادي/تحديد/بحث) + BottomBar (`ReplyIndicator` + `ChatInputBar` + `MediaStagingChatInput` + `StagedMediaRow`) + Body (`MessageList`/`SearchResultsList`) + `ScrollToFAB` + `ChatContextMenu` + `ForwardMessageDialog` + `FullImageViewer` + الحوارات + Snackbar + `LocalPremiumHaptics`.

## `ChatViewModel`

- الحقن: `@HiltViewModel` + `@ApplicationContext`, `SavedStateHandle`, `IChatRepository`. `peerId`/`peerName` من وسائط التنقل.
- **StateFlows:** `uiState` (رسائل، حالة اتصال، نص، draft، replyTo، مرفقات، isSending، أخطاء، `transportUsed: Map<String,TransportType>`)، `forwardDialogState`, `selectedMessages: Set<String>`, `uploadProgress: Map<String,Int>` (مع `sample(100ms)` + `WhileSubscribed(5000)`)، `searchQuery`, `searchResults`, `isSearching`.
- **أفعال:** `sendMessage()` (حماية double-tap 500ms)، `stageAttachment()` (حد 10)، `sendFileWithProgress()`/`cancelUpload()`، `deleteMessage()`، `addReaction()`، `openForwardDialog*()`/`forwardMessages()`، `toggleMessageSelection()`، `copySelectedMessagesToClipboard()`، `copyMessageToClipboard()`، `startSearch()`/`stopSearch()`/`updateSearchQuery()`.

## قرارات تقنية

- يصل إلى `ChatRepositoryImpl` عبر `as ChatRepositoryImpl` لدوال غير موجودة بالواجهة (`getMessages`, `searchMessagesInChat`, `getMessageAttachments`).
- تحديث فوري: جمع `chatRepo.getMessages(peerId)` مع `distinctUntilChanged()`.
- لا يوجد ترقيم صفحات في الواجهة: `getMessages` يُرجع المحادثة كاملة ويتعامل `LazyColumn` مع التمرير افتراضياً. ماكينة الترقيم اليدوي (صفحات 50، حد 200 رسالة) حُذفت لأنها كانت ميتة.
- LRU cache للمرفقات `LinkedHashMap` (200 إدخال).
- تتبع النقل: حفظ `TransportType` لكل رسالة؛ أيقونة Bluetooth لـ BLE و GridView لـ BOTH.
- رفع الملفات: `ConcurrentHashMap<String, Job>` للإلغاء الآمن.
- معالجة صور: Coil 3 `AsyncImage` + `crossfade(true)`؛ عند غياب `File(path)` يُعرض عنصر نائب بأيقونة `BrokenImage`.
- تمرير ذكي: `derivedStateOf` + `snapshotFlow` + `LaunchedEffect`.
