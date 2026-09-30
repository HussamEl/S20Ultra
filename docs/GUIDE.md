<div dir="rtl">

# دليل المشروع — Nästa Stopp 1.0

هذا الدليل لمن يستلم المشروع: ما هو التطبيق، وكيف بُني، وأين يوجد كل جزء، وكيف يُبنى ويُنشر ويُجرَّب، وما القواعد التي لا تُكسر ولماذا. يصف التطبيق كما هو الآن. تاريخ git قبل العلامة `v1.0` أرشيف لا تحتاجه للعمل.

- **دليل الاستعمال** للسائق، مع جداول الأرقام المرجعية: [README.md](../README.md).
- **القرارات وأسبابها**: [DECISIONS.md](../DECISIONS.md).
- **قواعد العمل المختصرة** (تُقرأ تلقائياً في كل محادثة جديدة مع Claude): [CLAUDE.md](../CLAUDE.md).

---

## 1. ما هو التطبيق

تطبيق أندرويد لسائق رحلات النقل المشترك (Region Värmland، السويد):
1. يقرأ العناوين وأوقات الرحلات من **لقطات شاشة** (قراءة نص على الجهاز)، أو من صفحة **YouDrive** (موقع التوزيع الذي يعمل به السائق) داخل التطبيق.
2. يرتّب المحطات، ويفتح **خرائط Google** بالملاحة إليها، 10 محطات في كل مرة.
3. **يعلن بالسويدية** المحطة التالية والتي بعدها، ويعرض **زراً عائماً** فوق الخرائط فيه الشارع الحالي والسرعة والرحلة التالية.
4. يعرض الوجهة القادمة على **تابلت للركاب** عبر البلوتوث.
5. ينبّه عند **إضافة رحلة أو إلغائها** في YouDrive.

| الشيء | القيمة |
|---|---|
| الحزمة | `se.eldebosh.nastastopp` |
| الإصدار | `versionName` و`versionCode` في `app/build.gradle.kts` (1.0 = البداية، `versionCode 1`) |
| الأدوات | Kotlin 2.4، AGP 9.4، Gradle 9.8، Jetpack Compose (Material 3) |
| أندرويد | `minSdk 29` (أندرويد 10)، `targetSdk/compileSdk 37` |
| القراءة من الصور | ML Kit Text Recognition **المدمج** (النموذج داخل التطبيق، بلا Play Services) |
| مكتبات أخرى | `androidx.webkit`، kotlinx.serialization، coroutines |
| لا يوجد | Hilt، Firebase، تحليلات، تقارير أعطال، موقع في الخلفية، إذن التخزين |
| الاختبارات | نحو 200 اختبار: JUnit للمنطق + Robolectric (أندرويد 13، sdk 33) |
| R8 | **مطفأ** في release (راجع القسم 9) |
| الأجهزة | جوال Samsung Galaxy S20 Ultra (أندرويد 13)، وتابلت Galaxy Tab S9+ لشاشة الركاب |

---

## 2. المعمارية

```
┌──────────────────────────── ui/ (Compose) ────────────────────────────┐
│ AppRoot → الشاشات (Home, Review, Active, Settings, Help, …)          │
│ YouDriveActivity (شريط Compose + WebView)                            │
└───────────────▲──────────────────────────────▲────────────────────────┘
                │ StateFlow                    │ StateFlow
┌───────────────┴─────────── طبقة أندرويد ─────┴────────────────────────┐
│ RouteController (مصدر الحقيقة للمسار)   YouDriveWatcher (صفحة الموقع)   │
│ OcrEngine · Geocoding · Announcer · MapsLauncher · OverlayManager      │
│ CurrentStreet · StreetMapStore · StreetService · StreetCaller         │
│ DisplayLinkServer/Client · Notifications · SettingsStore · TripHistory │
└───────────────▲───────────────────────────────────────────────────────┘
                │ دوال Kotlin خالصة
┌───────────────┴───────────── core/ (بلا أندرويد) ─────────────────────┐
│ parse/ (العناوين والأوقات) · ocr/ (التقسيم والدمج) · geo/ (الشارع)   │
│ route/ (الإعلانات والخرائط) · link/ · display/ · youdrive/            │
└───────────────────────────────────────────────────────────────────────┘
```

- **`core/`** منطق خالص بلا أي اعتماد على أندرويد، فيُختبر بسرعة في JUnit. كل منطق صعب يوضع هنا.
- **حقن يدوي**: `AppGraph` في `App.kt` ينشئ كل الكائنات مرة واحدة لكل عملية. الشاشات والخدمات تصل إليها عبر `App.from(context).graph`.
- **الحالة** `StateFlow`، تقرؤها الواجهة بـ `collectAsStateWithLifecycle()`. كل الاستدعاءات على الـ main thread، إلا ما يعمل في coroutines خلفية.
- **نشاطان**:
  - `MainActivity`: كل الشاشات، بالتنقّل عبر `MainViewModel.stack`.
  - `YouDriveActivity`: نافذة YouDrive الكاملة.

---

## 3. رحلة البيانات من الصورة إلى الإعلان

1. **الاستيراد**: الصورة تأتي بالمشاركة (`MainActivity.handleShareIntent`) أو من منتقي الصور في `AppRoot`، ثم `MainViewModel.importImages(uris)` ← `ScreenshotImporter.import`.
2. **القراءة (OCR)** في `ocr/OcrEngine`:
   - تُفتح الصورة **في الذاكرة فقط**، ولا تُنسخ.
   - تُقسَّم اللقطات الطويلة إلى أجزاء متداخلة (`core/ocr/TilePlanner`).
   - تُقرأ الأجزاء بـ ML Kit، ثم تُدمج الأسطر وتُزال المكررات وتُرتَّب (`core/ocr/OcrLineMerger`).
   - فشل صورة يُعرض بمرحلته (`ReadStage`) دون أي نص منها.
3. **الاستخراج** في `core/parse/AddressExtractor.extract(lines)`:
   - يبحث عن رمز بريدي سويدي، وشارع مع رقم، ومدينة من `Localities` (`assets/localities_se.txt`)، ويتجاهل كل نص آخر.
   - الوقت من `TripTimes`: على سطر العنوان، أو فوقه أو تحته حسب تصميم الصفحة.
   - **نوع الرحلة** (`TripKind`: PULL_OUT / PICK_UP / DROP_OFF / PULL_IN) من كلمة YouDrive القريبة عبر `TripKinds.labelIn`. الوقت والنوع يُختار لهما **اتجاه واحد** بالأغلبية (`timesAndKinds`)، فلا تأخذ رحلة وقت جارتها أو نوعها. محطة ليس لها شيء في ذلك الاتجاه تأخذ ما على الجهة الأخرى إن لم تأخذه الرحلة المجاورة (`TripTimes.Nearby`): وقت نقطة الانطلاق في YouDrive بجانب عنوانها، وأوقات الرحلات تحت عناوينها.
   - **اسم الراكب**: السطر فوق العنوان إن كان اسماً واضحاً (`personName`)، **الأول والأخير فقط**.
   - الناتج: `ExtractedStop(displayText, candidates, time, kind, name, …)`.
