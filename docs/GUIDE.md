<div dir="rtl">

# دليل المشروع الكامل — Nästa Stopp 1.0

هذا الدليل يشرح **كل الكود**: كيف يعمل التطبيق من الداخل، وأين يوجد كل جزء، وكيف تُضاف ميزة أو يُعدَّل شيء بأمان. هو نقطة البداية لأي محادثة جديدة عن المشروع.

- **دليل الاستعمال** للسائق: [README.md](../README.md)، وفيه جداول الأرقام المرجعية كاملة.
- **سجل القرارات** وسببها، نسخة بنسخة: [DECISIONS.md](../DECISIONS.md).
- **قواعد العمل المختصرة**: [CLAUDE.md](../CLAUDE.md)، ويُقرأ تلقائياً في كل محادثة جديدة.

---

## 1. لمحة سريعة

| الشيء | القيمة |
|---|---|
| الحزمة | `se.eldebosh.nastastopp` |
| الإصدار | `versionName "1.0"`، `versionCode 14` (في `app/build.gradle.kts`) |
| اللغة والأدوات | Kotlin 2.4، AGP 9.4، Gradle 9.8، Jetpack Compose (Material 3) |
| الأندرويد | `minSdk 29` (أندرويد 10)، `targetSdk/compileSdk 37` |
| القراءة من الصور | ML Kit Text Recognition **المدمج** (نموذج Latin داخل التطبيق، دون Play Services) |
| المكتبات الأخرى | `androidx.webkit` (هوية كروم لصفحة YouDrive)، kotlinx.serialization، coroutines |
| لا يوجد | Hilt/DI framework، Firebase، تحليلات، تقارير أعطال، إذن الموقع، إذن التخزين |
| الاختبارات | 145 اختباراً: وحدة (JUnit) + Robolectric (أندرويد 13، sdk 33) |
| R8/minify | **مطفأ** في release، لأنه كان يحذف مسجِّلات ML Kit (راجع DECISIONS 1.0.1) |

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
│ DisplayLinkServer/Client · Notifications · SettingsStore · TripHistory │
└───────────────▲───────────────────────────────────────────────────────┘
                │ دوال Kotlin خالصة
