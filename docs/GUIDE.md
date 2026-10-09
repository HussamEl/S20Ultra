<div dir="rtl">

# دليل المشروع — Nästa Stopp

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
| الاختبارات | نحو 280 اختباراً: JUnit للمنطق + Robolectric (أندرويد 13، sdk 33) |
| R8 | **مطفأ** في release (راجع القسم 10) |
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
   - ثم يبدأ تحديد المواقع في الخلفية (`Geocoding.locate`): أولاً في **سجل عناوين Lantmäteriet** على الجوال (`core/geo/AddressRegister`، `assets/addresses.txt` من `tools/make-addresses.py`: عناوين بلدية كارلستاد السارية، شارع ورقم ورمز بريدي ومدينة ونقطة، بلا إنترنت)، ويُقبل العنوان منه فقط إذا طابق رمزه البريدي المكتوب، أو مدينته البريدية المكتوبة (والبلدية المكتوبة فقط إن لم تكن اسم مدينة بريدية وكان العنوان في مدينة واحدة فيها)؛ ثم عبر `Geocoder` النظام، مهلة 15 ثانية لكل عنوان. يُقرأ السجل مرة واحدة في الخلفية عند بدء التطبيق (`Geocoding.register`). عنوان بلا مدينة ولا رمز بريدي يُبحث عنه في Värmland وحدها، ويُقبل فقط في مدينة واحدة (`GeoLogic.inOneTown`).
5. **المراجعة** في `ReviewScreen`: نقل، حذف (مع تراجع)، تعديل، إضافة يدوية، ترتيب حسب الوقت.
6. **البدء** بـ `RouteController.start()`: إعلان أول محطتين، وفتح الخرائط بأول 10 محطات (`MapsLauncher` + `core/route/MapsUrlBuilder`؛ المحطة التي حفظ السائق لها مدخلاً تُعطى بنقطته)، وتشغيل `StreetService` إن سمح السائق بالموقع.
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

- **الكامل** (الافتراضي، الإعداد 136): «Nästa stopp: Klockan åtta noll två. Storgatan 14, Herrhagen, Karlstad. Klockan åtta trettio. Kungsgatan 5, Kronoparken.» (كل محطة بوقت رحلتها قبل عنوانها، والتي بعدها بوقتها بلا أي كلمة قبله، `RouteController.announcementFor` → `Announcements.nextStops`).
- **مكان يُكتب ببلدته أولاً بلا رقم** («Edsvalla centrum»): `AddressExtractor.inNamedTown` يجعل البلدة المكتوبة بلدته، ويسأل المكان فيها ثم البلدة نفسها. `Geocoding.locate` يتابع المرشح التالي حين لا يجيب الـ geocoder، ويرمي `GeocoderFailed` إن لم يجد شيئاً وفشل سؤال، فيعيد `RouteController` المحطة حتى `GEO_TRIES` مرات (`GEO_RETRY_MS` × المحاولة) قبل «لم يوجد».
- **بطاقة الرحلة على الجوال** (`TripCardDialog`، 300): ضغطة ☰ في المراجعة؛ ضغطة مطوّلة على ☰ تسحب (`detectDragGesturesAfterLongPress`). الوقت الثاني باسم YouDrive (`TripCardText.secondLabel`).
- **الوقت يُقرأ كما تُظهره الساعة بالكلمات** (`Announcements.timeWords`/`spokenTime`/`clock`/`at`): 08:02 «Klockan åtta noll två»، 09:30 «Klockan nio trettio»، 09:00 «Klockan nio»؛ بلا «minuter» وبلا صفر قبل الساعة. الكلمات مكتوبة، فكل صوت يقرؤها كما هي.
- **ما تعرضه شاشة الركاب يُنطق خطوات** (`Announcements.isShow`/`steps`): لكل رحلة وقتها ثم عنوانها («Nästa stopp: Klockan … .» ثم «Storgatan 14, … .» ثم «Klockan … .» ثم عنوانها؛ والرحلة المضغوطة على الشاشة، قادمة أو منتهية، `Announcements.shown`: «Klockan sju trettio.» ثم عنوانها). تبدأ كل رحلة بجملة «Klockan <أرقام بالكلمات>.»، فلا تحتاج كلمة قبلها. `Announcer.speak` يضع قبل كل خطوة صمت `Announcements.silenceBefore`: `LEAD_MS` قبل الأولى، `TO_ADDRESS_MS` بين الوقت وعنوانه، `TO_NEXT_MS` قبل الرحلة التالية (`playSilentUtterance`)، فينطق الجوال والتابلت معاً. عند انتهاء كل خطوة يصدر `Announcer.said` رقمها (للإعلان الحالي فقط) لتتبعه شاشة الركاب على الجهاز نفسه.
  - المحطة التالية: الشارع ورقمه، ثم الحي، ثم المدينة (`fullSpokenName`).
  - التي بعدها: الشارع ورقمه، ثم الحي (`thenSpokenName`).