4. **القائمة**: `RouteController.addExtracted` ← `Stop` في `RouteData`.
   - رحلة **Pull-out** ليست محطة: تُحفظ في `RouteData.depot` (نقطة الانطلاق، بطاقة رمادية)، ولا تُرسل إلى الخرائط ولا يُعلَن عنها.
   - عنوانان متتاليان متطابقان يُدمجان، إلا إذا اختلف نوعهما.
   - ثم يبدأ تحديد المواقع في الخلفية (`Geocoding.locate`، عبر `Geocoder` النظام، مهلة 15 ثانية لكل عنوان).
5. **المراجعة** في `ReviewScreen`: نقل، حذف (مع تراجع)، تعديل، إضافة يدوية، ترتيب حسب الوقت.
6. **البدء** بـ `RouteController.start()`: إعلان أول محطتين، وفتح الخرائط بأول 10 محطات (`MapsLauncher` + `core/route/MapsUrlBuilder`)، وتشغيل `StreetService` إن سمح السائق بالموقع.
7. **أثناء القيادة**:
   - `next()`: يسجّل الرحلة في `TripHistory` وينتقل ويعلن. في نهاية مجموعة الـ 10 تُفتح الخرائط بالمجموعة التالية.
   - `back()`: يتراجع عن آخر next.
   - `repeat()`: يعيد الإعلان.
   - `end()`: ينهي المسار، والرحلات غير المكتملة تبقى في السجل.
   - **التقدّم بضغطة السائق فقط**. الموقع لا ينقل المسار أبداً.
8. **الحفظ**: `RouteRepository` يحفظ في ملف خاص بلا نسخ احتياطي، ويحذفه بعد 12 ساعة من إنشائه (عند الفتح، وعند العودة للتطبيق، وبمنبّه `ExpiryReceiver`).
9. **ما يراه الآخرون**:
   - `RouteController.display` يبني `DisplaySnapshot` لشاشة الركاب، ويرسله `DisplayLinkServer`.
   - `RouteNotifier` يحدّث إشعار المسار، و`OverlayManager` يحدّث الزر العائم.

---

## 4. الإعلانات

(`core/route/Announcements` + `RouteController.announcementFor` + `core/geo/GeoLogic`)

- **الكامل** (الافتراضي، الإعداد 136): «Nästa stopp: Storgatan 14, Herrhagen, Karlstad. Därefter: Kungsgatan 5, Kronoparken.»
  - المحطة التالية: الشارع ورقمه، ثم الحي، ثم المدينة (`fullSpokenName`).
  - التي بعدها: الشارع ورقمه، ثم الحي (`thenSpokenName`).
- **الإعدادان 106 / 107**: المحطتان بالحي فقط أو بالمدينة فقط (`spokenName`).
- **لا يُنطق اسم راكب أبداً**: اسم العائلة الذي تضعه بعض القوائم قبل الشارع يُحذف (`streetOf`).
- **اسم الحي آمن** (`GeoLogic.isSafeAreaName`): لا أرقام، ولا اسم شارع المحطة، والحي المنتهي بلاحقة شارع («…gatan») يُستبدل بالمدينة. المدينة تُنطق فقط إن كانت في القائمة المرفقة.
- **بضغطة السائق**:
  - شريط الشارع: الشارع الحالي مع الحي (`speakStreet`).
  - شارع المحطة التالية في الزر العائم (13): الشارع ورقمه (`speakStopStreet`).
- **الشارع الحالي وحده** كلما تغيّر (`geo/StreetCaller` ← `sayStreetChange`)، بعد أي إعلان وليس فوقه (`interrupt = false`)، مرة واحدة لكل شارع. يوقفه زر السماعة (4) أو الإعداد 137.
- **المحرّك** (`tts/Announcer`): إن لم يكن في المحرّك الافتراضي صوت سويدي وكان محرّك Google مثبّتاً، يُستعمل محرّك Google. إعلان طُلب والمحرّك يبدأ يُنطق فقط إن كان عمره أقل من 20 ثانية. صوت الخرائط ينخفض أثناء الكلام. التكرار بالإنجليزية اختياري (108).

---

## 5. الموقع واسم الشارع والسرعة

الملفات: `service/StreetService.kt`، `geo/CurrentStreet.kt`، `geo/StreetMapStore.kt`، `geo/StreetCaller.kt`، `core/geo/StreetMap.kt`، `core/geo/StreetMatcher.kt`، `core/geo/StreetLookup.kt`، `core/geo/Fix.kt`.

- **الإذن**: يُطلب عند «Start route» وفي الإعدادات (119)، **أثناء الاستخدام فقط**. الموقع الدقيق مطلوب: مع «التقريبي فقط» يظهر 119 بالأحمر.
- **`StreetService`**: خدمة أمامية من نوع location أثناء المسار. تستعمل `LocationManager` (بلا Play services): مزوّد GPS عند السماح بالموقع الدقيق، بدقة عالية، موقع كل ثانيتين حتى عند الوقوف. ترسل كل موقع (`Fix`) إلى `CurrentStreet` **فقط**.
- **`CurrentStreet`** (في الذاكرة فقط؛ لا يُحفظ ولا يُسجَّل ولا يُرسل):
  - **مع خريطة الشوارع**: كل موقع جيد يُطابق مع الطريق (`StreetMatcher`):
    - أقرب طريق مسمّى ضمن 30 م؛
    - الطريق العرضي على اتجاه السير يُحسب أبعد بـ 25 م عندما تتجاوز السرعة 3 م/ث (عند التقاطع يفوز الطريق الذي تسير عليه)؛
    - موقع أسوأ من 25 م لا يُستعمل. لا طريق = لا اسم.
  - **بدون الخريطة**: شارع الـ Geocoder يُقبل فقط إن كان عنوانه ضمن 40 م (`StreetLookup.pickNear`)، وإلا يظهر الحي وحده. الطلبات محدودة: 8 ثوانٍ و35 م على الأقل بين طلبين، وإعادة بعد 30 ثانية عند الفشل، وفقط حين تعرض شاشة ما الشارع.
  - **في الحالتين**: شارع جديد يحتاج **قراءتين متفقتين** (`StreetTracker`)، ويُمحى الاسم بعد 20 ثانية بلا طريق.
  - الحي يأتي من الـ Geocoder دائماً.
  - **السرعة**: سرعة GPS، أو من موقعين دقيقين (كلاهما ≤ 20 م، بينهما 0.5–10 ثوانٍ). تُعرض فقط إن كان عمرها ≤ 10 ثوانٍ (`speedNow`).