┌───────────────┴───────────── core/ (بلا أندرويد) ─────────────────────┐
│ parse/ (العناوين والأوقات) · ocr/ (التقسيم والدمج) · geo/ · route/    │
│ link/ (بروتوكول البلوتوث) · display/ · youdrive/ (مقارنة القوائم)      │
└───────────────────────────────────────────────────────────────────────┘
```

- **`core/`** منطق خالص بلا أي اعتماد على أندرويد، ولذلك يُختبر بسرعة في JUnit. كل منطق صعب (تحليل النص، الأوقات، مقارنة قوائم YouDrive…) يوضع هنا.
- **حقن الاعتماديات يدوي**: `AppGraph` في `App.kt` يُنشئ كل الكائنات مرة واحدة لكل عملية. أي شاشة أو خدمة تصل إليها عبر `App.from(context).graph`.
- **الحالة** تُعرض كـ `StateFlow`، وتقرؤها الواجهة بـ `collectAsStateWithLifecycle()`. كل الاستدعاءات على الـ main thread، إلا حيث تستخدم coroutines الخلفية.
- **نشاطان**:
  - `MainActivity`: كل الشاشات بالتنقّل عبر `MainViewModel.stack`.
  - `YouDriveActivity`: نافذة YouDrive الكاملة.

---

## 3. رحلة البيانات من الصورة إلى الإعلان

1. **الاستيراد**: تأتي الصورة إلى `MainActivity.handleShareIntent` (مشاركة) أو من منتقي الصور في `AppRoot`، ثم إلى `MainViewModel.importImages(uris)` ← `ScreenshotImporter.import`.
2. **القراءة (OCR)** في `ocr/OcrEngine`:
   - تُفتح الصورة من الـ URI **في الذاكرة فقط**.
   - تُقسَّم اللقطات الطويلة إلى أجزاء متداخلة (`core/ocr/TilePlanner`).
   - تُقرأ الأجزاء بـ ML Kit، ثم تُدمج الأسطر وتُزال المكررات وتُرتَّب (`core/ocr/OcrLineMerger`).
3. **الاستخراج** في `core/parse/AddressExtractor.extract(lines)`:
   - يبحث عن رمز بريدي سويدي، وشارع مع رقم، ومدينة من `Localities` (الملف `assets/localities_se.txt`).
   - يتجاهل الأسماء والهواتف والمبالغ وكل النص الآخر.
   - الوقت يأتي من `TripTimes.assign`: الوقت على سطر العنوان نفسه، أو فوقه أو تحته حسب تصميم الصفحة.
   - الناتج: `ExtractedStop(displayText, candidates, time, …)`.
4. **القائمة** في `RouteController.addExtracted` ← `Stop` في `RouteData`، ثم يبدأ تحديد الموقع في الخلفية (`Geocoding.locate`، عبر `android.location.Geocoder` الخاص بالنظام، مع مهلة 15 ثانية لكل عنوان).
5. **المراجعة** في `ReviewScreen`: نقل، حذف، تعديل، إضافة يدوية، ترتيب حسب الوقت.
6. **البدء** بـ `RouteController.start()`:
   - يعلن أول محطتين (`Announcer.speak`).
   - يفتح خرائط Google بأول 10 محطات (`MapsLauncher` + `core/route/MapsUrlBuilder`).
7. **أثناء القيادة**:
   - `next()`: يسجّل الرحلة في `TripHistory`، وينتقل، ويعلن. في نهاية مجموعة الـ 10 يفتح الخرائط بالمجموعة التالية.
   - `back()`: يتراجع عن آخر next.
   - `repeat()`: يعيد الإعلان.
   - `end()`: ينهي المسار ويحفظ الرحلات غير المكتملة في السجل.
8. **الحفظ**: `RouteRepository` يحفظ في ملف خاص بلا نسخ احتياطي، ويحذفه بعد 12 ساعة، ومعه `ExpiryReceiver` عبر `AlarmManager`.
9. **ما يراه الآخرون**:
   - `RouteController.display` يبني `DisplaySnapshot` لشاشة الركاب، ويرسله `DisplayLinkServer` بالبلوتوث.
   - `RouteNotifier` يحدّث إشعار المسار.
   - `OverlayManager` يحدّث الزر العائم.

**الإعلانات** (`core/route/Announcements` + `RouteController.spokenName`):
- الحي أو المدينة فقط: «Nästa stopp: X. Därefter: Y.»
- اسم الحي يُختار في `core/geo/GeoLogic.spokenName` من نتيجة الـ Geocoder مع فحص `isSafeAreaName`، ويخضع لإعداد «District / Town only».

---

## 4. YouDrive

الملفات: `youdrive/YouDriveWatcher.kt`، `youdrive/YouDriveActivity.kt`، `youdrive/YouDriveService.kt`، `core/youdrive/TripWatch.kt`، `core/youdrive/BrowserIdentity.kt`، `ui/screens/YouDriveScreen.kt`.

- **الصفحة**: `https://youdrive.regionvarmland.se/`، وهي تطبيق React (Trapeze «YouOperate Driver»)، والـ API على `youapi.regionvarmland.se`. تسجيل الدخول يبقى في `sessionStorage` داخل الـ WebView.
- **WebView واحد لكل التطبيق** داخل `YouDriveWatcher`، مبني على `MutableContextWrapper`:
  - `attach(activity)`: يعرض الصفحة في النافذة.
  - `detach()`: يُبقيها تعمل في الخلفية إذا كانت المراقبة مفعّلة، وإلا يغلقها.
- **القراءة**: `READ_PAGE_JS` يعيد `{t: النص الظاهر, p: هل يوجد حقل كلمة سر, f: ما أُصلح}`، ثم `onPageText` ← `TripWatch.tripsIn`. هذه الدالة تستعمل نفس `AddressExtractor`، فلا يؤخذ إلا الوقت والعنوان.
- **المقارنة** في `TripWatch.onReading`:
  - أول قراءة غير فارغة هي الأساس.
  - التغيير لا يُعتمد إلا إذا ظهر في **قراءتين متتاليتين**.
  - القراءات الفارغة تُتجاهل.
  - رحلة اختفت بعد موعدها بأكثر من 5 دقائق تُعتبر منتهية.
  - `isNewList`: لم يبقَ شيء من القائمة القديمة، أو تغيّر أكثر من النصف وأكثر من 3 ← قائمة جديدة **بلا تنبيه** (يوم آخر، أو عرض آخر).
