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
4. يعرض الوجهة القادمة على **تابلت للركاب** عبر البلوتوث، مع الساعة والطقس والوقت المتبقي من Google Maps.
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
│ WeatherSource (SMHI) · MapsNavigationListener (وقت Google Maps)        │
└───────────────▲───────────────────────────────────────────────────────┘
                │ دوال Kotlin خالصة
┌───────────────┴───────────── core/ (بلا أندرويد) ─────────────────────┐
│ parse/ (العناوين والأوقات) · ocr/ (التقسيم والدمج) · geo/ (الشارع)   │
│ route/ (الإعلانات والخرائط) · link/ · display/ · youdrive/            │
│ weather/ (الطقس) · nav/ (الوقت المتبقي)                               │
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
   - ثم يبدأ تحديد المواقع في الخلفية (`Geocoding.locate`، عبر `Geocoder` النظام، مهلة 15 ثانية لكل عنوان). عنوان بلا مدينة ولا رمز بريدي يُبحث عنه في Värmland وحدها، ويُقبل فقط في مدينة واحدة (`GeoLogic.inOneTown`).
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
   - `RouteController.display` يبني `DisplaySnapshot` لشاشة الركاب، ويضيف إليه الطقس (`WeatherSource`) والوقت المتبقي (`MapsNavigation`) عبر `setDisplayExtras`، ويرسله `DisplayLinkServer`.
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

الملفات: `service/StreetService.kt`، `geo/CurrentStreet.kt`، `geo/StreetMapStore.kt`، `geo/StreetCaller.kt`، `geo/TabletPosition.kt` (موقع التابلت لخريطته فقط)، `core/geo/StreetMap.kt`، `core/geo/StreetMatcher.kt`، `core/geo/StreetLookup.kt`، `core/geo/Fix.kt`.

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
    - **المكان** (`ExtractedStop.place` ← `Stop.place`): الكلمات قبل الشارع («Provby Vårdcentral Strandvägen 3» ← «Provby Vårdcentral»)، أو المكان كله إن لم يكن له رقم («Centralsjukhuset Huvudentrén»). في البطاقة الاسمُ في سطره، فهذه الكلمات ليست اسم الراكب أبداً. شاشات السائق تعرضه قبل العنوان (`Stop.shownAddress`: «المكان · الشارع»).
    - **لا مدينة بالتخمين**: المكان الذي بلا مدينة ولا رمز بريدي لا يأخذ مدينة بقية الرحلات. `Places.KNOWN` تعرف بعض الأماكن المشهورة (Centralsjukhuset ← Karlstad، فيُسأل عنه «Centralsjukhuset, Karlstad» أولاً). وإلا يبحث `Geocoding.locate` في Värmland وحدها، ويقبل الجواب فقط إن كانت كل نتائجه في مدينة واحدة (`GeoLogic.inOneTown`)؛ وإلا تبقى المحطة «المدينة غير معروفة» (`Stop.townUnknown`، `stop_town_unknown` في المراجعة) حتى يضيف السائق المدينة بالتعديل. `PlaceMemory` (`StoredPlaceMemory`، تفضيلات خاصة بالتطبيق) يتذكر المدينة التي أضافها السائق لمكان بلا رقم بيت، فيأتي بها المرة القادمة (`RouteController.remembered`)؛ لا يحفظ عنوان بيت ولا اسماً.
    - **الاسم** = السطر فوق العنوان (الأول والأخير فقط).
    - **منتهية** (Performed / Departed): تُضاف مع «Add all trips» معلَّمة (`ExtractedStop.youDriveDone` ← `Stop.youDriveDone`)، وتبقى في المسار حتى يمرّ بها Next. `syncTrips` يطابق أيضاً `completed`: الرحلة المنتهية هنا لا تُضاف ثانية، وتُحدَّث علامة YouDrive عليها فقط. `DoneMarks` (في `Components.kt`، المرجع 202): نقطة خضراء = YouDrive، زرقاء = هنا.
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
| `display/DisplaySnapshot.kt` | ما تعرضه شاشة الركاب: `DisplayItem(time, title, subtitle)`، و`weather` و`eta`. `title` شارع ورقم، أو اسم مكان الرعاية القصير («C-Sjukhuset»)، و`said` كيف يُقال حين يختلف («Centralsjukhuset, huvudentrén»). `place` (وجهة خريطة التابلت) يبدأ من الشارع، لا اسم دار رعاية قبله. |
| `display/TimeStatus.kt` | حالة وقت المحطة التالية: ON_TIME / SOON / DUE / LATE / VERY_LATE من الدقائق المتبقية (للون نقطتي الساعة في شاشة الركاب ولكبسولة الزر العائم)، و`countdown` («7:42»، «+3:10»). |
| `weather/SmhiForecast.kt` | رابط SMHI لمكان ثابت (Karlstad)، وقراءة أقرب ساعة (`air_temperature`، `symbol_code`)، و`DisplayWeather` بحالته السويدية ونوع رسمه. |
| `nav/RoutesApi.kt` | طلب Routes API من Google: الطريق من السيارة عبر المحطات بالترتيب (`body`: نقطة كل محطة أو عنوانها، الأخيرة وجهة والباقي `intermediates`، حتى 10، في الفئة الأساسية) وقراءته (`RouteLine`: الدقائق والمسافة والخط و`legs` لكل محطة؛ `to(i)` الدقائق والأمتار حتى المحطة i)؛ وأوقات السفر بين الجميع (`matrixBody`/`parseMatrix`، computeRouteMatrix: من السيارة ومن كل محطة إلى كل محطة؛ مع عنوان بلا نقطة 50 جواباً على الأكثر)؛ وفك خط Google المرمَّز، و`isKey`. |
| `nav/OrderPlanner.kt` | أفضل ترتيب لبضع رحلات (حتى 7، كل الترتيبات): استلام الراكب قبل توصيله (`allowed`، بـ `rider`)، ثم أقل دقائق تأخير عن الموعد، ثم الأقصر. `plan` يحسب وقت الوصول لكل رحلة من أوقات Google، مع `DWELL_SECONDS` (دقيقتان) لكل توقف. |
| `nav/MapWay.kt` | ما ترسمه خريطة التابلت: موقع السيارة والمحطات التالية (`Stop`: نقطة أو عنوان، ورقم الرحلة؛ `key` لتمييز ترتيب عن آخر). |
| `nav/MapsEta.kt` | قراءة الدقائق المتبقية والمسافة من نصوص إشعار Google Maps (سويدي، إنجليزي، عربي، أو وقت الوصول)، و`DisplayEta`. الأرقام فقط. |
| `youdrive/TripWatch.kt` | مقارنة قراءات YouDrive. |
| `youdrive/YouDriveCards.kt` | قراءة بطاقات YouDrive واحدة واحدة، مع المكان المكتوب قبل الشارع. |
| `core/parse/Places.kt` | الأماكن المسمّاة في العنوان: مكان الرعاية (مشفى، مركز صحي، رعاية أسنان) يُعرض للركاب ويُقال باسمه فقط (`publicName`، بلا قسم أو علاج)؛ أي مكان آخر (دار رعاية، سكن قصير…) لا يُعرض ولا يُقال أبداً. `KNOWN`: Centralsjukhuset يُكتب «C-Sjukhuset» ويُقال «Centralsjukhuset, …, Karlstad». `entrances`: كل كلمة مدخل (تنتهي بـ entrén/entré أو ingång، مع رقم أو حرف بعدها إن وُجد) باب لا قسم، فتُكتب مع اسم المكان كما هي («C-Sjukhuset Dialysentrén»، «C-Sjukhuset Huvudentrén») وتُنطق بعده («Centralsjukhuset, dialysentrén, Karlstad»). `written`: حيث كُتب «Centralsjukhuset» على أي شاشة يُكتب «C-Sjukhuset» (لا في نص بطاقة الرحلة). |
| `route/PlaceMemory.kt` | المدينة التي أضافها السائق لمكان بلا رقم بيت، تأتي معه المرة القادمة. |
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
| `overlay/PanelSource.kt` | من أين تأتي رحلة الزر وإلى أين تذهب أزراره: `RoutePanelSource` (الجوال) و`LinkPanelSource` (التابلت، عبر البلوتوث). |
| `overlay/OverlayTileService.kt` | مربع «Floating button» في الإعدادات السريعة. |
| `service/Notifications.kt` | القنوات، وإشعار المسار، وتنبيهات YouDrive، وإشعار «الزر العائم مغلق». |
| `service/RouteNotifier.kt` | يُبقي إشعار المسار متزامناً. |
| `service/RouteActionReceiver.kt` | أزرار الإشعار: Nästa / Upprepa / Avsluta / إظهار الزر العائم. |
| `link/Bluetooth.kt`، `DisplayLinkServer.kt`، `DisplayLinkClient.kt` | شاشة الركاب عبر البلوتوث (القسم 8). |
| `weather/WeatherWidgets.kt` | استضافة ودجيت تطبيق طقس على التابلت (`AppWidgetHost`): الاختيار والربط والإعداد والعرض (207). |
| `weather/WeatherSource.kt` | يجلب طقس SMHI كل 30 دقيقة ما دامت شاشة ركاب تعرض مساراً (`AppGraph` يقرر `want`). |
| `nav/MapsNavigation.kt` | `MapsNavigationListener` (خدمة إشعارات يسمح بها السائق، 203) يقرأ إشعار الملاحة في Google Maps فقط، و`MapsNavigation.eta`. لا يحفظ ولا يسجّل شيئاً. |
| `settings/SettingsStore.kt` | كل الإعدادات (`AppSettings`) في SharedPreferences، بلا عناوين. |
| `util/LocaleHelper.kt` | لغة الواجهة داخل التطبيق، و`explanationContext` (موارد الشروح بالعربية). |
| `util/SystemIntents.kt` | فحص الأذونات (ومنها `hasPreciseLocation` و`hasMapsTimeAccess`) وفتح صفحات إعدادات النظام. |
| `util/TimeLabels.kt` | «in 7 min»، «5 min late». |
| `util/DebugLog.kt` | سجلات في debug فقط. |
| `youdrive/*` | القسم 6. |