- **خريطة الشوارع** (`StreetMapStore`، الإعدادات 138 للتنزيل أو التحديث و139 للحذف):
  - الطرق المسمّاة للسيارات في Värmland من OpenStreetMap عبر Overpass API.
  - 48 مربعاً ثابتاً فوق المنطقة (مع هامش)، واحداً واحداً مع توقف ثانية.
  - كل مربع يكتمل يُحفظ في `streetmap/parts/` (`StreetMapPart`) حتى تُبنى الخريطة، فالتنزيل الذي يتوقف يكمل من المربع الذي توقف عنده («توقّف عند الجزء n من 48: اضغط للمتابعة»). الأجزاء الأقدم من 14 يوماً تُنزَّل من جديد.
  - الخادم المشغول (429 / 504): 8 محاولات لكل مربع خلال نحو 5 دقائق (توقف 10–60 ثانية)، بالتناوب بين overpass-api.de وoverpass.kumi.systems (`withRetries`)، والإعدادات تقول «الخادم مشغول، ننتظر».
  - تُقرأ الإجابة بالتدفق (`JsonReader`) وتُبنى في `StreetMapBuilder`، ثم تُحفظ ملفاً ثنائياً مضغوطاً في `noBackupFilesDir/streetmap/varmland.nsm`. تنزيل جديد يحلّ محلّها فقط إن اكتمل.
  - `StreetMap`: النقاط بأجزاء المليون من الدرجة، وشبكة خلايا ~110 م للبحث السريع.
  - **الطلب لا يحتوي موقع السيارة أبداً**. البيانات © OpenStreetMap contributors (ODbL)، مذكورة في الإعدادات.

---

## 6. YouDrive

الملفات: `youdrive/YouDriveWatcher.kt`، `youdrive/YouDriveActivity.kt`، `youdrive/YouDriveService.kt`، `youdrive/YouDriveLogin.kt`، `core/youdrive/TripWatch.kt`، `core/youdrive/YouDriveCards.kt`، `core/youdrive/AutoSignIn.kt`، `core/youdrive/BrowserIdentity.kt`، `ui/screens/YouDriveScreen.kt`.

- **الصفحة**: `https://youdrive.regionvarmland.se/`، تطبيق React (Trapeze «YouOperate Driver»)، والـ API على `youapi.regionvarmland.se`. الدخول يبقى في `sessionStorage` داخل الـ WebView.
- **WebView واحد لكل التطبيق** داخل `YouDriveWatcher`، مبني على `MutableContextWrapper`:
  - `attach(activity)` يعرضه في `YouDriveActivity` بحجم الشاشة الحقيقي؛
  - `detach()` يُبقيه يعمل في الخلفية إذا كانت المراقبة مفعّلة، وإلا يغلقه.
- **القراءة (نص وليس صورة)**: `READ_PAGE_JS` يعيد `{t: النص الظاهر, c: نص كل بطاقة رحلة, p: هل يوجد حقل كلمة سر ظاهر, f: ما أُصلح}`، ثم `onPageText` ← `TripWatch.tripsIn`.
  - **بطاقة بطاقة**: السكربت يبدأ من كلمة النوع الظاهرة ("Pick-up"…) ويصعد إلى أكبر عنصر لا يحوي كلمة نوع أخرى. ثم `YouDriveCards.parse` يقرأ كل بطاقة وحدها:
    - **الوقت** = الأول في البطاقة (المجدول)، لأن YouDrive يرتّب به. الثاني (المحجوز أو آخر موعد) يُحفظ في `WatchedTrip.booked` للمقارنة، لأنه لا يتغيّر عند إعادة الجدولة.
    - **العنوان** = أول سطر يقبله `AddressExtractor`، وإلا (مكان بلا رقم) السطر بعد اسم الراكب.
    - **الاسم** = السطر فوق العنوان (الأول والأخير فقط).
    - **منتهية** (Performed / Departed): لا تُضاف بـ «Add all trips».
  - بلا بطاقات يُقرأ النص كله كما في لقطات الشاشة.
- **المقارنة** (`TripWatch.onReading`):
  - أول قراءة غير فارغة هي الأساس، والتغيير يُعتمد إذا ظهر في **قراءتين متتاليتين**، والقراءات الفارغة تُتجاهل.
  - رحلة اختفت بعد موعدها بأكثر من 5 دقائق = منتهية وليست ملغاة.
  - `isNewList`: لم يبقَ شيء من القائمة، أو تغيّر أكثر من النصف وأكثر من 3 ← قائمة جديدة **بلا تنبيه** (يوم آخر أو عرض آخر).
- **التوقيت**: قراءة كل 60 ثانية (15 ثانية والنافذة ظاهرة)، وإعادة تحميل كل 5 دقائق **فقط والنافذة مغلقة**.
- **التنبيهات**: `Notifications.postTripChanges` على قناة `trip_changes` بصوت واهتزاز، 5 على الأكثر ثم ملخّص. التغيير يُطبَّق على القائمة **فقط بضغطة السائق** (Add / Remove). «Add all trips» مزامنة: رحلة موجودة (نفس العنوان والنوع والاسم، والوقتان ضمن 45 دقيقة، `sameTrip`) تُحدَّث ولا تتكرر.
- **الهوية مثل كروم**: `BrowserIdentity.chromeUserAgent` بلا `; wv` ولا `Version/4.0`، و`setUserAgentMetadata` يضع «Google Chrome» مكان «Android WebView».
- **الأمان**:
  - لا موقع للصفحة (`setGeolocationEnabled(false)`، ورفض كل طلب).
  - لا جسر JavaScript، ولا وصول للملفات، ولا طلبات منّا إلى خوادمهم.
  - لا روابط تطبيقات ولا نوافذ JS والصفحة في الخلفية.
  - أخطاء الشهادات تُعرض وتُرفض دائماً. `res/xml/network_security_config.xml` يضيف جذر «Telia Root CA v2» العام كمرجع ثقة لنطاق `regionvarmland.se` فقط، لأن أندرويد 13 وما قبله لا يحتويه.
  - نموذج دخول مخفي خارج الشاشة يُعاد إظهاره (ويُذكر في سطر الحالة).
  - `onRenderProcessGone` يفتح صفحة جديدة بدل انهيار التطبيق.
  - «Log out» يمسح التخزين والكوكيز والكاش ويهدم الـ WebView.