- **التوقيت**:
  - قراءة كل 60 ثانية، وكل 15 ثانية والنافذة ظاهرة.
  - إعادة تحميل كل 5 دقائق (`RELOAD_MS` ثابت)، **فقط والنافذة مغلقة**.
- **التنبيهات**: `Notifications.postTripChanges` على قناة `trip_changes` بصوت واهتزاز. حد أقصى 5 تنبيهات، ثم ملخّص.
- **الهوية مثل كروم**:
  - `BrowserIdentity.chromeUserAgent` يحذف `; wv` و`Version/4.0`.
  - `setUserAgentMetadata` يستبدل «Android WebView» بـ «Google Chrome».
  - ترويسة X-Requested-With لم يعد WebView يرسلها.
- **الأمان**:
  - لا موقع للصفحة (`setGeolocationEnabled(false)`).
  - لا روابط تطبيقات ولا نوافذ JS والصفحة في الخلفية.
  - أخطاء الشهادات تُرفض دائماً.
  - `res/xml/network_security_config.xml` يضيف جذر «Telia Root CA v2» العام (من سجل Mozilla) كمرجع ثقة لنطاق `regionvarmland.se` فقط. سبب ذلك أن أندرويد 13 وما قبله لا يحتوي هذا الجذر، فكانت صفحة YouDrive لا تُفتح (خطأ الشهادة 3). التحقق من الشهادات يبقى كاملاً.
  - `onRenderProcessGone` يعيد فتح الصفحة بدل إغلاق التطبيق.
  - «Log out» يمسح التخزين والكوكيز والكاش ويهدم الـ WebView.
- **الخدمة**: `YouDriveService` خدمة أمامية من نوع `specialUse` تُبقي العملية حية أثناء المراقبة. إشعارها صامت ويتحدّث عند تغيّر النص فقط.
- **ما زال يحتاج تجربة على الجوال**: قراءة الرحلات من الصفحة الحقيقية نجحت (19 رحلة)، لكن تنبيه الإضافة والإلغاء الحقيقي لم يُجرَّب بعد.

---

## 5. الملفات واحداً واحداً

المسار الأساسي: `app/src/main/java/se/eldebosh/nastastopp/`

### الجذر
| الملف | الدور |
|---|---|
| `App.kt` | `Application` + `AppGraph` (حقن يدوي لكل الكائنات). يطبّق لغة الواجهة وينشئ قنوات الإشعارات. |
| `MainActivity.kt` | النشاط الرئيسي: المشاركة (SEND/SEND_MULTIPLE)، وفتح YouDrive أو المراجعة من الإشعارات، وتشغيل خدمة YouDrive عند الاستئناف. |

### `core/` — منطق خالص (مختبَر في `app/src/test/.../core/`)
| الملف | الدور |
|---|---|
| `parse/AddressExtractor.kt` | قلب القراءة: من أسطر OCR إلى `ExtractedStop`. أيضاً `fromManualText` للإدخال اليدوي، و`isSameAddress` للمقارنة، و`STREET_SUFFIXES`. |
| `parse/TripTimes.kt` | إيجاد وقت كل رحلة، و`minutesUntil` (متأخر حتى 8 ساعات كحد أقصى)، و`level` (AHEAD/SOON/LATE)، و`normalizeTyped` للإدخال اليدوي. |
| `parse/Localities.kt` | قائمة المدن السويدية (من `assets/localities_se.txt`)، بحث لا يتأثر بحالة الأحرف ولا بعلامات å ä ö. |
| `parse/TextNorm.kt` | أدوات نص: `fold` (إزالة العلامات)، و`key`، وتشابه ليفنشتاين. |
| `parse/TitleCase.kt` | «STORGATAN 14» ← «Storgatan 14» بقواعد سويدية. |
| `ocr/TilePlanner.kt`, `OcrLineMerger.kt`, `OcrLine.kt` | تقسيم الصور الطويلة، ثم دمج النتائج وإزالة التكرار وترتيب القراءة. |
| `geo/GeoLogic.kt` | اختيار أفضل نتيجة Geocoder، واسم المنطقة المنطوق، والمسافة. |
| `geo/StreetLookup.kt` | منطق «الشارع الحالي». **خامل** منذ 1.4.5 (لا موقع). |
| `route/Announcements.kt` | نصوص الإعلانات السويدية والإنجليزية. |
| `route/MapsUrlBuilder.kt` | رابط اتجاهات خرائط Google، بحد أقصى 10 محطات لكل فتح. |
| `route/ArrivalDetector.kt` | آلة حالة الوصول والمغادرة من GPS. **خاملة** (لا موقع). |
| `link/LinkProtocol.kt`, `LinkTargets.kt` | رسائل JSON سطراً سطراً بين الجوال والتابلت، وترتيب الأجهزة المقترنة. |
| `display/DisplaySnapshot.kt` | ما تعرضه شاشة الركاب: `DisplayItem(time, title, subtitle)`. |
| `youdrive/TripWatch.kt` | مقارنة قراءات YouDrive (راجع القسم 4). |
| `youdrive/BrowserIdentity.kt` | هوية كروم للصفحة. |

