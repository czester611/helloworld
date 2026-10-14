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
- [ ] `Vec2`, `Vec3`, `Ring`, `Shape2`, `Bounds2`
- [ ] `Shapes` (circle, rect, roundedSquare, dShape)
- [ ] `GeometryOps` (most JTS, offset, boolean, simplify, isValid), `GeometryException`
- [ ] `CircleFit.kasa`
- [ ] Testy z DESIGN 6.2 „geom”

## M2 — Siatka (`core/mesh`)
- [ ] `Transform3`, `Prism`, `Solid.footprint`, `Mesh`, `Tri`
- [ ] `MeshBuilder.buildShells` / `build` (ściany, pokrywy CDT, transformacja, wyznacznik)
- [ ] `MeshValidator` (szczelność, orientacja, objętość)
- [ ] Testy 6.2 „mesh”

## M3 — Eksport (`core/export`)
- [ ] `StlWriter` (binarny, LE, nagłówek bez „solid”)
- [ ] `ThreeMfWriter` (3 wpisy ZIP, obiekty + kompozyt, Locale.ROOT)
- [ ] `PartExporter`, `FileNames`
- [ ] Testy 6.2 „export” (+ pliki w `build/test-output/`)

## M4 — Kompensacja (`core/fit`)
- [ ] Enumy i `CompensationTable`, `ResolvedFeature`
- [ ] `FitMath` (target, cad)
- [ ] `FeedbackInterpreter`, `CompensationEstimator`, `MaterialDefaults`
- [ ] `FeedbackNoteParser`
- [ ] Testy 6.2 „fit / kompensacja” (wszystkie liczby)

## M5 — Projekty (`core/design`)
- [ ] `PartDesign`, `OutlineDesign`, `HoleFeature`, `TemplateDesign`, `PrintSettings`, `BuildResult`
- [ ] `OutlineBuilder` (warstwy, fazki schodkowe, stopa słonia, walidacja, plaster)
- [ ] `ElephantFoot`, `CalibrationCoupon`, `DesignBuilder`, `DesignJson`
- [ ] Testy 6.2 „design”

## M6 — Szablony (`core/templates`)
- [ ] `ParamSpec` & co., `PartTemplate`, `TemplateRegistry`
- [ ] `BushingTemplate`, `KnobTemplate`, `CapTemplate`, `BracketTemplate`
- [ ] `Involute`, `SpurGearTemplate`
- [ ] Testy 6.2 „templates”

## M7 — Post-processing zdjęcia (`core/sheet`, `core/photo`)
- [ ] `MarkerSheetSpec`
- [ ] `PixelMapping`, `CameraPose`, `ThicknessCorrection`
- [ ] `RawContours`, `PhotoOutline`, `HoleCandidate`, `OutlinePostProcessor`
- [ ] Testy 6.2 „photo (core)”

## M8 — Vision (`geometry/vision`)
- [ ] `MatUtils`, `MarkerDetector`, `SheetRectifier`, `Segmenter`, `CameraPoseEstimator`
- [ ] `PhotoPipeline` (+ `PipelineInput`, `PipelineResult`, `PipelineException`)
- [ ] `MarkerSheetRenderer`
- [ ] `SyntheticPhotoFactory` + testy 6.2 „vision”, zapis `build/selftest_photo.jpg`

## M9 — Dane aplikacji
- [ ] Encje i DAO (7 tabel), `FitLabDatabase` v1, schemat w `app/schemas/`
- [ ] `AppSettings`, `FileStore`
- [ ] Repozytoria (Profile z `ensureDefaults`, Compensation, Part, Test)
- [ ] `AppContainer`, `FitLabApp` (OpenCV init, seed)

## M10 — Szkielet UI
- [ ] Theme, `Routes`, `FitLabNavHost`
- [ ] Komponenty: `ShapeCanvas`, `MmField`, `FitClassSelector`, `VerdictSelector`, `ErrorBanner`, `ConfirmDialog`
- [ ] Ekrany: Home, NewPart, Profiles, Compensation

## M11 — Szablony w UI i eksport
- [ ] TemplatePicker, TemplateEditor (walidacja, podgląd, tabela cech)
- [ ] PartDetail (+ historia prób), ExportSheet, `ShareHelper`, FileProvider

## M12 — Wyniki prób i kalibracja
- [ ] TestResult (werdykty, kwoty, pomiar, parser notatki, uczenie)
- [ ] Calibration (eksport kuponu, 5 pomiarów)

## M13 — Ścieżka zdjęcia
- [ ] `ImageLoader`, `ExifReader`, `MarkerSheetPdf`, `OpenCvInit`
- [ ] MarkerSheet (PDF, korekta skali)
- [ ] PhotoCapture (aparat/galeria, grubość, pipeline, próg)
- [ ] OutlineEditor (skala ref., obrót, lustro, otwory, kontury, fazki)

## M14 — Diagnostyka i porządki
- [ ] Diagnostics + `assets/selftest_photo.jpg`
- [ ] `docs/MANUAL_TEST.md`, aktualizacja `README.md`