- **الخدمة**: `YouDriveService` خدمة أمامية `specialUse` تُبقي العملية حية أثناء المراقبة فقط. إشعارها صامت ويتحدّث عند تغيّر النص.
- **الدخول التلقائي** (المفتاح 156، مطفأ افتراضياً):
  - YouDrive ينسى الدخول كلما أُغلقت نافذته، حتى في كروم.
  - معلومات الدخول تُحفظ فقط إذا كتبها السائق بنفسه في الإعدادات (157): `YouDriveLogin` يشفّرها AES-GCM بمفتاح من Android Keystore لا يغادر الجوال، في مساحة خاصة بلا نسخ احتياطي.
  - `SignInScript` يكتبها فقط في نموذج YouDrive نفسه على العنوان الصحيح (ويتحقق منه داخل الصفحة أيضاً)، وفي الحقول الفارغة فقط، كنصوص JSON.
  - `AutoSignIn`: محاولتان على الأكثر، بينهما 20 ثانية. بعد «Log out» لا شيء حتى يدخل السائق بنفسه.

---

## 7. الملفات واحداً واحداً

المسار الأساسي: `app/src/main/java/se/eldebosh/nastastopp/`

### الجذر
| الملف | الدور |
|---|---|
| `App.kt` | `Application` + `AppGraph` (كل الكائنات). يطبّق لغة الواجهة وينشئ قنوات الإشعارات. |
| `MainActivity.kt` | المشاركة (SEND / SEND_MULTIPLE)، وفتح YouDrive أو المراجعة من الإشعارات، وتشغيل خدمة YouDrive عند العودة. |

### `core/` — منطق خالص (اختباراته في `app/src/test/.../core/`)
| الملف | الدور |
|---|---|
| `parse/AddressExtractor.kt` | قلب القراءة: من أسطر OCR إلى `ExtractedStop`. أيضاً `fromManualText` للإدخال اليدوي، و`isSameAddress`. |
| `parse/TripKinds.kt` | `TripKind` و`labelIn` (الكلمة كاملة فقط). |
| `parse/TripTimes.kt` | وقت كل رحلة، و`minutesUntil` (متأخر حتى 8 ساعات)، و`level` (AHEAD / SOON / LATE)، و`normalizeTyped`. |
| `parse/Localities.kt` | المدن السويدية من `assets/localities_se.txt`، بلا اعتبار لحالة الأحرف ولا لـ å ä ö. |
| `parse/TextNorm.kt` | `fold` و`key` وتشابه ليفنشتاين. |
| `parse/TitleCase.kt` | «STORGATAN 14» ← «Storgatan 14» بقواعد سويدية. |
| `ocr/TilePlanner.kt`، `OcrLineMerger.kt`، `OcrLine.kt` | تقسيم الصور الطويلة، ثم دمج الأسطر وإزالة التكرار وترتيب القراءة. |
| `geo/GeoLogic.kt` | أفضل نتيجة Geocoder، والاسم المنطوق (`spokenName`، `fullSpokenName`، `isSafeAreaName`)، والمسافة. |
| `geo/StreetLookup.kt` | متى نسأل الـ Geocoder عن الشارع الحالي، و`pickNear`. |
| `geo/Fix.kt` | موقع واحد (الوقت، الإحداثيات، السرعة، الدقة، الاتجاه). |
| `geo/StreetMap.kt` | خريطة الشوارع وشبكتها وملفها الثنائي، و`StreetMapBuilder`. |
| `geo/StreetMatcher.kt` | `StreetMatcher` (الطريق من المسافة والاتجاه) و`StreetTracker` (قراءتان متفقتان). |
| `geo/StreetMapPart.kt` | طرق مربع تنزيل واحد، تُحفظ على القرص حتى تكتمل الخريطة، و`RoadSink`. |
| `route/Announcements.kt` | نصوص الإعلانات السويدية والإنجليزية. |
| `route/MapsUrlBuilder.kt` | رابط اتجاهات خرائط Google، 10 محطات على الأكثر. |
| `link/LinkProtocol.kt`، `LinkTargets.kt` | رسائل JSON سطراً سطراً بين الجوال والتابلت، وترتيب الأجهزة المقترنة. |
| `display/DisplaySnapshot.kt` | ما تعرضه شاشة الركاب: `DisplayItem(time, title, subtitle)`. |
| `youdrive/TripWatch.kt` | مقارنة قراءات YouDrive. |
| `youdrive/YouDriveCards.kt` | قراءة بطاقات YouDrive واحدة واحدة. |
| `youdrive/AutoSignIn.kt` | متى يُضغط Login تلقائياً، و`SignInScript`. |
| `youdrive/BrowserIdentity.kt` | هوية كروم للصفحة. |

### طبقة أندرويد
| الملف | الدور |
|---|---|
| `route/RouteController.kt` | **مصدر الحقيقة للمسار**: الإضافة والتعديل والحذف والنقل، وstart / next / back / repeat / openMaps / end، ومزامنة YouDrive (`syncTrips`، `insertTrip`، `removeTrip`)، والإعلانات، و`display`. |
| `route/model/RouteModels.kt` | `Stop`، `RouteData`، `GeoPoint`، `GeoStatus`. |
| `route/RouteRepository.kt` | حفظ المسار وحذفه بعد 12 ساعة. |
| `route/TripHistory.kt` | «Previous trips»، مع مدة الحفظ (12 ساعة، 24 ساعة، 7 أيام). |
| `route/ExpiryReceiver.kt` | منبّه يحذف البيانات المنتهية. |
| `ocr/OcrEngine.kt` | ML Kit + التقسيم، ومراحل الفشل (`ReadStage`). |
| `importer/ScreenshotImporter.kt` | الصور ← OCR ← الاستخراج. |
| `geo/Geocoding.kt` | `locate` (العنوان ← الإحداثيات) و`reverse` (الإحداثيات ← العناوين القريبة). |
| `geo/CurrentStreet.kt` | الشارع الحالي والسرعة (القسم 5). |
| `geo/StreetMapStore.kt` | تنزيل خريطة الشوارع وحفظها وتحميلها (`OverpassDownload`). |
| `geo/StreetCaller.kt` | نطق الشارع عند تغيّره. |
| `service/StreetService.kt` | خدمة الموقع أثناء المسار (القسم 5). |
| `tts/Announcer.kt` | TextToSpeech بالسويدية، وخفض صوت الخرائط، و`TtsStatus`. |
| `maps/MapsLauncher.kt` | فتح خرائط Google، وإشعار «افتح الخرائط» حين يمنع النظام الفتح من الخلفية. |
| `overlay/OverlayManager.kt` | الزر العائم (القسم 8). |
| `overlay/OverlayTileService.kt` | مربع «Floating button» في الإعدادات السريعة. |
| `service/Notifications.kt` | القنوات، وإشعار المسار، وتنبيهات YouDrive، وإشعار «الزر العائم مغلق». |
| `service/RouteNotifier.kt` | يُبقي إشعار المسار متزامناً. |
| `service/RouteActionReceiver.kt` | أزرار الإشعار: Nästa / Upprepa / Avsluta / إظهار الزر العائم. |
| `link/Bluetooth.kt`، `DisplayLinkServer.kt`، `DisplayLinkClient.kt` | شاشة الركاب عبر البلوتوث (القسم 8). |
| `settings/SettingsStore.kt` | كل الإعدادات (`AppSettings`) في SharedPreferences، بلا عناوين. |
| `util/LocaleHelper.kt` | لغة الواجهة داخل التطبيق، و`explanationContext` (موارد الشروح بالعربية). |
| `util/SystemIntents.kt` | فحص الأذونات (ومنها `hasPreciseLocation`) وفتح صفحات إعدادات النظام. |
| `util/TimeLabels.kt` | «in 7 min»، «5 min late». |
| `util/DebugLog.kt` | سجلات في debug فقط. |
| `youdrive/*` | القسم 6. |