### الواجهة `ui/`
| الملف | الدور |
|---|---|
| `theme/Palette.kt` | الطبقة 1: الألوان الخام. |
| `theme/AppColors.kt` | الطبقة 2: أدوار الألوان، `DayColors` و`NightColors`، و`DisplayColors` (شاشة الركاب على الأسود)، و`trip(kind)` و`status(level)`، وأدوار الزر العائم `panel`. |
| `theme/AppEffects.kt` | الطبقة 3: الظلال، وتصغير الزر عند الضغط، وتلاشي الألوان. |
| `theme/Theme.kt` | `NastaTheme(appearance)`، و`AppTheme.colors` / `AppTheme.effects`، والتحويل إلى Material 3، والخطوط والأشكال. |
| `theme/SystemBars.kt` | أيقونات شريط الحالة وخلفية النافذة حسب المظهر. |
| `Components.kt` | `AppButton`، `TopBar`، `SectionTitle`، `AppCard`، `TripSurface`، `ListRow`، `IconBadge`، `KindLabel`، `Chevron`، `Paragraph`. `TouchTarget = 48.dp`. |
| `Explain.kt` | `explain(id)`، و`HelpDot` (علامة «?»)، و`Hint`. |
| `Refs.kt` | الأرقام المرجعية (`ref`، `refCorner`) ومعرّفات الاختبار `ref_<n>`. |
| `AppRoot.kt` | يوزّع الشاشات، ويطلب الأذونات، ويعرض snackbar، ويوفّر `LocalExplainResources`. |
| `MainViewModel.kt` | مكدس الشاشات (`Screen`)، والاستيراد، وفتح YouDrive أو المراجعة. |
| `screens/*.kt` | كل شاشة في ملف. |
| `screens/RouteMap.kt` + `assets/route_map.html` | خريطة Google على التابلت في WebView خاص (المفتاح 208)، والصفحة لا تحصل على الموقع. خريطة كل دقيقة مسطّحة للنظر فقط (اللمس لا يصلها). خريطة السائق (`reveal(..., held = true)` → `hold(true)` في الصفحة، بعد ضغطته فقط) خريطة Google ثلاثية الأبعاد بمبانٍ حقيقية (`Map3DElement` من `importLibrary('maps3d')`، `mode: 'HYBRID'`، تُنشأ أول مرة تُطلب وتبقى، فـ Google يحسب تحميلاً واحداً لكل WebView): `tour()` تبدأ فوق السيارة ناظرة نحو المحطة (`flyCameraTo` بمدة 0)، ثم تطير إليها (`flyCameraTo` بمدة بين `FLY_MIN_MS` و`FLY_MAX_MS` حسب المسافة؛ الطيران القطعي يرتفع وينزل وحده) وتنزل إلى مبناها (`STOP_RANGE` 280 م، ميل `STOP_TILT` 65°، `altitudeMode: 'RELATIVE_TO_GROUND'`)، ثم تعرض صور الشارع من Google (Street View) عند المبنى إن وُجدت ضمن 80 م (`findStreet` بوعد `getPanorama` يُلتقط فشله، ثم `showStreet`: `StreetViewPanorama` يُنشأ مرة ويُعاد استعماله، موجَّه نحو المبنى بـ `bearing`)، وإلا تدور حوله مرة ببطء (`flyCameraAround`، `AROUND_MS`). `street(on)` يبدّل بين صور الشارع والخريطة (الزر 244)، و`onStreet` يخبر Kotlin (`streetShown`، `noStreet` → «لا صور شارع هنا» في 233). اللمس يصل إليها (`setOnTouchListener` يعيد false ما دامت `held`) ويوقف الطيران، وكل لمسة تبقيها مفتوحة (`onTouch` → `moments.touched()`). أزرار Kotlin: `zoom(±1)` (المسافة ÷2 أو ×2، أو تكبير صور الشارع)، `toCar()`، `toStop()`، `whole()` (السيارة والطريق والمحطات معاً من فوق)، `lookAt(i)` (المحطة i من الطريق)، `tour()`، `street(on)`. الطريق `setWay({stops, focus, legs})`: المحطات بالترتيب بأحرف A، B، C… (`pinSvg`: دائرة بحرفها، الأحمر الأكبر للمحطة المنظور إليها)، وخط لكل مرحلة (`legs`؛ حتى المحطة المنظور إليها كامل، وبعدها أفتح)؛ في الخريطة المسطّحة `Polyline` و`Dot` بـ SVG، وفي ثلاثية الأبعاد `Polyline3DElement` ملتصق بالأرض و`Marker3DElement` بـ SVG في `template`؛ والسيارة نقطة زرقاء. `legs: null` يرسم المحطات فوراً بترتيبها الجديد حتى يأتي الطريق. إن لم تُرسم الخريطة ثلاثية الأبعاد (لا WebGL2، أو خطأ: `failed3d` → `on3d(false)` و`onProblem('3d')` → `noThreeD`) تُمسَك المسطّحة وتتحرك باللمس (`gestureHandling: 'greedy'`) وبالأزرار نفسها؛ `setCar` من Kotlin بموقع التابلت (`MapWay` من `geo/TabletPosition`)، و`show(way)` بالمحطة التالية والثلاث بعدها لخريطة كل دقيقة، و`focus(stops, at)` لطريق السائق؛ وطلب الطريق كل 4 دقائق على الأكثر للمحطات نفسها بترتيبها (`keyOf`)، والطرق المعروفة تُحفظ قليلاً (`known`، 12) فلا يُسأل Google ثانية عن ترتيب جُرِّب. `route` و`routeKey` يقولان لأي ترتيب هذا الطريق. `suggest(stops, trips, now)` يسأل computeRouteMatrix ثم `OrderPlanner.best` → `suggestion`. الصفحة نفسها تُظهر الخريطة وتخفيها (`reveal(x, y, ms)` و`conceal(ms)`: تكبير وشفافية بـ CSS، والحواف الذائبة بتدرّجات بلون الخلفية)، ولا يُكبَّر الـ WebView ولا يُشفَّف من Compose أبداً، لأن WebView مرسوماً هكذا قد يبقى فارغاً. تُنشأ الخريطة حين يصير لعنصرها حجم (`importLibrary('maps')`)، وسكربت Google يُعاد طلبه كل 30 ثانية ما دام لا يأتي (لا إنترنت بعد). ما يوقفها يصل عبر الجسر: `onProblem` (السكربت لم يُحمَّل، الصور لم تأتِ خلال `TILES_WAIT_MS`، خطأ في الصفحة) → `trouble`، و`onTiles` → `tiles`، وردّ Routes API إن لم يعطِ طريقاً → `routeAnswer`. لا شيء من ذلك يُسجَّل. |
| `screens/HostedWidget.kt` | عرض ودجيت الطقس بحجم مكانه. |

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
  - الكبسولة بعد التصغير 17: عدّ تنازلي إلى وقت الرحلة بشكل الساعة (`TimeStatus.countdown` يعيد `Countdown(hours, minutes, seconds)`، كل ثانية): الساعات متوسطة (من ساعة فأكثر، ثم «:»)، والدقائق كبيرة، والثواني صغيرة على يمينها بلا نقطتين، على خط أساس واحد وبخط الزر المعتاد (بلا نقطة حالة). لون الأرقام والإطار من `TimeStatus` (`PanelRoles.status`)، والإطار ينبض (`ValueAnimator`) عند DUE وVERY_LATE. العدّ على زجاج أعمق (`well`) ليُقرأ فوق أي خريطة. وعلى جانبيها سهمان رفيعان شفافان عائمان (`Chevron`: ‹ 12 = `source.back()`، › 14 = `source.next()`، ضغطة مطوّلة = `repeat()`؛ `CHEVRON_ALPHA`) بهالة داكنة (`PanelRoles.halo`) بلا زجاج؛ الكل يُسحب لتحريك الزر. على التابلت `bubbleScale = 2`، و`swellLastMinute`: في آخر 60 ثانية تتضخّم الكبسولة مع سهميها إلى الضعف (`sizeBubble`) وتومض، ثم تعود.
