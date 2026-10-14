# Notatki implementacyjne

Szczegółowe decyzje i odstępstwa: `docs/DECISIONS.md` (zgodnie z DESIGN.md). Tu tylko ustalenia od użytkownika.

- Domyślny profil drukarki przy pierwszym uruchomieniu: **Snapmaker U1** (FDM, toolchanger, dysza 0,4 mm, warstwa 0,2 mm,
  stopa słonia 0,2 mm; ustawiony jako aktywny). Pole druku ok. 270×270×270 mm jest z pamięci użytkownika i oznaczone w kodzie
  (`ProfileRepository.DEFAULT_BUILD_VOLUME_MM`) jako DO WERYFIKACJI; trafia tylko do notatki profilu, nie do obliczeń.
  Kompensacje nadal uczone per drukarka + filament, priory PLA/PETG z DESIGN.md. Eksport 3MF/STL bez zmian (Snapmaker Orca).

## Weryfikacja i odstępstwa (kroki M9–M14)
- Kod `app/` nie był budowany (brak Android SDK, Google Maven zwraca 403). Zweryfikowano pomocniczo kompilację Kotlina
  repozytoriów, encji, ViewModeli (w tym Photo/Diagnostics/OutlineEditor/TestResult/Calibration) i `ImageLoader` względem
  stubów API Androida/Room/lifecycle — bez błędów. Ekrany Compose, nawigacja, ExportSheet, MarkerSheetPdf, ShareHelper,
  MainActivity i konfiguracja Gradle aplikacji NIE zostały zweryfikowane kompilacją.
- `app/schemas/.../1.json` zostanie wygenerowany przy pierwszym buildzie z SDK i powinien zostać skomitowany.
- Zamiast `ExposedDropdownMenuBox` (materiał filamentu) użyto `OutlinedButton` + `DropdownMenu`, a zamiast `SegmentedButton`
  (status części) — `FilterChip` (unikanie niestabilnych/zmiennych API Material3).
- `ImageLoader` ma też wariant z `File` (`ImageDecoder.createSource(File)`), bo zdjęcia są najpierw kopiowane do cache.
- NewPart i TemplatePicker nie mają ViewModeli (statyczne ekrany).
- Pole „Bias” w Kompensacji używa zwykłej klawiatury (klawiatura dziesiętna nie zawsze ma minus).
- Eksport: próba (TestIteration) zapisywana jest w momencie wygenerowania pliku (także przy późniejszym anulowaniu wyboru lokalizacji).