### طبقة أندرويد
| الملف | الدور |
|---|---|
| `route/RouteController.kt` | **مصدر الحقيقة للمسار**: الإضافة والتعديل والحذف والنقل، وstart/next/back/repeat/openMaps/end، والإدراج من YouDrive (`importTrips`، `insertTrip`، `removeTrip`)، وبناء `display`، و`spokenName`. |
| `route/model/RouteModels.kt` | `Stop`، `RouteData`، `GeoPoint`، `GeoStatus`. عناوين وأوقات فقط. |
| `route/RouteRepository.kt` | حفظ المسار في ملف خاص، وحذف ما عمره أكثر من 12 ساعة. |
| `route/TripHistory.kt` | «Previous trips» في الصفحة الرئيسية، مع مدة الحفظ (12 ساعة، 24 ساعة، 7 أيام). |
| `route/ExpiryReceiver.kt` | منبّه يحذف البيانات المنتهية. |
| `ocr/OcrEngine.kt` | ML Kit + التقسيم. الأخطاء تُعرض بمرحلة الفشل (`ReadStage`) دون أي نص. |
| `importer/ScreenshotImporter.kt` | يمرّر الصور إلى OCR ثم الاستخراج، ويعيد عدد العناوين والأخطاء. |
| `geo/Geocoding.kt` | `locate` (من العنوان إلى الإحداثيات، يجرّب عدة صيغ)، و`reverse` (خامل). |
| `geo/CurrentStreet.kt` | الشارع الحالي، **خامل** (لا موقع). |
| `tts/Announcer.kt` | TextToSpeech بالسويدية، مع خفض صوت الخرائط مؤقتاً أثناء الكلام، والتحقق من وجود الصوت السويدي (`TtsStatus`). |
| `maps/MapsLauncher.kt` | فتح خرائط Google. من الخلفية يضيف إشعار «افتح الخرائط» لأن أندرويد قد يمنع فتح نشاط من الخلفية. |
| `overlay/OverlayManager.kt` | الزر العائم (Views، وليس Compose): اللوحة، والفقاعة، والسحب، والأرقام المرجعية 1–17، وتذكير الإشعار عند الإغلاق. الألوان في `companion object`. |
| `overlay/OverlayTileService.kt` | مربع «Floating button» في الإعدادات السريعة. |
| `service/Notifications.kt` | القنوات، وإشعار المسار، وتنبيهات YouDrive، وإشعار «الزر العائم مغلق». |
| `service/RouteNotifier.kt` | يُبقي إشعار المسار متزامناً مع المسار. |
| `service/RouteActionReceiver.kt` | أزرار الإشعار: Nästa / Upprepa / Avsluta / إظهار الزر العائم. |
| `link/Bluetooth.kt`, `DisplayLinkServer.kt`, `DisplayLinkClient.kt` | شاشة الركاب عبر RFCOMM بين أجهزة مقترنة (قناة آمنة + قناة احتياطية، وإعادة اتصال تلقائية). |
| `settings/SettingsStore.kt` | كل الإعدادات (`AppSettings`) في SharedPreferences، بلا عناوين. فيه ترحيل `SCHEMA` واللغة. |
| `util/LocaleHelper.kt` | لغة الواجهة داخل التطبيق، و`explanationContext` (موارد الشروح بالعربية). |
| `util/SystemIntents.kt` | فحص الأذونات وفتح صفحات إعدادات النظام. |
| `util/TimeLabels.kt` | «in 7 min»، «5 min late»، وألوانها، والمدة والمسافة. |
| `util/DebugLog.kt` | سجلات في debug فقط. **ممنوع** تسجيل عنوان أو نص OCR في release. |
| `youdrive/*` | راجع القسم 4. |