- **المصدر** (`PanelSource`): `OverlayManager(context, source, settings, scope, wanted)`.
  - الجوال: `graph.overlay` بـ `RoutePanelSource` (المسار نفسه، والشارع والسرعة من `CurrentStreet`، واسم الراكب)، ويظهر إن لم يكن الجهاز تابلتاً.
  - التابلت: `graph.tabletPanel` بـ `LinkPanelSource`، ويظهر فقط في دور شاشة الركاب مع المفتاح 206 (`AppSettings.tabletPanel`) وما دام الاتصال قائماً والمسار نشطاً. الرحلة من `DisplaySnapshot` (بلا اسم)، ولا شريط شارع ولا سرعة (`street == null`). Next وBack والإعادة تُرسل `LinkMessage.Command` إلى الجوال، و`DisplayLinkServer.carryOut` ينفّذها بـ `controller`. والترتيب الذي يضعه السائق على خريطة التابلت يصل `LinkMessage.Order` (أرقام الرحلات فقط) فينفّذه `controller.reorder`. ضغطة شارع المحطة تنطقه على التابلت نفسه.
  - `wanted` يفصل الاثنين، فلا يلمس أحدهما إشعار الآخر.
- **السلوك**:
  - السحب من أي مكان يحرّكه، والمكان يُحفظ. «×» و«–» يستجيبان للضغط الحقيقي فقط.
  - أعلى البطاقة يبقى 100 dp على الأقل تحت أعلى الشاشة، والهامش الشفاف حول البطاقة صغير (2 dp فوقها، 6 بجانبها، 12 تحتها للظل)، لأن النافذة تأخذ كل ضغطة في مستطيلها: فلا تغطي الشريط العلوي للتطبيق ولا شريط الاتجاهات في الخرائط.
  - بعد الإغلاق أثناء المسار: إشعار صامت، ومربع الإعدادات السريعة، والمفاتيح تعيده.
  - يغيب ما دامت شاشة الركاب ظاهرة على الجوال (`suppress`).
- **للاختبار**: كل جزء له `id/ref_<n>`، ومع `setprop log.tag.NastaStoppRefs DEBUG` يسجّل حدود الأجزاء على الشاشة فقط.

