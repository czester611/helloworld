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
- Eksport: próba (TestIteration) jest zapisywana dopiero po udanym zapisie pliku („Zapisz plik…”) lub uruchomieniu udostępniania (`ExportService.commit`); przy anulowaniu plik z cache jest usuwany i próba nie powstaje (numer nie jest zużyty). Android nie informuje, czy użytkownik faktycznie wysłał plik z okna udostępniania, więc tam moment zapisu to otwarcie okna. Poprzednio próba powstawała przy generowaniu pliku. Brak testu JUnit: zmiana dotyczy tylko warstwy app (Room/SAF), bez czystej logiki w geometry/core.

## Zalecenia filamentu i orientacji (zadanie A)
- Logika w czystym Kotlinie: `geometry/core/.../advice/UsageAdvice.kt` (`UsageType`, `UsageAdvisor.advise`, `matchProfile`) z testami
  `UsageAdvisorTest`. Mapowanie: PLA (wnętrze, prototyp, estetyczne), PETG (pasowane, ruchome, obciążone), ASA (ciepło/słońce,
  zewnątrz; PETG jako alternatywa), TPU (uszczelka/amortyzator), nylon/PA jako alternatywa dla ruchomych i obciążonych.
- `Material` nie ma wartości nylon (nie zmieniano enuma ani serializacji): nylon to opcja z `Material.OTHER` i `nameHint`;
  profil dopasowywany po nazwie (zawiera „nylon” lub „PA”, materiał OTHER).
- UI: karta „Zalecenia” (`AdviceCard`) na ekranie szczegółów części. Wybór typu zastosowania ustawia jako aktywny pasujący
  profil filamentu głównej rekomendacji (jeśli istnieje); alternatywy mają przycisk „Użyj profilu”. Uczenie kompensacji per
  filament bez zmian. Brak pasującego profilu: komunikat zamiast zmiany.
- Ostrzeżenie o częściach krytycznych dla bezpieczeństwa i uwaga „sprawdź w instrukcji drukarki” (komora, limit dyszy Snapmaker U1)
  są stałymi elementami każdej rekomendacji; aplikacja niczego nie zakłada o tych parametrach.
- Karta nie została jeszcze dodana do arkusza eksportu; jest tylko na szczegółach części.