### الواجهة `ui/`
| الملف | الدور |
|---|---|
| `theme/Palette.kt` | الطبقة 1: الألوان الخام. |
| `theme/AppColors.kt` | الطبقة 2: أدوار الألوان، `DayColors` و`NightColors`، و`trip(kind)` و`status(level)`، وأدوار الزر العائم `panel`. |
| `theme/AppEffects.kt` | الطبقة 3: الظلال، وتصغير الزر عند الضغط، وتلاشي الألوان. |
| `theme/Theme.kt` | `NastaTheme(appearance)`، و`AppTheme.colors` / `AppTheme.effects`، والتحويل إلى Material 3، والخطوط والأشكال. |
| `theme/SystemBars.kt` | أيقونات شريط الحالة وخلفية النافذة حسب المظهر. |
| `Components.kt` | `AppButton`، `TopBar`، `SectionTitle`، `AppCard`، `TripSurface`، `ListRow`، `IconBadge`، `KindLabel`، `Chevron`، `Paragraph`. `TouchTarget = 48.dp`. |
| `Explain.kt` | `explain(id)`، و`HelpDot` (علامة «?»)، و`Hint`. |
| `Refs.kt` | الأرقام المرجعية (`ref`، `refCorner`) ومعرّفات الاختبار `ref_<n>`. |
| `AppRoot.kt` | يوزّع الشاشات، ويطلب الأذونات، ويعرض snackbar، ويوفّر `LocalExplainResources`. |
| `MainViewModel.kt` | مكدس الشاشات (`Screen`)، والاستيراد، وفتح YouDrive أو المراجعة. |
| `screens/*.kt` | كل شاشة في ملف. |

### الموارد
- **النصوص**: `values/strings.xml` = **العربية (الافتراضية)**، `values-en/` = الإنجليزية (لغة الواجهة الافتراضية)، `values-sv/` = السويدية. **كل مفتاح في الملفات الثلاثة.** `strings_fixed.xml` لما لا يُترجم.
- **الأيقونات**: `res/drawable/ic_*.xml` (vector). أيقونة التطبيق: `ic_launcher_background` (تدرّج أصفر) + `ic_launcher_foreground` (دبوس + سهم) + `ic_launcher_monochrome`.
- **xml**: `backup_rules` و`data_extraction_rules` (تستثني كل شيء)، و`locales_config`، و`network_security_config`.
- **`values/ids.xml`**: معرّفات أجزاء الزر العائم `ref_<n>`.

---

## 8. الزر العائم وشاشة الركاب

### الزر العائم (`overlay/OverlayManager.kt`)
- **Views وليس Compose**، في نافذة `TYPE_APPLICATION_OVERLAY`.
- **بطاقة زجاج مدخَّن** في المظهرين (`glass()`): إطار شفاف، ونصوص على زجاج أعمق (`well`)، وحافتان فاتحة وداكنة. لا ألوان خاصة به: `PanelColors` يحوّل أدوار `AppColors.panel` عند البناء.
- **الأجزاء** (أرقامها في README):
  - الرأس: الساعة 8، رقم الرحلة 9، التصغير 6، الإغلاق 7.
  - صف الشارع: دائرة السرعة 15 بجانب شريط الشارع 2 (فيه الحي 3 وزر نطق الشارع 4).
  - الرحلة التالية 16: شريط جانبي بلون النوع، الوقت 10 مع شارع المحطة 13، الاسم 18 مع حالة الوقت 11، المدينة 19 بلا رمز بريدي.
  - الأزرار: Back 1 وNext 5 (ضغطة مطوّلة على Next أو شريط الشارع = إعادة الإعلان).
  - الكبسولة بعد التصغير 17.
- **السلوك**:
  - السحب من أي مكان يحرّكه، والمكان يُحفظ. «×» و«–» يستجيبان للضغط الحقيقي فقط.
  - أعلى البطاقة يبقى 100 dp على الأقل تحت أعلى الشاشة، والهامش الشفاف حول البطاقة صغير (2 dp فوقها، 6 بجانبها، 12 تحتها للظل)، لأن النافذة تأخذ كل ضغطة في مستطيلها: فلا تغطي الشريط العلوي للتطبيق ولا شريط الاتجاهات في الخرائط.
  - بعد الإغلاق أثناء المسار: إشعار صامت، ومربع الإعدادات السريعة، والمفاتيح تعيده.
  - يغيب ما دامت شاشة الركاب ظاهرة على الجوال (`suppress`).
- **للاختبار**: كل جزء له `id/ref_<n>`، ومع `setprop log.tag.NastaStoppRefs DEBUG` يسجّل حدود الأجزاء على الشاشة فقط.