### شاشة الركاب (`link/*`، `ui/screens/PassengerDisplayScreen.kt`، `DisplayRoleScreen.kt`)
- البلوتوث RFCOMM بين أجهزة مقترنة. الجوال يستمع على قناة آمنة وقناة احتياطية، ويخدم الأجهزة المقترنة به فقط.
- التابلت يجد الجوال وحده: آخر جهاز أولاً، ثم الجوالات، ثم التابلتات والحواسيب. السماعات وأنظمة السيارة تُتخطّى.
- يُرسل `DisplaySnapshot` فقط: حتى 7 رحلات منتهية (`earlier`، و`previous` آخرها)، والحالية، وحتى 7 قادمة. الوقت، والشارع مع الرقم، والحي تحته (الإعداد 114؛ مطفأً = الحي فقط)، ونوع الرحلة (`kind`، لشريط الزر العائم على التابلت)، وعلامتا الانتهاء (`doneInYouDrive`، `doneHere`)، والعنوان والنقطة التي ترسم إليها خريطة التابلت (`place`، `lat`/`lng`)، و**الاسم الأخير لراكب المحطة التالية فقط** (`lastName`)، ورقم الرحلة في المسار (`id`) ورقم يجمع استلام الراكب وتوصيله (`rider`، من ترتيب ظهور الاسم، لا الاسم نفسه). لا اسم أول ولا اسم رحلة أخرى، ولا موقع الجوال. ولا يُرسل التابلت إلى الجوال إلا الزر الذي ضُغط في زره العائم (`LinkMessage.Command`: NEXT / BACK / REPEAT) والترتيب الذي وضعه السائق على خريطته (`LinkMessage.Order`: أرقام الرحلات). `RouteController.reorder` يضع هذه الرحلات في الأماكن التي تشغلها الآن بالترتيب الجديد، ويترك غيرها؛ ومع مسار نشط يُعيد الإعلان إن تغيّرت المحطة التالية أو التي بعدها، ويفتح Maps من جديد (`openMaps(fromBackground = true)`) إن تغيّرت أول 10. مفتاح الانتقال المشترك `DisplayItem.trip` (الرحلة بلا علاماتها وبلا الاسم) كي لا تتغيّر هويتها حين تُعلَّم أو تصير المحطة التالية.
- إعادة الاتصال كل 3 ثوانٍ، وping كل 10 ثوانٍ، وقطع الاتصال الصامت بعد 30 ثانية، وسطر أطول من 64 KB يقطع الاتصال.
- يحمل الـ snapshot أيضاً، ما دام المسار نشطاً، **الطقس** (`DisplayWeather`: الحرارة ورمز SMHI) و**الوقت المتبقي** (`DisplayEta`: الدقائق والمسافة). الجوال وحده يجلبهما.
- **التابلت ينطق كل إعلان** يصدره الجوال عند ضغط Next (مفعّل افتراضياً، ويُطفأ بالمفتاح 198 في شاشة إعداد التابلت). لا زر صوت: ضغطة عنوان المحطة التالية تعيد الإعلان على الجهاز نفسه.
- **الترتيب للركاب**: السطر الأعلى صغير في الزاوية العليا اليمنى، وتحته مباشرة تبدأ المحطة التالية (الشارع والرقم بأكبر حجم يتسع في `TITLE_ROOM` من الارتفاع، والحي تحته). في الأسفل سطر واحد من اليسار: الساعة في الزاوية السفلى اليسرى، ثم في منتصف ارتفاعها وقت المحطة التالية (`TripTime`، 90، دائماً للمحطة التالية)، ثم «Därefter» (94) والرحلة التي بعدها (229). `Stage` يبلّغ بـ `onShown` عن الرحلة المعروضة في الأعلى مع المحطة التالية التي تنتمي إليها، وهل الشاشة في عرض التركيز. الشاشة للعرض الأفقي فقط (لا تُضبط المقاسات للطولي). كل عنصر بحجم محتواه فقط: لا صناديق فارغة تدفع ما حولها. الشاشة سوداء حتى الحافة: `AppRoot` لا يضع `safeDrawingPadding` لشاشتي العرض، و`PassengerDisplayScreen` يلوّن كل الشاشة ثم يُبعد محتواه عن فتحة الكاميرا (وشاشة الإعداد في `DisplayRoleScreen` تضع الهامش بنفسها). كلمات الركاب بالسويدية مثل الإعلانات (`strings_fixed.xml`)، وسطر الاتصال بلغة التطبيق.
- **الحركة**:
  - `Stage`: `SharedTransitionLayout` + `AnimatedContent` مفتاحه المحطة الحالية؛ «Därefter» (`ThenChip`) والمحطة التالية لهما نفس المفتاح (`DisplayItem.trip`)، فتكبر «Därefter» إلى أعلى الشاشة عند Next وتعود عند Back، وتنتقل الرحلة التي بعدها إلى مكان «Därefter».
  - الشاشة كلها من اليسار إلى اليمين (`LocalLayoutDirection` = Ltr) مهما كانت لغة التطبيق.
  - `spoken` يزيد مع كل إعلان (من `controller.announcements` على الجوال، ومن `DisplayLinkClient.announcements` على التابلت، ومع ضغطة عنوان المحطة التالية). `Spotlight` يتبع الإعلان: يعيد الصفحة إلى المحطة التالية، ثم `NEXT_STOP` (يضيء العنوان)، ثم عند ذكر «Därefter» (التوقيت من طول النص، نحو 75 ms للحرف) رحلتها: ينتقل الـ pager إلى صفحتها وتبقى `THEN_SHOWN_MS`، ثم `goHome`.
  - **عرض التركيز**: ما دام `Spotlight.on` ليس `NONE` (إعلان أو رحلة مضغوطة)، `Stage` يبلّغ `onShown(next, inMiddle, focus = true)`: يتلاشى سطر الساعة (`line`، `CLOCK_FADE_MS`، وتُنزع ضغطة الساعة) ورموز السطر العلوي (`chrome`، و`TopLine(enabled = false)` كي لا تُضغط وهي مخفية) والنقاط، وتتلاشى «Därefter» والتي بعدها بعد لحظة (`STRIP_FADE_DELAY_MS`)، فلا يبقى إلا العنوان والوقت (وزر البيت). الوقت الكبير `FocusTime` (225) مرسوم بحجمه الفعلي (لا تكبير لطبقة، فيبقى حاداً) وبلا تنفّس، تحت العنوان في منتصف المساحة الباقية حتى أسفل الشاشة (`StopHero` المضيء يبلّغ أين ينتهي: `onBottom`)، بحجم `FOCUS_FILL` من تلك المساحة أو `FOCUS_WIDTH` من عرض الشاشة، وليس أكبر من `FOCUS_MAX`. الظهور `Entrance` + `Modifier.entering`: من ضباب (`BlurEffect` على Android 12+) مع ارتفاع `ENTER_RISE` وحجم `ENTER_SCALE` → 1 خلال `APPEAR_MS`، ثم شعاع ضوء (`SrcAtop` في طبقة `Offscreen`، على الحروف فقط) خلال `SHINE_MS`؛ الوقت ثم العنوان (`ENTER_TITLE_DELAY_MS`) ثم الحي (`ENTER_AREA_DELAY_MS`). صفحة «Därefter» أو الرحلة المضغوطة تُعرض مباشرة (`scrollToPage`) وتدخل بالطريقة نفسها.
  - **ضغطة «Därefter» أو التي بعدها أو رحلة في الشريط** (`showCard`): الرحلة في السطر الأسفل تكبر في مكانها (`CARD_TAP_SWELL` 1.22، من منتصف أسفلها)، والشريط يُطوى (`pick`)، وينتقل الـ pager إلى صفحة الرحلة في عرض التركيز، ثم `goHome` بعد 6 ثوانٍ (`SHOW_TRIP_MS`). المحطة التالية في القائمة تعيد الإعلان (`onSpeakNext`). تُنطق بـ `Announcements.at(time, name, coming)`: القادمة «Klockan 8 och 05 ska vi till Hamngatan 7, Skoghall.»، والمنتهية «Klockan 7 och 30: Järnvägsgatan 3B, Storfors.». ضغطة عنوان صفحة غير التالية تنطقها بالطريقة نفسها. «Därefter» تكبر في مكانها أيضاً حين يذكرها الإعلان.
  - **التصفّح**: `HorizontalPager` على التابلت و`VerticalPager` على الجوال: `earlier` ثم المحطة التالية (الصفحة `home` = عدد المنتهية، `StopHero(HeroRole.NEXT)` بمفتاح الانتقال) ثم `upcoming`، والباقي بلا مفتاح (كي لا يتكرر مع البطاقة). وقت كل صفحة تحت عنوانها (`StopHero(showTime)`، 230) ما دامت غير المحطة التالية أو أثناء سحب الـ pager أو مع الشريط، بحجم `PAGE_TIME_FILL` مما بقي تحت العنوان ومرسوماً بحجمه؛ ووقت المحطة التالية بجانب الساعة لا يتغيّر مع التصفّح، ويقفز عند Next فقط (`PAGE_POP` 1.5 ثم spring). `HomeButton` (200) صغير عائم في الزاوية العليا اليسرى من السطر الأعلى، يظهر مع التصفّح أو الشريط أو عرض التركيز؛ ضغطته تزيد `homeCalls`، و`Stage` يوقف `Spotlight` (`stop`) ويعود (`goHome`)، فيختفي الزر. وتعود المحطة التالية أيضاً عند أي إعلان أو بعد 30 ثانية دون لمس (`BROWSE_RETURN_MS`). لا يلمس المسار أبداً. `PageDots` (201) عائمة في أسفل الشاشة تماماً وفي منتصفها (`DOTS_LOW`)، لا تأخذ ارتفاعاً.
  - **شريط كل الرحلات** (`Browse`، `TripStrip`، 226): `LazyListState` واحد على مستوى الشاشة، وسطر الساعة و«Därefter» والتي بعدها تحمل `Modifier.scrollable` على الحالة نفسها مع `browse.drags` و`snap` (`rememberSnapFlingBehavior` بـ `SnapPosition.Center`)، فأول سحب يُخرج الشريط ويحرّكه. `open` ما دام السحب (`collectIsDraggedAsState` لسطر الأسفل وللشريط) أو الانزلاق بعده، ثم `BROWSE_LINGER_MS` (3 ثوانٍ) ثم `homeCalls`. وهو مفتوح يحلّ مكان السطر الأسفل (ذلك السطر يتلاشى، ولا يأخذ اللمس إلا للسحب الذي أخرجه)، ويتوقف سحب الـ pager. كل رحلة بعرض `STRIP_TRIP_SHARE`، وحشوة جانبية بنصف الباقي كي تقف أي رحلة في الوسط؛ الرحلة في الوسط (`middle`، الأقرب إلى منتصف `layoutInfo`) مضيئة وأكبر (`STRIP_LIT_SCALE`)، والـ pager يتبعها (`animateScrollToPage`) بعد أول قياس جديد للشريط (`following`، كي لا يقفز إلى وسطه القديم). الصفحات نفسها: المنتهية ثم المحطة التالية («NÄSTA STOPP»، `passenger_next_stop`) ثم القادمة («DÄREFTER» فوق أولاها)، لكلٍّ `StripTrip`: الوقت والشارع والحي. يُرسم في المحتوى الأحدث فقط من `AnimatedContent` (`latest`) كي لا يتشارك شريطان الحالة، ويُطوى عند تغيّر المحطة، ويعود بعد الطيّ إلى المحطة التالية في وسطه (`requestScrollToItem`).
  - **السطر الأسفل**: `Row` في الزاوية السفلى اليسرى فوق كل شيء: `Clock` و`NextArrow` (224) و`TripTime` للمحطة التالية، في منتصف الارتفاع نفسه؛ كل سطر أرقام بارتفاع حبره فقط (`ink`: يُقصّ ما فوق الأرقام `DIGIT_ASCENT - DIGIT_HEIGHT` وما تحتها `DIGIT_DESCENT`)، فتتحاذى منتصفاتها، والثواني معلّقة تحت الدقائق لا تأخذ ارتفاعاً (تحتها `SECONDS_CLEAR` إلى الحافة). ارتفاعه وعرضه (`onSizeChanged`) يمرّان إلى `Stage`: `ThenLine` (`Layout` خاص) يحجز هذا الارتفاع أسفل الصفحات ويضع بعده مباشرة وفي منتصفه السهم 227 و«Därefter» ثم السهم 228 والرحلة التي بعدها. `ThenChip` (94 و229): الوقت وبداية الشارع فقط (`softWrap = false` مع `Ellipsis` حتى `THEN_STREET_EMS` من حجمها: «Hamngat…»)؛ الأولى وقتها أزرق و«DÄREFTER» (`ThenLabel`) معلّقة فوقها (`hangAbove`)، والثانية أهدأ (`AFTER_ALPHA`). الأسهم الثلاثة صفراء (`accent`) فاقعة (`ARROW_LOW` 0.35 → `ARROW_HIGH` 1)، ثلاث علامات › لكلٍّ، وضوء واحد يمرّ على التسع (`ARROW_LINES`، `ARROW_FLOW_MS`): حلقة إطارات واحدة في الشاشة (`withInfiniteAnimationFrameMillis`) تعطيه للأسهم كلها عبر `LocalArrowPhase`، ويُقرأ عند الرسم فقط فتبقى متزامنة ولا يُعاد تركيب شيء. `TopLine` في الزاوية العليا اليمنى، رموزه `TOP_ICON`: نقطة الاتصال واسم الجوال (87، `labelMedium`؛ `DisplayRoleScreen` يقصّه إلى 10 أحرف، `NAME_CHARS`؛ ضغطته تفتح `DropdownMenu` فيه «خروج» 86 → `onExit`، ولا × منفصل؛ على الجوال بلا اتصال يُغلق العرض بزر الرجوع)، و`MapSign` (219: الدبوس وحده، ضغطته `showWay` للمحطة التالية؛ يظهر حين يوجد `routeMap`)، و`WeatherSign` (222: `WeatherGlyph` والحرارة؛ ضغطته `moments.playInfo(WEATHER)`؛ يظهر إن وُجد طقس SMHI أو ودجيت)، وزر الوضع (223: الشمس في الأسود والقمر في الفاتح، `onToggleLook`). 
  - ضغطة رحلة أو عنوان صفحة: `Announcements.at` («Klockan 8 och 05 ska vi till …» للقادمة، «Klockan 7 och 30: …» للمنتهية). ضغطة الساعة: `Announcements.clock` («Klockan är 8 och 05»). الاثنتان عبر `onSay` على الجهاز نفسه (`graph.announcer`).
  - `Clock`: بلا إطار؛ الساعات متوسطة (`HOUR_SHARE`) ومنتصفها بمستوى منتصف الدقائق، ثم النقطتان (`Colon`: دائرتان مرسومتان فوق بعضهما تماماً في منتصف ارتفاع الأرقام، `COLON_DOT` و`COLON_SPREAD`، وعلى جانبيهما مسافة `COLON_SIDE`) بمستواها، ثم الدقائق كبيرة، والثواني صغيرة (`SECOND_SHARE`) بخط Light أزرق تحت الدقائق بمسافة `SECONDS_DROP`، معلّقة لا تأخذ ارتفاعاً؛ كل رقم منها `AnimatedContent` خاص (`RollingDigits`) يتدحرج كالعدّاد: الجديد يصعد من أسفل خانته والقديم يخرج من أعلاها (`ROLL_MS`، مقصوصاً في خانته). كل سطر أرقام صندوقه بارتفاع صعود الخط ونزوله (`DIGIT_ASCENT`، `DIGIT_DESCENT`)، ومنه تُحسب الإزاحات. الأرقام بلا حدّ؛ حالة وقت المحطة التالية في لون النقطتين (`TimeStatus`): أخضر (`success`)، برتقالي (`soon`)، أحمر (`danger`)، وتنبضان بسرعة عند DUE وVERY_LATE (`BEAT_MS`)، وإلا تومضان مع الثواني؛ وصفراوان (`accent`) بلا مسار. ضغطة الساعة تنطق الوقت (`onSay`) وتشغّل `moments.playTime(tapped = true)`: نابض لطيف ثم الخلفية الصمّاء، 4 ثوانٍ.
  - `TimeFace` (داخل `TripTime`): بشكل الساعة، ونقطتاه صفراوان. يتنفّس 1 → 1.2 خلال 2.2 ثانية حول نقطتيه (`ColonLine`، الخط الذي يعطيه كل `Colon`) بـ `breathe`، فتبقى النقطتان ثابتتين: يُرسم بأكبر حجم في طبقة خاصة (`CompositingStrategy.Offscreen`) ويُصغَّر كصورة، لأن النص المكبَّر مباشرة يقفز بين أحجام خطوطه. يأخذ عرض حجمه الأكبر، فلا يغطي في تنفّسه السهمين ولا الساعة بجانبه. حين تتغيّر المحطة التالية يكبر في مكانه من `PAGE_POP` (0.6) إلى 1 بنابض بلا تجاوز يُذكر. `Colon` لا يشغّل نبضه (`rememberInfiniteTransition`) إلا وهو ينبض، فالنقطتان الساكنتان لا تطلبان إطارات.
  - **الضغط المطوّل** على عنوان صفحة أو «Därefter» أو التي بعدها أو رحلة في الشريط، أو ضغطة `MapSign`: `showWay` (فقط حين يوجد `routeMap` ولم يرفض Google المفتاح) → `onWantPosition` (يطلب إذن الموقع على التابلت إن لم يُعطَ بعد، والطلب يتبع الضغطة)، و`routeMap.focus(MapWay.Stop(lat, lng, place))` (null = المحطة التالية؛ إن لم يُعرف موقع التابلت بعد يُطلب الطريق مع أول موقع) و`moments.playFocus()` (`Info.FOCUS`، `holding` يوقف لحظات الدقيقة حتى الإغلاق، أو دقيقتين بعد آخر استعمال: `FOCUS_MAX_MS`، تتجدد مع كل لمسة أو زر عبر `moments.touched()`)، و`unfocus` عند الإغلاق. طريقها حول الرحلة المنظور إليها (`wayTrip`؛ null للمحطة التالية): حتى ثلاث رحلات قبلها وثلاث بعدها من `ahead` (`DisplaySnapshot.around`)، من السيارة. أسفلها `WayStrip` (245–251): بطاقة لكل رحلة بحرفها (الحمراء المنظور إليها) ووقتها ونقطة نوعها وبداية شارعها ودقائق مرحلتها، ومتى تُبلغ بألوان حالة الساعة إن بدأ الطريق بالمحطة التالية (`fromNext`)؛ وفوقها الدقائق والمسافة إلى المنظور إليها والطريق كله والفرق عن ترتيب الجوال. ضغطة على بطاقة تختارها (`WayEdit.picked`) وتدير الخريطة إليها (`lookAt`) وتعطيها سهمين (246، 247) يحرّكانها مكاناً؛ الترتيب المجرَّب (`WayEdit.preview`) يُرسم بعد `ORDER_ASK_MS` من آخر سهم. «اقترح» (248) يسأل Google ويضع أفضل ترتيب للوزن فقط، «تراجع» (249)، «اعتمد» (250) يرسل أرقام الرحلات إلى الجوال (`onOrder` → `DisplayLinkClient.order`)؛ ولا يُعتمد ترتيب فيه توصيل قبل استلام الراكب نفسه. `WayEdit.settle` يُسقط الترتيب المجرَّب حين يصير ترتيب الجوال أو تتغيّر رحلاته. هذه خريطة السائق (`held`): `MapLayer` يرفعها فوق الشاشة المتراجعة (`zIndex` `MAP_HELD_Z`) وتحتها طبقة تمسك اللمس (`MAP_FLOOR_BELOW`)، لأن لمس الـ View يصل في Compose إلى ما تحته أيضاً (`PointerInteropFilter` يشارك اللمس مع الإخوة)، فلولاها لنطقت ضغطة على الخريطة العنوان المخفي تحتها وأغلقتها. فوقها `MapControls` (`MAP_OVER_Z`): × أعلى اليمين (243)، وعلى اليمين تقريب (237)، تبعيد (238)، السيارة (239)، مبنى المحطة (240)، الطريق كاملاً (241)، والطيران مرة أخرى (242، مع الخريطة ثلاثية الأبعاد فقط). ضغطة على الخريطة لا تغلقها (طبقة `settle` لا تُرسم معها)؛ × أو أي شيء يُنطق يغلقها. وهي مفتوحة تغطي الساعة. الشاشة لا تفتح خرائط Google أبداً.
  - الخريطة (`MapLayer`، 210) تحت كل شيء، تملأ الشاشة، وغير مرئية حتى لحظتها، فتُحمَّل مرة واحدة. وهي مخفية لا يُرسم الـ WebView أصلاً (`View.INVISIBLE`، يعود `VISIBLE` في `reveal` ويختفي بعد `conceal` بـ `HIDE_AFTER_MS`): صفحة بملء الشاشة تُرسم تحت العرض مع كل إطار من الأسهم والثواني تُثقل التابلت. لا يغيّرها Compose بشيء: حين يصير `info` متجهاً إلى الظهور مع `Info.MAP` أو `Info.FOCUS` تُستدعى `routeMap.reveal` بموضع الدبوس (`pinAt` من `MapSign`، كنسبة من حجمها) و`INFO_IN_MS`، وحين يتجه إلى الاختفاء `conceal(moments.outMs)` (`SETTLE_MS` بعد ضغطة، وإلا `INFO_OUT_MS`). الصفحة تكبّرها من الدبوس كما تكبر الساعة، وحوافها تذوب في لون الخلفية (أعلى 18%، أسفل 34%، الجانبان 14%)، فلا إطار لها، وتضع الطريق في الوسط الصافي (`clear()`: حشوة `fitBounds`). في الثانية 45، إن كانت جاهزة والتابلت يعرف مكانه، تظهر 10 ثوانٍ (`Info.MAP`) بالشكل نفسه، وتحتها في الجزء الذائب الدقائق والمسافة (`MapMomentText`، 205)؛ وحتى يعرف التابلت مكانه «Söker bilens position…»، وتحتها بخط صغير ما يمنع الخريطة أو الطريق (`mapNote`، 233؛ ومع خريطة السائق أيضاً «3D-kartan laddas…» أو «Ingen 3D-karta här (…)»). وإلا يظهر الوقت المتبقي وحده. خريطة كل دقيقة تبقى مسطّحة للنظر فقط، وضغطة تعيد الشاشة.
  - **موقع الخريطة**: `geo/TabletPosition` على التابلت (LocationManager، GPS بدقة عالية كل ثانية، `MAX_ACCURACY_M` 30)، يبدأ ويتوقف مع ظهور الشاشة (`LifecycleStartEffect` في `AppRoot`) وفقط إن وُجدت الخريطة والإذن. كل موقع مع المحطة التالية (`DisplayItem.place` و`lat`/`lng` من الجوال) يذهب إلى `routeMap.show(MapWay)`. الجوال لا يرسل موقعه (لا `Where` في البروتوكول).
  - **بطاقة الرحلة**: رمز الشخص (`PersonGlyph` مفرّغ) تحت المحطة التالية (231 في `StopHero`) وبجانب كل رحلة أخرى لها بطاقة (234، `PersonSign`: صفحات `StopHero` الأخرى، `ThenChip`، `StripTrip`) يفتح `TripCard` (235) عبر `onCard` ← `openCard`. `TripCard` يرتّب النص بترتيب نافذة تفاصيل YouDrive عبر `core/youdrive/TripCardText.of`: العنوان «Pick-up 09:58»، الاسم، «Estimated time»، «Client's negotiated time»، النوع والحالة، ثم صف لكل حقل (عنوانه بلون خافت وقيمته): Address (سطر أو سطران)، Phone number، Space Type(s)، Mobility Aids (الرموز مكتوبة كما يكتبها YouDrive: SP Sittande passagerare، FRA Fram، ROL Rollator fällbar، RU Rullstol، TRP Transportrullstol، HLI Hämtas/Lämnas inne، AVD Hämtning på avdelning، TRA Trappklättrare؛ الرقم بعد الرمز عدد ويُكتب بعد المعنى: RU1 «Rullstol 1» أي كرسي متحرك واحد؛ رمز غير معروف يبقى كما هو)، Fare amount («Client fee»)، Compensation، Eligibility، Instructions. كل كلمة تبقى كما هي، وتُحذف فقط أزرار الصفحة («Arrive») وعدّادها («27 min»). الأرقام بـ `DigitFont` (`withDigitFont`)، والبطاقة تُمرَّر إن طالت وتُغلق بضغطة؛ لا يُنطق منها شيء، ولحظات الدقيقة تنتظر ما دامت مفتوحة.
  - **حفظ البطاقة**: `YouDriveCards.parseCard` يحفظ نص البطاقة كله كما هو في `ExtractedStop.card` ← `Stop.card` ← `DisplayItem.card` (لكل رحلة من YouDrive؛ لا بطاقة للقطات الشاشة ولا للمحطات اليدوية). `DisplayItem.trip` يتجاهلها. تذهب مع المسار إلى شاشة الركاب فقط: لا في الإعلانات ولا الإشعار ولا «Previous trips» ولا السجل (`theTripCardGoesOnlyToTheDisplay`).
  - **اسم الراكب**: `DisplaySnapshot.build(nextName)` يضع الاسم الأخير لراكب المحطة التالية فقط في `current.lastName` (`RouteController.lastNameOf`)؛ `DisplayItem.trip` يتجاهله كي تبقى «Därefter» → المحطة التالية حركة واحدة. `StopHero` يضع تحت الحي رمز شخص صغيراً مفرّغاً فقط (231، `PersonGlyph`: رأس وكتفان بخط، `PERSON_SIZE` من حجم الاسم)؛ ضغطته تُظهر الاسم بجانبه (236) مدة `NAME_OPEN_MS` (15 ثانية) أو حتى ضغطة أخرى على الرمز أو تغيّر المحطة، ويتلاشى الكل في عرض التركيز. ضغطة الاسم: `Announcements.passenger` على هذا الجهاز و`moments.playInfo(Info.NAME)`: الاسم كبيراً مع شارعه في `InfoMoment` (232).
  - ودجيت الطقس (211) يأخذ لحظة الطقس بدل رسم SMHI، ولا يستقبل اللمس (ضغطة تعيد الشاشة).
  - `Moments` (`rememberMoments`): عند تغيّر الدقيقة ينتقل الوقت (الساعات والنقطتان والدقائق والثواني قطعةً واحدة، `growTogether`) إلى وسط الشاشة ويكبر خلال 5 ثوانٍ (حتى 5× أو 90% من الشاشة) بلون جديد كل مرة (`showHues`، `hue`) ويخفت الباقي (`stepBack`، بـ `ModulateAlpha` كي لا يُقصّ شيء)؛ في آخر 1.8 ثانية من الكبر تتحوّل الخلفية بالتدريج إلى صمّاء (`solid`) وتبقى ثانيتين، ثم يعود الوقت خلال 1.2 ثانية وتعود الشفافية بالتدريج خلال 1.8 ثانية. في الثانية 27 يظهر الطقس (`InfoMoment`، 204: `WeatherGlyph` والحرارة والحالة السويدية)، وفي الثانية 45 الوقت المتبقي إن وُجد (205: `RouteGlyph` والدقائق والمسافة)، 7 ثوانٍ لكل منهما (1.2 + 4.6 + 1.2). ضغطة في أي مكان أثناء أي منها تعيد الشاشة (`settle`). لا شيء أثناء النطق، وأي نطق يوقفها. ولا تأتي لحظات الدقيقة وحدها والشاشة مشغولة (`Moments.paused`: رحلة معروضة كبيرة أو تُنطق، الشريط ظاهر، صفحة غير المحطة التالية، بطاقة رحلة مفتوحة)، فلا تتحرك ساعتان معاً؛ الضغطات تبقى تعمل. وحين يكبر الوقت يختفي السطر الأسفل بجانبه أسرع بكثير من الباقي (`lineBack`، `LINE_FADE_SPEED` 6): وقت المحطة التالية والأسهم و«Därefter» يذهبون قبل أن تصلهم الساعة. القيم المتحركة تُقرأ عند الرسم (`graphicsLayer`، `Canvas`) أو عبر `derivedStateOf` (`focusSeen`، `infoShown`)، فلا تُعاد تركيبة الشاشة كلها مع كل إطار. `time` يُمرَّر في الاختبارات.