### الواجهة `ui/`
| الملف | الدور |
|---|---|
| `theme/Theme.kt` | **الهوية البصرية**: الألوان (`Brand`، `TimeColor`، `Located`، `NotLocated`، `Warning`، `Hairline`)، والخطوط، والأشكال. |
| `Components.kt` | مكونات موحّدة: `AppButton`، `TopBar`، `SectionTitle`، `AppCard`، `CardDivider`، `ListRow`، `IconBadge`، `Chevron`، `Paragraph`. `TouchTarget = 48.dp`. |
| `Explain.kt` | الشروح: `explain(id)` (بالعربية أثناء الإعداد)، و`HelpDot` (علامة «?» الصغيرة التي تفتح الشرح)، و`Hint` (نص شرح داخل نافذة حوار). |
| `Refs.kt` | الأرقام المرجعية: `Modifier.ref(n)` (رقم في سطر خاص فوق العنصر، و`centered = true` داخل الصفوف) و`Modifier.refCorner(n)` (في زاوية الأيقونات والمفاتيح). `RefNumbers.enabled` يتبع الإعداد 105. |
| `AppRoot.kt` | يوزّع الشاشات، ويطلب الأذونات، ويعرض رسائل snackbar، ويوفّر `LocalExplainResources`. |
| `MainViewModel.kt` | مكدس الشاشات (`Screen`)، والاستيراد، وفتح YouDrive أو المراجعة. |
| `screens/*.kt` | كل شاشة في ملف. الأرقام المرجعية لكل شاشة في README. |

### الموارد
- **النصوص**:
  - `res/values/strings.xml` = **العربية (الافتراضية!)**.
  - `values-en/strings.xml` = الإنجليزية (لغة الواجهة الحالية).
  - `values-sv/strings.xml` = السويدية.
  - **كل مفتاح جديد يجب أن يُضاف إلى الملفات الثلاثة.**
  - `strings_fixed.xml` فيه نصوص لا تُترجم.
- **الأيقونات**: `res/drawable/ic_*.xml` (vector). أيقونة التطبيق: `ic_launcher_background.xml` (تدرّج أزرق) + `ic_launcher_foreground.xml` (دبوس + سهم) + `ic_launcher_monochrome.xml`.
- **ملفات xml**: `backup_rules.xml` و`data_extraction_rules.xml` (تستثني كل شيء)، و`locales_config.xml`.

---

## 6. نظام التصميم (للحفاظ على مظهر موحّد)

- **الألوان**: من `MaterialTheme.colorScheme` أو من ثوابت `Theme.kt` فقط، ولا ألوان عشوائية.
  - الإجراء الرئيسي: `primary` (Brand).
  - الأوقات: `TimeColor`.
  - الحالة: `Located` و`NotLocated` و`Warning`.
  - النص الثانوي: `onSurfaceVariant`.
  - الحدود: `Hairline`.
- **الأحجام**:
  - الأزرار 48 dp افتراضياً، و52 dp للأزرار الرئيسية، و60 dp لـ Next و Back.
  - الأيقونات 20–24 dp.
  - الزوايا: `shapes.medium` = 14، و`large` = 20.
- **المكوّنات**:
  - مجموعة إعدادات أو خيارات: `AppCard { ListRow(...); CardDivider(); ListRow(...) }`.
  - زر: `AppButton(text, onClick, Modifier.ref(n)..., primary = true/false)`.
- **الشرح**: لا تضع فقرة شرح في الشاشة؛ ضع `HelpDot(R.string.x_hint)` بجانب العنوان، أو استعمل `ListRow(help = ...)`.
- **الأرقام المرجعية**:
  - كل عنصر جديد يأخذ رقماً غير مستعمل من نطاق شاشته (راجع جداول README)، ويُضاف إلى الجدول.
  - `ref` للعناصر العادية، و`refCorner` للأيقونات والمفاتيح.
  - داخل صفوف البطاقات يُمرَّر `ref` إلى `ListRow(ref = n)`، حتى لا يُقصّ الرقم عند زاوية البطاقة.
