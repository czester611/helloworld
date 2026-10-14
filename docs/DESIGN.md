# FitLab — dokument projektowy (DESIGN.md)

Wersja dokumentu: 1.0 (2026-10-14). Status: zatwierdzony do implementacji.

Ten dokument jest **jedynym wejściem** dla implementatora. Wszystkie decyzje są już podjęte.
Jeżeli coś jest niejasne, wybierz najprostsze rozwiązanie zgodne z literą tego dokumentu
i zapisz je w `docs/DECISIONS.md` (krótko: data, krok, decyzja, powód). Nie zmieniaj decyzji
z sekcji 2 bez wyraźnego powodu technicznego (np. biblioteka nie istnieje w podanej wersji).

Konwencja językowa: opisy po polsku, nazwy techniczne (klasy, pliki, komendy, pola) po angielsku.
Teksty widoczne dla użytkownika w aplikacji: **po polsku**.

---

## Spis treści

1. Cel, zakres MVP, poza zakresem
2. Decyzje architektoniczne (stos, wersje, moduły, on-device vs PC)
3. Model danych
4. Algorytmy
5. Ekrany i nawigacja
6. Strategia testów
7. Plan implementacji (kroki M0–M14)
8. Ryzyka i pułapki dla implementatora
9. Instrukcja dla implementatora (konwencje, commity, weryfikacja)

Załącznik A: słownik pojęć. Załącznik B: stałe liczbowe w jednym miejscu.

---

## 1. Cel, zakres MVP, poza zakresem

### 1.1 Cel

FitLab to hobbystyczna aplikacja na Androida (jeden użytkownik, jedno urządzenie, bez kont i chmury),
która pomaga odtworzyć złamaną/zgubioną część i wydrukować ją na drukarce 3D FDM tak, żeby **pasowała
za pierwszym lub drugim razem**. Główna pętla:

1. Użytkownik fotografuje element z góry, leżący na kartce A4 z 4 markerami ArUco (arkusz generuje aplikacja),
   **albo** wybiera szablon parametryczny (tuleja, pokrętło, zaślepka, wspornik L, koło zębate) i wpisuje wymiary z suwmiarki.
2. Aplikacja tworzy przybliżony model 2,5D (obrys wyciągnięty na zmierzoną grubość, dokładność ok. 0,3–0,5 mm).
3. Użytkownik poprawia wymiary w edytorze (skala z wymiaru referencyjnego, grubość, średnice otworów z suwmiarki).
4. Aplikacja generuje **wydruk próbny** („plaster”, ang. *fit-test slice*): cienki fragment części zawierający pasowania.
5. Użytkownik zapisuje wynik próby per cecha (np. „otwór za ciasno o 0,2 mm”) — z formularza albo z notatki tekstowej.
6. Aplikacja uczy się **kompensacji** (poprawek) dla pary drukarka + filament, per typ cechy (otwór, czop, obrys, szczelina),
   i automatycznie stosuje ją w kolejnych częściach.
7. Eksport STL (binarny) lub 3MF do slicera (udostępnienie przez Android Share lub zapis pliku).
8. Biblioteka „Moje części” z historią prób.

### 1.2 Zakres MVP (wszystko poniżej MUSI powstać)

- Generowanie arkusza markerów A4 (PDF) do wydruku na zwykłej drukarce papierowej.
- Zdjęcie przez systemową aplikację aparatu (`ActivityResultContracts.TakePicture`) lub wybór z galerii (`PickVisualMedia`).
- Pipeline zdjęcie → obrys: wykrycie markerów ArUco, homografia, rektyfikacja, segmentacja (Otsu), kontury,
  korekcja grubości (paralaksa), uproszczenie wielokąta, wykrycie okrągłych otworów.
- Edytor obrysu: skala z wymiaru referencyjnego (X i/lub Y), obrót o 90°, lustro, grubość, lista otworów
  (edycja średnicy, dodawanie dotknięciem, usuwanie), zamiana wewnętrznego konturu na otwór, fazka otworu, fazka górnej krawędzi.
- 5 szablonów parametrycznych: `BUSHING` (tuleja), `KNOB` (pokrętło z otworem D), `CAP` (zaślepka okrągła/kwadratowa),
  `BRACKET` (wspornik L), `SPUR_GEAR` (koło zębate ewolwentowe).
- Kupon kalibracyjny (płytka 44×20×3 z otworem, szczeliną i czopem) do szybkiej kalibracji nowego filamentu.
- Generacja siatki trójkątów (prism/extrusion), walidacja szczelności siatki, eksport STL binarny i 3MF.
- Kompensacja: typy `HOLE`, `SLOT`, `PIN`, `OUTLINE`; klasy pasowania `PRESS`, `SLIDE`, `LOOSE`;
  uczenie z wyników prób (średnia ważona z priorytetem i odrzucaniem odstających), ręczne nadpisanie.
- Profile drukarek i filamentów (konfigurowalne), domyślne wartości startowe dla PLA i PETG (oraz ABS/ASA/TPU).
- Biblioteka części, historia prób (TestIteration), wyniki prób (FitResult), notatki.
- Ekran diagnostyki: uruchamia pipeline na wbudowanym syntetycznym zdjęciu (sprawdza OpenCV na urządzeniu).

### 1.3 Poza zakresem (jawnie — NIE implementować)

- Fotogrametria z wielu zdjęć (1–2 mm), skan 3D, ARCore/głębia — etap późniejszy.
- Odbicie symetryczne połówki złamanej części („mirror completion”) — etap późniejszy (proste lustro całego obrysu JEST w MVP).
- Gwinty, otwory poziome w częściach ze zdjęcia, wnętrza, podcięcia, detale < 1–2 mm (skan ich nie odda; użytkownik
  dodaje otwory ręcznie, gwinty robi gwintownikiem lub wkładką).
- Edycja pojedynczych wierzchołków obrysu, operacje boolowskie 3D, zaokrąglenia 3D, podgląd 3D (wystarcza widok 2D z góry).
- Generowanie G-code, integracja ze slicerem/OctoPrint/Bambu Cloud, druk bezpośredni.
- Podgląd na żywo z CameraX z nakładką markerów (etap 2; MVP używa systemowej aplikacji aparatu).
- Synchronizacja, konta, chmura, backup do chmury, iOS, wiele urządzeń, udostępnianie społecznościowe.
- Rozpoznawanie części przez ML, automatyczny dobór szablonu.
- Kompensacja zależna od rozmiaru cechy (model liniowy) i od osi Z — dane (nominalMm) są zapisywane, model później.
- Lokalizacja na inne języki niż polski.

---

## 2. Decyzje architektoniczne

### 2.1 Zasady nadrzędne

1. **Cała logika (geometria, kompensacja, szablony, eksport, przetwarzanie konturu) jest w czystym Kotlinie/JVM**,
   testowalna JUnit bez Androida i bez telefonu. Aplikacja Android to cienka warstwa: UI, baza, pliki, intencje.
2. **Wszystko działa na urządzeniu (on-device)**, offline. Brak serwera. PC służy tylko jako miejsce, gdzie trafia plik STL/3MF
   do slicera. Fotogrametria (etap późniejszy) — ewentualnie na PC (np. Meshroom/COLMAP), poza tą aplikacją.
3. Minimum zależności. Brak DI frameworka (ręczny `AppContainer`), brak biblioteki ikon, brak biblioteki 3D.
4. Jednostki: **milimetry (Double)** wszędzie w logice. Kąty w radianach w kodzie, w stopniach tylko w UI.

### 2.2 Struktura repozytorium i buildów (composite build)

Kluczowa decyzja: logika jest w **osobnym buildzie Gradle `geometry/`** (moduły `:core` i `:vision`), który
nie wymaga Android SDK ani repozytorium Google Maven. Główny build (Android) włącza go przez `includeBuild("geometry")`.

Uzasadnienie (zweryfikowane w środowisku agenta 2026-10-14): w środowisku bez dostępu do `dl.google.com` (Google Maven
zablokowany, brak Android SDK) i tak da się zbudować i przetestować cały rdzeń komendą `./gradlew -p geometry test`.
Ten układ (Gradle 9.6.1, Kotlin 2.4.21, JTS 1.20.0, kotlinx-serialization 1.11.0, JUnit 6.1.3, openpnp OpenCV 4.9.0-0)
został zbudowany i przetestowany na JDK 21 przed napisaniem tego dokumentu.

```
helloworld/
├── settings.gradle.kts            # build główny (Android): includeBuild("geometry"), include(":app")
├── build.gradle.kts               # wtyczki apply false + buildscript classpath KGP
├── gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/{gradle-wrapper.jar, gradle-wrapper.properties}
├── .github/workflows/ci.yml
├── docs/DESIGN.md, docs/TASKS.md, docs/DECISIONS.md, docs/MANUAL_TEST.md
├── geometry/                      # OSOBNY build Gradle, czysty JVM
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── core/                      # com.fitlab.core.*  — zero zależności od OpenCV/Androida
│   │   ├── build.gradle.kts
│   │   └── src/{main,test}/kotlin/com/fitlab/core/...
│   └── vision/                    # com.fitlab.vision.* — OpenCV Java API (compileOnly)
│       ├── build.gradle.kts
│       └── src/{main,test}/kotlin/com/fitlab/vision/...
└── app/                           # com.fitlab.app.* — Android, Compose, Room
    ├── build.gradle.kts
    ├── schemas/                   # schematy Room (commitowane)
    └── src/main/{AndroidManifest.xml, kotlin/com/fitlab/app/..., res/..., assets/selftest_photo.jpg}
```

Komendy:
- testy rdzenia (zawsze działają, główne kryterium ukończenia kroków M1–M8): `./gradlew -p geometry test`
- budowa aplikacji (wymaga Android SDK i dostępu do Google Maven): `./gradlew :app:assembleDebug`
- testy jednostkowe aplikacji (opcjonalne): `./gradlew :app:testDebugUnitTest`

### 2.3 Wersje (sprawdzone 2026-10-14, że istnieją)

| Element | Wersja | Uwagi |
|---|---|---|
| Gradle (wrapper) | **9.6.1** | AGP 9.4 wymaga Gradle ≥ 9.6.0 |
| JDK do budowania | 17+ (testowane na 21) | NIE używać `jvmToolchain(17)` — patrz pułapki |
| Android Gradle Plugin | **9.4.0** | max API 37, wbudowany Kotlin (bez `kotlin-android`) |
| Kotlin (KGP, compose, serialization plugins) | **2.4.21** | KGP wymuszony przez `buildscript classpath` |
| KSP | **2.3.12** | KSP2, wersjonowanie niezależne od Kotlina |
| Compose BOM | **2026.09.00** | ui 1.12.1, material3 1.4.0 |
| androidx.activity:activity-compose | 1.13.0 | |
| androidx.lifecycle:lifecycle-viewmodel-compose / lifecycle-runtime-compose | 2.11.0 | |
| androidx.navigation:navigation-compose | 2.10.2 | trasy tekstowe (string routes) |
| androidx.core:core-ktx | 1.19.1 | |
| androidx.room (runtime, compiler, gradle plugin) | **2.8.5** | `room-ktx` NIE jest potrzebny (scalony z runtime) |
| androidx.exifinterface:exifinterface | 1.4.2 | ogniskowa 35 mm z EXIF |
| org.opencv:opencv (Android AAR, Maven Central) | **4.13.0** | zawiera `objdetect.ArucoDetector` |
| org.openpnp:opencv (JVM, tylko testy/kompilacja `:vision`) | **4.9.0-0** | natywne biblioteki linux/mac/win w JAR |
| org.locationtech.jts:jts-core | **1.20.0** | offset, boolean 2D, triangulacja, upraszczanie |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.11.0 | NIE 1.12.0-RC |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.11.0 | |
| org.junit:junit-bom (Jupiter) | 6.1.3 | wymaga `junit-platform-launcher` testRuntimeOnly |
| junit:junit (testy app, opcjonalnie) | 4.13.2 | |
| CameraX (etap 2, NIE w MVP) | 1.6.2 | tylko informacyjnie |

`minSdk = 29` (Android 10): `ImageDecoder` (API 28) z automatyczną obsługą orientacji EXIF, scoped storage bez dodatkowych
uprawnień; aplikacja hobbystyczna na własny telefon — brak potrzeby wspierania starszych. `compileSdk = 37`
(maksimum AGP 9.4, najnowsze biblioteki androidx tego wymagają lub wymagą wkrótce). `targetSdk = 36` (znane zachowania,
m.in. wymuszone edge-to-edge — obsługiwane przez `Scaffold`).

### 2.4 Uzasadnienie wyborów bibliotek

- **JTS (jts-core)** zamiast pisania własnej geometrii 2D: dojrzała, czysto-Javowa, działa na Androidzie. Daje:
  `BufferOp` (offset z łączeniem MITRE/ROUND — potrzebny do kompensacji), operacje boolowskie (`difference`, `union`,
  `intersection`), `ConstrainedDelaunayTriangulator` (triangulacja wielokąta z dziurami bez dodawania wierzchołków),
  `TopologyPreservingSimplifier` (Douglas–Peucker bez samoprzecięć), `GeometryFixer`, `Orientation.isCCW`.
  Zweryfikowane: kwadrat 20×20 z dziurą 10×10 po buforze −0,1 (MITRE) daje dokładnie 19,8×19,8 i dziurę 10,2×10,2.
- **Własny kod siatki i eksportu** (zamiast bibliotek 3D): model to wyłącznie graniastosłupy (ekstruzje kształtów 2D),
  co wystarcza dla MVP. STL binarny to 84 B nagłówka + 50 B/trójkąt; 3MF to ZIP z 3 plikami XML (`java.util.zip`).
  Brak dobrej, lekkiej biblioteki CSG/3MF na JVM — własny kod jest krótszy niż integracja.
- **OpenCV**: na Androidzie oficjalny AAR `org.opencv:opencv:4.13.0` z Maven Central. Moduł `:vision` kompiluje się
  przeciwko `org.openpnp:opencv:4.9.0-0` (`compileOnly`) — to samo API Java `org.opencv.*`, z natywnymi bibliotekami
  dla desktopu, więc testy wizji działają w CI bez telefonu (zweryfikowane: wykrywanie ArUco działa na JVM).
  Używamy tylko stabilnych API obecnych w 4.9 i 4.13 (lista w 4.1.0).
- **Systemowy aparat zamiast CameraX w MVP**: pełna rozdzielczość, autofocus i stabilizacja „za darmo”, zero kodu cyklu życia
  kamery i zero uprawnień. CameraX z podglądem markerów na żywo — etap 2.
- **Room** dla historii i profili (relacje, zapytania, Flow). **SharedPreferences** dla kilku ustawień (aktywny profil,
  korekta skali arkusza) — bez DataStore, żeby nie dodawać zależności.
- **Ręczne DI** (`AppContainer` w `Application`) — mała aplikacja, Hilt to zbędna złożoność i kolejna wtyczka KSP.
- **Navigation Compose z trasami tekstowymi** — prostsze niż trasy typowane; nie wymaga serializacji tras.

### 2.5 Pakiety

`geometry/core` — pakiet bazowy `com.fitlab.core`:

| Pakiet | Zawartość |
|---|---|
| `geom` | `Vec2`, `Vec3`, `Ring` (zamknięty wielokąt), `Shape2` (obrys + dziury), `Bounds2`, `Shapes` (koło, prostokąt, zaokrąglony prostokąt, kształt D), `GeometryOps` (most do JTS: offset, boolean, upraszczanie, walidacja), `CircleFit` |
| `mesh` | `Transform3`, `Prism`, `Solid`, `Mesh`, `Tri`, `MeshBuilder`, `MeshValidator`, `MeshReport` |
| `export` | `StlWriter`, `ThreeMfWriter`, `ExportFormat`, `PartExporter`, `FileNames` |
| `fit` | `CompensationKind`, `FitClass`, `FitReference`, `CompensationTable`, `FitMath`, `ResolvedFeature`, `Verdict`, `Feedback`, `FeedbackInterpreter`, `BiasSample`, `CompensationEstimator`, `Material`, `MaterialDefaults`, `FeedbackNoteParser`, `ParsedNote` |
| `design` | `PartDesign` (sealed), `OutlineDesign`, `HoleFeature`, `TemplateDesign`, `PrintSettings`, `BuildResult`, `DesignBuilder`, `OutlineBuilder`, `ElephantFoot`, `CalibrationCoupon`, `DesignJson` |
| `templates` | `TemplateId`, `ParamSpec`, `ParamType`, `FitSlotSpec`, `TemplateInput`, `ValidationError`, `PartTemplate`, `BushingTemplate`, `KnobTemplate`, `CapTemplate`, `BracketTemplate`, `SpurGearTemplate`, `Involute`, `TemplateRegistry` |
| `sheet` | `MarkerSheetSpec`, `MarkerPlacement` |
| `photo` | `PixelMapping`, `CameraPose`, `ThicknessCorrection`, `RawContours`, `OutlinePostProcessor`, `PhotoOutline`, `HoleCandidate` |

`geometry/vision` — `com.fitlab.vision`: `MarkerDetector`, `DetectedMarkers`, `SheetRectifier`, `RectifyResult`,
`Segmenter`, `SegmentationParams`, `SegmentationResult`, `CameraPoseEstimator`, `PhotoPipeline`, `PipelineInput`,
`PipelineResult`, `PipelineException`, `MarkerSheetRenderer`, `MatUtils`.
Testy: `com.fitlab.vision.testing.SyntheticPhotoFactory` (w `src/test`).