- **الخطوط** (في `theme/Theme.kt`، الملفات في `res/font/`، والترخيص SIL OFL في `assets/licenses/`):
  - العناوين والكلمات: Barlow Semi Condensed (`DisplayFont`)، بأسلوب لوحات الطرق والنقل، وحروفه الضيقة تُبقي أسماء الشوارع الطويلة كبيرة.
  - الأرقام (الساعة، الأوقات، الحرارة، الدقائق): Atkinson Hyperlegible Next (`DigitFont`)، مصمَّم لضعاف البصر (صفر مشطوب، 1 و7 واضحان). أوزان ثابتة Light/Medium/SemiBold/Bold مستخرجة من الخط المتغيّر. أرقامه متناسبة العرض؛ `TABULAR` («tnum») فقط لما يتغيّر كل ثانية (الثواني) كي لا يهتز.
- **الألوان**: شاشة الركاب سوداء افتراضياً أياً كان مظهر التطبيق (`DisplayTheme(dark)` يوفّر `DisplayColors` لها ولنوافذها، أو `DayColors` حين يطفئ زر الوضع 223 الإعداد `displayDark` على هذا الجهاز؛ `displayColors(dark)` لخلفية الخريطة)، وخريطة التابلت بالرمادي الداكن على الأسود.
- العنوان الكبير لا ينقسم في وسط كلمة: حجمه محدود بأطول كلمة.

