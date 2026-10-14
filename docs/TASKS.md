# FitLab — lista zadań

Źródło prawdy: `docs/DESIGN.md` (sekcja 7). Odhaczaj `[x]` po spełnieniu kryterium kroku i commicie.
Kryterium bazowe każdego kroku: `./gradlew -p geometry test` zielone; od M9 dodatkowo `./gradlew :app:assembleDebug`.

## M0 — Szkielet projektu
- [x] Wrapper Gradle 9.6.1 (wygenerowany w pustym katalogu tymczasowym, skopiowany)
- [x] `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` (root) wg DESIGN 2.6
- [x] `geometry/` (settings, build, `core`, `vision`) wg DESIGN 2.6
- [x] `VersionTest` (core) i test wersji OpenCV (vision)
- [x] `app/` minimalny: manifest bez uprawnień, `FitLabApp`, `MainActivity`, `strings.xml`, `themes.xml`
- [x] `.github/workflows/ci.yml`, dopiski do `.gitignore`, `docs/DECISIONS.md`

## M1 — Geometria 2D (`core/geom`)
- [x] `Vec2`, `Vec3`, `Ring`, `Shape2`, `Bounds2`
- [x] `Shapes` (circle, rect, roundedSquare, dShape)
- [x] `GeometryOps` (most JTS, offset, boolean, simplify, isValid), `GeometryException`
- [x] `CircleFit.kasa`
- [x] Testy z DESIGN 6.2 „geom”

## M2 — Siatka (`core/mesh`)
- [x] `Transform3`, `Prism`, `Solid.footprint`, `Mesh`, `Tri`
- [x] `MeshBuilder.buildShells` / `build` (ściany, pokrywy CDT, transformacja, wyznacznik)
- [x] `MeshValidator` (szczelność, orientacja, objętość)
- [x] Testy 6.2 „mesh”

## M3 — Eksport (`core/export`)
- [x] `StlWriter` (binarny, LE, nagłówek bez „solid”)
- [x] `ThreeMfWriter` (3 wpisy ZIP, obiekty + kompozyt, Locale.ROOT)
- [x] `PartExporter`, `FileNames`
- [x] Testy 6.2 „export” (+ pliki w `build/test-output/`)

## M4 — Kompensacja (`core/fit`)
- [x] Enumy i `CompensationTable`, `ResolvedFeature`
- [x] `FitMath` (target, cad)
- [x] `FeedbackInterpreter`, `CompensationEstimator`, `MaterialDefaults`
- [x] `FeedbackNoteParser`
- [x] Testy 6.2 „fit / kompensacja” (wszystkie liczby)

## M5 — Projekty (`core/design`)
- [x] `PartDesign`, `OutlineDesign`, `HoleFeature`, `TemplateDesign`, `PrintSettings`, `BuildResult`
- [x] `OutlineBuilder` (warstwy, fazki schodkowe, stopa słonia, walidacja, plaster)
- [x] `ElephantFoot`, `CalibrationCoupon`, `DesignBuilder`, `DesignJson`
- [x] Testy 6.2 „design”

## M6 — Szablony (`core/templates`)
- [x] `ParamSpec` & co., `PartTemplate`, `TemplateRegistry`
- [x] `BushingTemplate`, `KnobTemplate`, `CapTemplate`, `BracketTemplate`
- [x] `Involute`, `SpurGearTemplate`
- [x] Testy 6.2 „templates”

## M7 — Post-processing zdjęcia (`core/sheet`, `core/photo`)
- [x] `MarkerSheetSpec`
- [x] `PixelMapping`, `CameraPose`, `ThicknessCorrection`
- [x] `RawContours`, `PhotoOutline`, `HoleCandidate`, `OutlinePostProcessor`
- [x] Testy 6.2 „photo (core)”

## M8 — Vision (`geometry/vision`)
- [x] `MatUtils`, `MarkerDetector`, `SheetRectifier`, `Segmenter`, `CameraPoseEstimator`
- [x] `PhotoPipeline` (+ `PipelineInput`, `PipelineResult`, `PipelineException`)
- [x] `MarkerSheetRenderer`
- [x] `SyntheticPhotoFactory` + testy 6.2 „vision”, zapis `build/selftest_photo.jpg`

## M9 — Dane aplikacji
- [x] Encje i DAO (7 tabel), `FitLabDatabase` v1 (schemat w `app/schemas/` zostanie wygenerowany przy pierwszym buildzie z Android SDK)
- [x] `AppSettings`, `FileStore`
- [x] Repozytoria (Profile z `ensureDefaults`, Compensation, Part, Test)
- [x] `AppContainer`, `FitLabApp` (OpenCV init, seed)

## M10 — Szkielet UI
- [x] Theme, `Routes`, `FitLabNavHost`
- [x] Komponenty: `ShapeCanvas`, `MmField`, `FitClassSelector`, `VerdictSelector`, `ErrorBanner`, `ConfirmDialog`
- [x] Ekrany: Home, NewPart, Profiles, Compensation

## M11 — Szablony w UI i eksport
- [x] TemplatePicker, TemplateEditor (walidacja, podgląd, tabela cech)
- [x] PartDetail (+ historia prób), ExportSheet, `ShareHelper`, FileProvider

## M12 — Wyniki prób i kalibracja
- [x] TestResult (werdykty, kwoty, pomiar, parser notatki, uczenie)
- [x] Calibration (eksport kuponu, 5 pomiarów)

## M13 — Ścieżka zdjęcia
- [x] `ImageLoader`, `ExifReader`, `MarkerSheetPdf`, `OpenCvInit`
- [x] MarkerSheet (PDF, korekta skali)
- [x] PhotoCapture (aparat/galeria, grubość, pipeline, próg)
- [x] OutlineEditor (skala ref., obrót, lustro, otwory, kontury, fazki)

## M14 — Diagnostyka i porządki
- [x] Diagnostics + `assets/selftest_photo.jpg`
- [x] `docs/MANUAL_TEST.md`, aktualizacja `README.md`