`app` — `com.fitlab.app`:

| Pakiet | Zawartość |
|---|---|
| (root) | `FitLabApp` (Application), `MainActivity`, `AppContainer` |
| `data.db` | `FitLabDatabase`, encje `*Entity`, DAO `*Dao` |
| `data.repo` | `PartRepository`, `TestRepository`, `ProfileRepository`, `CompensationRepository` |
| `data.settings` | `AppSettings` (SharedPreferences) |
| `data.files` | `FileStore` (zdjęcia, eksporty, cache) |
| `platform` | `ImageLoader` (Uri→Mat), `ExifReader`, `MarkerSheetPdf`, `ShareHelper`, `OpenCvInit` |
| `ui.theme` | `FitLabTheme` |
| `ui.nav` | `Routes`, `FitLabNavHost` |
| `ui.components` | `ShapeCanvas`, `MmField`, `FitClassSelector`, `VerdictSelector`, `ConfirmDialog`, `ErrorBanner` |
| `ui.screens.<nazwa>` | `<Nazwa>Screen.kt` + `<Nazwa>ViewModel.kt` dla każdego ekranu z sekcji 5 |

### 2.6 Pliki konfiguracyjne Gradle (dokładna treść — krok M0)

`geometry/settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
rootProject.name = "geometry"
include(":core", ":vision")
```

`geometry/build.gradle.kts`:
```kotlin
plugins {
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.serialization") version "2.4.21" apply false
}
subprojects {
    group = "com.fitlab"
    version = "0.1.0"
}
```

`geometry/core/build.gradle.kts`:
```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
dependencies {
    implementation("org.locationtech.jts:jts-core:1.20.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
```

`geometry/vision/build.gradle.kts`:
```kotlin
plugins {
    kotlin("jvm")
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
dependencies {
    implementation(project(":core"))
    implementation("org.locationtech.jts:jts-core:1.20.0")
    compileOnly("org.openpnp:opencv:4.9.0-0")
    testImplementation("org.openpnp:opencv:4.9.0-0")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
}
```

`settings.gradle.kts` (root):
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "FitLab"
includeBuild("geometry")
include(":app")
```

`build.gradle.kts` (root):
```kotlin
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 ma wbudowany Kotlin; podbijamy KGP do wersji zgodnej z wtyczką compose (musi być identyczna).
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("androidx.room") version "2.8.5" apply false
}
```

`gradle.properties` (root):
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.fitlab.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.fitlab.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation("com.fitlab:core:0.1.0")      // z includeBuild("geometry")
    implementation("com.fitlab:vision:0.1.0")    // z includeBuild("geometry")
    implementation("org.locationtech.jts:jts-core:1.20.0")
    implementation("org.opencv:opencv:4.13.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation("junit:junit:4.13.2")
}
```

`.github/workflows/ci.yml`:
```yaml
name: CI
on: [push, pull_request]
jobs:
  geometry:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew -p geometry test --console=plain
  android:
    runs-on: ubuntu-latest
    needs: geometry
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :app:assembleDebug --console=plain
```

Dopisz do istniejącego `.gitignore`: `.idea/`, `*.iml`, `.kotlin/`, `geometry/.gradle/`, `geometry/build/`, `.DS_Store`.

---

## 3. Model danych

### 3.1 Model domenowy w `:core` (czysty Kotlin, `@Serializable` tam gdzie zapisywany jako JSON)

Konwencje: współrzędne 2D części w mm, oś X w prawo, **oś Y w górę** (matematyczna). Pierścień (`Ring`) jest zamknięty
niejawnie — **ostatni punkt NIE powtarza pierwszego**. Obrys zewnętrzny CCW (pole ze znakiem > 0), dziury CW.

```kotlin
// com.fitlab.core.geom
@Serializable data class Vec2(val x: Double, val y: Double)          // + operatory plus, minus, times(Double), length()
@Serializable data class Vec3(val x: Double, val y: Double, val z: Double)
@Serializable data class Ring(val points: List<Vec2>) {             // >= 3 punkty, bez powtórzonego końca
    fun signedArea(): Double                                         // wzór shoelace, CCW > 0
    fun isCcw(): Boolean = signedArea() > 0
    fun reversed(): Ring
    fun bounds(): Bounds2
    fun perimeter(): Double
    fun centroid(): Vec2                                             // centroid pola wielokąta
}
@Serializable data class Shape2(val outer: Ring, val holes: List<Ring> = emptyList()) {
    fun normalized(): Shape2       // outer CCW, holes CW (odwraca w razie potrzeby)
    fun area(): Double             // |outer| - sum |holes|
    fun bounds(): Bounds2
    fun translated(d: Vec2): Shape2
}
data class Bounds2(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) { val width; val height; val center }

// com.fitlab.core.mesh
data class Transform3(val m: DoubleArray /*3x3 row-major, 9 el.*/, val t: Vec3) {
    fun apply(p: Vec3): Vec3; fun determinant(): Double
    companion object {
        val IDENTITY: Transform3
        fun translation(t: Vec3): Transform3
        // kolumny = obrazy osi lokalnych X, Y, Z w świecie
        fun fromAxes(xAxis: Vec3, yAxis: Vec3, zAxis: Vec3, origin: Vec3 = Vec3(0.0, 0.0, 0.0)): Transform3
    }
}
data class Prism(val shape: Shape2, val z0: Double, val z1: Double, val transform: Transform3 = Transform3.IDENTITY) // z1 > z0
data class Solid(val prisms: List<Prism>) { fun footprint(): List<Shape2> }   // rzut z góry do miniatur
data class Tri(val a: Int, val b: Int, val c: Int)
data class Mesh(val vertices: List<Vec3>, val triangles: List<Tri>) {
    fun bounds(): Pair<Vec3, Vec3>
    fun normalizedForPrint(): Mesh  // przesuń: minZ = 0, środek XY bounding boxa w (0,0)
    operator fun plus(other: Mesh): Mesh  // łączenie (przesunięcie indeksów)
}
data class MeshReport(val triangleCount: Int, val isClosed: Boolean, val isConsistentlyOriented: Boolean,
                      val volumeMm3: Double, val degenerateTriangles: Int)

// com.fitlab.core.fit
@Serializable enum class CompensationKind { HOLE, SLOT, PIN, OUTLINE }
//   HOLE    = wewnętrzna cecha okrągła (otwór)            -> isInternal = true
//   SLOT    = wewnętrzna cecha liniowa/dowolna (szczelina, wycięcie, wewnętrzny kontur) -> isInternal = true
//   PIN     = zewnętrzna cecha okrągła (czop, wałek)      -> isInternal = false
//   OUTLINE = zewnętrzny obrys/wymiar liniowy             -> isInternal = false
@Serializable enum class FitClass(val clearanceMm: Double) { PRESS(0.00), SLIDE(0.15), LOOSE(0.40) }
//   luz średnicowy (na średnicy/szerokości), etykiety PL: "wcisk", "suwliwe", "luźne"
@Serializable enum class FitReference { ORIGINAL, MATING }
//   ORIGINAL = zmierzono tę samą cechę na oryginalnej części (cel = wymiar zmierzony, klasa pasowania ignorowana)
//   MATING   = zmierzono część współpracującą (wałek/otwór/rurę) -> cel uwzględnia luz klasy pasowania
@Serializable data class CompensationTable(val biasMm: Map<CompensationKind, Double>) {
    fun bias(kind: CompensationKind): Double = biasMm[kind] ?: 0.0
}
@Serializable data class ResolvedFeature(
    val id: String,               // np. "outline", "hole-1", "inner", "bore"
    val label: String,            // PL, np. "Otwór H1 Ø"
    val kind: CompensationKind,
    val reference: FitReference,
    val fit: FitClass?,           // null gdy reference == ORIGINAL
    val nominalMm: Double,        // wartość wpisana/zmierzona
    val targetMm: Double,         // wymiar, który ma wyjść po wydruku
    val biasMm: Double,           // kompensacja użyta przy generacji
    val cadMm: Double             // wymiar w modelu (target - bias)
)
@Serializable enum class Verdict { TOO_TIGHT, OK, TOO_LOOSE, MEASURED }
sealed interface Feedback {
    data class Fit(val verdict: Verdict /* TOO_TIGHT|OK|TOO_LOOSE */, val amountMm: Double) : Feedback
    data class Measured(val measuredMm: Double) : Feedback
}
data class BiasSample(val biasMm: Double, val createdAtMillis: Long)
@Serializable enum class Material { PLA, PETG, ABS, ASA, TPU, OTHER }
data class ParsedNote(val verdict: Verdict?, val amountMm: Double?)

// com.fitlab.core.design
@Serializable data class PrintSettings(val layerHeightMm: Double = 0.2, val elephantFootMm: Double = 0.2)
@Serializable sealed interface PartDesign
@Serializable @SerialName("outline") data class OutlineDesign(
    val outer: Ring,                              // mm, CCW, wycentrowany (centroid ~ (0,0))
    val innerContours: List<Ring> = emptyList(),  // niekołowe wycięcia przelotowe (kompensacja SLOT), CW
    val holes: List<HoleFeature> = emptyList(),   // otwory okrągłe przelotowe
    val thicknessMm: Double,
    val topChamferMm: Double = 0.0                // fazka górnej krawędzi zewnętrznej (schodkowa), 0 = brak
) : PartDesign
@Serializable data class HoleFeature(
    val id: String,                    // "hole-1", "hole-2", ... (stabilne w obrębie części)
    val center: Vec2,
    val nominalDiameterMm: Double,
    val reference: FitReference = FitReference.ORIGINAL,
    val fit: FitClass = FitClass.SLIDE,
    val chamferMm: Double = 0.0,       // fazka wejścia od góry (schodkowa), 0 = brak
    val needsCaliperCheck: Boolean = false   // true gdy średnica pochodzi ze zdjęcia
)
@Serializable @SerialName("template") data class TemplateDesign(
    val templateId: TemplateId,
    val values: Map<String, Double>,
    val fits: Map<String, FitClass>
) : PartDesign
data class BuildResult(val solid: Solid, val features: List<ResolvedFeature>, val warnings: List<String>)

// com.fitlab.core.templates
@Serializable enum class TemplateId { BUSHING, KNOB, CAP, BRACKET, SPUR_GEAR }
enum class ParamType { MM, COUNT, BOOL }
data class ParamSpec(val key: String, val label: String, val type: ParamType, val default: Double,
                     val min: Double, val max: Double, val hint: String? = null)
data class FitSlotSpec(val key: String, val label: String, val default: FitClass)
data class ValidationError(val key: String?, val message: String)   // message po polsku
data class TemplateInput(val values: Map<String, Double>, val fits: Map<String, FitClass>) {
    fun v(key: String): Double; fun count(key: String): Int; fun bool(key: String): Boolean; fun fit(key: String): FitClass
}
```

### 3.2 Model trwały (Room, `app`)

Wszystkie enumy zapisywane jako `String` (`enum.name`), mapowanie w repozytoriach (bez TypeConverterów).
Czasy: `Long` epoch millis. Klucze: `Long` autoGenerate (poza `compensation_rule`).
`FitLabDatabase` version = 1, `exportSchema = true`.

**`printer_profile` — `PrinterProfileEntity`**

| Pole | Typ | Domyślnie | Opis |
|---|---|---|---|
| id | Long PK auto | | |
| name | String | "Moja drukarka" | |
| nozzleDiameterMm | Double | 0.4 | informacyjnie |
| layerHeightMm | Double | 0.2 | do schodków fazek, stopy słonia, wysokości plastra |
| elephantFootMm | Double | 0.2 | 0 = wyłączone; wcięcie pierwszej warstwy |
| notes | String | "" | |
| createdAt | Long | | |

**`filament_profile` — `FilamentProfileEntity`**

| Pole | Typ | Domyślnie | Opis |
|---|---|---|---|
| id | Long PK auto | | |
| name | String | | np. "PLA czarny Devil" |
| material | String (`Material`) | PLA | wyznacza wartości startowe kompensacji |
| brand | String | "" | |
| color | String | "" | |
| notes | String | "" | |
| createdAt | Long | | |

**`compensation_rule` — `CompensationRuleEntity`** (PK złożony: printerProfileId, filamentProfileId, kind;
FK do obu profili z `onDelete = CASCADE`)

| Pole | Typ | Opis |
|---|---|---|
| printerProfileId | Long | |
| filamentProfileId | Long | |
| kind | String (`CompensationKind`) | |
| biasMm | Double | aktualny błąd druku średnicowy (printed − cad), stosowany jako cad = target − bias |
| sampleCount | Int | liczba próbek użytych w ostatnim przeliczeniu |
| manualOverride | Boolean | true = użytkownik wpisał ręcznie, nie przeliczaj automatycznie |
| updatedAt | Long | |

**`part` — `PartEntity`**

| Pole | Typ | Opis |
|---|---|---|
| id | Long PK auto | |
| name | String | |
| source | String: `PHOTO` / `TEMPLATE` | |
| templateId | String? | `TemplateId.name` gdy TEMPLATE |
| designJson | String | `PartDesign` przez `DesignJson` |
| photoPath | String? | ścieżka względna w `filesDir` (np. `photos/<uuid>.jpg`) |
| status | String: `DRAFT` / `TESTING` / `DONE` | DRAFT po utworzeniu, TESTING po pierwszym eksporcie, DONE ustawia użytkownik |
| notes | String | |
| createdAt, updatedAt | Long | |

**`measurement` — `MeasurementEntity`** (dziennik pomiarów suwmiarką; FK partId CASCADE)

| Pole | Typ | Opis |
|---|---|---|
| id | Long PK auto | |
| partId | Long | |
| label | String | np. "Grubość", "Szerokość X (ref.)", "Otwór H1", "shaftDiameter" |
| valueMm | Double | |
| targetKey | String? | klucz parametru/cechy, któremu przypisano wartość (np. `thickness`, `refWidth`, `hole-1`, `shaftDiameter`) |
| createdAt | Long | |

**`test_iteration` — `TestIterationEntity`** (FK partId CASCADE, nullable dla kalibracji)

| Pole | Typ | Opis |
|---|---|---|
| id | Long PK auto | |
| partId | Long? | null dla `CALIBRATION` |
| number | Int | kolejny numer w obrębie części (1, 2, ...); dla kalibracji kolejny numer globalny kalibracji |
| type | String: `FIT_TEST` / `FULL_PART` / `CALIBRATION` | |
| printerProfileId, filamentProfileId | Long | profil aktywny w chwili generacji |
| designJson | String | migawka projektu |
| compensationJson | String | migawka `CompensationTable` |
| featuresJson | String | `List<ResolvedFeature>` |
| exportFormat | String? | `STL` / `THREE_MF` |
| exportFileName | String? | |
| note | String | notatka użytkownika z próby |
| createdAt | Long | |
| resultRecordedAt | Long? | null = wynik jeszcze nie wpisany |

**`fit_result` — `FitResultEntity`** (FK testIterationId CASCADE; indeks na `(printerProfileId, filamentProfileId, kind)`)

| Pole | Typ | Opis |
|---|---|---|
| id | Long PK auto | |
| testIterationId | Long | |
| featureId | String | `ResolvedFeature.id` |
| kind | String | |
| verdict | String (`Verdict`) | |
| amountMm | Double? | dla TOO_TIGHT/TOO_LOOSE |
| measuredMm | Double? | dla MEASURED |
| biasUsedMm | Double | z migawki |
| sampleBiasMm | Double | wynik `FeedbackInterpreter` — to jest próbka do uczenia |
| printerProfileId, filamentProfileId | Long | zdenormalizowane z iteracji (szybkie zapytania) |
| includeInLearning | Boolean | domyślnie true; użytkownik może wyłączyć próbkę |
| createdAt | Long | |

**`AppSettings` (SharedPreferences `fitlab_settings`)**: `activePrinterId: Long`, `activeFilamentId: Long`,
`sheetScaleX: Double = 1.0`, `sheetScaleY: Double = 1.0` (korekta skali wydrukowanego arkusza, sekcja 4.2).
Double zapisuj jako `Long` przez `java.lang.Double.doubleToRawLongBits`.

DAO (minimalny zestaw, wszystkie `suspend` lub `Flow`):
- `PartDao`: `observeAll(): Flow<List<PartEntity>>` (ORDER BY updatedAt DESC), `get(id)`, `observe(id)`, `insert`, `update`, `delete`.
- `MeasurementDao`: `forPart(partId)`, `insert`.
- `TestIterationDao`: `observeForPart(partId)` (ORDER BY number DESC), `get(id)`, `insert`, `update`, `maxNumber(partId): Int?`, `maxCalibrationNumber(): Int?`.
- `FitResultDao`: `forIteration(id)`, `insertAll`, `samples(printerId, filamentId, kind): List<FitResultEntity>` (WHERE includeInLearning = 1 ORDER BY createdAt DESC), `setInclude(id, include)`, `observeForProfile(printerId, filamentId)`.
- `ProfileDao`: CRUD dla obu profili, `observePrinters()`, `observeFilaments()`, `countPrinters()`.
- `CompensationRuleDao`: `forPair(printerId, filamentId): List<...>`, `observeForPair(...)`, `upsert` (`@Upsert`).

---

## 4. Algorytmy

### 4.0 Wspólne konwencje liczbowe