---

## 9. نظام التصميم

الهدف: تغيير الألوان أو التأثيرات **في مكان واحد**، دون لمس الشاشات. ثلاث طبقات في `ui/theme/`:

| الطبقة | الملف | ما فيها | متى تعدّلها |
|---|---|---|---|
| 1. الألوان الخام | `Palette.kt` | كل لون بقيمته، مسمّى بما **هو** (YouDriveGreen، TaxiYellow، Sky…). | لتغيير درجة لون في كل مكان. |
| 2. الأدوار | `AppColors.kt` | دور كل لون، ومجموعتان: `DayColors` و`NightColors`، و`DisplayColors` لشاشة الركاب (أسود دائماً). | لتغيير لون عنصر، أو لإضافة مظهر. |
| 3. التأثيرات | `AppEffects.kt` | الظلال، وتصغير الزر عند الضغط، وتلاشي الألوان. `0.dp` أو `1f` أو `0` يطفئ التأثير. | لإضافة تأثير أو ضبطه. |

- **`Theme.kt`**: `NastaTheme(appearance)` يختار النهاري أو الليلي (130–132: Day / Night / Automatic)، ويوفّر `AppTheme.colors` و`AppTheme.effects`، ويحوّل الأدوار إلى Material 3. `DisplayTheme` يفعل ذلك بـ `DisplayColors` لشاشة الركاب، أياً كان المظهر.
- **القاعدة**: لا `Color(0x…)` خارج `Palette.kt` و`AppColors.kt`.
- **الأدوار الأساسية**:

| الدور | نهاري | ليلي | أين |
|---|---|---|---|
| `background` / `card` / `cardBorder` | رمادي فاتح / أبيض / خط رفيع | أزرق ليلي داكن | الصفحة والبطاقات |
| `text` / `textMuted` | أسود / رمادي | أبيض مزرق / رمادي مزرق | النصوص |
| `action` / `onAction` | أسود | الأزرق الفاتح Sky | الأزرار الرئيسية |
| `accent` / `onAccent` | الأصفر مع نص أسود | الأصفر | Next، Start route، وقت الرحلة الحالية، نقطتا الساعة في شاشة الركاب بلا مسار |
| `info` | أزرق SkyInk | Sky | المفاتيح والعناوين والروابط |
| `highlight` | أزرق SkyInk | Sky | شاشة الركاب: الثواني، وما يُنطق، والبطاقة المضغوطة |
| `showHues` | ألوان *700 (برتقالي، بنفسجي، فيروزي، وردي، زمردي، أرجواني) | ألوان *400 | شاشة الركاب: الوقت الكبير والطقس والوقت المتبقي، لون مختلف كل مرة |
| `soon` | برتقالي Orange600 | Ember400 | شاشة الركاب: نقطتا الساعة قبل وقت المحطة التالية |
| `pickUp` / `dropOff` / `depot` | أخضر YouDrive / أبيض / رمادي | أخضر داكن / بطاقة داكنة / رمادي داكن | بطاقات الرحلات (`trip(kind)`) |
| `currentBorder` | أسود | أصفر | إطار الرحلة الحالية |
| `success` / `warning` / `danger` | أخضر / كهرماني / أحمر | نفسها أفتح | الحالة (`status(level)`) |
| `panel` | زجاج أسود مدخَّن | زجاج كحلي مدخَّن | الزر العائم |