### شاشة الركاب (`link/*`، `ui/screens/PassengerDisplayScreen.kt`، `DisplayRoleScreen.kt`)
- البلوتوث RFCOMM بين أجهزة مقترنة. الجوال يستمع على قناة آمنة وقناة احتياطية، ويخدم الأجهزة المقترنة به فقط.
- التابلت يجد الجوال وحده: آخر جهاز أولاً، ثم الجوالات، ثم التابلتات والحواسيب. السماعات وأنظمة السيارة تُتخطّى.
- يُرسل `DisplaySnapshot` فقط: رحلة سابقة، والحالية، و3 قادمة. الوقت، والشارع مع الرقم، والحي تحته (الإعداد 114؛ مطفأً = الحي فقط). **لا أسماء أبداً.**
- إعادة الاتصال كل 3 ثوانٍ، وping كل 10 ثوانٍ، وقطع الاتصال الصامت بعد 30 ثانية، وسطر أطول من 64 KB يقطع الاتصال.
- **التابلت ينطق كل إعلان** يصدره الجوال عند ضغط Next (مفعّل افتراضياً، ويُطفأ بالمفتاح 198 في شاشة إعداد التابلت)، وزر الصوت (93) يعيده عليه.
- **الترتيب للركاب**: المحطة التالية في الوسط («NÄSTA STOPP» على شارة، ووقتها بلون `highlight`، أزرق التطبيق، ثم الشارع والرقم بأكبر حجم يتسع، والحي تحته)، والساعة كبيرة في الزاوية، والرحلات التالية بطاقات تحتها (جنباً إلى جنب في الوضع الأفقي، وبعضها تحت بعض على الجوال، والأولى «Därefter» أكبر ومؤطّرة)، وزر صوت دائري. كلمات الركاب بالسويدية مثل الإعلانات (`strings_fixed.xml`)، وسطر الاتصال بلغة التطبيق.
- **الحركة**:
  - `Stage`: `SharedTransitionLayout` + `AnimatedContent` مفتاحه المحطة الحالية؛ البطاقة والمحطة التالية لهما نفس المفتاح (`DisplayItem`)، فتكبر بطاقة «Därefter» إلى وسط الشاشة عند Next وتعود عند Back.
  - `spoken` يزيد مع كل إعلان (من `controller.announcements` على الجوال، ومن `DisplayLinkClient.announcements` على التابلت، ومع ضغطة زر الصوت). `Spotlight` يتبع الإعلان: `NEXT_STOP` (يقفز «NÄSTA STOPP» ووقته، ويكبر العنوان ببطء حتى 1.25× بقدر ما تتسع الشاشة ويضيء، والباقي يخفت)، ثم البطاقة 0 عند ذكر «Därefter» (التوقيت من طول النص، نحو 75 ms للحرف)، فتكبر 1.6× نحو داخل الشاشة (`TransformOrigin` حسب مكانها) وتعود خلال 4 ثوانٍ.
  - **التصفّح**: `HorizontalPager` على التابلت و`VerticalPager` على الجوال في `Stage`؛ الصفحة 0 هي `StopHero(next = true)` بمفتاح الانتقال المشترك، والباقي `StopHero(next = false)` تحت «DÄREFTER» بلا مفتاح (كي لا يتكرر المفتاح مع البطاقة). البطاقة المعروضة تتلوّن (`shownAbove`)، و`PageDots` (201). `HomeButton` (200) يظهر خارج الصفحة 0، وأي إعلان أو 30 ثانية دون لمس (`BROWSE_RETURN_MS`) تعيد الصفحة 0. لا يلمس المسار أبداً.
  - ضغطة بطاقة: `Announcements.following` («Därefter: …») وتكبر مثلها. ضغطة الساعة: `Announcements.clock` («Klockan är 8 och 05»). الاثنتان عبر `onSay` على الجهاز نفسه (`graph.announcer`).
  - `Clock`: بلا إطار؛ الدقائق كبيرة، والساعات أصغر وأعلاها بمستوى الدقائق، والثواني تحت الدقائق بخط Light أزرق، والنقطتان تنبضان. `rememberMinuteGrowth`: عند تغيّر الدقيقة تنتقل الدقائق إلى وسط الشاشة وتكبر خلال 5 ثوانٍ (حتى 5× أو 90% من الشاشة) ويخفت الباقي (`stepBack`، بـ `ModulateAlpha` كي لا يُقصّ شيء)، ثم تعود خلال 1.2 ثانية. لا يحدث أثناء النطق، وأي نطق يوقفه. `time` يُمرَّر في الاختبارات.
- **الخط**: Barlow Semi Condensed (`DisplayFont` في `theme/Theme.kt`، الملفات في `res/font/`، والترخيص SIL OFL في `assets/licenses/`)، بأسلوب لوحات الطرق والنقل، وحروفه الضيقة تُبقي أسماء الشوارع الطويلة كبيرة. الأرقام متساوية العرض.
- العنوان الكبير لا ينقسم في وسط كلمة: حجمه محدود بأطول كلمة.

---

## 9. نظام التصميم

الهدف: تغيير الألوان أو التأثيرات **في مكان واحد**، دون لمس الشاشات. ثلاث طبقات في `ui/theme/`:

| الطبقة | الملف | ما فيها | متى تعدّلها |
|---|---|---|---|
| 1. الألوان الخام | `Palette.kt` | كل لون بقيمته، مسمّى بما **هو** (YouDriveGreen، TaxiYellow، Sky…). | لتغيير درجة لون في كل مكان. |
| 2. الأدوار | `AppColors.kt` | دور كل لون، ومجموعتان: `DayColors` و`NightColors`. | لتغيير لون عنصر، أو لإضافة مظهر. |
| 3. التأثيرات | `AppEffects.kt` | الظلال، وتصغير الزر عند الضغط، وتلاشي الألوان. `0.dp` أو `1f` أو `0` يطفئ التأثير. | لإضافة تأثير أو ضبطه. |

- **`Theme.kt`**: `NastaTheme(appearance)` يختار النهاري أو الليلي (130–132: Day / Night / Automatic)، ويوفّر `AppTheme.colors` و`AppTheme.effects`، ويحوّل الأدوار إلى Material 3.
- **القاعدة**: لا `Color(0x…)` خارج `Palette.kt` و`AppColors.kt`.
- **الأدوار الأساسية**:

| الدور | نهاري | ليلي | أين |
|---|---|---|---|
| `background` / `card` / `cardBorder` | رمادي فاتح / أبيض / خط رفيع | أزرق ليلي داكن | الصفحة والبطاقات |
| `text` / `textMuted` | أسود / رمادي | أبيض مزرق / رمادي مزرق | النصوص |
| `action` / `onAction` | أسود | الأزرق الفاتح Sky | الأزرار الرئيسية |
| `accent` / `onAccent` | الأصفر مع نص أسود | الأصفر | Next، Start route، وقت الرحلة الحالية |
| `info` | أزرق SkyInk | Sky | المفاتيح والعناوين والروابط |
| `highlight` | أزرق SkyInk | Sky | شاشة الركاب: شارة «NÄSTA STOPP» ووقتها، والثواني، وما يُنطق |
| `pickUp` / `dropOff` / `depot` | أخضر YouDrive / أبيض / رمادي | أخضر داكن / بطاقة داكنة / رمادي داكن | بطاقات الرحلات (`trip(kind)`) |
| `currentBorder` | أسود | أصفر | إطار الرحلة الحالية |
| `success` / `warning` / `danger` | أخضر / كهرماني / أحمر | نفسها أفتح | الحالة (`status(level)`) |
| `panel` | زجاج أسود مدخَّن | زجاج كحلي مدخَّن | الزر العائم |