- Wszystkie wymiary kompensacji i luzów są **średnicowe** (na całej średnicy/szerokości). Offset obrysu (promieniowy) = połowa.
- Okrąg → wielokąt: `n = clamp(ceil(π·D / 0.4), 32, 256)` wierzchołków, `θ_k = 2πk/n`, punkty `(cx + r·cosθ_k, cy + r·sinθ_k)`
  (CCW). Cięciwa ≤ 0,4 mm, strzałka < 0,01 mm dla D ≤ 50 — pomijalne.
- Usuwanie punktów zdublowanych: kolejne punkty bliżej niż 0,001 mm są scalane (`Ring.cleaned()`).
- Formatowanie liczb do plików i UI: zawsze `Locale.ROOT` w plikach (kropka), w UI `Locale("pl")` (przecinek). Pola tekstowe
  w UI akceptują i kropkę, i przecinek.

### 4.1 Arkusz markerów i wykrywanie (`MarkerSheetSpec`, `MarkerDetector`)

**Arkusz A4 (210×297 mm), układ współrzędnych arkusza: x w prawo, y w dół, mm, (0,0) = lewy górny róg strony.**

| Element | Pozycja (mm) |
|---|---|
| Słownik | `Objdetect.DICT_4X4_50`, ID 0–3 |
| Marker ID 0 (lewy górny) | x 15–45, y 15–45 |
| Marker ID 1 (prawy górny) | x 165–195, y 15–45 |
| Marker ID 2 (prawy dolny) | x 165–195, y 252–282 |
| Marker ID 3 (lewy dolny) | x 15–45, y 252–282 |
| Pole robocze (ramka szara 0,3 mm) | x 20–190, y 55–242 (170×187 mm) |
| Linijka kontrolna 100 mm | x 55–155, y 265–266, kreski co 10 mm, podpis "100 mm" |
| Tekst | y ≈ 290: "FitLab A4 v1 — drukuj w skali 100% (bez dopasowania do strony)" |

Narożniki markera o lewym górnym rogu (x0, y0) i boku s = 30 w kolejności OpenCV (TL, TR, BR, BL):
`(x0,y0), (x0+s,y0), (x0+s,y0+s), (x0,y0+s)`. `MarkerSheetSpec.cornersMm(id): List<Vec2>` zwraca je
z uwzględnieniem korekty skali: `x' = x·sheetScaleX`, `y' = y·sheetScaleY` (domyślnie 1,0). Korekta skali jest ustawiana
w ustawieniach: użytkownik mierzy odległość zewnętrznych krawędzi markerów 0–1 (oczekiwane 180,0 mm) i 0–3 (oczekiwane
267,0 mm); `sheetScaleX = zmierzone/180`, `sheetScaleY = zmierzone/267`.

**Renderowanie arkusza** (`MarkerSheetRenderer.render(pxPerMm = 12.0): Mat` CV_8UC1): obraz 2520×3564, białe tło 255.
Markery: `Objdetect.generateImageMarker(dict, id, 360, markerMat, 1)` i `copyTo` do `submat(Rect(x0·12, y0·12, 360, 360))`.
Ramka pola roboczego: `Imgproc.rectangle` kolor 160, grubość 4 px. Linijka: prostokąt czarny + kreski 3 mm wysokości.
Tekst `Imgproc.putText` (FONT_HERSHEY_SIMPLEX, skala 1.6, grubość 3) — tylko ASCII (bez polskich znaków w putText!).
Aplikacja zamienia `Mat` na `Bitmap` i tworzy PDF (`android.graphics.pdf.PdfDocument`, strona 595×842 pt), rysując bitmapę
w prostokącie `RectF(0f, 0f, 595.2756f, 841.8898f)` bez filtrowania (`Paint().apply { isFilterBitmap = false }`).

**Wykrywanie** (`MarkerDetector.detect(gray: Mat): DetectedMarkers`):
```kotlin
val dict = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50)
val params = DetectorParameters()
params.set_cornerRefinementMethod(Objdetect.CORNER_REFINE_SUBPIX)
val detector = ArucoDetector(dict, params)
val corners = ArrayList<Mat>(); val ids = Mat()
detector.detectMarkers(gray, corners, ids)
// corners[i] to Mat 1x4 CV_32FC2; odczyt: FloatArray(8) przez corners[i].get(0, 0, arr)
```
Zachowaj tylko ID 0–3; przy duplikatach bierz pierwszy. Wynik: `Map<Int, List<Vec2>>` (4 narożniki w pikselach zdjęcia).
Jeśli wykryto < 3 markery → `PipelineException("Nie znaleziono markerów arkusza (znaleziono N z 4). Upewnij się, że cały arkusz jest w kadrze i dobrze oświetlony.")`.
3 markery → kontynuuj z ostrzeżeniem „Wykryto tylko 3 markery — dokładność może być niższa”.

### 4.2 Homografia, rektyfikacja, skala mm/px (`SheetRectifier`)

Stała: **`RECT_PX_PER_MM = 10.0`** (obraz zrektyfikowany 2100×2970 px). Skala mm/px jest więc stała i znana z definicji:
0,1 mm/px — nie trzeba jej „mierzyć”, bo homografia odwzorowuje znane mm arkusza na znane piksele.

**Konwencja pikseli (KRYTYCZNE, używaj wszędzie):** środek piksela (u, v) ma współrzędne całkowite (konwencja OpenCV).
Piksel (u, v) obrazu zrektyfikowanego pokrywa obszar arkusza `[u/k, (u+1)/k) × [v/k, (v+1)/k)` mm, a jego środek to
`((u+0.5)/k, (v+0.5)/k)` mm. Zatem:
- mm → px: `u = x·k − 0.5`, `v = y·k − 0.5`
- px → mm: `x = (u + 0.5)/k`, `y = (v + 0.5)/k`

Kod tej konwersji jest w `com.fitlab.core.photo.PixelMapping` (czysty Kotlin, testowany) i jest używany przez `:vision`.

Algorytm:
1. `src` = wszystkie narożniki wykrytych markerów (piksele zdjęcia), `dst` = odpowiadające narożniki z `MarkerSheetSpec`
   przeliczone mm → px rektyfikacji (wzór wyżej). 16 par punktów przy 4 markerach.
2. `H = Calib3d.findHomography(MatOfPoint2f(src), MatOfPoint2f(dst), 0)` (metoda 0 = najmniejsze kwadraty; punkty są pewne).
   Jeśli `H.empty()` → `PipelineException("Nie udało się wyznaczyć perspektywy.")`.
3. Błąd reprojekcji: `Core.perspectiveTransform(src, H)` vs `dst`; średni błąd w px rektyfikacji > 3,0 →
   ostrzeżenie „Kartka może nie leżeć płasko lub zdjęcie jest poruszone”.
4. `Imgproc.warpPerspective(gray, rectified, H, Size(2100.0, 2970.0), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(255.0))`.
5. Zwróć `RectifyResult(rectified, H, meanReprojErrorPx, markerCornersPhotoPx: Map<Int, List<Vec2>>)`.

Prostowanie robimy na obrazie szarym (`Imgproc.cvtColor(rgba, gray, COLOR_RGBA2GRAY)`) — kolor nie jest potrzebny.
Zdjęcie przed przetworzeniem skalujemy tak, żeby dłuższy bok ≤ 4000 px.

### 4.3 Segmentacja konturu (`Segmenter`)

Parametry (`SegmentationParams`): `manualThreshold: Int? = null` (null = Otsu), `blurKernel = 5`, `openKernel = 3`,
`openIterations = 1`, `closeKernel = 5`, `closeIterations = 2`, `minPartAreaMm2 = 100.0`, `minHoleAreaMm2 = 3.0`.

ROI = pole robocze w px rektyfikacji: `Rect(200, 550, 1700, 1870)` (x 20–190 mm, y 55–242 mm).

1. `roi = rectified.submat(ROI)`; `Imgproc.GaussianBlur(roi, blurred, Size(5.0, 5.0), 0.0)`.
2. Próg: jeśli `manualThreshold == null` → `T = Imgproc.threshold(blurred, tmp, 0.0, 255.0, THRESH_BINARY or THRESH_OTSU)`
   (zwraca wartość progu); w przeciwnym razie `T = manualThreshold`.
3. Polaryzacja: `borderMean` = średnia jasność ramki 4 px przy krawędziach ROI (tło). Jeśli `borderMean > T` → przedmiot
   ciemniejszy od tła → `THRESH_BINARY_INV`, inaczej `THRESH_BINARY`. `Imgproc.threshold(blurred, mask, T, 255.0, typ)`.
   (Dzięki temu jasną część można sfotografować na ciemnej kartce położonej w polu roboczym.)
4. Kontrast: `meanFg`, `meanBg` = średnie `blurred` pod maską i poza nią; jeśli `|meanFg − meanBg| < 30` →
   ostrzeżenie „Słaby kontrast — połóż część na kartce w kontrastowym kolorze”.
5. Morfologia: `MORPH_OPEN` z elipsą 3×3 (1 iteracja), potem `MORPH_CLOSE` z elipsą 5×5 (2 iteracje).
6. `Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_NONE)`.
   `hierarchy` ma 4 kanały na kontur: [next, prev, firstChild, parent].
7. Wybór części: kontur bez rodzica (`parent == -1`) o największym `Imgproc.contourArea`. Jeśli pole < `minPartAreaMm2·k²`
   (= 10 000 px²) → `PipelineException("Nie znaleziono części w polu roboczym.")`.
   Jeśli bounding box konturu jest w odległości ≤ 2 px od krawędzi ROI → `PipelineException("Część wychodzi poza pole robocze — przesuń ją do środka ramki.")`.
8. Dziury: dzieci wybranego konturu (iteruj od `firstChild` po `next`) o polu ≥ `minHoleAreaMm2·k²` (= 300 px²).
9. Przesunięcie współrzędnych ROI → współrzędne całego obrazu zrektyfikowanego: `+ (200, 550)`.
10. Zwróć `SegmentationResult(outerPx: List<Vec2>, holesPx: List<List<Vec2>>, threshold: Int, mask: Mat, warnings)`.

### 4.4 Pozycja aparatu i korekcja grubości (paralaksa) (`CameraPoseEstimator`, `ThicknessCorrection`)

Problem: homografia prostuje **płaszczyznę kartki**. Górna powierzchnia części o grubości t jest bliżej aparatu, więc po
rektyfikacji jest powiększona względem punktu nadiru (rzut aparatu na kartkę) o `m = H/(H − t)`. Przy H = 300 mm
i t = 10 mm to +3,4% (≈ +1,7 mm na 50 mm). Gdy nadir leży wewnątrz części (zdjęcie robione centralnie nad częścią),
sylwetka widziana z góry = rzut górnej ściany, więc wystarczy przeskalować obrys względem nadiru o `1/m`.

**Estymacja pozycji aparatu** (`:vision`):
1. Ogniskowa: `f35` z EXIF `TAG_FOCAL_LENGTH_IN_35MM_FILM` (jeśli > 0), w przeciwnym razie **26 mm** (typowy główny aparat telefonu).
   `fPx = f35 / 43.2666 · sqrt(w² + h²)` (43,2666 = przekątna klatki 36×24 mm; w, h = wymiary zdjęcia PO przeskalowaniu).
2. `K = [[fPx, 0, w/2], [0, fPx, h/2], [0, 0, 1]]`, dystorsja = zera (`MatOfDouble(0.0, 0.0, 0.0, 0.0, 0.0)`).
3. `objectPoints` = narożniki markerów w mm arkusza jako `Point3(x, y, 0.0)`; `imagePoints` = wykryte narożniki (px zdjęcia).
4. `Calib3d.solvePnP(objectPoints, imagePoints, K, dist, rvec, tvec, false, Calib3d.SOLVEPNP_ITERATIVE)`.
5. `R = Calib3d.Rodrigues(rvec)`; środek aparatu `C = −Rᵀ·t` (w mm arkusza). Oś z arkusza wskazuje „w głąb kartki”,
   więc aparat ma `C.z < 0`. `heightMm = |C.z|`, `nadirMm = Vec2(C.x, C.y)` (współrzędne arkusza, y w dół).
6. Jeśli `heightMm` poza [100, 1500] → pozycja = null, ostrzeżenie „Nie udało się oszacować wysokości aparatu — pominięto korekcję grubości”.

**Korekcja** (`:core`, `ThicknessCorrection.apply(points: List<Vec2>, pose: CameraPose, thicknessMm: Double): List<Vec2>`):
`p' = n + (p − n) · (H − t)/H`, gdzie n = nadir, H = heightMm, t = grubość. Jeśli `t <= 0` lub pose == null → bez zmian.
Ostrzeżenie (generowane w `OutlinePostProcessor`): jeśli nadir leży poza bounding boxem obrysu powiększonym o 10 mm →
„Zdjęcie nie było robione centralnie nad częścią — wymiary mogą być zawyżone. Zrób zdjęcie z góry, z aparatem nad środkiem części.”

### 4.5 Post-processing konturu (`OutlinePostProcessor`, czysty Kotlin)

Wejście: `RawContours(outerPx, holesPx, pxPerMm = 10.0)`, `thicknessMm`, `pose: CameraPose?`, `sheetScale` (już uwzględniony
w homografii). Wyjście: `PhotoOutline(outer: Ring, innerContours: List<Ring>, holeCandidates: List<HoleCandidate>, warnings)`.

1. px → mm arkusza (`PixelMapping`), osobno obrys i dziury.
2. Korekcja grubości (4.4) dla wszystkich punktów (obrys i dziury).
3. Środek: `c = centroid(outer)` (centroid pola pierścienia zewnętrznego, w mm arkusza).
   Konwersja do układu części: `X = x − c.x`, `Y = −(y − c.y)` (odwrócenie osi Y). Po odwróceniu osi zmienia się orientacja —
   zawsze wywołaj `normalized()` (outer CCW, holes CW).
4. Korekta półpiksela: `findContours` zwraca środki pikseli **pierwszego planu** na brzegu. Dlatego:
   obrys zewnętrzny jest za mały o 0,5 px na stronę → `outer = offset(outer, +0.5/k)` (= +0,05 mm);
   kontury dziur leżą na pikselach części otaczających dziurę, więc dziura jest za duża o 0,5 px na stronę →
   każdą dziurę (jako wielokąt) `offset(hole, −0.5/k)`. Test syntetyczny (4.13) weryfikuje znak; jeśli wychodzi
   systematyczny błąd ~0,1 mm na średnicy, popraw znak i zapisz to w `DECISIONS.md`.
   Offset: `GeometryOps.offset(ring, d, JoinStyle.MITRE)`.
5. Uproszczenie: `TopologyPreservingSimplifier.simplify(polygon, 0.1)` (tolerancja 0,1 mm) — na całym wielokącie z dziurami.
6. Odrzuć dziury o polu < 3 mm².
7. Walidacja: `polygon.isValid`; jeśli nie — `GeometryFixer.fix(polygon)` i wybierz największy wielokąt.
8. Wykrywanie otworów okrągłych: dla każdej dziury (w mm): dopasowanie okręgu `CircleFit.kasa(points)`;
   `circularity = 4π·A / P²`; `rms` = pierwiastek ze średniej kwadratów `(|p − c| − r)`.
   Jeśli `circularity ≥ 0.85` i `rms ≤ max(0.10, 0.02·r)` → `HoleCandidate(center, diameter = round(2r, 0.05))`
   i dziura NIE trafia do `innerContours`. Pozostałe dziury → `innerContours`.
9. Edytor tworzy z kandydatów `HoleFeature(id = "hole-N", reference = ORIGINAL, fit = SLIDE, needsCaliperCheck = true)`.

**Dopasowanie okręgu Kåsa** (`CircleFit.kasa(points: List<Vec2>): Circle(center, radius)`), min. 3 punkty:
1. Wyśrodkuj: `m = średnia punktów`, `q_i = p_i − m`.
2. Rozwiąż metodą najmniejszych kwadratów `D·x + E·y + F = −(x² + y²)` dla `q_i`: równania normalne 3×3
   `AᵀA·[D,E,F]ᵀ = Aᵀb`, gdzie wiersz A = `[x, y, 1]`, b = `−(x² + y²)`; rozwiąż eliminacją Gaussa z częściowym wyborem.
3. `center = m + (−D/2, −E/2)`, `r = sqrt(D²/4 + E²/4 − F)`.

Uwaga dla użytkownika (stała w UI): średnice otworów ze zdjęcia są tylko orientacyjne (paralaksa ścian otworu zmniejsza
widoczny prześwit) — zawsze zmierz suwmiarką.

### 4.6 Operacje 2D na JTS (`GeometryOps`)

Most `Shape2 ↔ org.locationtech.jts.geom.Polygon` (fabryka `GeometryFactory()` z domyślną precyzją):
- `toJts(shape)`: `LinearRing` wymaga powtórzenia pierwszego punktu na końcu — dodaj przy konwersji.
- `fromJts(polygon)`: usuń powtórzony ostatni punkt, `normalized()`.
- `fromJtsMulti(geom)`: dla `MultiPolygon` zwróć listę; większość operacji wymaga jednego wielokąta — jeśli wynik ma > 1 wielokąt,
  zwróć błąd domenowy `GeometryException("Kształt rozpadł się na kilka części")` (wyjątek: `footprint`).