- **المكوّنات** (`ui/Components.kt`): `AppButton` (action / tonal)، `AppCard`، `TripSurface(kind, current)` لكل بطاقة رحلة، `KindLabel`، `ListRow`، `TopBar`، `SectionTitle`.
- **التباين مضمون باختبار**: `ThemeContrastTest` يفحص كل زوج نص/خلفية في المظهرين وفي `DisplayColors` (WCAG: 4.5 للنص، و3 للحدود والنص العريض)، و`checkPanel` يفحص الزر العائم فوق أبيض وأسود وأخضر حديقة وأزرق طريق.
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
- **`app/src/test/.../core/*`**: المنطق الخالص. أهمها `AddressExtractorTest` و`AddressExtractorFuzzTest` و`TripTimesTest` و`TripWatchTest` و`YouDriveCardsTest` و`StreetMatcherTest` و`GeoLogicTest`، ولشاشة الركاب `SmhiForecastTest` و`MapsEtaTest` و`TimeStatusTest`.
- **`app/src/test/.../robo/*`**: Robolectric على أندرويد 13:
  - `UiSmokeRoboTest`: الشاشات، والإعدادات الافتراضية.
  - `FloatingPanelRoboTest`: الزر العائم، والشارع، والسرعة، والنطق.
  - `RouteControllerRoboTest`، `YouDriveRoboTest`، `YouDriveLoginRoboTest`، `StreetServiceRoboTest`، `StreetMapDownloadRoboTest`، `DisplayLinkRoboTest`، `DisplayFeaturesRoboTest`، `TripHistoryRoboTest`.
  - `ScreenshotsRoboTest`: يرسم كل شاشة إلى صورة في `app/build/screenshots/` (للفحص محلياً فقط)، ويرسم صور `testdata/screenshots/`.
- **`ui/theme/ThemeContrastTest`**: تباين الألوان.

---

## 11. قواعد لا تُكسر

1. **الصور** لا تُنسخ ولا تُحفظ.
2. **لا يُحفظ إلا العنوان والوقت ونوع الرحلة واسم الراكب الأول والأخير.** الاسم كاملاً على شاشات السائق فقط. شاشة الركاب تعرض **الاسم الأخير لراكب المحطة التالية فقط**، وتنطقه حين يُضغط عليها فقط. **لا اسم في الإعلانات، ولا في إشعار أو في السجل، ولا يُسجَّل** (`namesStayOnTheDriversScreens`).
   - **الاستثناء الوحيد، بقرار السائق: بطاقة رحلة YouDrive كاملة** (`Stop.card`) تذهب مع المسار إلى بطاقة الرحلة في شاشة الركاب (235)، ولا تُفتح إلا بضغطة السائق على رمز الشخص للرحلة (231 أو 234). التابلت بجانبه والركاب بعيدون خلفه ولا يلمسونه. لا تُنطق أبداً، ولا تظهر في إعلان أو إشعار أو «Previous trips» أو سجل (`theTripCardGoesOnlyToTheDisplay`).
3. **لا سجلات** لعناوين أو نص OCR في release (`DebugLog` فقط). `allowBackup=false`.
4. **موقع الجوال لاسم الشارع والسرعة فقط**: أثناء الاستخدام، لا في الخلفية، لا ينقل المسار، لا يُحفظ ولا يُسجَّل ولا يُرسل. **صفحة YouDrive لا تحصل عليه أبداً.** وموقع التابلت لخريطته فقط (أدناه).
5. **اسم الشارع لا يُخترع**: القسم 5 كله. لا تطابق = لا اسم.
6. **الإنترنت** لصفحة YouDrive، ولتنزيل خريطة الشوارع بضغطة السائق، ولطقس SMHI لمكان ثابت (Karlstad) ما دامت شاشة ركاب تعرض مساراً، فقط. التنزيل والطقس لا يرسلان موقع السيارة. لا Firebase ولا تحليلات ولا تقارير أعطال ولا Hilt.
   - **الوصول إلى الإشعارات** (203، يمنحه السائق) لإشعار الملاحة في Google Maps فقط: تُؤخذ منه الدقائق والمسافة لا غير، ولا يُحفظ شيء ولا يُسجَّل.
   - **خريطة التابلت** (وافق عليها السائق): موقع السيارة من GPS التابلت نفسه (`TabletPosition`، ما دامت شاشة الركاب ظاهرة، لا في الخلفية)، والتابلت يرسله مع المحطة التالية (بلا اسم) إلى Google بمفتاح السائق (208). لا يُحفظ ولا يُسجَّل، والجوال لا يرسل موقعه.
   - **ودجيت الطقس** على التابلت (207): يرسمه تطبيق الطقس، وتطبيقنا يعرضه فقط.
7. **الإعلانات**: كما في القسم 4. **لا اسم راكب في أي إعلان.** شاشة الركاب تنطق الاسم الأخير لراكب المحطة التالية حين يُضغط عليه فقط.
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