- **الإعدادان 106 / 107**: المحطتان بالحي فقط أو بالمدينة فقط (`spokenName`).
- **لا يُنطق اسم راكب أبداً**: اسم العائلة الذي تضعه بعض القوائم قبل الشارع يُحذف (`streetOf`).
- **أحياء كارلستاد من البلدية نفسها** (`core/geo/Districts`، `assets/karlstad_districts.json`): حدود الأحياء الـ 66 من بيانات بلدية كارلستاد المفتوحة «Stadsdelar» (CC0)، وأسماء الشوارع في كل حي من OpenStreetMap (ODbL)، بلا أرقام بيوت ولا أسماء أشخاص: بداية أرشيف العناوين الخاص بالتطبيق، يصنعه `tools/make-districts.py`. حي المحطة (`Districts.district`): الحي الذي تقع فيه نقطتها إن كان شارعها يمرّ فيه أيضاً؛ وإلا الحي الوحيد لشارعها؛ وإلا في كارلستاد **لا حي** (المدينة وحدها)، فلا يُخمَّن أبداً. حي الـ Geocoder لا يُؤخذ في كارلستاد (أحياؤه كثيراً ما تكون الحي المجاور)، ويبقى خارجها. الحي الرسمي يُقبل كما هو ولو سُمّي باسم شارع («Edsgatan»، `official`). وحي شريط الشارع من موقع السيارة في الحدود نفسها.
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
  - الحي من حدود أحياء كارلستاد (`Districts.at`) إن كانت السيارة فيها، وإلا من الـ Geocoder.
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
    - **لا مدينة بالتخمين**: المكان الذي بلا مدينة ولا رمز بريدي لا يأخذ مدينة بقية الرحلات. `Places.KNOWN` تعرف بعض الأماكن المشهورة (Centralsjukhuset ← Karlstad، فيُسأل عنه «Centralsjukhuset, Karlstad» أولاً). وإلا يبحث `Geocoding.locate` في Värmland وحدها، ويقبل الجواب فقط إن كانت كل نتائجه في مدينة واحدة (`GeoLogic.inOneTown`). والمكتوب برمز بريدي أو مدينة لا يأخذ إلا نتيجة تطابق أحدهما (`GeoLogic.choose`)؛ نقطة في مكان آخر لا تُقبل أبداً؛ وإلا تبقى المحطة «المدينة غير معروفة» (`Stop.townUnknown`، `stop_town_unknown` في المراجعة) حتى يضيف السائق المدينة بالتعديل. `PlaceMemory` (`StoredPlaceMemory`، تفضيلات خاصة بالتطبيق) يتذكر المدينة التي أضافها السائق لمكان بلا رقم بيت، فيأتي بها المرة القادمة (`RouteController.remembered`)؛ لا يحفظ عنوان بيت ولا اسماً.
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
| `route/MapsUrlBuilder.kt` | روابط Google Maps (بلا مفتاح ولا كلفة): الاتجاهات بـ 10 محطات على الأكثر؛ `navigateUrl` لمحطة واحدة (`dir_action=navigate`)؛ `streetViewUrl` (`map_action=pano`)؛ `point(lat, lng)` بست خانات عشرية. |
| `geo/Coordinates.kt` | `parse`: نقطة يلصقها السائق من Google Maps (درجات ودقائق وثوانٍ، أو عشرية بنقطة أو فاصلة، أو رابط)، داخل حدود السويد فقط؛ و`format`. |
| `link/LinkProtocol.kt`، `LinkTargets.kt` | رسائل JSON سطراً سطراً بين الجوال والتابلت، وترتيب الأجهزة المقترنة. |
| `display/DisplaySnapshot.kt` | ما تعرضه شاشة الركاب: `DisplayItem(time, title, subtitle)`، و`weather` و`eta`. `title` شارع ورقم، أو اسم مكان الرعاية القصير («C-Sjukhuset»)، و`said` كيف يُقال حين يختلف («Centralsjukhuset, huvudentrén»). `place` (وجهة خريطة التابلت) يبدأ من الشارع، لا اسم دار رعاية قبله. |
| `display/TimeStatus.kt` | حالة وقت المحطة التالية: ON_TIME / SOON / DUE / LATE / VERY_LATE من الدقائق المتبقية (للون نقطتي الساعة في شاشة الركاب ولكبسولة الزر العائم)، و`countdown` («7:42»، «+3:10»). |
| `weather/SmhiForecast.kt` | رابط SMHI لمكان ثابت (Karlstad)، وقراءة أقرب ساعة (`air_temperature`، `symbol_code`)، و`DisplayWeather` بحالته السويدية ونوع رسمه. |
| `nav/RoutesApi.kt` | طلب Routes API من Google: الطريق من السيارة عبر المحطات بالترتيب (`body`: نقطة كل محطة أو عنوانها، الأخيرة وجهة والباقي `intermediates`، حتى 10، في الفئة الأساسية) وقراءته (`RouteLine`: الدقائق والمسافة والخط و`legs` لكل محطة؛ `to(i)` الدقائق والأمتار حتى المحطة i)؛ وأوقات السفر بين الجميع (`matrixBody`/`parseMatrix`، computeRouteMatrix: من السيارة ومن كل محطة إلى كل محطة؛ مع عنوان بلا نقطة 50 جواباً على الأكثر)؛ وفك خط Google المرمَّز، و`isKey`. |
| `nav/OrderPlanner.kt` | أفضل ترتيب لبضع رحلات (حتى 7، كل الترتيبات): استلام الراكب قبل توصيله (`allowed`، بـ `rider`؛ توصيلٌ بلا استلام قبله راكبٌ في السيارة أصلاً، بقدر ما تزيد توصيلاته على استلاماته: رحلتان للراكب نفسه في اليوم)، ثم أقل دقائق تأخير عن الموعد، ثم الأقصر. `best` لا يُبقي ترتيباً غير مسموح أبداً، والجوال يرفض مثله من التابلت (`RouteController.reorder`). `plan` يحسب وقت الوصول لكل رحلة من أوقات Google، مع `DWELL_SECONDS` (دقيقتان) لكل توقف. |
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
| `route/RouteController.kt` | **مصدر الحقيقة للمسار**: الإضافة والتعديل والحذف والنقل، وstart / next / back / repeat / openMaps / end، ومزامنة YouDrive (`syncTrips`، `insertTrip`، `removeTrip`)، والإعلانات، و`display`. مداخل السائق: `entranceOf`، `setEntrance` (فارغان = حذف؛ `savedAtMs` يبقى و`updatedAtMs` يتجدد)، `clearEntrances`. ما يُعطى لـ Google Maps: `mapsDestination` في الدفعة (المدخل نقطةً إن وُجد، وإلا العنوان نصاً، لأن Google يقود إلى العنوان من جهته المعتادة أدق من نقطة Geocoder لم يتحقق منها أحد)، و`navigateTo` / `streetViewAt` لمحطة واحدة بـ `pointOf` (المدخل، وإلا نقطة العنوان؛ لا بحث باسم). نقطة المدخل هي أيضاً ما ترسمه خريطة التابلت (`DisplayItem.lat/lng`). |
| `route/model/RouteModels.kt` | `Stop`، `RouteData`، `GeoPoint`، `GeoStatus`. |
| `route/RouteRepository.kt` | حفظ المسار وحذفه بعد 12 ساعة. |
| `route/Entrances.kt` | مداخل السائق: `Entrance(address, lat, lng, note, savedAtMs, updatedAtMs)` لكل عنوان (`Stop.entranceKey`: الشارع ورقمه مع الرمز البريدي أو المدينة كما كُتبت، مطويّة؛ بلا اسم). `StoredEntrances` في `noBackupFilesDir/entrances.json` (ملف مؤقت ثم إعادة تسمية)، حتى يحذفها السائق (259). نقطة العنوان من الـ Geocoder تبقى على المحطة كما هي؛ المدخل بجانبها لا مكانها. |
| `route/TripHistory.kt` | «Previous trips»، مع مدة الحفظ (12 ساعة، 24 ساعة، 7 أيام). |
| `route/ExpiryReceiver.kt` | منبّه يحذف البيانات المنتهية. |
| `ocr/OcrEngine.kt` | ML Kit + التقسيم، ومراحل الفشل (`ReadStage`). |
| `importer/ScreenshotImporter.kt` | الصور ← OCR ← الاستخراج. |
| `geo/Geocoding.kt` | `locate` (العنوان ← الإحداثيات: سجل Lantmäteriet أولاً ثم الـ geocoder) و`reverse` (الإحداثيات ← العناوين القريبة). |
| `core/geo/AddressRegister.kt` | سجل عناوين Lantmäteriet (`assets/addresses.txt`): `find` بالشارع والرقم حيث الرمز البريدي أو المدينة المكتوبة فقط. |
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
| `settings/SettingsStore.kt` | كل الإعدادات (`AppSettings`) في SharedPreferences، بلا عناوين. وهو أيضاً `WindowPlaces`: مكان كل نافذة عائمة في شاشة الركاب وحجمها (`window_<name>_x/_y/_scale`). |
| `settings/WindowPlaces.kt` | `WindowPlace` (وسط النافذة كنسبة من عرض الشاشة وارتفاعها، وحجمها `scale` بين `SMALLEST` 0.6 و`LARGEST` 2.2)، و`WindowPlaces` (`InMemory` للاختبارات والمعاينات). |
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
| `Refs.kt` | الأرقام المرجعية (`ref`، `refCorner`) ومعرّفات الاختبار `ref_<n>`. و`RefBounds` لاختبارات الأجهزة: بعد `adb shell setprop log.tag.NastaStoppRefs DEBUG` وإعادة تشغيل التطبيق يسجّل حدود كل عنصر مرقّم على الشاشة (المعرّف والحدود فقط، مرة في الثانية على الأكثر)، لأن UI Automator لا يجد شاشة الركاب ساكنة أبداً؛ ولا يُراقَب شيء ما لم يُفعَّل. |
| `AppRoot.kt` | يوزّع الشاشات، ويطلب الأذونات، ويعرض snackbar، ويوفّر `LocalExplainResources`. |
| `MainViewModel.kt` | مكدس الشاشات (`Screen`)، والاستيراد، وفتح YouDrive أو المراجعة. |
| `screens/*.kt` | كل شاشة في ملف. |
| `screens/RouteMap.kt` + `assets/route_map.html` | خريطة Google على التابلت في WebView خاص (المفتاح 208)، والصفحة لا تحصل على الموقع. **صفحة واحدة وخريطة واحدة طوال عمر الشاشة**: Google يحسب كل خريطة تُنشأ، لا ما يُفعل بها. تغيير الوضع (223) لا يصنع خريطة جديدة: `setLook(night, ground)` يعيد تلوينها (`--ground` و`styles`، وألوان المحطات الليلية `nightColors`، والحدود `drawBorders`). خريطة كل دقيقة مسطّحة للنظر فقط (اللمس لا يصلها). خريطة السائق (`reveal(..., held = true)` → `hold(true)` في الصفحة، بعد ضغطته فقط) هي نفسها تحت لمسه (`gestureHandling: 'greedy'`)، وكل لمسة تبقيها مفتوحة (`onTouch` → `moments.touched()`). **خريطة السائق مسطّحة دائماً**، وثلاثي الأبعاد وصورة الشارع في الصفحة لخريطة الدورة فقط (كل 5 دقائق على الأكثر، انظر «خريطة الدورة»)؛ على خريطته الزران 242 و244 يفتحان تطبيقات Google نفسها (`MapsLauncher.openEarth`، `MapsUrlBuilder.streetViewUrl`). أزرار Kotlin (صغيرة على يسار الخريطة؛ التكبير بالأصابع): `showSatellite(on)` → `satellite(on)` (`hybrid` أو `roadmap` للخريطة نفسها، 270)، `toCar()`، `toStop()`، `whole()` (الطريق كله)، `lookAt(i)` (المحطة i من الطريق). الطريق `setWay({stops, focus, legs, colors, nightColors, times, labels, names})`: المحطات بالترتيب بأحرف A، B، C… (`pinHtml`: نقطة صغيرة على موضعها، وفوقها على ذيل قصير شارة بلونها فيها الحرف والوقت، وتحتها العنوان والاسم الأخير بهالة كأسماء الخريطة؛ المنظور إليها أكبر في حلقة حمراء؛ النصوص تُهرَّب `esc` ولا تُرسل إلى Google)، وخط لكل مرحلة (`legs`؛ حتى المحطة المنظور إليها كامل، وبعدها أفتح) بـ `Polyline`؛ المراحل التي تشترك في شارع (`besides`: ثلاثة مربعات ~50 م مشتركة) أرفع ومُزاحة `APART_PX` بكسل جنباً إلى جنب (`shifted`، الاتجاه لا يغيّر الجهة) وتُعاد مع التكبير. المحطات `Dot` بـ HTML، والسيارة نقطة زرقاء. الحدود (`__BORDERS__` ← `assets/boundaries.json` من `tools/make-boundaries.py`: مناطق RegSO من SCB، CC0، للمحافظتين 17 و18، مبسّطة، كل حد مرة واحدة بأعلى مستوى) خطوط متقطعة رفيعة جداً (`icons` بلا خط)، الأحياء من التكبير 12 (`DISTRICTS_FROM_ZOOM`). `legs: null` يرسم المحطات فوراً بترتيبها الجديد حتى يأتي الطريق. `clear()` حشوة `fitBounds`: للخريطة الممسوكة يسار عريض لقائمة الرحلات (`LIST_ROOM`) ويمين للأزرار، وإلا أسفل للدقائق. `setCar` من Kotlin بموقع التابلت (`MapWay` من `geo/TabletPosition`)، و`show(way)` بالمحطة التالية والاثنتين بعدها لخريطة كل دقيقة، و`focus(stops, at)` لطريق السائق؛ وطريق خريطة كل دقيقة لا يُطلب ثانية للمحطات نفسها بترتيبها (`keyOf`) إلا بعد 4 دقائق (`REFRESH_MS`) **و**تحرّك السيارة 1.5 كم (`MOVED_M`)؛ والطرق المعروفة (`known`، 12، في الذاكرة فقط) تُستعمل بلا سؤال Google ما دامت أحدث من 15 دقيقة (`KNOWN_MS`) والسيارة ضمن 1 كم من مكان طلبها (`REUSE_M`): فتح محطة مرة ثانية أو تجريب ترتيب جُرِّب لا يكلّف شيئاً. `route` و`routeKey` يقولان لأي ترتيب هذا الطريق. `suggest(stops, trips, now)` يسأل computeRouteMatrix ثم `OrderPlanner.best` → `suggestion`. الصفحة نفسها تُظهر الخريطة وتخفيها (`reveal(x, y, ms)` و`conceal(ms)`: تكبير وشفافية بـ CSS، والحواف الذائبة بتدرّجات بلون الخلفية)، ولا يُكبَّر الـ WebView ولا يُشفَّف من Compose أبداً، لأن WebView مرسوماً هكذا قد يبقى فارغاً. تُنشأ الخريطة حين يصير لعنصرها حجم (`importLibrary('maps')`)، وسكربت Google يُعاد طلبه كل 30 ثانية ما دام لا يأتي (لا إنترنت بعد). ما يوقفها يصل عبر الجسر: `onProblem` (السكربت لم يُحمَّل، الصور لم تأتِ خلال `TILES_WAIT_MS`، خطأ في الصفحة) → `trouble`، و`onTiles` → `tiles`، وردّ Routes API إن لم يعطِ طريقاً → `routeAnswer`. لا شيء من ذلك يُسجَّل. |
| `screens/FloatingWindow.kt` | نافذة عائمة ينقلها السائق ويغيّر حجمها كأي نافذة (بطاقة الرحلة 235، قائمة الرحلات): `WindowState` (المكان `place`: المنتصف والعرض والطول نسباً من الشاشة، 0 = بحجم محتواها؛ `moveBy` لا يُخرجها من الشاشة؛ `resizeBy(side, by)`: الحافة الجانبية تغيّر العرض وحده، والعليا أو السفلى الطول وحده، والزاوية الاثنين، والحافة المقابلة ثابتة، بين `WindowPlace.SMALLEST` من الشاشة وكلها؛ `zoomBy` بإصبعين للاثنين معاً؛ `keep` يحفظها في `WindowPlaces`)، و`FloatingWindow` (`Layout` يقيس المحتوى بالحجم المحفوظ بالضبط، فيُعاد ترتيبه بلا تكبير والنص بحجمه؛ إطار `EDGE` بلا علامات حوله: `resizes`، `Side`؛ إصبعان في أي مكان منها ينقلانها ويكبّرانها عبر `PointerEventPass.Initial` ولا يُمسّ لمس الإصبع الواحد)، و`Modifier.movesWindow` (شريطها). |
| `screens/WayList.kt` | قائمة رحلات طريق السائق الصغيرة على خريطته (245–251، 260)، نافذة عائمة (`FloatingWindow`: شريطها 265، إطارها 266؛ تبلّغ الخريطة بمكانها عبر `RouteMap.listAt` فيبتعد الطريق عن جهتها)، و`WayEdit` (الترتيب المجرَّب `preview`، المختارة `picked` وهي المنظور إليها `lookedIndex`، السحب `dragging`، المرسَل `sent`، رأي Google `advice`؛ `move`، و`settle` تضع الرحلة المضافة في مكانها من الترتيب المجرَّب). كل سطر بحرفه في لون محطته (`AppColors.wayStops`)، و× (268) يزيل الرحلة من طريق هذه الخريطة فقط (`removed` في `PassengerDisplayScreen`)، و«+ سابقة» (269) يضيف التي قبل الطريق (`DisplaySnapshot.around(…, earlier)`). السحب من المقبض فوراً أو من السطر بعد ضغطة مطوّلة، بـ `ReorderState` (كقائمة المراجعة): إزاحة السطر المسحوب تُحسب من تخطيط القائمة الحالي (`offset`) فلا يقفز حين يتبادل مكانه، والأسطر الأخرى تنزلق (`animateItem`). |
| `screens/HostedWidget.kt` | عرض ودجيت الطقس بحجم مكانه. |