Funkcje:
```kotlin
enum class JoinStyle { MITRE, ROUND }
fun offset(shape: Shape2, distanceMm: Double, join: JoinStyle = JoinStyle.MITRE): Shape2
    // BufferParameters: joinStyle = JOIN_MITRE + mitreLimit = 3.0, albo JOIN_ROUND z quadrantSegments = 8
    // dodatni distance powiększa obrys i ZMNIEJSZA dziury (semantyka JTS)
fun offsetRing(ring: Ring, distanceMm: Double, join: JoinStyle = JoinStyle.MITRE): Ring  // ring jako pełny wielokąt
fun difference(a: Shape2, cutters: List<Shape2>): Shape2
fun intersection(a: Shape2, b: Shape2): Shape2
fun union(shapes: List<Shape2>): List<Shape2>
fun simplify(shape: Shape2, toleranceMm: Double): Shape2   // TopologyPreservingSimplifier
fun isValid(shape: Shape2): Boolean
```

### 4.7 Ekstruzja do siatki (`MeshBuilder`)

`MeshBuilder.buildShells(solid: Solid): List<Mesh>` = lista siatek, po jednej na graniastosłup (każdy prism = osobna, szczelna powłoka);
`MeshBuilder.build(solid: Solid): Mesh` = ich suma (`plus`).
Nakładające się/stykające powłoki są dopuszczalne: slicery (PrusaSlicer, OrcaSlicer, Bambu Studio, Cura) sumują bryły
jednego obiektu. Wszystkie granice z graniastosłupów z `transform == IDENTITY` są wielokrotnością `layerHeightMm`
(zaokrąglaj: `round(z / h) · h`), żeby płaszczyzny cięcia slicera nie wypadały na stykach.

`prismMesh(p: Prism): Mesh`:
1. `s = p.shape.normalized()`; wszystkie pierścienie `cleaned()`.
2. Indeks wierzchołków: mapa klucz → indeks, klucz = `(round(x·1e6), round(y·1e6), poziom)` gdzie poziom ∈ {BOTTOM, TOP}.
   Dla każdego punktu każdego pierścienia twórz wierzchołek dolny `(x, y, z0)` i górny `(x, y, z1)`.
3. Ściany boczne: dla każdego pierścienia (zewnętrzny CCW i dziury CW — ta sama formuła!) i każdej krawędzi `i → i+1 (mod n)`:
   `b_i, b_{i+1}` (dół), `t_i, t_{i+1}` (góra): trójkąty `(b_i, b_{i+1}, t_{i+1})` i `(b_i, t_{i+1}, t_i)`.
   Sprawdzenie: krawędź od (0,0) do (1,0) pierścienia CCW → normalna (0,−1,0), czyli na zewnątrz. Dla dziur CW normalna
   wskazuje do wnętrza dziury, czyli też „na zewnątrz materiału”.
4. Pokrywy: `ConstrainedDelaunayTriangulator.triangulate(jtsPolygon)` → `GeometryCollection` trójkątów (`Polygon`).
   Każdy trójkąt: 3 pierwsze współrzędne → indeksy przez mapę (BOTTOM i TOP). Jeśli współrzędnej nie ma w mapie →
   `IllegalStateException("Triangulator added vertex")` (nie powinno się zdarzyć; triangulator nie dodaje punktów).
   Orientacja: policz pole ze znakiem trójkąta w XY; dla TOP wymagaj CCW (normalna +Z), dla BOTTOM — odwrotnej kolejności (normalna −Z).
5. Transformacja: każdy wierzchołek `p.transform.apply(v)`. Jeśli `determinant() < 0`, odwróć kolejność wszystkich trójkątów.
6. Siatka końcowa: `normalizedForPrint()` wykonuje eksport, nie `MeshBuilder`.

`MeshValidator.check(mesh): MeshReport`:
- Krawędzie skierowane z każdego trójkąta `(a,b), (b,c), (c,a)`. `isConsistentlyOriented` ⇔ każda krawędź skierowana występuje
  dokładnie raz. `isClosed` ⇔ dla każdej krawędzi `(a,b)` istnieje dokładnie jedna `(b,a)`.
- `volumeMm3 = Σ v_a · (v_b × v_c) / 6` (dodatni dla poprawnej orientacji).
- `degenerateTriangles` = trójkąty o polu < 1e-9 mm².
Eksport (UI) blokuje zapis, gdy `!isClosed || !isConsistentlyOriented || volumeMm3 <= 0` i pokazuje „Błąd siatki — zgłoś projekt”.

### 4.8 Budowa części z obrysu, otwory, fazki (`OutlineBuilder`)

`OutlineBuilder.build(d: OutlineDesign, comp: CompensationTable, print: PrintSettings, heightOverrideMm: Double? = null, includeChamfers: Boolean = true): BuildResult`

Definicje:
- `T = heightOverrideMm ?: d.thicknessMm`, `h = print.layerHeightMm`.
- `bOut = comp.bias(OUTLINE)`, `bSlot = comp.bias(SLOT)`, `bHole = comp.bias(HOLE)`.
- Obrys CAD: `outerCad = offsetRing(d.outer, −bOut/2, MITRE)`.
- Wewnętrzne kontury CAD: dla każdego `r`: `offsetRing(r jako wielokąt, −bSlot/2, MITRE)` (bSlot zwykle ujemny → wycięcie rośnie).
- Otwór: `target = if (reference == ORIGINAL) nominal else nominal + fit.clearanceMm`; `cad = target − bHole`.

**Budowa warstwowa** (jednolity mechanizm dla fazek i stopy słonia):
1. Punkty podziału z: `{0, T}`, `h` (jeśli `print.elephantFootMm > 0`), oraz dla każdej fazki `c > 0` (otworu lub górnej):
   `n = max(1, ceil(c / h))` schodków; podziały `T − k·h` dla k = 1..n. Zaokrąglij wszystkie do wielokrotności h, odrzuć
   ≤ 0 i ≥ T, posortuj, usuń duplikaty.
2. Dla każdego przedziału `[za, zb]` oblicz `zm = (za + zb)/2` i kształt `shapeAt(zm)`:
   - `outer(zm)`: `outerCad`; jeśli `topChamferMm > 0` i `zm > T − nTop·h`: krok `k = ceil((T − zm)/h)` (1 = najwyższy),
     inset = `topChamfer · (nTop − k + 1) / nTop` → `offsetRing(outerCad, −inset)`.
   - otwór o średnicy `cad`; jeśli `chamferMm > 0` i `zm > T − n·h`: krok k jak wyżej, średnica `cad + 2·chamfer·(n − k + 1)/n`.
   - `shape = difference(outer(zm), innerContoursCad + kółka otworów)`.
   - stopa słonia: jeśli `zb <= h + 1e-9` i `elephantFootMm > 0`: `shape = offset(shape, −elephantFootMm, MITRE)`
     (zmniejsza obrys i powiększa otwory). Jeśli wynik pusty lub pole < 50% oryginału → pomiń, dodaj ostrzeżenie
     „Pominięto kompensację stopy słonia (zbyt cienkie ścianki)”.
   - `Prism(shape, za, zb)`.
3. Cechy (`ResolvedFeature`):
   - `"outline"`, label „Obrys (szerokość X)”, kind OUTLINE, ORIGINAL, `nominal = d.outer.bounds().width`,
     `target = nominal`, `cad = outerCad.bounds().width`.
   - każdy otwór: `"hole-N"`, label „Otwór HN Ø”, kind HOLE.
   - każdy wewnętrzny kontur: `"slot-N"`, label „Wycięcie SN (szerokość X)”, kind SLOT, ORIGINAL, nominal = szerokość bbox.
   - `biasMm` = użyta kompensacja danego typu.
4. Walidacja wejścia (błędy w PL): `thicknessMm` w [0.4, 200]; każdy otwór: `cad ≥ 0.8`, cały okrąg otworu wewnątrz
   `outerCad` z ścianką ≥ 0.8 mm (sprawdź: `outerPolygon.buffer(−0.8).contains(circle)`), otwory nie nachodzą na siebie.

**Plaster próbny dla obrysu:** `build(d, comp, print, heightOverrideMm = min(d.thicknessMm, 3.0), includeChamfers = false)`.

**Przykład (test):** obrys kwadrat 20×20 (od −10 do 10), otwór Ø10 ORIGINAL w (0,0), T = 5, kompensacja OUTLINE +0,2,
HOLE −0,3, stopa słonia 0 → kształt CAD: kwadrat 19,8×19,8 (od −9,9 do 9,9), otwór Ø10,3; jeden prism z0=0, z1=5;
objętość siatki ≈ (19,8² − π·5,15²)·5 = (392,04 − 83,323)·5 ≈ 1543,6 mm³ (tolerancja ±1,5 mm³ przez wielokąt koła).

### 4.9 Kompensacja — wzory

Definicja: **bias** = średnicowy błąd druku dla typu cechy = `wymiar_wydrukowany − wymiar_CAD`.
Typowo: otwory drukują się za małe (bias HOLE ujemny, −0,2…−0,4), czopy i obrysy za duże (bias dodatni, +0,05…+0,2).

1. **Wymiar docelowy** (`FitMath.target(kind, reference, fit, nominal)`):
   - `reference == ORIGINAL` → `target = nominal`.
   - `reference == MATING` i kind wewnętrzny (HOLE, SLOT) → `target = nominal + fit.clearanceMm`.
   - `reference == MATING` i kind zewnętrzny (PIN, OUTLINE) → `target = nominal − fit.clearanceMm`.
2. **Wymiar CAD** (`FitMath.cad(target, bias)`) = `target − bias`.
   Dla obrysów stosowanych przez offset: offset promieniowy = `−bias/2`.
3. **Próbka z oceny jakościowej** (`FeedbackInterpreter.sample(kind, biasUsed, feedback)`), a = kwota (mm, średnicowo, ≥ 0):
   - wewnętrzne (HOLE, SLOT): `TOO_TIGHT → biasUsed − a`; `TOO_LOOSE → biasUsed + a`; `OK → biasUsed`.
   - zewnętrzne (PIN, OUTLINE): `TOO_TIGHT → biasUsed + a`; `TOO_LOOSE → biasUsed − a`; `OK → biasUsed`.
   Uzasadnienie: „otwór za ciasno o a” = wydrukowany otwór jest o a mniejszy od docelowego, więc prawdziwy błąd jest o a
   bardziej ujemny; następny CAD = target − (biasUsed − a) = poprzedni CAD + a.
4. **Próbka z pomiaru suwmiarką** (`Feedback.Measured`): `sample = measured − cadMm` (cadMm z `ResolvedFeature`).
5. Każda próbka jest **bezwzględnym oszacowaniem** biasu (nie przyrostem) — dlatego można je uśredniać bez podwójnego liczenia.

**Uczenie / uśrednianie** (`CompensationEstimator.estimate(prior: Double, samples: List<BiasSample>): Double`):
Stałe: `PRIOR_WEIGHT = 0.5`, `DECAY = 0.7`, `MAX_SAMPLES = 10`, `OUTLIER_MM = 0.3`, `CLAMP_MM = 1.0`.
1. Posortuj próbki malejąco po `createdAtMillis` (stabilnie), weź pierwsze 10.
2. Jeśli próbek ≥ 3: `med` = mediana (dla parzystej liczby — średnia dwóch środkowych); odrzuć próbki z `|s − med| > 0.3`.
3. Wagi po filtrowaniu: najnowsza `w_0 = 1`, kolejne `w_i = 0.7^i`.
4. `bias = (0.5·prior + Σ w_i·s_i) / (0.5 + Σ w_i)`; obetnij do [−1.0, 1.0].
5. Brak próbek → `prior`.

`prior` = `MaterialDefaults.bias(material, kind)`:

| Material | HOLE | SLOT | PIN | OUTLINE |
|---|---|---|---|---|
| PLA | −0.25 | −0.20 | +0.10 | +0.10 |
| PETG | −0.30 | −0.25 | +0.15 | +0.15 |
| ABS | −0.30 | −0.25 | +0.05 | 0.00 |
| ASA | −0.30 | −0.25 | +0.05 | 0.00 |
| TPU | −0.35 | −0.30 | +0.20 | +0.20 |
| OTHER | jak PLA | | | |

Uzasadnienie: typowe obserwacje FDM (dysza 0,4, warstwa 0,2): otwory 0,2–0,4 mm za małe, ścianki zewnętrzne lekko za duże;
PETG bardziej „puchnie”, ABS/ASA kurczą się. To tylko wartości startowe — kupon kalibracyjny je szybko poprawia.

**Stosowanie:** `CompensationRepository.tableFor(printerId, filamentId): CompensationTable` — dla każdego kind:
`rule.biasMm` jeśli reguła istnieje, inaczej prior materiału. Przeliczenie (`recompute(printerId, filamentId, kind)`) po
każdym zapisie wyników i po zmianie `includeInLearning`: jeśli `manualOverride` → aktualizuj tylko `sampleCount`;
inaczej `biasMm = estimate(prior, próbki)`.

**Parser notatki** (`FeedbackNoteParser.parse(text): ParsedNote`):
1. `t = text.lowercase(Locale.ROOT)`.
2. Werdykt (pierwsze dopasowanie wygrywa w tej kolejności):
   - TOO_TIGHT: regex `ciasn|nie wchodzi|nie wejdzie|za grub|za szerok` (dla otworu: „za ciasno”).
   - TOO_LOOSE: regex `luźn|luzn|lata|wypada|za cienk|za wąsk|za wask|za duży luz|za duzy luz`.
   - OK: regex `\bok\b|pasuje|idealn|dobrze`.
3. Kwota: pierwsze dopasowanie `(\d+(?:[.,]\d+)?)\s*mm` albo, gdy brak „mm”, pierwsza liczba `(\d+[.,]\d+|\d+)`;
   zamień `,` na `.`; akceptuj tylko 0 < a ≤ 2.0, inaczej null.
4. Przykłady: „za ciasno o 0,2 mm” → (TOO_TIGHT, 0.2); „trochę luźno, 0.1” → (TOO_LOOSE, 0.1);
   „pasuje idealnie” → (OK, null); „nie wchodzi” → (TOO_TIGHT, null); „” → (null, null); „za ciasno o 5 mm” → (TOO_TIGHT, null).
Parser tylko **wypełnia formularz** — użytkownik zawsze widzi i zatwierdza werdykt i kwotę.

### 4.10 Szablony parametryczne

Wspólne: wszystkie prismy z `z0 = 0` i `transform = IDENTITY` przechodzą przez `ElephantFoot.apply(solid, print)` —
dzieli prism na `[0, h]` z kształtem `offset(shape, −elephantFootMm)` i `[h, z1]` (te same reguły pomijania co w 4.8).
Każdy szablon implementuje:
```kotlin
interface PartTemplate {
    val id: TemplateId
    val displayName: String      // PL
    val description: String      // PL, 1–2 zdania
    val params: List<ParamSpec>
    val fitSlots: List<FitSlotSpec>
    fun validate(input: TemplateInput, comp: CompensationTable): List<ValidationError>
    fun build(input: TemplateInput, comp: CompensationTable, print: PrintSettings): BuildResult
    fun buildFitTest(input: TemplateInput, comp: CompensationTable, print: PrintSettings): BuildResult
}
```
`validate` sprawdza najpierw zakresy z `ParamSpec` (komunikat: „<label>: wartość spoza zakresu <min>–<max>”), potem reguły szablonu.
`build` wywołuje `validate` i rzuca `IllegalArgumentException` przy błędach (UI wywołuje `validate` wcześniej).
Skróty: `circle(D)` = okrąg o średnicy D w (0,0); `ring(Do, Di)` = `Shape2(circle(Do), [circle(Di).reversed()])`;
`bX = comp.bias(X)`; `t(...)` = `FitMath.target`.

**BUSHING — „Tuleja”**

| key | label | typ | default | min | max |
|---|---|---|---|---|---|
| shaftDiameter | Średnica wałka (wewnątrz) | MM | 8.0 | 1 | 100 |
| housingDiameter | Średnica otworu oprawy (na zewnątrz) | MM | 12.0 | 2 | 150 |
| length | Długość | MM | 10.0 | 1 | 200 |
| flangeDiameter | Średnica kołnierza (0 = brak) | MM | 0.0 | 0 | 200 |
| flangeThickness | Grubość kołnierza | MM | 2.0 | 0.6 | 20 |

Fit slots: `innerFit` „Pasowanie na wałku” = SLIDE; `outerFit` „Pasowanie w oprawie” = PRESS.
Obliczenia: `idCad = t(HOLE, MATING, innerFit, shaftDiameter) − bHOLE`; `odCad = t(PIN, MATING, outerFit, housingDiameter) − bPIN`;
`flCad = flangeDiameter − bOUTLINE`.
Walidacja: `idCad + 1.6 ≤ odCad` („Ścianka tulei < 0,8 mm”); jeśli flangeDiameter > 0: `flangeDiameter ≥ housingDiameter + 2`
i `flangeThickness < length`.
Bryła: bez kołnierza `Prism(ring(odCad, idCad), 0, length)`; z kołnierzem `Prism(ring(flCad, idCad), 0, ft)` +
`Prism(ring(odCad, idCad), ft, length)`.
Cechy: `inner` (HOLE), `outer` (PIN), opcjonalnie `flange` (OUTLINE, ORIGINAL).
Plaster: `Prism(ring(odCad, idCad), 0, min(length, 5.0))`, cechy inner, outer.
Przykład (test): shaft 8,0 SLIDE, housing 12,0 PRESS, bHOLE −0,25, bPIN +0,10 → idCad = 8,40, odCad = 11,90.

**KNOB — „Pokrętło”**