- **المكوّنات** (`ui/Components.kt`): `AppButton` (action / tonal)، `AppCard`، `TripSurface(kind, current)` لكل بطاقة رحلة، `KindLabel`، `ListRow`، `TopBar`، `SectionTitle`.
- **التباين مضمون باختبار**: `ThemeContrastTest` يفحص كل زوج نص/خلفية في المظهرين (WCAG: 4.5 للنص، و3 للحدود والنص العريض)، و`checkPanel` يفحص الزر العائم فوق أبيض وأسود وأخضر حديقة وأزرق طريق.
- **كيف أغيّر لوناً؟** غيّر القيمة في `Palette.kt`، أو الدور في `DayColors` / `NightColors`، ثم شغّل الاختبارات.
- **كيف أضيف تأثيراً؟** حقل في `AppEffects` بقيمته للنهار والليل، ويُستعمل عبر `AppTheme.effects`.
- **كيف أضيف مظهراً؟** مجموعة `AppColors` جديدة، وقيمة في `settings/Appearance`، وسطر في `AppTheme.colorsFor`.
- **الأحجام**: الأزرار 48 dp، و52 dp للرئيسية، و60 dp لـ Next و Back في شاشة المسار. الزوايا: `shapes.medium` = 14، `large` = 20.
- **الشرح**: لا فقرات شرح في الشاشة. `HelpDot(R.string.x_hint)` بجانب العنوان، أو `ListRow(help = …)`. نصّه بالعربية ما دام 104 مفعّلاً.
- **الأرقام المرجعية**:
  - كل عنصر جديد يأخذ رقماً غير مستعمل من نطاق شاشته، ويُضاف إلى جدول README.
  - `ref(n)` للعناصر العادية (`centered = true` داخل الصفوف)، و`refCorner(n)` للأيقونات والمفاتيح، و`ListRow(ref = n)` داخل صفوف البطاقات.
  - **مخفية افتراضياً** (الإعداد 105)، ومعرّفات `ref_<n>` موجودة دائماً.
- **الاتجاه**: نص قد يكون عربياً أو سويدياً يُكتب بـ `style.copy(textDirection = TextDirection.Content)`. الواجهة العربية RTL تلقائياً.

---

## 10. البناء والاختبار والنشر

```bash
./gradlew test assembleRelease lintDebug lintRelease   # كل شيء؛ يجب أن ينتهي بـ exit 0
./gradlew :app:testDebugUnitTest --tests '*ScreenshotsRoboTest'   # صور الشاشات في app/build/screenshots/
```

خطوات كل إصدار (بالترتيب، **ولا commit قبل نجاح كل شيء**):
1. **رفع الرقمين** في `app/build.gradle.kts`: `versionCode` +1 دائماً، و`versionName` الجديد.
2. **البناء الكامل** بالأمر أعلاه، مع **فحص رمز الخروج**: كل الاختبارات تنجح، وlint = «No issues found».
3. **فحص الـ APK** (`app/build/outputs/apk/release/app-release.apk`، والأدوات في `/opt/android-sdk/build-tools/37.0.0/`):
   - `aapt2 dump badging`: الرقم، ووجود إذن الموقع FINE / COARSE، وغياب BACKGROUND.
   - `apksigner verify --print-certs`: الـ SHA-256 يبدأ بـ `1ae627778bdd`.
   - `dexdump`: وجود `TextRegistrar` (مسجّل ML Kit).
4. **النسخ والتوثيق**: انسخ الـ APK إلى `dist/NastaStopp.apk` (خارج git)، وحدّث DECISIONS.md وREADME إن تغيّر قرار أو واجهة.
5. **commit وpush** للكود إلى `claude/nasta-stopp-android-app-soeru7`.
6. **النشر في GitHub Releases**:
   - ملاحظات الإصدار بالإنجليزية في ملف (بلا بيانات ركاب)؛
   - `tools/publish-apk.sh <الملف>` يرفع الـ APK على فرع مؤقت `apk-drop/v<الإصدار>`؛
   - `.github/workflows/publish-apk.yml` ينشئ الإصدار `v<الإصدار>` على commit الكود، ويحذف الفرع المؤقت؛
   - تأكّد أن الإصدار ظهر ومعه الملف.
   - إصدار خاطئ يُحذف بـ workflow «Delete release» (`.github/workflows/delete-release.yml`) يُشغَّل يدوياً مع اسم العلامة.
7. **التسليم**: zip للـ APK (نحو 18 MB) يُرسل للسائق، و«DEVICE-TEST READY» على PR #1 (القسم 13).

- **التوقيع**: `keystore.properties` في الجذر (خارج git) ← المفتاح في `~/.nastastopp-signing/`. عند حسام نسخة احتياطية خاصة منه، ولا يُرفع أبداً إلى المستودع ولا إلى GitHub. بدون الملف يُوقَّع release بمفتاح debug، ولا يُثبَّت فوق النسخة الموقّعة.
- **R8 مطفأ**: في وضعه الكامل حذف منشئات مسجِّلات ML Kit التي تُنشأ بالانعكاس، ففشلت القراءة من كل الصور، والاختبارات لا تكشف ذلك. `proguard-rules.pro` جاهز ليوم تشغيله، مع فحص `dexdump`.
- **ABIs**: release لـ `arm64-v8a` و`armeabi-v7a` فقط (مكتبة OCR نحو 11 MB لكل واحد).

### الاختبارات
- **`app/src/test/.../core/*`**: المنطق الخالص. أهمها `AddressExtractorTest` و`AddressExtractorFuzzTest` و`TripTimesTest` و`TripWatchTest` و`YouDriveCardsTest` و`StreetMatcherTest` و`GeoLogicTest`.
- **`app/src/test/.../robo/*`**: Robolectric على أندرويد 13:
  - `UiSmokeRoboTest`: الشاشات، والإعدادات الافتراضية.
  - `FloatingPanelRoboTest`: الزر العائم، والشارع، والسرعة، والنطق.
  - `RouteControllerRoboTest`، `YouDriveRoboTest`، `YouDriveLoginRoboTest`، `StreetServiceRoboTest`، `StreetMapDownloadRoboTest`، `DisplayLinkRoboTest`، `DisplayFeaturesRoboTest`، `TripHistoryRoboTest`.
  - `ScreenshotsRoboTest`: يرسم كل شاشة إلى صورة في `app/build/screenshots/` (للفحص محلياً فقط)، ويرسم صور `testdata/screenshots/`.