### الموارد
- **النصوص**: `values/strings.xml` = **العربية (الافتراضية)**، `values-en/` = الإنجليزية (لغة الواجهة الافتراضية)، `values-sv/` = السويدية. **كل مفتاح في الملفات الثلاثة.** `strings_fixed.xml` لما لا يُترجم.
- **الأيقونات**: `res/drawable/ic_*.xml` (vector). أيقونة التطبيق: `ic_launcher_background` (تدرّج أصفر) + `ic_launcher_foreground` (دبوس + سهم) + `ic_launcher_monochrome`.
- **xml**: `backup_rules` و`data_extraction_rules` (تستثني كل شيء)، و`locales_config`، و`network_security_config`.
- **`values/ids.xml`**: معرّفات أجزاء الزر العائم `ref_<n>`.

---

## 8. الزر العائم وشاشة الركاب

### الزر العائم (`overlay/OverlayManager.kt`)
- **الزر الكامل Compose** (`overlay/FloatingPanel.kt`) في نافذة `TYPE_APPLICATION_OVERLAY`: `ComposeView` داخل `PanelFrame`، و`PanelOwner` يعطي النافذة دورة حياة (Lifecycle وSavedState وViewModelStore) لأنها بلا Activity. بشكل شاشة الركاب: `DisplayTheme(dark = true)`، `DisplayFont` و`DigitFont`، والوقت `TimeFace` نفسه (مع `PersonGlyph` و`statusColor` و`ThenLabel` من `PassengerDisplayScreen`، `internal`). من اليسار إلى اليمين دائماً.
- **النافذة كأي نافذة** (`PanelFrame`): هامش شفاف `EDGE_DP` حولها، إصبع فيه يغيّر الحجم من تلك الجهة (`onInterceptTouchEvent` بـ `rawX/rawY`: الجانبية العرض وحده، العليا أو السفلى الطول وحده، الزاوية الاثنين، والجهة المقابلة ثابتة، بين `PANEL_MIN_*` والشاشة)؛ إصبع يتحرك على الشريط العلوي (`PanelActions.barAt` يعطي مستطيله) ينقلها؛ كل ما عدا ذلك للزر نفسه. الحجم يُحفظ (`SettingsStore.overlaySize`) ومعه `sized`: بلا حجم محفوظ تكون النافذة بطول محتواها، ومعه تملؤها (وقد تكون أكبر منه، والمحتوى يُمرَّر).
- **الأجزاء** (أرقامها في README): الشريط (الشاشة المتصلة 279، الساعة 8 → `source.sayTime()`، رقم الرحلة 9، البيت 278 ينبض، التصغير 6، الإغلاق 7)؛ صف الشارع (السرعة 15، الشارع 2 مع الحي 3 وزر النطق 4)؛ الرحلة المعروضة (`Hero`: رمز الشخص، الوقت 10 بشكل الساعة بحجم عرض النافذة، المدينة والحي 19، العنوان 13 بـ autoSize: ضغطة = `sayStop()` للتالية أو `Announcements.at` لغيرها، ضغطة مطوّلة = `Remote.OPEN_MAP`؛ الاسم 18 من `source.fullName`، والحالة 11)؛ سطر الرحلات 277 (`LazyRow` بكل الرحلات، ضغطة أو سحب يستقر = عرضها فوق و`Remote.SHOW_TRIP`، والعودة بعد `BROWSE_RETURN_MS`)؛ Back 1 وNext 5؛ وقسم خريطة شاشة الركاب 296 ما دام `mapView.open` (`MapPart`: 280–286 والقائمة 287–295، كلها `Remote`).
- **الكبسولة Views** في نافذة overlay أيضاً: زجاج مدخَّن (`glass()`) بلا ألوان خاصة: `PanelColors` يحوّل أدوار `AppColors.panel`.
  - الكبسولة بعد التصغير 17: عدّ تنازلي إلى وقت الرحلة بشكل الساعة (`TimeStatus.countdown` يعيد `Countdown(hours, minutes, seconds)`، كل ثانية): الساعات متوسطة (من ساعة فأكثر، ثم «:»)، والدقائق كبيرة، والثواني صغيرة على يمينها بلا نقطتين، على خط أساس واحد وبخط الزر المعتاد (بلا نقطة حالة). لون الأرقام والإطار من `TimeStatus` (`PanelRoles.status`)، والإطار ينبض (`ValueAnimator`) عند DUE وVERY_LATE. العدّ على زجاج أعمق (`well`) ليُقرأ فوق أي خريطة. وعلى جانبيها سهمان رفيعان شفافان عائمان (`Chevron`: ‹ 12 = `source.back()`، › 14 = `source.next()`، ضغطة مطوّلة = `repeat()`؛ `CHEVRON_ALPHA`) بهالة داكنة (`PanelRoles.halo`) بلا زجاج؛ الكل يُسحب لتحريك الزر. على التابلت `bubbleScale = 2`، و`swellLastMinute`: في آخر 60 ثانية تتضخّم الكبسولة مع سهميها إلى الضعف (`sizeBubble`) وتومض، ثم تعود.