| key | label | typ | default | min | max |
|---|---|---|---|---|---|
| shaftDiameter | Średnica ośki | MM | 6.0 | 2 | 30 |
| shaftFlat | Wymiar na spłaszczeniu (0 = okrągła) | MM | 4.5 | 0 | 30 |
| holeDepth | Głębokość otworu | MM | 10.0 | 2 | 100 |
| knobDiameter | Średnica pokrętła | MM | 30.0 | 8 | 150 |
| knobHeight | Wysokość pokrętła | MM | 15.0 | 3 | 100 |
| gripCount | Liczba rowków chwytu (0 = gładkie) | COUNT | 18 | 0 | 60 |
| gripDepth | Głębokość rowków | MM | 0.8 | 0.2 | 3 |

Fit slot: `shaftFit` „Pasowanie na ośce” = PRESS.
Obliczenia: `dCad = t(HOLE, MATING, shaftFit, shaftDiameter) − bHOLE`; `flatCad = t(HOLE, MATING, shaftFit, shaftFlat) − bHOLE`.
Otwór D: `intersection(circle(dCad), prostokąt [−dCad, dCad] × [−dCad, flatCad − dCad/2])` (spłaszczenie po stronie +Y);
gdy `shaftFlat == 0` — sam okrąg.
Obrys pokrętła: `R = knobDiameter/2 − bOUTLINE/2`; `n = max(gripCount·16, 96)`; `r(θ) = R − gripDepth·(0.5 − 0.5·cos(gripCount·θ))`
(gdy gripCount = 0: okrąg R).
Walidacja: `holeDepth ≤ knobHeight − 1`; jeśli shaftFlat > 0: `shaftDiameter/2 < shaftFlat < shaftDiameter`;
`dCad + 4 ≤ knobDiameter − 2·gripDepth`.
Bryła: `Prism(knob − D, 0, holeDepth)` + `Prism(knob, holeDepth, knobHeight)` (otwór od spodu, płaski wierzch).
Cechy: `shaft` (HOLE, Ø), `flat` (HOLE, wymiar na płasku; tylko gdy shaftFlat > 0).
Plaster: `Prism(circle(dCad + 6) − D, 0, min(holeDepth, 6.0))`.

**CAP — „Zaślepka” (do rury lub profilu)**

| key | label | typ | default | min | max |
|---|---|---|---|---|---|
| square | Profil kwadratowy (0 = rura okrągła) | BOOL | 0 | 0 | 1 |
| tubeInner | Wewnętrzny wymiar rury/profilu (Ø lub bok) | MM | 20.0 | 3 | 150 |
| plugLength | Długość wtyku | MM | 8.0 | 2 | 50 |
| capSize | Wymiar kapturka (Ø lub bok, zwykle = zewn. wymiar rury) | MM | 22.0 | 4 | 200 |
| capThickness | Grubość kapturka | MM | 2.0 | 0.8 | 10 |
| wall | Ścianka wtyku (0 = pełny) | MM | 2.0 | 0 | 20 |

Fit slot: `plugFit` „Pasowanie wtyku” = PRESS.
Kind wtyku: `PIN` gdy okrągły, `OUTLINE` gdy kwadratowy. `plugCad = t(kind, MATING, plugFit, tubeInner) − b(kind)`;
`capCad = capSize − bOUTLINE`.
Kształty: okrągłe — `circle`; kwadratowe — `roundedSquare(side, r = 1.0)` = kwadrat o boku `side − 2r` wyśrodkowany,
`offset(+r, ROUND)`.
Walidacja: `capSize ≥ tubeInner + 1`; jeśli wall > 0: `plugCad − 2·wall ≥ 1.0`.
Bryła: `Prism(cap, 0, capThickness)` + `Prism(plug [− wewnętrzny kształt o wymiarze plugCad − 2·wall gdy wall > 0], capThickness, capThickness + plugLength)`.
Cechy: `plug`, `cap` (OUTLINE ORIGINAL).
Plaster: kapturek grubości 1.2 + wtyk o długości `min(plugLength, 5.0)`.

**BRACKET — „Wspornik kątowy L”**

| key | label | typ | default | min | max |
|---|---|---|---|---|---|
| width | Szerokość | MM | 20.0 | 5 | 200 |
| legA | Długość ramienia poziomego | MM | 30.0 | 5 | 200 |
| legB | Wysokość ramienia pionowego | MM | 30.0 | 5 | 200 |
| thickness | Grubość | MM | 3.0 | 1.2 | 20 |
| screwDiameter | Średnica śruby (0 = bez otworów) | MM | 4.0 | 0 | 20 |
| holesPerLeg | Otworów na ramię | COUNT | 1 | 1 | 2 |

Fit slot: `holeFit` „Pasowanie śruby” = LOOSE. `holeCad = t(HOLE, MATING, holeFit, screwDiameter) − bHOLE`.
Walidacja: `thickness < min(legA, legB)`; jeśli screwDiameter > 0: `holeCad + 3 ≤ min(legA − thickness, legB − thickness)`
i `holeCad + 3 ≤ width / holesPerLeg`.
Otwory: środki wzdłuż szerokości `w_i = width·(i + 0.5)/holesPerLeg`.
- Płyta pozioma: kształt w XY: prostokąt [0, legA]×[0, width] minus okręgi w `(thickness + (legA − thickness)/2, w_i)`;
  `Prism(shape, 0, thickness)`.
- Płyta pionowa: kształt w lokalnym (u, v): prostokąt [0, width]×[0, legB] minus okręgi w `(w_i, thickness + (legB − thickness)/2)`;
  `Prism(shape, 0, thickness, Transform3.fromAxes(xAxis = (0,1,0), yAxis = (0,0,1), zAxis = (1,0,0)))`
  (u → świat Y, v → świat Z, ekstruzja → świat X; wyznacznik +1). Płyty nachodzą na siebie w narożniku — to zamierzone.
Cechy: `hole` (HOLE) — jedna cecha dla wszystkich otworów.
Plaster: tylko płyta pozioma, wysokość `min(thickness, 2.0)`.

**SPUR_GEAR — „Koło zębate (ewolwentowe, α = 20°)”**

| key | label | typ | default | min | max |
|---|---|---|---|---|---|
| teeth | Liczba zębów | COUNT | 20 | 8 | 150 |
| tipDiameter | Średnica wierzchołków (zmierzona) | MM | 22.0 | 5 | 300 |
| thickness | Grubość koła | MM | 6.0 | 1 | 50 |
| boreDiameter | Średnica osi (0 = bez otworu) | MM | 5.0 | 0 | 100 |
| backlash | Luz międzyzębny | MM | 0.1 | 0 | 0.5 |
| hubDiameter | Średnica piasty (0 = brak) | MM | 0.0 | 0 | 200 |
| hubHeight | Wysokość piasty | MM | 0.0 | 0 | 50 |

Fit slot: `boreFit` „Pasowanie na osi” = PRESS.
Moduł: `m = tipDiameter / (teeth + 2)`. UI pokazuje m i podpowiedź najbliższego modułu standardowego
{0.5, 0.6, 0.7, 0.75, 0.8, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0}, gdy różnica < 3% (bez automatycznej zmiany).
Algorytm profilu (`Involute.gearRing(teeth z, module m, alpha = 20°): Ring`):
1. `rp = m·z/2`, `rb = rp·cos α`, `ra = rp + m`, `rf = rp − 1.25·m`.
2. `inv(x) = tan x − x`; połowa kątowej grubości zęba na kole podziałowym `ψ = π/(2z)`; `φ0 = ψ + inv(α)`.
3. Punkt ewolwenty dla parametru s: promień `ρ(s) = rb·sqrt(1 + s²)`, kąt rozwinięcia `θ(s) = s − atan(s)`.
   `sMax = sqrt((ra/rb)² − 1)`, `sMin = if (rf > rb) sqrt((rf/rb)² − 1) else 0`.
4. Ząb wycentrowany na kącie 0: bok górny (y > 0) ma kąty biegunowe `φ0 − θ(s)`, bok dolny `−(φ0 − θ(s))`.
5. Dla zęba i (kąt obrotu `γ_i = 2πi/z`) dodawaj punkty CCW:
   a) jeśli `rf < rb`: punkt na `rf` pod kątem `γ_i − φ0` (odcinek radialny do koła zasadniczego);
   b) bok dolny: 12 punktów s od sMin do sMax: `(ρ(s), γ_i − (φ0 − θ(s)))`;
   c) łuk wierzchołkowy: 4 punkty wewnętrzne na `ra` równomiernie między `γ_i − (φ0 − θ(sMax))` a `γ_i + (φ0 − θ(sMax))`;
   d) bok górny: 12 punktów s od sMax do sMin: `(ρ(s), γ_i + (φ0 − θ(s)))`;
   e) jeśli `rf < rb`: punkt na `rf` pod kątem `γ_i + φ0`;
   f) łuk dna: 6 punktów wewnętrznych na `rf` między `γ_i + φ0` a `γ_{i+1} − φ0`.
   Współrzędne biegunowe (ρ, φ) → `(ρ cos φ, ρ sin φ)`. Na koniec `cleaned()`.
Kompensacja i luz: `gearCad = offset(gearRing, −(bOUTLINE/2 + backlash/4), MITRE)`; otwór `boreCad = t(HOLE, MATING, boreFit, bore) − bHOLE`.
Walidacja: `boreCad + 2 ≤ 2·rf`; jeśli hubDiameter > 0: `hubDiameter ≥ boreCad + 2` i `hubDiameter ≤ 2·rf` i hubHeight > 0.
Bryła: `Prism(gearCad − circle(boreCad), 0, thickness)` [+ `Prism(ring(hubDiameter − bOUTLINE, boreCad), thickness, thickness + hubHeight)`].
Cechy: `tip` (OUTLINE, ORIGINAL, cad = 2·max promień wierzchołka po offsecie), `bore` (HOLE, gdy > 0).
Plaster: koło bez piasty, wysokość 2.0.
Test: z = 20, m = 1 (tip 22,0), bez kompensacji: wszystkie promienie ≤ 11,0 + 1e-9, min promień ≥ 8,75 − 1e-9,
maksymalny promień = 11,0 ± 1e-9 (punkty łuku wierzchołkowego leżą na ra), wielokąt poprawny (JTS isValid),
pole w (π·8,75², π·11²).

### 4.11 Kupon kalibracyjny (`CalibrationCoupon.build(comp, print): BuildResult`)

Płytka 44×20×3 mm (współrzędne: x 0–44, y 0–20), z kompensacją:
- Obrys: prostokąt `offsetRing(rect(0,0,44,20), −bOUTLINE/2)`; cechy `plateLength` (OUTLINE, nominal 44, cad 44 − bOUT)
  i `plateWidth` (OUTLINE, nominal 20, cad 20 − bOUT).
- Otwór Ø10 w (10, 10): `cad = 10 − bHOLE`; cecha `hole` (HOLE, nominal 10).
- Szczelina: prostokąt środek (23, 10), szerokość X `6 − bSLOT`, długość Y `12 − bSLOT`; cecha `slot` (SLOT, nominal 6, mierzona szerokość).
- Czop Ø8 w (35, 10) na płytce: `Prism(circle(8 − bPIN) przesunięty do (35,10), 3.0, 11.0)`; cecha `pin` (PIN, nominal 8).
- Wszystkie z `reference = ORIGINAL` (cel = nominał). Stopa słonia przez `ElephantFoot.apply`.
Użytkownik mierzy suwmiarką i wpisuje 5 wartości → 5 próbek `Feedback.Measured`.

### 4.12 Eksport STL i 3MF

Przed eksportem: `mesh = MeshBuilder.build(solid).normalizedForPrint()`; `MeshValidator.check(mesh)` musi przejść.

**STL binarny** (`StlWriter.write(mesh, out: OutputStream, header: String = "FitLab")`), little-endian
(`ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)`):
- 80 bajtów nagłówka: ASCII nagłówka obcięte/dopełnione zerami. **Nagłówek NIE może zaczynać się od „solid”** (niektóre programy
  uznałyby plik za ASCII STL).
- `uint32` liczba trójkątów.
- Dla każdego trójkąta: normalna (3×float32, znormalizowany iloczyn wektorowy `(b−a)×(c−a)`, zero dla zdegenerowanych),
  3 wierzchołki (9×float32), `uint16` atrybut = 0. Razem 50 bajtów.
- Rozmiar pliku = `84 + 50·n`.

**3MF** (`ThreeMfWriter.write(solidMeshes: List<Mesh>, name: String, out: OutputStream)`): ZIP (`ZipOutputStream`, DEFLATED)
z dokładnie trzema wpisami:

`[Content_Types].xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>
</Types>
```
`_rels/.rels`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Target="/3D/3dmodel.model" Id="rel0" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>
</Relationships>
```
`3D/3dmodel.model` — każdy prism jako osobny `<object>` (powłoka szczelna), plus obiekt-kompozyt z `<components>`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<model unit="millimeter" xml:lang="en-US" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
  <metadata name="Title">NAZWA</metadata>
  <metadata name="Application">FitLab</metadata>
  <resources>
    <object id="1" type="model">
      <mesh>
        <vertices>
          <vertex x="0.0000" y="0.0000" z="0.0000"/>
        </vertices>
        <triangles>
          <triangle v1="0" v2="1" v3="2"/>
        </triangles>
      </mesh>
    </object>
    <!-- ... object id="2".."N" ... -->
    <object id="N+1" type="model" name="NAZWA">
      <components>
        <component objectid="1"/>
        <!-- ... -->
      </components>
    </object>
  </resources>
  <build>
    <item objectid="N+1"/>
  </build>
</model>
```
Liczby: `String.format(Locale.ROOT, "%.4f", v)`. Nazwa: escape XML (`&`, `<`, `>`, `"`, `'`). Indeksy wierzchołków od 0 w obrębie obiektu.
Wszystkie powłoki są już przesunięte przez wspólne `normalizedForPrint` (przesunięcie liczone dla całości, potem stosowane do każdej powłoki).
Dlatego `PartExporter` buduje listę siatek per prism, liczy wspólne przesunięcie i przekazuje gotowe siatki do writerów.

`PartExporter.export(solid, format: ExportFormat { STL, THREE_MF }, name, out): MeshReport`.
Nazwy plików (`FileNames.exportName(partName, type, number, format)`): slug nazwy (małe litery, „ąćęłńóśźż” → „acelnoszz”,
inne znaki niealfanumeryczne → „-”, max 40 znaków) + `_` + `fit|full|cal` + `_` + numer + `.stl`/`.3mf`.
Przykład: „Pokrętło pralki”, FIT_TEST, 2, STL → `pokretlo-pralki_fit_2.stl`.

### 4.13 Pełny pipeline zdjęcia (`PhotoPipeline.run(input: PipelineInput): PipelineResult`)

`PipelineInput(rgbaOrGray: Mat, focal35mm: Double?, thicknessMm: Double, segmentation: SegmentationParams, sheet: MarkerSheetSpec)`.
1. Szarość (jeśli 4 kanały → `COLOR_RGBA2GRAY`; jeśli 3 → `COLOR_BGR2GRAY`).
2. `MarkerDetector.detect` → `SheetRectifier.rectify` → `CameraPoseEstimator.estimate` (może zwrócić null).
3. `Segmenter.segment(rectified, params)`.
4. `OutlinePostProcessor.process(RawContours, thicknessMm, pose)`.
5. `PipelineResult(outline: PhotoOutline, rectified: Mat, mask: Mat, threshold: Int, pose: CameraPose?, warnings: List<String>)`.
Każdy `Mat` tymczasowy zwalniaj (`release()`) w `finally`. Zwracane `rectified` i `mask` zwalnia wywołujący.
Wywołanie zawsze na `Dispatchers.Default`.

---

## 5. Ekrany i nawigacja

### 5.1 Ogólne

- Jedna aktywność `MainActivity` z `FitLabNavHost`; `enableEdgeToEdge()`; każdy ekran w `Scaffold` z `TopAppBar` (tytuł PL,
  przycisk „Wstecz” jako `TextButton("Wstecz")` gdy jest poprzedni ekran). **Bez biblioteki ikon** — przyciski tekstowe.
- Motyw: Material3, `dynamicColor` na Android 12+, inaczej domyślny jasny/ciemny.
- Każdy ekran: `XxxScreen(viewModel, onNavigate...)` + `XxxViewModel` z `StateFlow<XxxUiState>`; ViewModel tworzony przez
  `viewModel(factory = viewModelFactory { initializer { XxxViewModel(app.container, savedStateHandle-args) } })`.
- Pola liczbowe: komponent `MmField(label, value: String, onValueChange, error: String?)` — klawiatura `KeyboardType.Decimal`,
  akceptuje „,” i „.”, wyświetla jednostkę „mm”.
- Długie operacje (pipeline, eksport): `CircularProgressIndicator` + blokada przycisków; błędy w `ErrorBanner` (czerwona karta z tekstem).

### 5.2 Trasy (`Routes`)

| Trasa | Ekran |
|---|---|
| `home` | HomeScreen (start) |
| `new` | NewPartScreen (wybór źródła) |
| `photo` | PhotoCaptureScreen |
| `templates` | TemplatePickerScreen |
| `template/{templateId}?partId={partId}` | TemplateEditorScreen (partId = -1 → nowa) |
| `outline/{partId}` | OutlineEditorScreen |
| `part/{partId}` | PartDetailScreen |
| `test/{iterationId}` | TestResultScreen |
| `profiles` | ProfilesScreen |
| `compensation` | CompensationScreen |
| `calibration` | CalibrationScreen |
| `markers` | MarkerSheetScreen |
| `diagnostics` | DiagnosticsScreen |

### 5.3 Ekrany

