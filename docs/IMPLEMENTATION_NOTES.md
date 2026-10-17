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

## Obudowa (enclosure)
- Logika: `core/enclosure` (`EnclosureSpec`, `EnclosureBuilder`, `EnclosureResult`); serializacja przez `EnclosureDesign`
  (podtyp `PartDesign`, `"type":"enclosure"`, w pakiecie `design` — wymóg sealed). Część w bazie: `source = ENCLOSURE`,
  próby jako `FULL_PART` / `FIT_TEST` (plasterek) — istniejące `ExportService.commit` (próba dopiero po udanym zapisie).
- Zdjęcie daje tylko obrys; wysokość obiektu, wystające elementy i położenie portów podaje użytkownik (komunikat w UI:
  `EnclosureBuilder.MANUAL_INPUT_NOTE`). Ścieżka ze zdjęcia: `photo?enclosure=true` → edytor obrysu → ekran Obudowa;
  część ze zdjęcia jest zamieniana w obudowę (`PartRepository.convertToEnclosure`, zdjęcie zostaje). Tryb „wg wymiarów”: prostokąt.
- Kompensacja bez nowego typu cechy: wnęka = obrys + luz (domyślnie 0,5 mm/stronę) jako SLOT (offset −bias/2); wymiary zewnętrzne
  i zakładka jako OUTLINE; otwory pod wkręty i wycięcia okrągłe jako HOLE, wycięcia prostokątne jako SLOT. Zakładka pokrywy:
  cel = wnęka − luz klasy pasowania (domyślnie SLIDE 0,15 mm średnicowo), pierścień 1,2 mm, wysokość 2 mm.
- Obrys zewnętrzny: wnęka → offset (ścianka − R, MITRE) → offset +R (ROUND), więc ostre rogi są zaokrąglone promieniem R (2 mm).
- Ścianki z wycięciami: warstwy `Prism` dzielone na przedziały z (podłoga, początek/koniec wycięć, wysokość podpórek, szczyt);
  w każdym przedziale od pierścienia ścianki odejmowany jest przekrój wycięcia (prostokąt; okrąg jako stos cięciw co wysokość warstwy),
  potem suma ze słupkami/podpórkami i odjęcie otworów. Położenie wycięcia wyznacza promień od krawędzi do wnęki (działa też dla
  obrysów nieprostokątnych i krawędzi wg indeksu). Pozycja = ŚRODEK wycięcia od początku krawędzi (N/S od zachodu, E/W od południa),
  wysokość środka od górnej powierzchni podłogi (przy podpórkach dodaj ich wysokość).
- Tryb śrub: 4 słupki w narożnikach prostokąta otaczającego wnękę (Ø = otwór + 2×2 mm, otwór 2,5 mm). Słupek kolidujący z obiektem
  (dla prostokątnego obiektu — zawsze, bo wnęka ma ostre rogi) jest domyślnie wyprowadzany na zewnątrz ścianki jako „ucho”
  (odstępstwo od „pomiń słupek”; przełącznik `screwEars = false` przywraca pomijanie z ostrzeżeniem). Pokrywa: otwór przelotowy
  = Ø wkręta + klasa LOOSE (3,4 mm dla M3) i zagłębienie na łeb (1,9·Ø+0,4, głębokość = grubość pokrywy − 0,8, max 2 mm) od strony stołu.
- Pokrywa jest budowana od razu w pozycji do druku (płaska zewnętrzna strona na z=0, zakładka do góry; lustro względem osi X
  odpowiada obrotowi o 180°). Dno i pokrywa leżą obok siebie z odstępem 10 mm. Brak podpór poza mostem nad zagłębieniem na łeb.
- Eksport: 3MF z dwoma obiektami (`PartExporter.exportGroups3mf`, `ThreeMfWriter.writeParts`) albo dwa pliki STL
  (`FileNames.exportName("<nazwa> base|lid", ...)`; „Udostępnij” = ACTION_SEND_MULTIPLE, „Zapisz plik…” = dwa kolejne okna zapisu).
- Plasterek pasowania (`EnclosureBuilder.buildFitTest`): narożnik 25×25 mm dna (podłoga + ścianka do wys. zakładki + 1 mm) i pokrywy
  z zakładką (albo słupkiem i otworem), bez wycięć, szczelin i podpórek; wchodzi przez `DesignBuilder.buildFitTest`.
- Ostrzeżenia: pole druku (270×270×270 mm, wartość NIEZWERYFIKOWANA, `MachineLimits`), obrys < 10 mm, ścianka nie będąca
  wielokrotnością dyszy, zakładka wyższa niż zapas nad obiektem, wycięcia poza krawędzią/ścianką/strefą zakładki, siatka (`MeshValidator`).
- Zalecenia materiału: `UsageAdvisor.forEnclosure(outdoor)` — wnętrze: PLA (alt. PETG), zewnątrz: ASA (alt. PETG) + wskazówka orientacji.
- Zweryfikowane: `./gradlew -p geometry test` (liczby: wnęka 51×31, zewn. 55×35, wysokości 18,6 / 3,6, objętości, zakładka w ściance,
  objętość usunięta przez wycięcia, słupki, szczeliny, podpórki, siatki zamknięte bez zdegenerowanych trójkątów). Ekrany Compose
  weryfikuje tylko CI (`assembleDebug`); nie sprawdzano ich na urządzeniu.