- **المصدر** (`PanelSource`): `OverlayManager(context, source, settings, scope, wanted)`.
  - الجوال: `graph.overlay` بـ `RoutePanelSource` (المسار نفسه و`controller.display`، والشارع والسرعة من `CurrentStreet`، واسم الراكب الكامل `fullName`، والتحكم بشاشة الركاب عبر `DisplayLinkServer`: `remote()` و`mapView` و`displayName`)، ويظهر إن لم يكن الجهاز تابلتاً.
  - التابلت: `graph.tabletPanel` بـ `LinkPanelSource`، ويظهر فقط في دور شاشة الركاب مع المفتاح 206 (`AppSettings.tabletPanel`) وما دام الاتصال قائماً والمسار نشطاً. الرحلة من `DisplaySnapshot` (بلا اسم)، ولا شريط شارع ولا سرعة (`street == null`). Next وBack والإعادة تُرسل `LinkMessage.Command` إلى الجوال، و`DisplayLinkServer.carryOut` ينفّذها بـ `controller`. والترتيب الذي يضعه السائق على خريطة التابلت يصل `LinkMessage.Order` (أرقام الرحلات فقط) فينفّذه `controller.reorder`. ضغطة شارع المحطة تنطقه على التابلت نفسه.
  - `wanted` يفصل الاثنين، فلا يلمس أحدهما إشعار الآخر.
- **السلوك**:
  - الكبسولة تُسحب من أي مكان، والزر الكامل من شريطه؛ المكان يُحفظ.
  - أعلى البطاقة يبقى 100 dp على الأقل تحت أعلى الشاشة، والهامش الشفاف حول البطاقة صغير (2 dp فوقها، 6 بجانبها، 12 تحتها للظل)، لأن النافذة تأخذ كل ضغطة في مستطيلها: فلا تغطي الشريط العلوي للتطبيق ولا شريط الاتجاهات في الخرائط.
  - بعد الإغلاق أثناء المسار: إشعار صامت، ومربع الإعدادات السريعة، والمفاتيح تعيده.
  - يغيب ما دامت شاشة الركاب ظاهرة على الجوال (`suppress`).
  - ويغيب ما دامت نافذة حوار أو قائمة من التطبيق مفتوحة (`MainActivity.onWindowFocusChanged`: التطبيق في المقدمة وفقد التركيز)، كي لا يغطّي أزرارها.
- **للاختبار**: أجزاء الزر الكامل `ref_<n>` (Compose، `testTagsAsResourceId`)، وأجزاء الكبسولة `id/ref_<n>`؛ ومع `setprop log.tag.NastaStoppRefs DEBUG` تُسجَّل حدود الأجزاء على الشاشة فقط. في Robolectric تُرسم نافذة الزر بـ Compose، فتجد `FloatingPanelRoboTest` أجزاءها بقاعدة compose.
- **اللحظات** (`rememberMoments`): الوقت ثم الطقس ثم الخريطة (أو الوقت المتبقي) بالتناوب، كل واحدة بعد `MOMENT_GAP_S` (50) ثانية من انتهاء السابقة، تُعدّ فقط ما دام لا شيء يُعرض ولا يُنطق والشاشة غير مشغولة والسيارة تتحرك (`awake`). الخريطة `MAP_HOLD_MS` (نحو 40 ثانية). `awake` من `geo/CarMotion` (حساس التسارع `SENSOR_DELAY_UI` ما دامت الشاشة ظاهرة، وسرعة `TabletPosition`) عبر `core/display/CarStillness` (اهتزاز حول الجاذبية مُنعَّم، `MOVING_SHAKE`، أو سرعة ≥ `MOVING_SPEED`؛ دقيقتان `STILL_AFTER_MS`). `MotionSign` (297) خط عمودي يتموّج بقدر `level`. رموز السطر الأعلى في `AnimatedVisibility` تظهر بضغطة أو سحب للأسفل على السطر (298) مدة `TOP_SHOWN_MS`.
- **لا خريطة في اللحظات**: الخريطة لا تُطلب إلا حين يفتحها السائق (219، ضغط مطوّل)؛ `rememberMoments` يتناوب الوقت والطقس والوقت المتبقي، و`MapLayer` يكشفها للسائق فقط (`tour = false`).
- **التحكم بشاشة الركاب** (`LinkMessage.Remote` من الجوال، `LinkMessage.MapView` من التابلت): `DisplayLinkServer.remote()` يرسل إلى كل شاشة متصلة، و`mapView` آخر ما قالته خريطتها (يُمسح حين لا تبقى شاشة). على التابلت `DisplayLinkClient.remotes` → `PassengerDisplayScreen(remote, onMapView)`: كل `Remote` يُنفَّذ كضغطة هناك (`SHOW_TRIP` → `showCall` فيقلب `Stage` إلى الرحلة؛ `OPEN_MAP` → `showWay`؛ أزرار الخريطة على `RouteMap` و`moments.touched()`؛ `TRY_ORDER` يقبل الرحلات نفسها فقط؛ `APPLY` كـ «اعتمد» مع شرط الاستلام قبل التوصيل؛ `SUGGEST` بـ `suggestOrder`؛ `SAY_TIME` كضغطة الساعة)، و`MapView` يُرسل كلما تغيّر ما تعرضه الخريطة (`DisplayLinkClient.mapView` لا يكرر المرسَل). رسالة من نوع لا يعرفه جهاز تُتجاهل، فالنسخ القديمة لا تنكسر.