**HomeScreen „Moje części”**
- Pasek: tytuł „Moje części”; akcje tekstowe w menu przepełnienia (`DropdownMenu` pod przyciskiem „Więcej”): „Profile”,
  „Kompensacja”, „Kalibracja”, „Arkusz markerów”, „Diagnostyka”.
- Pod paskiem: karta aktywnego profilu: „Drukarka: X · Filament: Y (PLA)” — klik → `profiles`.
- Lista (`LazyColumn`) kart części: miniatura 64 dp (`ShapeCanvas` z `Solid.footprint()`), nazwa, chip statusu
  („Szkic”/„Testowanie”/„Gotowe”), „Próby: N”, data ostatniej zmiany. Klik → `part/{id}`.
- Stan pusty: tekst „Brak części. Dodaj pierwszą — ze zdjęcia albo z szablonu.” + przycisk „Nowa część”.
- FAB „+ Nowa część” → `new`.

**NewPartScreen** — dwie duże karty: „Ze zdjęcia” (opis: płaska część, arkusz z markerami) → `photo`;
„Z szablonu” (tuleja, pokrętło, zaślepka, wspornik, koło zębate) → `templates`. Link „Nie masz arkusza? Wydrukuj arkusz markerów” → `markers`.

**PhotoCaptureScreen** (stany: `Instructions` → `HasPhoto` → `Processing` → `Result` | `Error`)
- Instructions: lista wskazówek: „Połóż kartkę na płaskim stole”, „Część w ramce pola roboczego”, „Rób zdjęcie z góry,
  z aparatem nad środkiem części, z wysokości ~30–50 cm”, „Rozproszone światło, bez lampy błyskowej”,
  „Jasna część? Połóż pod nią ciemną kartkę w polu roboczym”. Przyciski: „Zrób zdjęcie” (`TakePicture` do URI z FileProvider
  w `cacheDir/capture/`), „Wybierz z galerii” (`PickVisualMedia(ImageOnly)`).
- HasPhoto: miniatura zdjęcia, pole `MmField("Grubość części (zmierz suwmiarką)")` wymagane (0,4–200), pole „Nazwa części”
  (domyślnie „Część ze zdjęcia DD.MM”), przycisk „Przetwórz”.
- Processing: spinner „Wykrywanie markerów i obrysu…”.
- Result: obraz zrektyfikowany (przycięty do pola roboczego) z nałożonym obrysem (kolor primary, 2 dp) i otworami (kolor tertiary);
  wymiary bbox „Szer. × Wys.: 41,2 × 25,1 mm”; lista ostrzeżeń (karty żółte); suwak „Próg” z przełącznikiem „Auto (Otsu)”
  — zmiana uruchamia ponownie tylko segmentację + post-processing (homografia z cache); przyciski „Ponów zdjęcie” i „Dalej”.
  „Dalej”: kopiuje zdjęcie do `filesDir/photos/<uuid>.jpg`, tworzy `PartEntity(source = PHOTO, status = DRAFT,
  designJson = OutlineDesign(...))`, zapisuje `Measurement("Grubość", t, "thickness")`, nawiguje do `outline/{id}` (usuwając `photo` ze stosu).
- Error: komunikat z `PipelineException` + „Spróbuj ponownie”.

**TemplatePickerScreen** — lista 5 kart (`displayName`, `description`). Klik → `template/{id}?partId=-1`.

**TemplateEditorScreen**
- Sekcja „Wymiary”: dla każdego `ParamSpec`: MM → `MmField` (z `hint` pod polem), COUNT → pole liczbowe całkowite,
  BOOL → `Switch`. Sekcja „Pasowania”: dla każdego `FitSlotSpec` → `FitClassSelector` (3 `FilterChip`: „Wcisk”, „Suwliwe”, „Luźne”).
- Podgląd: `ShapeCanvas` z `footprint()` aktualnej bryły (przeliczanie z debounce 300 ms), pod spodem tabela cech:
  „Cecha | Nominał | Cel | Kompensacja | CAD” (z `BuildResult.features`, liczby z 2 miejscami po przecinku).
- Dla SPUR_GEAR: wiersz informacyjny „Moduł m = 1,00 (standard: 1,0)”.
- Błędy walidacji pod polami (`ValidationError.key`) lub na górze (key == null). Przycisk „Zapisz” aktywny tylko bez błędów.
- Pole „Nazwa części” (domyślnie `displayName`). Zapis: nowa → `PartEntity(source = TEMPLATE)`, zapis pomiarów MM jako
  `MeasurementEntity(label, value, targetKey = key)`; istniejąca → update `designJson`. Potem → `part/{id}`.

**OutlineEditorScreen**
- Główny obszar: `ShapeCanvas` (pełna szerokość, wysokość 300 dp): obrys, wewnętrzne kontury (przerywane), otwory z etykietami
  H1, H2…, siatka co 10 mm, wymiary bbox. Odwzorowanie mm → ekran: dopasowanie bbox z marginesem 16 dp, oś Y w górę.
- Tryb „Dodaj otwór”: przełącznik; dotknięcie na kanwie → dialog „Średnica otworu (mm)” → nowy `HoleFeature`.
- Panel (przewijany `Column`):
  - „Grubość” `MmField`.
  - „Wymiar referencyjny”: pola „Zmierzona szerokość X” i „Zmierzona wysokość Y” (opcjonalne) + przycisk „Zastosuj skalę”:
    `sx = W_zmierz / W_bbox`, `sy` analogicznie; jeśli podano tylko jedno — skala jednolita. Skaluje obrys, kontury i środki
    otworów (średnic otworów NIE skaluje). Zapisuje `Measurement`.
  - Przyciski: „Obróć 90°” (`(x, y) → (−y, x)`), „Lustro” (`x → −x`, potem `normalized()`).
  - „Fazka górnej krawędzi” `MmField` (0 = brak).
  - Lista otworów: wiersz na otwór: etykieta, `MmField` średnicy (z ostrzeżeniem „zmierz suwmiarką” gdy `needsCaliperCheck`
    — znika po edycji), wybór „Zmierzono: oryginalny otwór / wałek współpracujący”, przy wałku `FitClassSelector`,
    `MmField` „Fazka”, przycisk „Usuń”.
  - Lista wewnętrznych konturów: „Wycięcie S1 (12,1 × 6,0 mm)” + przycisk „Zamień na otwór” (dopasowanie Kåsa → `HoleFeature`) / „Usuń”.
- Dolny pasek: „Zapisz” (walidacja `OutlineBuilder` jak w 4.8; błędy pokazane w `ErrorBanner`) → `part/{id}`.

**PartDetailScreen**
- Nagłówek: nazwa (edycja w dialogu), status (`SegmentedButton`: Szkic / Testowanie / Gotowe), notatki (pole wieloliniowe, zapis przy wyjściu).
- Podgląd `ShapeCanvas` + przycisk „Edytuj” (→ `outline/{id}` albo `template/{templateId}?partId={id}`).
- Karta „Kompensacja dla: Drukarka · Filament”: 4 wiersze bias (np. „Otwory: −0,30 mm”).
- Tabela cech (z bieżącą kompensacją): Cecha | Nominał | Cel | CAD.
- Przyciski: „Wydruk próbny (plaster)” i „Pełna część” → `ExportSheet` (bottom sheet): wybór formatu (STL / 3MF, domyślnie 3MF),
  informacja „Trójkąty: N, wymiary: X × Y × Z mm”, przyciski „Udostępnij” i „Zapisz plik…”. Po udanym eksporcie: zapis
  `TestIterationEntity` (number = max + 1, migawki JSON, format, nazwa pliku), status części → TESTING (jeśli był DRAFT),
  Snackbar „Zapisano próbę #N. Po wydruku wpisz wynik.”
- „Historia prób”: lista iteracji: „#3 · Plaster · 12.10 · 3MF · wynik: za ciasno (otwór)” / „wynik: brak” — klik → `test/{id}`.

**TestResultScreen**
- Nagłówek: „Próba #N — Plaster / Pełna część / Kalibracja”, data, profil.
- Pole „Notatka z próby” (wieloliniowe) + przycisk „Wypełnij z notatki” (parser 4.9; wypełnia werdykt/kwotę cechy
  zaznaczonej jako „bieżąca” — domyślnie pierwszej cechy bez wyniku).
- Karta per cecha (`ResolvedFeature`): etykieta, „CAD: 10,30 mm · cel: 10,00 mm”, `VerdictSelector` (4 `FilterChip`:
  „Za ciasno”, „OK”, „Za luźno”, „Zmierzyłem”), przy ciasno/luźno — chipy kwoty `0,05 / 0,1 / 0,2 / 0,3 / 0,5` + pole „inna”;
  przy „Zmierzyłem” — `MmField("Zmierzony wymiar")`; opcja „Pomiń tę cechę” (domyślnie wszystkie cechy bez werdyktu są pomijane).
  Podpowiedź: „Kwota = o ile średnica/szerokość powinna być większa lub mniejsza”.
- Checkbox „Ucz kompensację na podstawie tej próby” (domyślnie zaznaczony; odznaczony → `includeInLearning = false`).
- „Zapisz wynik”: `FitResultEntity` dla każdej ocenionej cechy, `resultRecordedAt = now`, `recompute` dla dotkniętych typów,
  potem dialog podsumowania: „Kompensacja zaktualizowana: Otwory −0,25 → −0,38 mm”. Ponowne otwarcie ekranu pokazuje zapisane
  wyniki (edycja = usunięcie starych wyników tej iteracji i zapis nowych + recompute).

**ProfilesScreen** — dwie sekcje: „Drukarki” i „Filamenty”; wiersz z `RadioButton` (aktywny) + nazwa + „Edytuj”.
Dialog edycji drukarki: nazwa, dysza, wysokość warstwy, stopa słonia. Filamentu: nazwa, materiał (`ExposedDropdownMenuBox` z `Material`),
marka, kolor. Dodanie filamentu tworzy reguły dla wszystkich drukarek z priorami materiału (`sampleCount = 0`).
Dodanie drukarki tworzy reguły dla wszystkich filamentów. Usuwanie zablokowane dla aktywnego profilu i ostatniego profilu danego rodzaju.

**CompensationScreen** (dla aktywnej pary) — 4 karty (Otwory / Szczeliny / Czopy / Obrysy): wartość bias, „Próbek: N”,
przełącznik „Ręcznie” + `MmField` (zapis `manualOverride = true`), przycisk „Przywróć domyślne” (usuwa override, ustawia prior,
`recompute`). Pod spodem lista próbek (`FitResult`) danego typu: data, część, werdykt, próbka, `Switch` „Uwzględnij”.

**CalibrationScreen** — krok 1: opis kuponu + „Eksportuj kupon” (ten sam `ExportSheet`; tworzy `TestIteration(type = CALIBRATION, partId = null)`);
krok 2 (gdy istnieje kalibracja bez wyniku): 5 pól pomiarów (długość, szerokość, otwór, szczelina, czop) → zapis jako
`Feedback.Measured` → `recompute` → podsumowanie zmian.

**MarkerSheetScreen** — podgląd arkusza (bitmapa), instrukcja: „Drukuj w skali 100% (wyłącz dopasowanie do strony).
Sprawdź linijką kontrolną: 100 mm”. Przycisk „Zapisz PDF” (`CreateDocument("application/pdf")`, nazwa `fitlab_markery_A4.pdf`),
„Udostępnij PDF”. Sekcja „Korekta skali wydruku”: pola „Odległość 0–1 (oczekiwane 180,0)” i „0–3 (oczekiwane 267,0)” → `AppSettings`.

**DiagnosticsScreen** — przycisk „Test OpenCV”: ładuje `assets/selftest_photo.jpg` (wygenerowany przez test w kroku M8,
skopiowany do `app/src/main/assets`), uruchamia pipeline z grubością 0, pokazuje wynik: „OK: 40,0 × 25,0 mm, otwór Ø8,0”
lub błąd. Wyświetla też wersję OpenCV (`Core.VERSION`) i wersję aplikacji.

### 5.4 Przepływ główny (skrót)

`home` → FAB → `new` → `photo` → (zdjęcie, grubość, przetwarzanie) → `outline/{id}` → Zapisz → `part/{id}` → „Wydruk próbny” →
eksport/udostępnienie → (użytkownik drukuje) → `part/{id}` → historia → `test/{iterationId}` → zapis wyniku (uczenie) →
`part/{id}` → kolejny plaster lub „Pełna część” → status „Gotowe”.

### 5.5 Pliki i uprawnienia

- **Manifest bez żadnych uprawnień** (w szczególności BEZ `android.permission.CAMERA` — gdy jest zadeklarowane, a nieprzyznane,
  `ACTION_IMAGE_CAPTURE` rzuca `SecurityException`).
- `FileProvider` z authority `${applicationId}.fileprovider`, `res/xml/file_paths.xml`:
  `<cache-path name="capture" path="capture/"/>`, `<cache-path name="exports" path="exports/"/>`.
- Eksport: zapis do `cacheDir/exports/<nazwa>`, potem `Intent.ACTION_SEND` (type `model/stl` lub `model/3mf`,
  `FLAG_GRANT_READ_URI_PERMISSION`, `Intent.createChooser`) albo `CreateDocument` (mime `application/octet-stream`) i kopiowanie strumienia.
- `ImageLoader.load(uri, maxSide = 4000)`: `ImageDecoder.createSource(contentResolver, uri)`, `decodeBitmap { decoder, info, _ ->
  decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE; skalowanie setTargetSize gdy dłuższy bok > 4000 }`, następnie
  `Utils.bitmapToMat(bitmap, mat)` (RGBA). Ogniskowa: `ExifInterface(contentResolver.openInputStream(uri))
  .getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)`.
- OpenCV: `OpenCvInit.ensure()` wywołuje `OpenCVLoader.initLocal()` raz (w `FitLabApp.onCreate`); jeśli false — Diagnostyka pokazuje błąd.

---

## 6. Strategia testów

### 6.1 Zasady

- Testy rdzenia: JUnit Jupiter w `geometry/core/src/test` i `geometry/vision/src/test`; uruchamiane `./gradlew -p geometry test`.
- Pomocnik `assertClose(expected, actual, tol = 1e-6, message)` w `com.fitlab.core.testutil.Asserts` (w źródłach testów core;
  vision ma własną kopię — nie współdzielimy źródeł testowych między modułami).
- Każda klasa publiczna z `:core` ma co najmniej jeden test. Testy deterministyczne (bez `Random` bez ziarna).
- Aplikacja Android: brak testów instrumentacyjnych w MVP; ręczna lista kontrolna w `docs/MANUAL_TEST.md` (krok M14).

### 6.2 Konkretne przypadki testowe (wejście → oczekiwane wyjście)

**geom**
- `Ring([(0,0),(10,0),(10,10),(0,10)]).signedArea() == 100.0`; po `reversed()` == −100.0.
- `Shapes.circle(Vec2(0,0), 10.0)`: liczba punktów = clamp(ceil(π·10/0.4), 32, 256) = 79; każdy punkt w odległości 5,0 ± 1e-9.
- `GeometryOps.offset(kwadrat 20×20 z dziurą 10×10, −0.1, MITRE)` → bbox 19,8 × 19,8; dziura 10,2 × 10,2; pole = 288,0 ± 1e-6.
- `GeometryOps.offset(kwadrat 20×20, +0.5, MITRE)` → bbox 21 × 21 (narożniki ostre).
- `CircleFit.kasa(36 punktów okręgu środek (3, −2), r = 7)` → środek ± 1e-6, r ± 1e-6; z szumem ±0,05 (deterministyczny: `0.05·sin(17k)`) → r ± 0,03.

**mesh**
- Prism kwadrat 10×10, z 0..5 → 12 trójkątów (2 na pokrywę ×2 + 8 boków), `isClosed`, `isConsistentlyOriented`, objętość 500 ± 1e-9.
- Prism kwadrat 20×20 z dziurą Ø10 (79 punktów), z 0..5 → zamknięta, objętość = (400 − poleWielokąta79)·5 ± 1e-6.
- Prism z `Transform3.fromAxes((0,1,0),(0,0,1),(1,0,0))` → zamknięta, objętość dodatnia, bounds zgodne z mapowaniem.
- Odwrócona transformacja (wyznacznik −1, np. `fromAxes((−1,0,0),(0,1,0),(0,0,1))`) → nadal objętość dodatnia.
- `normalizedForPrint()` → minZ == 0, środek bbox XY == (0,0).

**export**
- STL dla kostki 10×10×5 (12 trójkątów): rozmiar 84 + 600 = 684 bajty; bajty 80..83 = 12 (LE); nagłówek nie zaczyna się od „solid”;
  odczyt zwrotny (prosty parser w teście) daje te same wierzchołki z dokładnością float.
- 3MF: ZIP zawiera dokładnie `[Content_Types].xml`, `_rels/.rels`, `3D/3dmodel.model`; model parsuje się `DocumentBuilderFactory`
  (namespace-aware); liczba `<vertex>` i `<triangle>` zgodna z siatką; atrybut `unit="millimeter"`.
- Test lokalizacji: ustaw `Locale.setDefault(Locale("pl", "PL"))` w teście → w XML brak przecinków w liczbach (`x="1.5000"`). Przywróć locale w `finally`.
- `FileNames.exportName("Pokrętło pralki", FIT_TEST, 2, STL) == "pokretlo-pralki_fit_2.stl"`.