- **`ui/theme/ThemeContrastTest`**: تباين الألوان.

---

## 11. قواعد لا تُكسر

1. **الصور** لا تُنسخ ولا تُحفظ.
2. **لا يُحفظ إلا العنوان والوقت ونوع الرحلة واسم الراكب الأول والأخير.** الاسم على شاشات السائق فقط: **لا يُنطق، ولا يُرسل إلى شاشة الركاب، ولا يوضع في إشعار أو في السجل، ولا يُسجَّل** (`namesStayOnTheDriversScreens`).
3. **لا سجلات** لعناوين أو نص OCR في release (`DebugLog` فقط). `allowBackup=false`.
4. **الموقع لاسم الشارع والسرعة فقط**: أثناء الاستخدام، لا في الخلفية، لا ينقل المسار، لا يُحفظ ولا يُسجَّل ولا يُرسل. **صفحة YouDrive لا تحصل عليه أبداً.**
5. **اسم الشارع لا يُخترع**: القسم 5 كله. لا تطابق = لا اسم.
6. **الإنترنت** لصفحة YouDrive ولتنزيل خريطة الشوارع بضغطة السائق فقط. التنزيل لا يرسل موقع السيارة. لا Firebase ولا تحليلات ولا تقارير أعطال ولا Hilt.
7. **الإعلانات**: كما في القسم 4. **لا يُنطق اسم راكب أبداً.**
8. **بيانات دخول YouDrive**: لا تدخل الكود ولا المستودع ولا السجلات ولا الردود، ولا يستعملها Claude. التطبيق يحفظها مشفّرة فقط إذا كتبها السائق بنفسه (القسم 6).
9. **لا يُعطَّل فحص شهادات TLS أبداً**، ولا يُلغى `HTTPS_PROXY` في بيئة البناء.
10. **لا شيء يظهر وحده**: كل نافذة أو رسالة أو صوت أو فتح للخرائط بعد فعل من السائق. الاستثناءات فقط: تنبيهات YouDrive، وإشعار «افتح الخرائط» عند منع النظام، واسم الشارع الحالي ما دام «انطق الشارع» مفعّلاً (4 / 137).

---

## 12. كيف أضيف أو أعدّل…

- **إعداداً جديداً**:
  1. حقل في `AppSettings` بقيمة افتراضية.
  2. في `SettingsStore`: ثابت `K_…`، وسطر في `update`، وسطر في `read`.
  3. صف في `SettingsScreen`: `SwitchRow(R.string.x, R.string.x_hint, value, رقم) { … }`.
  4. النصوص في الملفات الثلاثة، والرقم في جدول README.
- **نصاً جديداً**: في `values/` (عربي) و`values-en/` و`values-sv/`. lint يعترض إذا نقصت ترجمة.
- **نص إعلان**: `core/route/Announcements.kt` واختبار `AnnouncementsTest`.
- **شكل صفحة YouDrive تغيّر** (لم تعد الرحلات تُقرأ):
  1. خذ نص الصفحة (بلا بيانات دخول، وبأسماء مخترعة).
  2. أضِفه حالة اختبار في `YouDriveCardsTest` أو `TripWatchTest` أو `YouDriveRoboTest`.
  3. عدّل `YouDriveCards` أو `TripWatch.tripsIn` أو `AddressExtractor`.
- **صيغة عنوان جديدة**: حالة في `AddressExtractorTest` أولاً، ثم عدّل `AddressExtractor`.
- **منطقة أخرى غير Värmland لخريطة الشوارع**: حدود `OverpassDownload` (`SOUTH`، `WEST`، `NORTH`، `EAST`) واسم الملف في `StreetMapStore`.

---

## 13. التجربة على الجوال الحقيقي

- **من يجرّب**: جلسة Claude ثانية على لابتوب السائق، متصلة بالجوال عبر adb. هي تثبّت كل نسخة وتجرّبها. جلسة السحابة لا تتصل بالجوال أبداً.
- **النسخة**: إصدار GitHub `v<الإصدار>`. الرابط المباشر: `https://github.com/HussamEl/S20Ultra/releases/download/v<الإصدار>/NastaStopp.apk`. موقّعة بنفس المفتاح دائماً، فتُثبَّت فوق السابقة بـ `adb install -r`.
- **معرّفات ثابتة**: كل عنصر مرقّم له `resource-id` = `ref_<n>`، وأجزاء الزر العائم `se.eldebosh.nastastopp:id/ref_<n>`. الأرقام هي جداول README.
- **صور اختبار مخترعة** (بلا ركاب حقيقيين) في `testdata/screenshots/`، ونتائجها المتوقعة وكل الكلمات المخترعة في `testdata/README.md`.
- **دورة العمل**:
  1. تُنشر «DEVICE-TEST READY <sha>» على PR #1 مع رابط الـ APK وقائمة مرقّمة: الخطوات التي يمسّها التغيير، ومجموعة فحص سريعة، وما يحتاج يدَي حسام (الدخول، الشروط، نوافذ النظام)، وما هو عاجل للسائق.
  2. يردّ المجرّب بـ «DEVICE-TEST RESULT <sha>».
  3. حسام يقرّر متى تجرّب جلسة اللابتوب ويخبرها بنفسه. لا تُكتب لها تعليمات ولا يُنتظر ردّها.
- **YouDrive**: موقع التوزيع الحقيقي، فالدخول يدوي من حسام. المجرّب لا يكتب بيانات دخول أبداً.

---

## 14. لمن يستلم

- **التواصل**: حسام (السائق وصاحب المشروع) يكتب بالعربية، والرد بالعربية ومختصراً. لا تُرسل له صور من التطبيق. كل نسخة تُرسل له zip للـ APK.
- **المستودع**: `HussamEl/S20Ultra`، فرع العمل `claude/nasta-stopp-android-app-soeru7`، وPR #1 مسودة تجمع العمل ودورة التجربة.
- **بداية 1.0**: تُثبَّت بعد حذف أي نسخة سابقة من الجوال (الرقم الداخلي يبدأ من 1)، ثم تُعاد الأذونات. بعدها كل نسخة تُثبَّت فوق السابقة.
- **ما يحتاج تجربة على الأجهزة**: تنزيل خريطة الشوارع ودقة الشارع أثناء القيادة، والسرعة، والدخول التلقائي إلى YouDrive، وتنبيه حقيقي عند إضافة رحلة أو إلغائها، والاتصال بالتابلت.

</div>