### شاشة الركاب (`link/*`، `ui/screens/PassengerDisplayScreen.kt`، `DisplayRoleScreen.kt`)
- البلوتوث RFCOMM بين أجهزة مقترنة. الجوال يستمع على قناة آمنة وقناة احتياطية، ويخدم الأجهزة المقترنة به فقط، ولا يرسل شيئاً قبل أن يقول التابلت أولاً إنه شاشة ركاب بالنسخة نفسها من الاتصال (`LinkProtocol.isDisplayHello`، خلال `HELLO_MS` 10 ثوانٍ، وإلا يُغلق). والتابلت لا يعدّ الاتصال قائماً (ولا يحفظ الجهاز ولا يأخذ رسائله) قبل أن يجيب الجوال بأنه جهاز تحكم بالنسخة نفسها (`isControllerHello`)؛ ورسالة تُقرأ بعد إيقاف الاتصال لا تُؤخذ.
- التابلت (`DisplayLinkClient`): إن انقطع الاتصال يبقى آخر ما وصل `STALE_MS` (دقيقتين) ثم يُمسح؛ ويُمسح فوراً عند `stop` (الخروج من الشاشة أو اختيار جهاز آخر) أو الاتصال بجوال آخر (`snapshotFrom`). خريطة الدقيقة لا تُطلب إلا والاتصال قائم، وبطاقة الرحلة تُغلق عند الانقطاع.
- التابلت يجد الجوال وحده: آخر جهاز أولاً، ثم الجوالات، ثم التابلتات والحواسيب. السماعات وأنظمة السيارة تُتخطّى.
- يُرسل `DisplaySnapshot` فقط: حتى 7 رحلات منتهية (`earlier`، و`previous` آخرها)، والحالية، وحتى 7 قادمة. الوقت، والشارع مع الرقم، والحي تحته (الإعداد 114؛ مطفأً = الحي فقط)، ونوع الرحلة (`kind`، لشريط الزر العائم على التابلت)، وعلامتا الانتهاء (`doneInYouDrive`، `doneHere`)، والعنوان والنقطة التي ترسم إليها خريطة التابلت (`place`، `lat`/`lng`)، و**الاسم الأخير لراكب كل رحلة قادمة** (`lastName`؛ لا للمنتهية: للخريطة، ولرمز المحطة التالية)، ورقم الرحلة في المسار (`id`) ورقم يجمع استلام الراكب وتوصيله (`rider`، من ترتيب ظهور الاسم، لا الاسم نفسه). لا اسم أول ولا اسم رحلة منتهية، ولا موقع الجوال. ولا يُرسل التابلت إلى الجوال إلا الزر الذي ضُغط في زره العائم (`LinkMessage.Command`: NEXT / BACK / REPEAT) والترتيب الذي وضعه السائق على خريطته (`LinkMessage.Order`: أرقام الرحلات). `RouteController.reorder` يضع هذه الرحلات في الأماكن التي تشغلها الآن بالترتيب الجديد، ويترك غيرها؛ ومع مسار نشط يُعيد الإعلان إن تغيّرت المحطة التالية أو التي بعدها، ويفتح Maps من جديد (`openMaps(fromBackground = true)`) إن تغيّرت أول 10. مفتاح الانتقال المشترك `DisplayItem.trip` (الرحلة بلا علاماتها وبلا الاسم) كي لا تتغيّر هويتها حين تُعلَّم أو تصير المحطة التالية.
- إعادة الاتصال كل 3 ثوانٍ، وping كل 10 ثوانٍ، وقطع الاتصال الصامت بعد 30 ثانية، وسطر أطول من 64 KB يقطع الاتصال.
- يحمل الـ snapshot أيضاً، ما دام المسار نشطاً، **الطقس** (`DisplayWeather`: الحرارة ورمز SMHI) و**الوقت المتبقي** (`DisplayEta`: الدقائق والمسافة). الجوال وحده يجلبهما.
- **التابلت ينطق كل إعلان** يصدره الجوال عند ضغط Next (مفعّل افتراضياً، ويُطفأ بالمفتاح 198 في شاشة إعداد التابلت). لا زر صوت: ضغطة عنوان المحطة التالية تعيد الإعلان على الجهاز نفسه.
- **الترتيب للركاب**: السطر الأعلى صغير: الاتصال (`ConnectionSign`) في الزاوية العليا اليسرى، والرموز (`TopLine`) والبطارية في اليمنى. تحته مباشرة تبدأ المحطة التالية: سطر `HeroLine` (رمز الشخص 231، ووقتها 230 بشكل الساعة `TimeFace` بلا ثوانٍ، والمدينة والحي 92 على يمينه)، ثم الشارع والرقم بأكبر حجم يتسع في `TITLE_ROOM` من الارتفاع. `StopHero` يقيس العنوان أولاً (`Layout`)، ويعطي الوقت ما يبقى فوقه: `HERO_TIME_FILL` من ذلك الارتفاع، وحتى `HERO_TIME_WIDTH` من العرض (`timeEms`)، بين `HERO_TIME_MIN` و`HERO_TIME_MAX`. الوقت لا يتكرر في الصفحة: لا وقت كبير آخر تحت العنوان في عرض التركيز. في الأسفل سطر واحد من اليسار: الساعة في الزاوية السفلى اليسرى، ثم أوقات المحطة التالية والثلاث بعدها بينها أسهم التدفّق (`ComingTrip`: 299، 94، 229، 272). `Stage` يبلّغ بـ `onShown` عن الرحلة المعروضة في الأعلى مع المحطة التالية التي تنتمي إليها، وهل الشاشة في عرض التركيز. الشاشة للعرض الأفقي فقط (لا تُضبط المقاسات للطولي). كل عنصر بحجم محتواه فقط: لا صناديق فارغة تدفع ما حولها. الشاشة سوداء حتى الحافة: `AppRoot` لا يضع `safeDrawingPadding` لشاشتي العرض، و`PassengerDisplayScreen` يلوّن كل الشاشة ثم يُبعد محتواه عن فتحة الكاميرا (وشاشة الإعداد في `DisplayRoleScreen` تضع الهامش بنفسها). كلمات الركاب بالسويدية مثل الإعلانات (`strings_fixed.xml`)، وسطر الاتصال بلغة التطبيق.
- **الحركة**:
  - `Stage`: `AnimatedContent` مفتاحه المحطة الحالية: المحطة المغادَرة تختفي كلها (`OUT_MS`) قبل أن يظهر شيء من الجديدة، ثم تنبثق (`pop`: `Animatable` على الـ pager، شفافية وحجم من `POP_FROM` بمنحنى يتجاوز قليلاً ويعود، `POP_IN`)، ويعود السطر الأسفل (`thenShown`) بعد ذلك. السطر الأسفل: أوقات المحطة التالية (299، ضغطتها `onSpeakNext`) والثلاث بعدها (94، 229، 272)، حتى `COMING_MOST` 4 بقدر ما يتسع، موزّعة (`SpaceEvenly`) وبينها أسهم التدفّق.
  - الشاشة كلها من اليسار إلى اليمين (`LocalLayoutDirection` = Ltr) مهما كانت لغة التطبيق.
  - `spoken` يزيد مع كل إعلان (من `controller.announcements` على الجوال، ومن `DisplayLinkClient.announcements` على التابلت، ومع ضغطة عنوان المحطة التالية). `Spotlight.play` يتبع الإعلان خطوة خطوة (`bring`: `pop` → 0 أو انتظار `OUT_MS`، ثم الصفحة و`pop` → 1 بـ`POP_IN`؛ ثم `perform`؛ ثم `clearAway` وصفحة المحطة التي بعدها وانبثاقها و`perform`؛ ثم الهوم). الانتظار يتبع `voice` (للإعلانات: `Announcer.said` للجهاز الذي ينطقها) و`ownVoice` (لما يُضغط على الجهاز نفسه) مع حد `VOICE_SLACK_MS`، وإلا يقدّر من طول النص (`msToSayPart`، 75 ms للحرف). المجموعات تطابق صمت الصوت (التعليق فوق ثوابت `M2_…`). `Spotlight.playing` يبقى صحيحاً حتى نهاية آخر تشغيل (عدّاد `runs`)، و`quiet` للعرض الصامت في الهوم؛ حين يصير خطأ تُعاد `Show` وتعود الصفحة (`pop` → 1، M8) والخلفية (`ground` → 0).
  - **العرض الموحّد** (`Show`، README «الحركة الموحّدة للعناوين الكبيرة»، M1–M8؛ ثوابت `M2_…`–`M7_…` بأرقامها): `StopHero` يضع الساعة (`HeroLine`) في الأعلى والعنوان تحتها أسطراً (`addressLines`: الشارع **بلا رقم البناء**، أول كلمة ثم الباقي، `AddressLine` لكل سطر؛ الرقم يُنطق فقط، و`shownAddress` للشارع في سطر واحد في الشريط وغيره)، ويحفظ مكانيهما في `HeroSpots` لكل صفحة (`spotsOf` في `Stage`). `perform` (في `Stage`): `growthOf` يحسب الطريق إلى وسط `screen` والتكبير حتى يملأها؛ `clockMotion` (M2 يكبر بلون من `DisplayColors.showHues` وكلمته `TimeWord` «NÄSTA» للمحطة التالية فقط، M3 الكلمة تكبر `M3_WORD` ثم ترجع بارتداد ناعم `M3_WORD_BACK` عبر `Show.word`، M4 انسحاب) و`addressMotion` (M5 تكبير انسيابي `FastOutSlowInEasing`، M6 ثبات بلا حركة، M7 انسحاب) في `graphicsLayer` تُقرأ عند الرسم فقط. لا لهب ولا توهّج ولا نبض ولا انقلاب. الخلفية `ground` على مستوى الشاشة (`drawBehind`، إلى `DisplayColors.background`). في الهوم: حلقة كل `IDLE_TICK_MS` تعرض المحطة التالية بـ`spotlight.play(quiet = true)` بعد `IDLE_FIRST_MS` ثم `IDLE_REST_MS` من آخر عرض، ما دام `canIdle` (اللحظات خاملة، لا بطاقة ولا خريطة)؛ و`Moments.held` يجعل اللحظة تنتظرها دون أن تخسر دورها.
  - **سجل العناوين** (`route/AddressLog`): `AppGraph` يسجّل `RouteController.logLine` لكل محطة مع كل تغيّر للمسار (مرة في اليوم لكل عنوان)، في `noBackupFilesDir`؛ الإعدادات 302 (مشاركة عبر `ACTION_SEND` بضغطة السائق) و303 (حذف).
  - **المتاجر** (`Places.shopName`): تُنطق باسمها المختصر وتُكتب كاملة (`RouteController.saidStreet`، `displayTitle`).
  - **الخريطة**: المحطات المتقاربة تتباعد (`spread()` في `route_map.html`: `APART_PX`، `OUT_PX`، خط `.lead` ونقطة `.meet`)؛ المنظور إليها: وقتها أحمر ينبض ومؤشر `.mark` أحمر ينبض فوقها؛ وفي القوائم `WayTime`.
  - **عرض التركيز**: ما دام `Spotlight.playing` (إعلان أو رحلة مضغوطة أو عرض الهوم الصامت)، `Stage` يبلّغ `onShown(next, inMiddle, focus = true, quiet)`: يتلاشى سطر الساعة (`line`، `CLOCK_FADE_MS`، وتُنزع ضغطة الساعة) ورموز السطر العلوي (`chrome`، و`TopLine(enabled = false)`) والنقاط والسطر الأسفل (`thenShown`، بعد `STRIP_FADE_DELAY_MS` لما ضُغط فيه). زر البيت لا يظهر للعرض الصامت.
  - **ضغطة وقت في السطر الأسفل أو رحلة في الشريط** (`showCard`): الوقت يكبر في مكانه (`CARD_TAP_SWELL` 1.22)، والشريط يُطوى (`pick`)، ثم `onSay` فوراً (الصمت `LEAD_MS` في `Announcer`) و`bring` و`perform` كالإعلان، ثم الهوم. المحطة التالية تعيد الإعلان (`onSpeakNext`). تُنطق بـ `Announcements.shown(time, name)`: «Klockan åtta noll fem. Hamngatan 7, Skoghall.»، قادمة أو منتهية، بلا كلمة قبل الوقت. ضغطة عنوان صفحة غير التالية تنطقها فقط.
  - **التصفّح**: `HorizontalPager` على التابلت و`VerticalPager` على الجوال: `earlier` ثم المحطة التالية (الصفحة `home` = عدد المنتهية، `StopHero(HeroRole.NEXT)` بمفتاح الانتقال) ثم `upcoming`، والباقي بلا مفتاح (كي لا يتكرر مع البطاقة). وقت كل صفحة تحت عنوانها (`StopHero(showTime)`، 230) ما دامت غير المحطة التالية أو أثناء سحب الـ pager أو مع الشريط، بحجم `PAGE_TIME_FILL` مما بقي تحت العنوان ومرسوماً بحجمه؛ ووقت المحطة التالية بجانب الساعة لا يتغيّر مع التصفّح، ويقفز عند Next فقط (`PAGE_POP` 1.5 ثم spring). `HomeButton` (200) صغير عائم في الزاوية العليا اليسرى من السطر الأعلى، يظهر مع التصفّح أو الشريط أو عرض التركيز؛ ضغطته تزيد `homeCalls`، و`Stage` يوقف `Spotlight` (`stop`) ويعود (`goHome`)، فيختفي الزر. وتعود المحطة التالية أيضاً عند أي إعلان أو بعد 30 ثانية دون لمس (`BROWSE_RETURN_MS`). لا يلمس المسار أبداً. `PageDots` (201) عائمة في أسفل الشاشة تماماً وفي منتصفها (`DOTS_LOW`)، لا تأخذ ارتفاعاً.
  - **شريط كل الرحلات** (`Browse`، `TripStrip`، 226): `LazyListState` واحد على مستوى الشاشة، وسطر الساعة والأوقات فيه تحمل `Modifier.scrollable` على الحالة نفسها مع `browse.drags` و`snap` (`rememberSnapFlingBehavior` بـ `SnapPosition.Center`)، فأول سحب يُخرج الشريط ويحرّكه. `open` ما دام السحب (`collectIsDraggedAsState` لسطر الأسفل وللشريط) أو الانزلاق بعده، ثم `BROWSE_LINGER_MS` (3 ثوانٍ) ثم `homeCalls`. وهو مفتوح يحلّ مكان السطر الأسفل (ذلك السطر يتلاشى، ولا يأخذ اللمس إلا للسحب الذي أخرجه)، ويتوقف سحب الـ pager. كل رحلة بعرض `STRIP_TRIP_SHARE`، وحشوة جانبية بنصف الباقي كي تقف أي رحلة في الوسط؛ الرحلة في الوسط (`middle`، الأقرب إلى منتصف `layoutInfo`) مضيئة وأكبر (`STRIP_LIT_SCALE`)، والـ pager يتبعها (`animateScrollToPage`) بعد أول قياس جديد للشريط (`following`، كي لا يقفز إلى وسطه القديم). الصفحات نفسها: المنتهية ثم المحطة التالية («NÄSTA STOPP»، `passenger_next_stop`) ثم القادمة بلا كلمة فوقها، لكلٍّ `StripTrip`: الوقت والشارع (بلا رقم) والحي. يُرسم في المحتوى الأحدث فقط من `AnimatedContent` (`latest`) كي لا يتشارك شريطان الحالة، ويُطوى عند تغيّر المحطة، ويعود بعد الطيّ إلى المحطة التالية في وسطه (`requestScrollToItem`).
  - **السطر الأسفل**: `Row` في الزاوية السفلى اليسرى فوق كل شيء: `Clock` وحدها؛ كل سطر أرقام بارتفاع حبره فقط (`ink`: يُقصّ ما فوق الأرقام `DIGIT_ASCENT - DIGIT_HEIGHT` وما تحتها `DIGIT_DESCENT`)، والثواني معلّقة تحت الدقائق لا تأخذ ارتفاعاً (تحتها `SECONDS_CLEAR` إلى الحافة). ارتفاعه وعرضه (`onSizeChanged`) يمرّان إلى `Stage`: `ThenLine` (`Layout` خاص) يحجز هذا الارتفاع أسفل الصفحات ويعطي ما بقي من العرض بعد الساعة لمحتواه في منتصفه: أوقات المحطة التالية والرحلات بعدها بقدر ما يتسع (`COMING_MOST` 4، كلٌّ `COMING_MIN_WIDTH` 96dp على الأقل)، موزّعة على السطر، والسحب في أي مكان منه يُخرج الشريط. `ComingTrip`: الوقت فقط بـ`LineTime` (الساعات بحجمه بفرشاة من `highlight` إلى `accent` أفقياً؛ النقطتان؛ الدقائق بيضاء `LINE_MINUTE_SHARE` 0.85 منها؛ كلاهما `FontWeight.Normal`)، بحجم `COMING_SP` × `COMING_TIME_SHARE` للمحطة التالية و`COMING_LATER` لما بعدها، ثم شريط الحالة (`TimeStatus.of` لوقتها مع `nowMinutes`، بألوان `statusColor`) وعلامات الانتهاء؛ بلا عنوان ولا كلمة فوقه. قبل كل وقت سهم: الأول (227، من الساعة) `FIRST_ARROW` مرة، والباقي `COMING_LATER`. السهم أصفر (`accent`) فاقع (`ARROW_LOW` 0.35 → `ARROW_HIGH` 1)، علامتان ›، وضوء يمرّ عليه (`ARROW_FLOW_MS`، حلقة إطارات واحدة `withInfiniteAnimationFrameMillis` عبر `LocalArrowPhase`، تُقرأ عند الرسم فقط). `ConnectionSign` في الزاوية العليا اليسرى: نقطة الاتصال واسم الجوال (87، `labelMedium`؛ `DisplayRoleScreen` يقصّه إلى 10 أحرف، `NAME_CHARS`؛ ضغطته تفتح `DropdownMenu` فيه «خروج» 86 → `onExit`، ولا × منفصل؛ على الجوال بلا اتصال يُغلق العرض بزر الرجوع). `TopLine` في الزاوية العليا اليمنى، رموزه `TOP_ICON`: `MapSign` (219: الدبوس وحده، ضغطته `showWay` للمحطة التالية؛ يظهر حين يوجد `routeMap`)، و`WeatherSign` (222: `WeatherGlyph` والحرارة؛ ضغطته `moments.playInfo(WEATHER)`؛ يظهر إن وُجد طقس SMHI أو ودجيت)، وزر الوضع (223)، و`BatterySign` (271: بث `ACTION_BATTERY_CHANGED` الثابت، بلا إذن؛ رسم `Canvas` بقدر الشحن والنسبة، أخضر أثناء الشحن، أحمر حتى `BATTERY_LOW`). زر البيت (`HomeButton`، 200) صغير (`HOME_SIZE` في لمسة `HOME_TOUCH`) ينبض (`HOME_BEAT`) على يسار نقاط التصفّح (`BesideDots`: النقاط في المنتصف والزر بجانبها).
  - ضغطة رحلة أو عنوان صفحة: `Announcements.at`. ضغطة الساعة: `Announcements.clock` («Klockan är åtta noll fem»). الاثنتان عبر `onSay` على الجهاز نفسه (`graph.announcer`).
  - `Clock`: بلا إطار؛ الساعات متوسطة (`HOUR_SHARE`) ومنتصفها بمستوى منتصف الدقائق، ثم النقطتان (`Colon`: دائرتان مرسومتان فوق بعضهما تماماً في منتصف ارتفاع الأرقام، `COLON_DOT` و`COLON_SPREAD`، وعلى جانبيهما مسافة `COLON_SIDE`) بمستواها، ثم الدقائق كبيرة، والثواني صغيرة (`SECOND_SHARE`) بخط Light أزرق تحت الدقائق بمسافة `SECONDS_DROP`، معلّقة لا تأخذ ارتفاعاً؛ كل رقم منها `AnimatedContent` خاص (`RollingDigits`) يتدحرج كالعدّاد: الجديد يصعد من أسفل خانته والقديم يخرج من أعلاها (`ROLL_MS`، مقصوصاً في خانته). كل سطر أرقام صندوقه بارتفاع صعود الخط ونزوله (`DIGIT_ASCENT`، `DIGIT_DESCENT`)، ومنه تُحسب الإزاحات. الأرقام بلا حدّ؛ حالة وقت المحطة التالية في لون النقطتين (`TimeStatus`): أخضر (`success`)، برتقالي (`soon`)، أحمر (`danger`)، وتنبضان بسرعة عند DUE وVERY_LATE (`BEAT_MS`)، وإلا تومضان مع الثواني؛ وصفراوان (`accent`) بلا مسار. ضغطة الساعة تنطق الوقت (`onSay`) وتشغّل `moments.playTime(tapped = true)`: نابض لطيف ثم الخلفية الصمّاء، 4 ثوانٍ.
  - `TimeFace`: بشكل الساعة، ونقطتاه صفراوان (لوقت كل رحلة فوق عنوانها 230 ولوقت الزر العائم). يتنفّس 1 → 1.2 خلال 2.2 ثانية حول نقطتيه (`ColonLine`) بـ `breathe` حين يُطلب: يُرسم بأكبر حجم في طبقة خاصة (`CompositingStrategy.Offscreen`) ويُصغَّر كصورة، لأن النص المكبَّر مباشرة يقفز بين أحجام خطوطه. `Colon` لا يشغّل نبضه (`rememberInfiniteTransition`) إلا وهو ينبض، فالنقطتان الساكنتان لا تطلبان إطارات.
  - **الضغط المطوّل** على عنوان صفحة أو وقت في السطر الأسفل أو رحلة في الشريط، أو ضغطة `MapSign`: `showWay` (فقط حين يوجد `routeMap` ولم يرفض Google المفتاح) → `onWantPosition` (يطلب إذن الموقع على التابلت إن لم يُعطَ بعد، والطلب يتبع الضغطة)، و`routeMap.focus(MapWay.Stop(lat, lng, place))` (null = المحطة التالية؛ إن لم يُعرف موقع التابلت بعد يُطلب الطريق مع أول موقع) و`moments.playFocus()` (`Info.FOCUS`، `holding` يوقف لحظات الدقيقة حتى الإغلاق، أو دقيقتين بعد آخر استعمال: `FOCUS_MAX_MS`، تتجدد مع كل لمسة أو زر عبر `moments.touched()`)، و`unfocus` عند الإغلاق. طريقها حول الرحلة المنظور إليها (`wayTrip`؛ null للمحطة التالية): الرحلة قبلها والاثنتان بعدها من `ahead` (`DisplaySnapshot.around`، `BEFORE` 1 و`AFTER` 2)، وكل رحلة يضيفها السائق بـ «+ رحلة» (260، `added`، حتى `MOST` 7: التالية بعد الطريق، أو التي قبله إن لم يبقَ بعده شيء)، من السيارة. عليها `WayList` (245–251؛ نافذة عائمة، على يمينها أول مرة): سطر صغير لكل رحلة بمقبض سحب وحرفها (بلون محطتها كما على الخريطة، والمنظور إليها في حلقة حمراء) ووقتها ونقطة نوعها وبداية شارعها ودقائق مرحلتها، ومتى تُبلغ بألوان حالة الساعة إن بدأ الطريق بالمحطة التالية (`fromNext`)؛ وفوقها الدقائق والمسافة إلى المنظور إليها والطريق كله والفرق عن ترتيب الجوال. السحب من المقبض ينقل الرحلة إلى مكان أخرى، وضغطة على سطر تختاره (`WayEdit.picked`) وتدير الخريطة إليه (`lookAt`) وتعطيه سهمين ↑↓ (246، 247)؛ الترتيب المجرَّب (`WayEdit.preview`) يُرسم بعد `ORDER_ASK_MS` من آخر سهم، أو حين يرفع السائق إصبعه (`dragging`). «اقترح» (248) يسأل Google ويضع أفضل ترتيب للوزن فقط، «تراجع» (249)، «اعتمد» (250) يرسل أرقام الرحلات إلى الجوال (`onOrder` → `DisplayLinkClient.order`)؛ ولا يُعتمد ترتيب فيه توصيل قبل استلام الراكب نفسه. `WayEdit.settle` يضع الرحلة المضافة في مكانها من الترتيب المجرَّب، ويُسقطه حين يصير ترتيب الجوال أو تخرج إحدى رحلاته من الطريق. هذه خريطة السائق (`held`): `MapLayer` يرفعها فوق الشاشة المتراجعة (`zIndex` `MAP_HELD_Z`) وتحتها طبقة تمسك اللمس (`MAP_FLOOR_BELOW`)، لأن لمس الـ View يصل في Compose إلى ما تحته أيضاً (`PointerInteropFilter` يشارك اللمس مع الإخوة)، فلولاها لنطقت ضغطة على الخريطة العنوان المخفي تحتها وأغلقتها. فوقها `MapControls` (`MAP_OVER_Z`): × أعلى اليمين (243)، وعلى اليسار صغيرة (`MAP_BUTTON` 48dp): القمر الصناعي (270)، السيارة (239)، المحطة المنظور إليها (240)، الطريق كاملاً (241)، ثم تطبيقات Google عند نقطة المحطة المنظور إليها (نقطتها، وإلا نهاية مرحلتها في طريق Google): Google Earth ثلاثي الأبعاد (242، `onEarth` → `MapsLauncher.openEarth`، وبلا Google Earth صورة القمر الصناعي في Google Maps) وصور الشارع في Google Maps (244، `onStreetPhotos`)، يُفتحان من نشاط الشاشة نفسه (`from`، بلا مهمة جديدة) فيعيد زر الرجوع إلى الشاشة لا إلى الشاشة الرئيسية للتابلت، باهتان حتى تُعرف النقطة. ضغطة على الخريطة لا تغلقها (طبقة `settle` لا تُرسم معها)؛ × أو أي شيء يُنطق يغلقها. وهي مفتوحة تغطي الساعة. الشاشة لا تفتح تطبيقات Google إلا بهذين الزرين.
  - الخريطة (`MapLayer`، 210) تحت كل شيء، تملأ الشاشة، وغير مرئية حتى يفتحها السائق، فتُحمَّل مرة واحدة. وهي مخفية لا يُرسم الـ WebView أصلاً (`View.INVISIBLE`، يعود `VISIBLE` في `reveal` ويختفي بعد `conceal` بـ `HIDE_AFTER_MS`): صفحة بملء الشاشة تُرسم تحت العرض مع كل إطار من الأسهم والثواني تُثقل التابلت. لا يغيّرها Compose بشيء: حين يصير `info` متجهاً إلى الظهور مع `Info.MAP` أو `Info.FOCUS` تُستدعى `routeMap.reveal` بموضع الدبوس (`pinAt` من `MapSign`، كنسبة من حجمها) و`INFO_IN_MS`، وحين يتجه إلى الاختفاء `conceal(moments.outMs)` (`SETTLE_MS` بعد ضغطة، وإلا `INFO_OUT_MS`). الصفحة تكبّرها من الدبوس كما تكبر الساعة، وتملأ الشاشة إلى حوافها بلا إطار ولا تظليل (خريطة كل دقيقة وحدها تذوب في الأسفل 30% تحت الدقائق، وتختفي هذه مع خريطة السائق)، وتضع الطريق في الوسط الصافي (`clear()`: حشوة `fitBounds`). في الثانية 45، إن كانت جاهزة والتابلت يعرف مكانه، تظهر 10 ثوانٍ (`Info.MAP`) بالشكل نفسه، وتحتها في الجزء الذائب الدقائق والمسافة (`MapMomentText`، 205)؛ وحتى يعرف التابلت مكانه «Söker bilens position…»، وتحتها بخط صغير ما يمنع الخريطة أو الطريق (`mapNote`، 233). وإلا يظهر الوقت المتبقي وحده. خريطة كل دقيقة تبقى مسطّحة للنظر فقط، وضغطة تعيد الشاشة.
  - **موقع الخريطة**: `geo/TabletPosition` على التابلت (LocationManager، GPS بدقة عالية كل ثانية، `MAX_ACCURACY_M` 30)، يبدأ ويتوقف مع ظهور الشاشة (`LifecycleStartEffect` في `AppRoot`) وفقط إن وُجدت الخريطة والإذن. كل موقع مع المحطة التالية (`DisplayItem.place` و`lat`/`lng` من الجوال) يذهب إلى `routeMap.show(MapWay)`. الجوال لا يرسل موقعه (لا `Where` في البروتوكول).
  - **بطاقة الرحلة**: رمز الشخص (`PersonGlyph` مفرّغ) على يسار وقت المحطة التالية فوق عنوانها (231 في `HeroLine`) وكل رحلة أخرى لها بطاقة (234: `HeroLine` في الصفحات الأخرى، و`PersonSign` في `StripTrip`) يفتح `TripCard` (235) عبر `onCard` ← `openCard`. `TripCard` صغيرة (`CARD_WIDTH` 36% من العرض، وحتى `CARD_HEIGHT` نصف الارتفاع، نص `CARD_SP` 14) ويرتّب النص بترتيب نافذة تفاصيل YouDrive عبر `core/youdrive/TripCardText.of`: شريط بلون الرحلة (`colors.trip(kind)`، النوع من الرحلة أو من البطاقة) فيه العنوان «Pick-up 09:58» والحالة شارةً، ثم الاسم برمز شخص، والوقتان («Estimated time»، «Client's negotiated time») برمز ساعة، ثم سطر لكل حقل برمزه في دائرة (`CardLine`، `cardIcon`) واسمه الصغير فوق قيمته؛ أرقام Phone number كلها تحت رمز واحد، كل رقم في سطر (`PhoneLine`: `DigitFont` رفيع Light بتباعد حروف `PHONE_SPACING`، `CARD_PHONE` 1.3 من حجم النص، بلون `highlight`، مجموعات 3-4-3 بمسافة لا تنكسر من `TripCardText.spacedPhone`: «073 8669 883»، و+46 يُكتب محلياً)؛ Space Type(s) وMobility Aids شارات (`FlowRow`)، وInstructions (`CardNote`) مربع منفصل بخط أصفر لكل سطر من `core/youdrive/CardNotes.of`: يُقطع النص حيث قطعه المرسل (سطر جديد، «/» بجانبها مسافة أو في طرف السطر أو مضاعفة، نقطة نهاية جملة قبل حرف كبير؛ «/» بين كلمتين أو رقمين تبقى)، وكل رقم هاتف سويدي (0 أو ‎+46، ثم 8 إلى 10 أرقام بمسافات أو شَرطات) سطر يبدأ به (`Line.Phone`)، ومعه ما يدل على صاحبه حين يكون واضحاً: كلمة أو كلمتان من الحروف قبله («Dotter 070…» ← «070… Dotter»)، أو بعده إن بدأ الجزء برقم، والكلمة بين رقمين («alt») بعد الثاني؛ وما هو أطول يبقى سطراً وحده كما هو (`Line.Words`). الحقول: Address (سطر أو سطران)، Phone number، Space Type(s)، Mobility Aids (الرموز مكتوبة كما يكتبها YouDrive: SP Sittande passagerare، FRA Fram، ROL Rollator fällbar، RU Rullstol، TRP Transportrullstol، HLI Hämtas/Lämnas inne، AVD Hämtning på avdelning، TRA Trappklättrare؛ الرقم بعد الرمز عدد ويُكتب بعد المعنى: RU1 «Rullstol 1» أي كرسي متحرك واحد؛ رمز غير معروف يبقى كما هو)، Fare amount («Client fee»)، Compensation، Eligibility، Instructions. كل كلمة تبقى كما هي، وتُحذف فقط أزرار الصفحة («Arrive») وعدّادها («27 min»). الأرقام بـ `DigitFont` (`withDigitFont`)، والبطاقة تُمرَّر إن طالت وتُغلق بضغطة بجانبها (الطبقة الداكنة) أو × (264)، والضغط عليها نفسها لا يغلقها؛ لا يُنطق منها شيء، ولحظات الدقيقة تنتظر ما دامت مفتوحة. هي `FloatingWindow` (`cardPlace`، الاسم `trip_card`، أول مرة في الوسط): شريطها الملوّن ينقلها (261، `movesWindow`)، و− و+ فيه (262، 263)، وإصبعان عليها؛ وتفتح حيث تركها السائق وبحجمها (`places`، `SettingsStore` على التابلت). قائمة الرحلات كذلك (`listPlace`، `way_list`، أول مرة على اليسار).
  - **حفظ البطاقة**: `YouDriveCards.parseCard` يحفظ نص البطاقة كله كما هو في `ExtractedStop.card` ← `Stop.card` ← `DisplayItem.card` (لكل رحلة من YouDrive؛ لا بطاقة للقطات الشاشة ولا للمحطات اليدوية). `DisplayItem.trip` يتجاهلها. تذهب مع المسار إلى شاشة الركاب فقط: لا في الإعلانات ولا الإشعار ولا «Previous trips» ولا السجل (`theTripCardGoesOnlyToTheDisplay`).
  - **اسم الراكب**: `DisplaySnapshot.build(lastName)` يضع الاسم الأخير لراكب كل رحلة قادمة في `lastName` (`RouteController.lastNameOf`)، فتكتبه الخريطة تحت عنوان كل محطة (`mapStop` → `MapWay.Stop.name`، في الصفحة فقط)، والشاشة نفسها لا تُظهر إلا اسم المحطة التالية (`Stage` يعطي `onName` للصفحة `home` فقط)؛ `DisplayItem.trip` يتجاهله كي يبقى مفتاح الرحلة نفسه. `HeroLine` يضع على يسار وقتها رمز شخص صغيراً مفرّغاً فقط (231، `PersonGlyph`: رأس وكتفان بخط)؛ ضغطته تُظهر الاسم بجانبه (236) مدة `NAME_OPEN_MS` (15 ثانية) أو حتى ضغطة أخرى على الرمز أو تغيّر المحطة، ويتلاشى الكل في عرض التركيز. ضغطة الاسم: `Announcements.passenger` على هذا الجهاز و`moments.playInfo(Info.NAME)`: الاسم كبيراً مع شارعه في `InfoMoment` (232).
  - ودجيت الطقس (211) يأخذ لحظة الطقس بدل رسم SMHI، ولا يستقبل اللمس (ضغطة تعيد الشاشة).
  - `Moments` (`rememberMoments`): في دور الوقت ينتقل الوقت (الساعات والنقطتان والدقائق والثواني قطعةً واحدة، `growTogether`) إلى وسط الشاشة ويكبر خلال 5 ثوانٍ (حتى 5× أو 90% من الشاشة) بلون جديد كل مرة (`showHues`، `hue`) ويخفت الباقي (`stepBack`، بـ `ModulateAlpha` كي لا يُقصّ شيء)؛ في آخر 1.8 ثانية من الكبر تتحوّل الخلفية بالتدريج إلى صمّاء (`solid`) وتبقى ثانيتين، ثم يعود الوقت خلال 1.2 ثانية وتعود الشفافية بالتدريج خلال 1.8 ثانية. في الثانية 27 يظهر الطقس (`InfoMoment`، 204: `WeatherGlyph` والحرارة والحالة السويدية)، وفي الثانية 45 الوقت المتبقي إن وُجد (205: `RouteGlyph` والدقائق والمسافة)، 7 ثوانٍ لكل منهما (1.2 + 4.6 + 1.2). ضغطة في أي مكان أثناء أي منها تعيد الشاشة (`settle`). لا شيء أثناء النطق، وأي نطق يوقفها. ولا تأتي لحظات الدقيقة وحدها والشاشة مشغولة (`Moments.paused`: رحلة معروضة كبيرة أو تُنطق، الشريط ظاهر، صفحة غير المحطة التالية، بطاقة رحلة مفتوحة)، فلا تتحرك ساعتان معاً؛ الضغطات تبقى تعمل. وحين يكبر الوقت يختفي السطر الأسفل بجانبه أسرع بكثير من الباقي (`lineBack`، `LINE_FADE_SPEED` 6): أوقات السطر وأسهمه تذهب قبل أن تصلها الساعة. القيم المتحركة تُقرأ عند الرسم (`graphicsLayer`، `Canvas`) أو عبر `derivedStateOf` (`focusSeen`، `infoShown`)، فلا تُعاد تركيبة الشاشة كلها مع كل إطار. `time` يُمرَّر في الاختبارات.
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
2. **لا يُحفظ إلا العنوان والوقت ونوع الرحلة واسم الراكب الأول والأخير.** الاسم كاملاً على شاشات السائق فقط. شاشة الركاب تعرض **الاسم الأخير لراكب المحطة التالية** وتنطقه حين يُضغط عليها فقط، وخريطتها تكتب **الاسم الأخير لراكب كل محطة قادمة** تحت عنوانها (بقرار السائق؛ لا يُرسل إلى Google). لا اسم أول، ولا اسم رحلة منتهية. **لا اسم في الإعلانات، ولا في إشعار أو في السجل، ولا يُسجَّل** (`namesStayOnTheDriversScreens`).
   - **الاستثناء الوحيد، بقرار السائق: بطاقة رحلة YouDrive كاملة** (`Stop.card`) تذهب مع المسار إلى بطاقة الرحلة في شاشة الركاب (235)، ولا تُفتح إلا بضغطة السائق على رمز الشخص للرحلة (231 أو 234). التابلت بجانبه والركاب بعيدون خلفه ولا يلمسونه. لا تُنطق أبداً، ولا تظهر في إعلان أو إشعار أو «Previous trips» أو سجل (`theTripCardGoesOnlyToTheDisplay`).
3. **لا سجلات** لعناوين أو نص OCR في release (`DebugLog` فقط). `allowBackup=false`.
4. **موقع الجوال لاسم الشارع والسرعة فقط**: أثناء الاستخدام، لا في الخلفية، لا ينقل المسار، لا يُحفظ ولا يُسجَّل ولا يُرسل. **صفحة YouDrive لا تحصل عليه أبداً.** وموقع التابلت لخريطته فقط (أدناه).
5. **اسم الشارع لا يُخترع**: القسم 5 كله. لا تطابق = لا اسم.
6. **الإنترنت** لصفحة YouDrive، ولتنزيل خريطة الشوارع بضغطة السائق، ولطقس SMHI لمكان ثابت (Karlstad) ما دامت شاشة ركاب تعرض مساراً، ولخريطة Google على التابلت (أدناه)، فقط. التنزيل والطقس لا يرسلان موقع السيارة. لا Firebase ولا تحليلات ولا تقارير أعطال ولا Hilt.
   - **الوصول إلى الإشعارات** (203، يمنحه السائق) لإشعار الملاحة في Google Maps فقط: تُؤخذ منه الدقائق والمسافة لا غير، ولا يُحفظ شيء ولا يُسجَّل.
   - **خريطة التابلت** (وافق عليها السائق): موقع السيارة من GPS التابلت نفسه (`TabletPosition`، ما دامت شاشة الركاب ظاهرة، لا في الخلفية)، والتابلت يرسله مع محطات الطريق الذي يرسمه (نقاطها أو عناوينها، بلا اسم، حتى سبع) إلى Google بمفتاح السائق (208). لا يُحفظ ولا يُسجَّل، والجوال لا يرسل موقعه.
   - **ودجيت الطقس** على التابلت (207): يرسمه تطبيق الطقس، وتطبيقنا يعرضه فقط.
   - **روابط Google Maps** لمحطة واحدة (252–255): تفتح تطبيق Google Maps بإحداثيات المحطة، بلا مفتاح؛ لا يرسل تطبيقنا شيئاً بنفسه.
7. **الإعلانات**: كما في القسم 4. **لا اسم راكب في أي إعلان.** شاشة الركاب تنطق الاسم الأخير لراكب المحطة التالية حين يُضغط عليه فقط.
8. **بيانات دخول YouDrive**: لا تدخل الكود ولا المستودع ولا السجلات ولا الردود، ولا يستعملها Claude. التطبيق يحفظها مشفّرة فقط إذا كتبها السائق بنفسه (القسم 6).
9. **لا يُعطَّل فحص شهادات TLS أبداً**، ولا يُلغى `HTTPS_PROXY` في بيئة البناء.
10. **لا شيء يظهر وحده**: كل نافذة أو رسالة أو صوت أو فتح للخرائط بعد فعل من السائق. الاستثناءات فقط: تنبيهات YouDrive، وإشعار «افتح الخرائط» عند منع النظام، واسم الشارع الحالي ما دام «انطق الشارع» مفعّلاً (4 / 137).
11. **المداخل** (256–259): نقطة التوقف وملاحظة الدخول التي يكتبها السائق لعنوان تُحفظ على الجوال فقط (`noBackupFilesDir`)، مع الشارع ورقمه والرمز البريدي أو المدينة، **بلا اسم**، حتى يحذفها (259). لا تُسجَّل. تصل إلى التابلت نقطتها فقط (لا الملاحظة)، ولا تُنطق.

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
