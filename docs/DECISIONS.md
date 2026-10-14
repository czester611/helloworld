# Decyzje i odstępstwa od DESIGN.md

Format: data, krok, decyzja, powód.

## 2026-10-14 · M8 · Rozszerzenia API vision
- `PipelineResult` ma dodatkowe pole `meanReprojErrorPx` (wymagane przez test "średni błąd reprojekcji < 1 px").
- Dodano `PhotoPipeline.reprocess(rectified, ...)` — ponowna segmentacja na zrektyfikowanym obrazie (próg ręczny w UI,
  "homografia z cache"). Zwrócone `rectified` to ten sam obiekt `Mat`, nie kopia.
- `CameraPoseEstimator.estimate(markers, sheet, w, h, focal35mm)` zwraca `CameraPose?`; ostrzeżenie o braku pozy dodaje `PhotoPipeline`.
- Test syntetyczny: wykryty otwór ma Ø 8,15 mm (nie 8,00) — wynika z rysowania okręgu `Imgproc.circle` (promień 40 px
  zamalowuje piksele do ~40,5 px), a nie z błędu znaku korekcji półpiksela (obrys zewnętrzny wychodzi dokładnie 40,00 × 25,00 mm).