**fit / kompensacja**
- `FitMath.target(HOLE, MATING, SLIDE, 8.0) == 8.15`; `target(PIN, MATING, PRESS, 12.0) == 12.0`; `target(PIN, MATING, LOOSE, 12.0) == 11.6`;
  `target(HOLE, ORIGINAL, LOOSE, 5.0) == 5.0`.
- `FitMath.cad(10.0, −0.3) == 10.3`; `cad(20.0, 0.2) == 19.8`.
- `FeedbackInterpreter`: HOLE, biasUsed −0,25, TOO_TIGHT 0,2 → −0,45; HOLE TOO_LOOSE 0,1 → −0,15; PIN biasUsed +0,10, TOO_TIGHT 0,2 → +0,30;
  OUTLINE TOO_LOOSE 0,05 (biasUsed 0,1) → 0,05; OK → biasUsed; Measured: cad 10,25, zmierzone 10,05 → −0,20.
- `CompensationEstimator.estimate`:
  - prior −0,25, brak próbek → −0,25.
  - prior −0,25, [−0,45] → −0,383333 (±1e-6).
  - prior −0,25, najnowsze pierwsze [−0,40, −0,30, −0,50] → −0,364312 (±1e-6).
  - prior −0,25, [−0,30, −0,35, +0,40, −0,32] → +0,40 odrzucone (mediana −0,31) → −0,307361 (±1e-6).
  - prior +0,10, [+0,30, +0,20] (2 próbki — bez filtrowania) → +0,222727 (±1e-6).
  - prior 0, [5.0] → wynik 3,333 obcięty do 1,0.
  - kolejność: próbki podane w losowej kolejności z `createdAtMillis` → wynik jak dla posortowanych.
- `FeedbackNoteParser` — 6 przykładów z 4.9.
- `MaterialDefaults.bias(PETG, HOLE) == −0.30`.

**design**
- Kwadrat 20×20 + otwór Ø10 ORIGINAL, T = 5, OUTLINE +0,2, HOLE −0,3, stopa słonia 0 → 1 prism; bbox kształtu 19,8 × 19,8;
  otwór: wszystkie punkty dziury w odległości 5,15 ± 1e-6 od (0,0); objętość siatki ≈ 1543,6 ± 1,5; cechy: outline (cad 19,8), hole-1 (cad 10,3).
- Ten sam projekt, stopa słonia 0,2, warstwa 0,2 → 2 prismy: [0; 0,2] z bbox 19,4 i [0,2; 5] z bbox 19,8.
- Otwór Ø5 z fazką 0,6, warstwa 0,2, T = 3 → prismy z granicami {0, 2.4, 2.6, 2.8, 3.0} (stopa słonia 0);
  średnice otworu w kolejnych przedziałach od góry: 5+1,2=6,2; 5+0,8=5,8; 5+0,4=5,4; dolny 5,0 (przy bias 0).
- Plaster: T = 8 → wysokość 3,0; T = 2 → 2,0.
- `DesignJson` round-trip dla `OutlineDesign` i `TemplateDesign` (równość obiektów); JSON zawiera `"type":"outline"`.
- Kupon kalibracyjny przy zerowej kompensacji: bbox 44×20×11 po `normalizedForPrint`, 5 cech, siatka zamknięta.
- Walidacja: otwór wychodzący poza obrys → błąd; grubość 0,2 → błąd.

**templates**
- BUSHING przykład z 4.10 (8,40 / 11,90); kołnierz → 2 prismy; walidacja ścianki (shaft 10, housing 11) → błąd.
- KNOB: shaftFlat 4,5, shaftDiameter 6, PRESS, bias 0 → otwór D: maks. Y punktów otworu = 4,5 − 3 = 1,5 ± 1e-9; min Y = −3,0 ± 0,01.
- CAP square: tubeInner 20, PRESS, bias OUTLINE 0,1 → bok wtyku 19,9.
- BRACKET: domyślne → 2 prismy, siatka zamknięta, bbox całości 30 × 20 × 30.
- SPUR_GEAR: test z 4.10; z = 50 (rf > rb) też poprawny wielokąt; walidacja otworu za dużego → błąd.
- Dla każdego szablonu z domyślnymi parametrami i kompensacją PLA: `validate` pusty, `build` i `buildFitTest` dają siatkę zamkniętą.

**photo (core)**
- `PixelMapping`: k = 10: mm (80, 120) → px (799.5, 1199.5); px (800, 1200) → mm (80.05, 120.05).
- `ThicknessCorrection`: nadir (0,0), H = 300, t = 10, punkt (100, 0) → (96,6667, 0); t = 0 → bez zmian.
- `OutlinePostProcessor` (bez pozy aparatu, grubość 0): obrys = 4 punkty środków pikseli brzegowych (800,1200), (1199,1200),
  (1199,1449), (800,1449) → po konwersji 80,05…119,95 × 120,05…144,95 mm i korekcie +0,05 mm: szerokość 40,0 ± 0,02, wysokość 25,0 ± 0,02,
  centroid (0,0), orientacja CCW. Dziura = 72 punkty okręgu o środku (999,5; 1324,5) px i promieniu **40,5 px** (środki pikseli części
  otaczających otwór Ø8 mm) → po korekcie −0,05 mm jeden `HoleCandidate` o średnicy 8,00 ± 0,05 i środku (0,0) ± 0,02, `innerContours` puste.
- `MarkerSheetSpec.cornersMm(1)` == [(165,15),(195,15),(195,45),(165,45)]; ze skalą X 1,01 → x·1,01.

**vision (JVM + openpnp)**
- Inicjalizacja w `@BeforeAll`: `nu.pattern.OpenCV.loadLocally()`.
- `MarkerSheetRenderer.render()` → detekcja 4 markerów ID 0–3; narożniki = spec·12 − 0,5 px ± 1,0 px.
- `SyntheticPhotoFactory` (6.3) + `PhotoPipeline` (grubość 0): obrys 40,0 ± 0,2 × 25,0 ± 0,2 mm, jeden `HoleCandidate` Ø 8,0 ± 0,2,
  środek otworu (0,0) ± 0,2; brak wyjątków; średni błąd reprojekcji < 1 px.
- Ten sam obraz z częścią dotykającą krawędzi pola roboczego → `PipelineException` z tekstem „poza pole robocze”.
- Obraz bez markerów (jednolity szary) → `PipelineException` z „Nie znaleziono markerów”.
- `CameraPoseEstimator`: K (f = 3000, cx = 2000, cy = 1500, obraz 4000×3000), R = I, środek aparatu C = (105, 148, −400) → tvec = −R·C
  = (−105, −148, 400); `Calib3d.projectPoints` narożników markerów → estymacja: height 400 ± 2, nadir (105, 148) ± 1.
  Drugi wariant z `rvec = (0.1, 0.05, 0)` i `tvec = −R·C` → te same tolerancje.
- Zapis artefaktu: test `writesSelfTestAsset` zapisuje obraz syntetyczny do `build/selftest_photo.jpg` (`Imgcodecs.imwrite`);
  implementator kopiuje go ręcznie do `app/src/main/assets/selftest_photo.jpg` w kroku M14.

### 6.3 Syntetyczne obrazy testowe (`SyntheticPhotoFactory`, `src/test` modułu vision)

```kotlin
object SyntheticPhotoFactory {
    const val K = 10.0   // px/mm arkusza syntetycznego
    fun sheetWithPart(touchBorder: Boolean = false): Mat   // CV_8UC1 2100x2970
    fun photo(sheet: Mat, noiseSeed: Long = 42): Mat       // CV_8UC1 3000x4000 (szer. x wys.)
}
```
1. Arkusz: `Mat(2970, 2100, CV_8UC1, Scalar(255.0))`. Markery: `generateImageMarker(dict, id, 300, m, 1)` kopiowane do
   `submat(Rect(x0·10, y0·10, 300, 300))` (pozycje z `MarkerSheetSpec`, skala 1,0).
2. Część (szarość 60): prostokąt mm x 80–120, y 120–145 → `Imgproc.rectangle(sheet, Point(800.0, 1200.0), Point(1199.0, 1449.0), Scalar(60.0), -1)`
   (piksele 800..1199 × 1200..1449 włącznie = dokładnie 40,0 × 25,0 mm wg konwencji 4.2).
   Dla `touchBorder = true`: x 20–60 mm (styk z krawędzią pola roboczego).
3. Otwór Ø8 w środku (100; 132,5) mm: `Imgproc.circle(sheet, Point(999.5·16, 1324.5·16), 40·16, Scalar(255.0), -1, Imgproc.LINE_8, 4)`
   (parametr `shift = 4` → współrzędne ułamkowe ×16).
4. „Zdjęcie”: `H = Imgproc.getPerspectiveTransform(src = narożniki arkusza (0,0),(2100,0),(2100,2970),(0,2970),
   dst = (400,300),(2650,420),(2750,3700),(300,3600))`; `warpPerspective(sheet, photo, H, Size(3000.0, 4000.0), INTER_LINEAR, BORDER_CONSTANT, Scalar(128.0))`.
5. Szum: `Mat noise(photo.size(), CV_16SC1)`; `Core.setRNGSeed(seed)`; `Core.randn(noise, 0.0, 3.0)`; dodaj z saturacją
   (konwersja do CV_16S, `Core.add`, `convertTo(CV_8U)`). Następnie `GaussianBlur(3×3, sigma 0.8)`.

---

## 7. Plan implementacji

Każdy krok kończy się zielonym kryterium i **osobnym commitem** (patrz sekcja 9). Kroki M1–M8 dotyczą tylko `geometry/`
i ich kryterium zawsze da się sprawdzić bez Android SDK. Kroki M9–M14 wymagają `./gradlew :app:assembleDebug`;
jeśli lokalnie brak Android SDK (`$ANDROID_HOME` pusty) lub Google Maven jest nieosiągalny — pisz kod starannie,
uruchom `./gradlew -p geometry test`, i w commicie dopisz linię `Not compiled locally: no Android SDK` (CI zweryfikuje).