- **الاتجاه**:
  - نص قد يكون عربياً أو سويدياً يُكتب بـ `style.copy(textDirection = TextDirection.Content)`.
  - الواجهة العربية RTL تلقائياً.
- **الزر العائم** (Views): ألوانه ثوابت في `OverlayManager.companion`، وهي تطابق `Theme.kt`، فعدّلها معاً.

---

## 7. البناء والاختبار والإصدار

```bash
./gradlew test assembleRelease lintDebug lintRelease   # كل شيء؛ يجب أن ينتهي بـ exit 0
./gradlew :app:testDebugUnitTest --tests '*ScreenshotsRoboTest'   # صور الشاشات في app/build/screenshots/
```

خطوات الإصدار (بالترتيب، **ولا commit قبل نجاح كل شيء**):
1. **رفع الرقمين** في `app/build.gradle.kts`: `versionCode` دائماً +1 حتى يُثبَّت التطبيق فوق القديم، و`versionName` الجديد.
2. **البناء الكامل** بالأمر أعلاه. **افحص رمز الخروج**: كل الاختبارات تنجح، و lint = «No issues found».
3. **فحص الـ APK**:
   - `aapt2 dump badging` للتأكد من الرقم ومن عدم وجود أي إذن موقع.
   - `apksigner verify --print-certs` للتأكد من التوقيع (SHA-256 يبدأ بـ `1ae62777…`).
   - `dexdump` للتأكد من وجود `TextRegistrar` (مسجّل ML Kit).
4. **النسخ والتوثيق**: انسخ الملف إلى `dist/NastaStopp.apk` (الملف الوحيد المسموح به في git من نوع apk)، وحدّث DECISIONS.md وREADME.
5. **commit وpush**، ثم ضغط الـ APK في zip (حوالي 18 MB) وإرساله للسائق.

- **التوقيع**: `keystore.properties` في الجذر (خارج git) ← المفتاح في `~/.nastastopp-signing/`. عند حسام نسخة احتياطية خاصة من المفتاح (منذ 2026-09-28)، ولا تُرفع أبداً إلى المستودع. بدون هذا الملف يُوقَّع release بمفتاح debug، ولا يُثبَّت فوق النسخة الحالية.
- **البناء دون إنترنت** قد يفشل إذا نقصت مكتبة من الكاش، فيُعاد بالإنترنت.

### الاختبارات
- **`app/src/test/.../core/*`**: منطق خالص. أهمها `AddressExtractorTest` و`AddressExtractorFuzzTest` و`TripTimesTest` و`TripWatchTest`.
- **`app/src/test/.../robo/*`**: Robolectric على أندرويد 13.
  - `UiSmokeRoboTest`: الشاشات بالعربية.
  - `FloatingPanelRoboTest`، `YouDriveRoboTest`، `DisplayLinkRoboTest`، `RouteControllerRoboTest`، `TripHistoryRoboTest`، `DisplayFeaturesRoboTest`.
  - `ScreenshotsRoboTest`: يرسم كل شاشة إلى صورة.

---

## 8. قواعد لا تُكسر

1. **الصور** لا تُنسخ ولا تُحفظ. تُقرأ في الذاكرة فقط.
2. **لا يُحفظ إلا العنوان والوقت**، ولا أسماء ولا هواتف ولا نص آخر، سواء من اللقطات أو من YouDrive.
3. **لا سجلات** لعناوين أو نص OCR في release. استعمل `DebugLog` فقط.
4. **النسخ الاحتياطي**: `allowBackup="false"`، وقواعد الاستخراج تستثني كل شيء.
5. **لا إذن موقع أبداً** (قرار السائق في 1.4.5). خرائط Google وحدها تستخدم الموقع. الأذونات محذوفة بـ `tools:node="remove"` في الـ manifest.
6. **الإنترنت** لصفحة YouDrive فقط. تقارير ML Kit (datatransport) معطّلة في الـ manifest.
7. **الإعلان الصوتي** يذكر الحي أو المدينة فقط. شاشة الركاب يمكن أن تعرض الشارع مع الرقم (إعداد 114، قرار السائق).
8. **لا Hilt ولا Firebase** ولا تحليلات ولا تقارير أعطال.
9. **لا تُستخدم ولا تُحفظ** بيانات دخول YouDrive الخاصة بالسائق، ولا يُعطَّل فحص شهادات TLS أبداً.
10. **لا شيء يظهر وحده**: الإشعارات والنوافذ وفتح الخرائط كلها تأتي بعد فعل من السائق. الاستثناء الوحيد تنبيهات YouDrive (وهذا هو غرضها) وإشعار «افتح الخرائط» عند منع النظام.

