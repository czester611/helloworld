# Obudowa: Raspberry Pi 3 B+ z ekranem OLED 2,42" (SSD1309, 128×64)

Dwuczęściowa obudowa do druku FDM: **dno** z czterema słupkami pod płytkę Pi i **pokrywa** z oknem na ekran OLED.
Generowana skryptem `build_case.py` (parametryczna), więc wymiary można zmienić i przeliczyć.

## Pliki

| Plik | Co to |
|---|---|
| `base_*.stl` | dno (drukuj podłogą na stole) |
| `lid_*.stl` | pokrywa, **już obrócona** płaską stroną ekranu na stół |
| `*_waveshare.stl` | wariant na moduł Waveshare 61,5 × 39,5 mm (bez znanego rozstawu otworów, ekran mocowany taśmą/klejem) |
| `*_solder72.stl` | wariant na moduł 72 × 43,5 mm z 4 otworami 68 × 39 mm, Ø 2,8 mm (czopy ustalające w pokrywie) |
| `build_case.py` | generator (`python3 build_case.py waveshare\|solder72 <katalog>`), wymaga `trimesh manifold3d numpy` |
| `preview_solder72.png` | podgląd |

**Który wariant?** Zmierz suwmiarką płytkę swojego modułu. Jeśli ma ~72 × 43,5 mm i cztery otwory w rogach, użyj `solder72`. Jeśli ~61,5 × 39,5, użyj `waveshare`. Jeśli wymiary są inne, zmień `OLED_PRESETS` w skrypcie i wygeneruj ponownie.

## Wymiary i założenia (zweryfikowane tylko częściowo)

- Płytka Pi 3 B+: 85 × 56 mm, 4 otwory M2.5 w rozstawie 58 × 49 mm (3,5 mm od krawędzi). Rozstaw otworów to wiedza ogólna, nie sprawdzona w oficjalnym rysunku.
- Wysokości złączy (USB ~15,6, Ethernet ~13,5, GPIO 8,5 mm) są szacunkowe. Wolna wysokość nad płytką: **17,5 mm** (`TOP_CLEAR`).
- Wymiary modułów OLED pochodzą z opisów sklepów (Waveshare, universal-solder). **Położenie okna względem płytki modułu jest założone jako środek**: jeśli okno wypada krzywo, zmień `WINDOW_OFFSET`. Okno ma wymiar obszaru aktywnego 55,0 × 27,5 mm + 1,2 mm marginesu z każdej strony.
- Otwory na złącza: Ethernet + 2×USB (prawa ścianka), zasilanie micro-USB + HDMI + audio (przednia ścianka), karta SD (lewa ścianka). Przy portach ścianka jest otwarta od góry, a pokrywa zamyka ją od góry.
- Pozycje złączy wzięte z pamięci (układ Pi 3 B+). Sprawdź przymiarką do własnej płytki przed pełnym wydrukiem.

## Weryfikacja wykonana w skrypcie

Model zderzony z uproszczoną bryłą Pi 3 B+ (płytka, złącza, SD) i modułem OLED: **przecięcie 0 mm³** dla dna, pokrywy i modułu (w obu wariantach), siatki zamknięte. To sprawdza tylko geometrię na założonych wymiarach, nie wydruk.

## Druk

- Materiał: PLA wystarczy w pomieszczeniu; PETG, jeśli Pi będzie mocno grzany. ASA/PETG poza domem.
- Warstwa 0,2 mm, ścianki 5 linii (2,0 mm), wypełnienie 15–20 %. Podpór nie powinno być potrzebne.
- Zakładka pokrywy ma luz 0,25 mm na stronę (`FIT`), a płytka 0,5 mm od ścianek (`PI_CLR`). Jeśli pokrywa wchodzi za ciasno lub za luźno, zmień `FIT` i przelicz. Dobrze najpierw wydrukować samo dno.

## Montaż

1. Płytkę Pi przykręć do słupków 4 wkrętami M2.5 × 6 (samogwint w otworach Ø 2,1 mm).
2. Moduł OLED wkładasz do kieszeni od spodu pokrywy ekranem do okna. Wariant `solder72`: czopy wchodzą w otwory modułu; całość i tak przyklej taśmą piankową lub kroplą kleju na gorąco. Wariant `waveshare`: tylko taśma/klej.
3. Okablowanie modułu do GPIO prowadź krótkimi przewodami lub taśmą. Nad złączem GPIO zostaje ok. 9 mm wolnego miejsca, więc **proste wtyki Dupont nie zmieszczą się**. Użyj przewodów lutowanych albo kątowych złączek. Jeśli chcesz wtyki proste, zwiększ `TOP_CLEAR` do ok. 28 mm.
4. Pokrywę dociskasz na zakładkę i przykręcasz 4 wkrętami M2.5 × 8 przez uszy w narożnikach.

Typowe połączenie SPI dla modułu SSD1309 (sprawdź opis swojego modułu, bo bywa 4-przewodowy lub I2C po zmianie rezystorów): VCC → 3,3 V, GND → GND, DIN → GPIO10 (MOSI), CLK → GPIO11 (SCLK), CS → GPIO8 (CE0), DC → GPIO25, RST → GPIO27. Włącz SPI w `raspi-config`.