### M0 — Szkielet projektu Gradle
- Cel: działający composite build, wrapper, CI, minimalna aplikacja.
- Pliki: wszystkie z 2.6; `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
  (Gradle 9.6.1); `geometry/core/src/main/kotlin/com/fitlab/core/Version.kt` (`object FitLabCore { const val VERSION = "0.1.0" }`)
  + test `VersionTest`; `geometry/vision/src/main/kotlin/com/fitlab/vision/VisionInfo.kt` (`fun openCvVersion(): String = org.opencv.core.Core.VERSION`)
  + test ładujący `nu.pattern.OpenCV.loadLocally()` i sprawdzający, że wersja zaczyna się od „4.”;
  `app/src/main/AndroidManifest.xml` (application `FitLabApp`, activity `MainActivity` exported z MAIN/LAUNCHER, bez uprawnień),
  `FitLabApp.kt`, `MainActivity.kt` (`ComponentActivity`, Compose: tekst „FitLab”), `res/values/strings.xml` (`app_name` = FitLab),
  `res/values/themes.xml` ze stylem `Theme.FitLab` o rodzicu `android:Theme.Material.Light.NoActionBar` (styl systemowy — NIE używaj
  `Theme.Material3.*`, bo wymaga biblioteki `com.google.android.material`, której nie dodajemy), `.gitignore` (dopiski),
  `docs/DECISIONS.md` (pusty nagłówek).
- Wrapper: wygeneruj w PUSTYM katalogu tymczasowym (`mkdir /tmp/w && cd /tmp/w && touch settings.gradle.kts && gradle wrapper --gradle-version 9.6.1`)
  i skopiuj `gradlew`, `gradlew.bat`, `gradle/` do repo (generowanie w repo konfigurowałoby AGP i mogłoby się nie udać).
- Kryterium: `./gradlew -p geometry test` → BUILD SUCCESSFUL, 2 testy zielone; (jeśli SDK) `./gradlew :app:assembleDebug`.
- Zależności: brak.

### M1 — Geometria 2D
- Cel: typy geometryczne i most do JTS.
- Pliki (`geometry/core/src/main/kotlin/com/fitlab/core/geom/`): `Vec2.kt`, `Vec3.kt`, `Ring.kt`, `Shape2.kt`, `Bounds2.kt`,
  `Shapes.kt` (`circle(center, d)`, `rect(x0, y0, x1, y1)`, `roundedSquare(center, side, r)`, `dShape(d, flatToEdge)`),
  `GeometryOps.kt`, `GeometryException.kt`, `CircleFit.kt`; testy: `RingTest`, `ShapesTest`, `GeometryOpsTest`, `CircleFitTest`
  + `testutil/Asserts.kt`.
- Kryterium: `./gradlew -p geometry :core:test` zielone, przypadki z 6.2 „geom”.
- Zależności: M0.

### M2 — Siatka (prism → mesh) i walidacja
- Pliki (`core/mesh/`): `Transform3.kt`, `Prism.kt`, `Solid.kt` (z `footprint()`), `Mesh.kt`, `MeshBuilder.kt`, `MeshValidator.kt`;
  testy `MeshBuilderTest`, `MeshValidatorTest`, `TransformTest`.
- Kryterium: `./gradlew -p geometry :core:test`, przypadki 6.2 „mesh”.
- Zależności: M1.

### M3 — Eksport STL i 3MF
- Pliki (`core/export/`): `StlWriter.kt`, `ThreeMfWriter.kt`, `ExportFormat.kt`, `PartExporter.kt`, `FileNames.kt`; testy `StlWriterTest`,
  `ThreeMfWriterTest`, `FileNamesTest`.
- Kryterium: testy 6.2 „export”. Dodatkowo test zapisuje `build/test-output/cube.stl` i `cube.3mf` (do ręcznego obejrzenia w slicerze).
- Zależności: M2.

### M4 — Kompensacja i informacja zwrotna
- Pliki (`core/fit/`): `CompensationKind.kt` (z `isInternal`), `FitClass.kt`, `FitReference.kt`, `CompensationTable.kt`, `FitMath.kt`,
  `ResolvedFeature.kt`, `Verdict.kt`, `Feedback.kt`, `FeedbackInterpreter.kt`, `BiasSample.kt`, `CompensationEstimator.kt`, `Material.kt`,
  `MaterialDefaults.kt`, `FeedbackNoteParser.kt`; testy dla każdego.
- Kryterium: testy 6.2 „fit / kompensacja” (wszystkie liczby).
- Zależności: M0 (niezależny od M1–M3; można robić równolegle).

### M5 — Projekty części, budowa warstwowa, plaster, kupon
- Pliki (`core/design/`): `PrintSettings.kt`, `PartDesign.kt` (sealed + `OutlineDesign`, `HoleFeature`, `TemplateDesign`), `BuildResult.kt`,
  `OutlineBuilder.kt`, `ElephantFoot.kt`, `CalibrationCoupon.kt`, `DesignBuilder.kt` (`build(design, comp, print)` i `buildFitTest(...)` —
  dispatch na `OutlineBuilder` lub `TemplateRegistry`; do czasu M6 gałąź template rzuca `NotImplementedError`), `DesignJson.kt`
  (`Json { classDiscriminator = "type"; ignoreUnknownKeys = true; encodeDefaults = true }`); testy `OutlineBuilderTest`,
  `ElephantFootTest`, `CalibrationCouponTest`, `DesignJsonTest`.
- Kryterium: testy 6.2 „design”.
- Zależności: M2, M4.

### M6 — Szablony parametryczne
- Pliki (`core/templates/`): `TemplateId.kt`, `ParamSpec.kt` (z `ParamType`, `FitSlotSpec`, `ValidationError`, `TemplateInput`),
  `PartTemplate.kt`, `BushingTemplate.kt`, `KnobTemplate.kt`, `CapTemplate.kt`, `BracketTemplate.kt`, `Involute.kt`,
  `SpurGearTemplate.kt`, `TemplateRegistry.kt` (`all: List<PartTemplate>`, `get(id)`, `defaultInput(id)`); uzupełnij `DesignBuilder`.
  Testy per szablon + `TemplateRegistryTest` (każdy szablon: domyślne → zamknięta siatka, plaster zamknięty).
- Kryterium: testy 6.2 „templates”.
- Zależności: M5.

### M7 — Post-processing zdjęcia (czysty Kotlin)
- Pliki (`core/sheet/`): `MarkerSheetSpec.kt`; (`core/photo/`): `PixelMapping.kt`, `CameraPose.kt`, `ThicknessCorrection.kt`,
  `RawContours.kt`, `PhotoOutline.kt` (z `HoleCandidate`), `OutlinePostProcessor.kt`; testy wg 6.2 „photo (core)”.
- Kryterium: `./gradlew -p geometry :core:test`.
- Zależności: M1.

### M8 — Moduł vision (OpenCV) i testy syntetyczne
- Pliki (`geometry/vision/src/main/kotlin/com/fitlab/vision/`): `MatUtils.kt` (konwersje `MatOfPoint` ↔ `List<Vec2>`, odczyt narożników),
  `MarkerDetector.kt`, `SheetRectifier.kt`, `Segmenter.kt`, `CameraPoseEstimator.kt`, `PhotoPipeline.kt` (z `PipelineInput`, `PipelineResult`,
  `PipelineException`), `MarkerSheetRenderer.kt`; testy: `testing/SyntheticPhotoFactory.kt`, `MarkerSheetRendererTest`, `PhotoPipelineTest`,
  `CameraPoseEstimatorTest`, `SelfTestAssetTest`.
- Kryterium: `./gradlew -p geometry test` (całość zielona), przypadki 6.2 „vision”.
- Zależności: M7.

### M9 — Warstwa danych aplikacji
- Pliki (`app/src/main/kotlin/com/fitlab/app/`): `data/db/` — 7 encji z 3.2, DAO, `FitLabDatabase.kt`; `data/settings/AppSettings.kt`;
  `data/files/FileStore.kt`; `data/repo/ProfileRepository.kt` (`ensureDefaults()`: gdy 0 drukarek → „Moja drukarka” (0,4/0,2/0,2),
  filamenty „PLA (domyślny)” i „PETG (domyślny)”, reguły z priorami, aktywne: drukarka + PLA), `CompensationRepository.kt`
  (`tableFor`, `recompute`, `setManual`, `resetToDefault`), `PartRepository.kt`, `TestRepository.kt` (`createIteration`, `saveResults`
  — w transakcji `withTransaction`); `AppContainer.kt`; `FitLabApp.kt` (tworzy kontener, `OpenCvInit.ensure()`, `ensureDefaults()` w `applicationScope`).
  Opcjonalnie testy JVM czystych funkcji mapujących w `app/src/test`.
- Kryterium: `./gradlew :app:assembleDebug` (generuje `app/schemas/.../1.json` — commituj go).
- Zależności: M6, M8.

### M10 — Szkielet UI, profile, kompensacja
- Pliki: `ui/theme/Theme.kt`, `ui/nav/Routes.kt`, `ui/nav/FitLabNavHost.kt`, `ui/components/{ShapeCanvas, MmField, FitClassSelector,
  VerdictSelector, ErrorBanner, ConfirmDialog}.kt`, `ui/screens/home/*`, `ui/screens/newpart/*`, `ui/screens/profiles/*`,
  `ui/screens/compensation/*`; `MainActivity` używa `FitLabNavHost`.
- Kryterium: `./gradlew :app:assembleDebug`; ręcznie: lista pusta, profile edytowalne, kompensacja pokazuje priory PLA.
- Zależności: M9.

### M11 — Szablony w UI, szczegóły części, eksport
- Pliki: `ui/screens/templates/{TemplatePickerScreen, TemplateEditorScreen, TemplateEditorViewModel}.kt`, `ui/screens/part/*`,
  `ui/components/ExportSheet.kt`, `platform/ShareHelper.kt`, `res/xml/file_paths.xml`, wpis `FileProvider` w manifeście.
- Kryterium: assembleDebug; ręcznie: utworzenie tulei, eksport STL i 3MF, plik otwiera się w slicerze, iteracja widoczna w historii.
- Zależności: M10.

### M12 — Wyniki prób, uczenie, kalibracja
- Pliki: `ui/screens/test/{TestResultScreen, TestResultViewModel}.kt`, `ui/screens/calibration/*`.
- Kryterium: assembleDebug; ręcznie: wpisanie „za ciasno o 0,2 mm” dla otworu → bias HOLE PLA z −0,25 na −0,3833 (patrz 6.2),
  ponowny eksport ma otwór CAD większy.
- Zależności: M11.

### M13 — Ścieżka zdjęcia: arkusz, zdjęcie, edytor obrysu
- Pliki: `platform/{ImageLoader, ExifReader, MarkerSheetPdf, OpenCvInit}.kt`, `ui/screens/markers/*`, `ui/screens/photo/{PhotoCaptureScreen,
  PhotoCaptureViewModel}.kt`, `ui/screens/outline/{OutlineEditorScreen, OutlineEditorViewModel}.kt`.
- Kryterium: assembleDebug; ręcznie (telefon): wydruk arkusza, zdjęcie prostokątnej płytki o znanych wymiarach → błąd ≤ 0,5 mm po korekcji grubości.
- Zależności: M12.

### M14 — Diagnostyka, dokumentacja, porządki
- Pliki: `ui/screens/diagnostics/*`, `app/src/main/assets/selftest_photo.jpg` (z testu M8), `docs/MANUAL_TEST.md` (lista kontrolna
  ścieżek z 5.4 + kalibracja + eksport), aktualizacja `README.md` (opis, komendy budowania, link do DESIGN.md).
- Kryterium: `./gradlew -p geometry test` i `./gradlew :app:assembleDebug` zielone; w Diagnostyce wynik „OK” na urządzeniu/emulatorze.
- Zależności: M13.

---

## 8. Ryzyka i pułapki

### 8.1 Ryzyka produktu/techniki i ograniczenia

| Ryzyko | Skutek | Ograniczenie |
|---|---|---|
| Paralaksa grubych części | obrys zawyżony o kilka % | korekcja 4.4 + ostrzeżenie o nadirze + wymiar referencyjny z suwmiarki w edytorze |
| Nieznana ogniskowa (brak EXIF) | błąd wysokości aparatu ±10% → ±0,2 mm na 50 mm | domyślnie 26 mm; skala z wymiaru referencyjnego koryguje resztę |
| Cienie, odblaski, jasna część na białej kartce | zły obrys | instrukcje, ciemna kartka pod jasną częścią, ręczny próg |
| Wydruk arkusza nie w skali 100% | błąd skali kilka % | linijka 100 mm, korekta skali w ustawieniach |
| Dystorsja obiektywu | błąd < 0,3 mm na krawędziach | część na środku pola roboczego; zdjęcia telefonów są zwykle korygowane |
| Pomiar otworów ze zdjęcia | otwory za małe | `needsCaliperCheck`, komunikat „zmierz suwmiarką” |
| Nakładające się powłoki w STL/3MF | slicer może pokazać ostrzeżenie | testowane podejście (slicery sumują bryły); w razie problemu „Napraw model” w slicerze; w przyszłości union 3D |
| Brak dostępu do Google Maven / Android SDK w środowisku implementatora | nie da się zbudować `:app` | rdzeń w osobnym buildzie, CI na GitHub Actions |
| Rate limit Maven Central (HTTP 429) | losowo nieudane pobieranie zależności | ponów po 60 s (`for i in 1 2 3 4 5; do ./gradlew -p geometry test && break; sleep 60; done`) |
| Niezgodność API openpnp 4.9 vs AAR 4.13 | `NoSuchMethodError` na urządzeniu | używaj tylko API z listy w 4.1–4.4; ekran Diagnostyki wykrywa problem |
| KSP 2.3.12 nie wspiera Kotlina 2.4.21 | błąd KSP | podbij KSP do najnowszego 2.3.x z Maven Central (`com.google.devtools.ksp:symbol-processing-gradle-plugin`) i zapisz w DECISIONS.md |
| compileSdk 37 niedostępny | błąd AGP | AGP pobiera platformę automatycznie; jeśli niemożliwe — compileSdk 36 i obniż biblioteki zgłaszające wymóg 37 |
| Uczenie na błędnych notatkach | rozjechana kompensacja | odrzucanie odstających, przełącznik „Uwzględnij”, ręczne nadpisanie, „Przywróć domyślne” |

### 8.2 Pułapki dla implementatora (czytaj przed każdym krokiem)

1. **Nie używaj `kotlin { jvmToolchain(17) }`** — w środowisku może być tylko JDK 21 i brak repozytorium toolchainów; używaj
   `java { sourceCompatibility/targetCompatibility }` + `compilerOptions.jvmTarget` jak w 2.6.
2. **Nie dodawaj `org.jetbrains.kotlin.android`** do app — AGP 9 ma wbudowany Kotlin; dodanie wtyczki to błąd builda.
   Nie używaj `kotlinOptions {}` (usunięte) — tylko `kotlin { compilerOptions {} }` jeśli w ogóle potrzebne.
3. **Wersja wtyczki compose = wersja KGP** (2.4.21). Dlatego KGP jest wymuszony w `buildscript` roota.
4. **Nie generuj wrappera w katalogu repo** — rób to w pustym katalogu tymczasowym i kopiuj pliki.
5. **Locale**: `String.format("%.4f")` w polskim locale daje przecinek → zepsuty 3MF. Zawsze `Locale.ROOT` w plikach.
   W UI parsuj liczby po zamianie `,` → `.`.
6. **Pierścienie JTS mają powtórzony punkt końcowy, nasze `Ring` nie.** Konwertuj w obu kierunkach w jednym miejscu (`GeometryOps`).
7. **Orientacja**: po każdej operacji JTS i po odbiciu osi Y wywołuj `normalized()`. Zła orientacja = odwrócone normalne = slicer pokaże dziury.
8. **Offset JTS**: dodatnia odległość powiększa obrys i ZMNIEJSZA dziury w tym samym wielokącie. Dlatego obrys i wycięcia
   kompensujemy osobno (4.8), a stopę słonia — jednym offsetem całego kształtu (celowo).
9. **Konwencja pikseli**: środek piksela = liczba całkowita; mm → px: `x·k − 0.5`. Pomyłka o pół piksela = 0,1 mm błędu.
10. **Narożniki ArUco**: kolejność TL, TR, BR, BL zgodnie z ruchem wskazówek zegara w obrazie; `Mat` 1×4 `CV_32FC2` — czytaj przez
    `get(0, 0, FloatArray(8))`. W Kotlinie używaj `params.set_cornerRefinementMethod(...)` (setter Java).
11. **`ImageDecoder` domyślnie tworzy bitmapy HARDWARE** — `Utils.bitmapToMat` wtedy się wywali. Ustaw `ALLOCATOR_SOFTWARE`.
    `Utils.bitmapToMat` wymaga `ARGB_8888` lub `RGB_565`.
12. **Zwalniaj `Mat`** (`release()`) — natywna pamięć nie jest odśmiecana szybko; 12 MP × kilka kopii = OOM.
13. **Nie deklaruj uprawnienia CAMERA** (patrz 5.5).
14. **OpenCV na Androidzie**: przed pierwszym użyciem `OpenCVLoader.initLocal()`; w testach JVM `nu.pattern.OpenCV.loadLocally()`.
    Nie importuj `nu.pattern` w kodzie `main` modułu vision.
15. **`compileOnly` openpnp**: moduł vision nie może przenosić openpnp do aplikacji — inaczej duplikaty klas `org.opencv.*`.
16. **Room**: nie dodawaj `room-ktx`; enumy jako String; `@Upsert` dla reguł; operacje wieloetapowe w `withTransaction`.
17. **Granice Z** zaokrąglaj do wielokrotności wysokości warstwy; nie twórz prismów o wysokości < 0,05 mm (pomijaj).
18. **Kwota w ocenie jest średnicowa** — offset obrysu to połowa. Nie dziel/mnoż podwójnie.
19. **Próbka = bezwzględny bias** liczony od `biasUsedMm` z migawki iteracji, NIE od bieżącej reguły (reguła mogła się zmienić od eksportu).
20. **Nie zmieniaj publicznych sygnatur z sekcji 3/4** bez wpisu w `DECISIONS.md`; kolejne kroki na nich polegają.
21. **Maven Central 429** — to limit po stronie serwera, nie błąd konfiguracji. Ponawiaj; nie zmieniaj repozytoriów.
22. **Edge-to-edge (targetSdk 36)**: zawartość w `Scaffold` używa `innerPadding`; inaczej treść wejdzie pod pasek statusu.
23. **`ConstrainedDelaunayTriangulator`** wymaga poprawnego wielokąta (bez samoprzecięć, bez zdublowanych punktów) — przed triangulacją `cleaned()`
    i `isValid`; jeśli niepoprawny → `GeometryFixer.fix`.
24. **Testy liczbowe**: porównuj z tolerancją, nigdy `==` na Double (poza wartościami dokładnie reprezentowalnymi).

---

## 9. Instrukcja dla implementatora

### 9.1 Kolejność pracy
1. Przeczytaj cały dokument. Potem wykonuj kroki M0 → M14 po kolei (M4 i M7 mogą być wcześniej, zgodnie z zależnościami).
2. Na początku każdego kroku przeczytaj ponownie jego opis, odpowiednie podsekcje 3–6 i sekcję 8.2.
3. Najpierw testy z sekcji 6.2 dla danego kroku (mogą być czerwone), potem implementacja, aż wszystkie zielone.
4. Nie zaczynaj kolejnego kroku, dopóki kryterium bieżącego nie jest spełnione.

### 9.2 Konwencje kodu
- Kotlin, styl `official`, wcięcie 4 spacje, max ~120 znaków w linii.
- Jedna klasa publiczna na plik (wyjątek: małe sealed/data class powiązane, np. `PartDesign.kt`).
- Klasy domenowe niemutowalne (`data class` z `val`), listy jako `List` (nie `MutableList`) w API.
- Brak `!!` w kodzie produkcyjnym (wyjątek: testy). Błędy domenowe: `IllegalArgumentException` dla złych danych wejściowych,
  `GeometryException`, `PipelineException` z komunikatem po polsku przeznaczonym dla użytkownika.
- Funkcje czyste w `object` lub klasach bezstanowych; brak singletonów ze stanem.
- Stałe liczbowe w `companion object` / `object` z nazwą i komentarzem jednostki (`const val OUTLIER_MM = 0.3`), zbiorczo w Załączniku B.
- Komentarze KDoc dla każdej publicznej funkcji w `:core` (1–3 zdania, jednostki).
- UI: każdy ekran = `Screen` (bezstanowy, przyjmuje `UiState` i lambdy) + `ViewModel`; brak logiki geometrycznej w Composable.
- Teksty UI po polsku bezpośrednio w kodzie Composable (MVP, jeden język) — wyjątek: `app_name` w `strings.xml`.
- Coroutines: IO na `Dispatchers.IO`, obliczenia geometrii/wizji na `Dispatchers.Default`, nigdy na wątku głównym.

### 9.3 Commity
- Pracuj na bieżącej gałęzi (nie twórz nowych bez potrzeby). Jeden krok = co najmniej jeden commit; komunikat:
  `M<n>: <krótki opis po angielsku>` + pusta linia + lista zmian w punktach + wynik kryterium, np.
  `Verified: ./gradlew -p geometry test (57 tests, all green)`.
- Nie commituj: `build/`, `.gradle/`, `local.properties`, plików IDE. Commituj: wrapper, `app/schemas/`, assety.
- Nie przepisuj historii (bez `--amend` na wypchniętych commitach, bez force-push).

### 9.4 Weryfikacja po każdym kroku (lista kontrolna)
1. `./gradlew -p geometry test --console=plain` — zielone (od M0 do końca; żaden krok nie może zepsuć wcześniejszych testów).
2. Od M9: `./gradlew :app:assembleDebug --console=plain` — zielone (lub adnotacja „Not compiled locally”).
3. Przejrzyj `git status` — brak przypadkowych plików.
4. Sprawdź, czy liczby z sekcji 6.2 dla tego kroku są pokryte testami (nazwy testów odpowiadają przypadkom).
5. Zapisz wszelkie odstępstwa od dokumentu w `docs/DECISIONS.md`.
6. Zaktualizuj `docs/TASKS.md` (odhacz pozycje).

---

## Załącznik A — słownik

| Termin | Znaczenie |
|---|---|
| bias | średnicowy błąd druku danego typu cechy: wydrukowany − CAD |
| cel (target) | wymiar, który ma wyjść po wydruku |
| CAD | wymiar w eksportowanym modelu = cel − bias |
| luz (clearance) | zamierzona różnica wymiarów pary współpracującej, z klasy pasowania |
| plaster (fit-test slice) | cienki wydruk próbny samego fragmentu z pasowaniami |
| stopa słonia | rozlanie pierwszej warstwy; kompensowane wcięciem pierwszej warstwy |
| nadir | rzut środka aparatu na płaszczyznę kartki |
| prism | graniastosłup: kształt 2D wyciągnięty między z0 a z1 (opcjonalnie obrócony) |

## Załącznik B — stałe

| Stała | Wartość | Miejsce |
|---|---|---|
| RECT_PX_PER_MM | 10.0 | `SheetRectifier` |
| SHEET_RENDER_PX_PER_MM | 12.0 | `MarkerSheetRenderer` |
| MARKER_SIZE_MM | 30.0 | `MarkerSheetSpec` |
| MAX_PHOTO_SIDE_PX | 4000 | `ImageLoader` |
| DEFAULT_FOCAL_35MM | 26.0 | `CameraPoseEstimator` |
| FULL_FRAME_DIAGONAL_MM | 43.2666 | `CameraPoseEstimator` |
| CAMERA_HEIGHT_RANGE_MM | 100..1500 | `CameraPoseEstimator` |
| REPROJ_WARN_PX | 3.0 | `SheetRectifier` |
| MIN_PART_AREA_MM2 / MIN_HOLE_AREA_MM2 | 100.0 / 3.0 | `SegmentationParams` |
| LOW_CONTRAST | 30 | `Segmenter` |
| SIMPLIFY_TOL_MM | 0.1 | `OutlinePostProcessor` |
| CIRCULARITY_MIN / CIRCLE_RMS_MM | 0.85 / max(0.10, 0.02·r) | `OutlinePostProcessor` |
| CIRCLE_SEGMENT_MM | 0.4 (n ∈ [32, 256]) | `Shapes` |
| MITRE_LIMIT | 3.0 | `GeometryOps` |
| MIN_WALL_MM | 0.8 | builders |
| FIT_TEST_MAX_HEIGHT_MM (obrys) | 3.0 | `OutlineBuilder` |
| FitClass clearance | PRESS 0.00, SLIDE 0.15, LOOSE 0.40 | `FitClass` |
| PRIOR_WEIGHT / DECAY / MAX_SAMPLES / OUTLIER_MM / CLAMP_MM | 0.5 / 0.7 / 10 / 0.3 / 1.0 | `CompensationEstimator` |
| NOTE_MAX_AMOUNT_MM | 2.0 | `FeedbackNoteParser` |