---

## 9. كيف أضيف أو أعدّل…

- **إعداد جديد**:
  1. أضِف حقلاً في `AppSettings` بقيمة افتراضية.
  2. أضِف مفتاحاً في `SettingsStore`: ثابتاً `K_…`، وسطراً في موضع الحفظ، وسطراً في موضع القراءة.
  3. أضِف صفاً في `SettingsScreen`: `SwitchRow(R.string.x, R.string.x_hint, value, رقم) { … }`.
  4. أضِف النصوص إلى الملفات الثلاثة.
  5. أضِف الرقم إلى جدول README.
- **نص جديد**: أضِفه إلى `values/` (عربي) و`values-en/` و`values-sv/`. lint يعترض إذا نقصت ترجمة.
- **نص إعلان**: عدّل `core/route/Announcements.kt` واختبار `AnnouncementsTest`.
- **شكل صفحة YouDrive تغيّر** (لم تعد الرحلات تُقرأ):
  1. خذ لقطة للصفحة، بدون بيانات دخول.
  2. أضِف النص المقروء كحالة اختبار في `TripWatchTest` أو `YouDriveRoboTest`.
  3. عدّل `TripWatch.tripsIn` أو `AddressExtractor`.
- **صيغة عنوان جديدة من تطبيق آخر**: أضِف حالة في `AddressExtractorTest` أولاً، ثم عدّل `AddressExtractor`.
- **إرجاع الموقع** (إن غيّر السائق رأيه):
  - الكود الخامل موجود: `ArrivalDetector` و`CurrentStreet` و`StreetLookup` و`Geocoding.reverse`.
  - `RouteService` محذوف، ويمكن استرجاعه من تاريخ git (قبل 1.4.5).
  - يحتاج أيضاً إعادة الأذونات في الـ manifest.

---

## 10. التجربة على الجوال الحقيقي

- **من يجرّب:** جلسة Claude ثانية على لابتوب السائق متصلة بالجوال (S20 Ultra، أندرويد 13) عبر adb. هي تثبّت كل نسخة وتجرّبها. جلسة السحابة لا تتصل بالجوال أبداً.
- **مكان النسخة:** `dist/NastaStopp.apk` في الفرع، موقّعة release بنفس المفتاح دائماً، فتُحدَّث فوق النسخة القديمة. الرابط المباشر: `https://github.com/HussamEl/S20Ultra/raw/<sha>/dist/NastaStopp.apk`.
- **معرّفات ثابتة للاختبار الآلي:**
  - كل عنصر مرقّم له `resource-id` = `ref_<n>` (عبر `Modifier.ref` مع `testTagsAsResourceId`).
  - أجزاء الزر العائم لها `id/ref_1` إلى `id/ref_17`.
- **صور اختبار مخترعة** (بلا ركاب حقيقيين): في `testdata/screenshots/`، ونتائجها المتوقعة في `testdata/README.md`.
- **دورة العمل:**
  1. نرسل «DEVICE-TEST READY» مع الـ SHA وقائمة الخطوات.
  2. يعود الرد «DEVICE-TEST RESULT».

---

## 11. معلومات للمحادثة القادمة

- **التواصل مع السائق**: يكتب بالعربية، والردّ يكون بالعربية ومختصراً. لا تُرسَل له صور من التطبيق (لتوفير التوكنز). يُرسَل ملف zip للـ APK فقط.
- **الجوال**: Samsung Galaxy S20 Ultra (أندرويد 13). **التابلت**: Galaxy Tab S9+.
- **الفرع**: `claude/nasta-stopp-android-app-soeru7` في `HussamEl/S20Ultra`.
- **ما يُنتظر تجربته على الجوال**:
  1. وصول تنبيه حقيقي عند إضافة رحلة أو إلغائها في YouDrive.
  2. «Add all trips» مع قائمة YouDrive الحقيقية (كان العدد 19).
  3. شكل الهوية الجديدة على الجهاز.

</div>
