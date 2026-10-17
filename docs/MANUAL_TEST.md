# Ręczna lista kontrolna (aplikacja Android)

Kod aplikacji nie był budowany w środowisku implementatora (brak Android SDK) — przejdź tę listę po pierwszym udanym
`./gradlew :app:assembleDebug`.

## Start
- [ ] Aplikacja startuje, lista „Moje części” pusta z komunikatem; karta „Drukarka: Snapmaker U1 · Filament: PLA (domyślny) (PLA)”.
- [ ] Profile: edycja drukarki i filamentu, dodanie filamentu, blokada usunięcia aktywnego/ostatniego.
- [ ] Kompensacja: wartości startowe PLA (otwory −0,25; szczeliny −0,20; czopy +0,10; obrysy +0,10).
- [ ] Diagnostyka: „Test OpenCV” zwraca „OK: 40,0 × 25,0 mm, otwór Ø8,x”; widoczne wersje OpenCV i aplikacji.

## Arkusz markerów
- [ ] „Arkusz markerów” pokazuje podgląd; „Zapisz PDF” i „Udostępnij PDF” działają.
- [ ] Wydruk 100%: linijka kontrolna ma 100 mm; odległość markerów 0–1 = 180 mm, 0–3 = 267 mm (korekta skali jeśli nie).

## Ścieżka ze zdjęcia
- [ ] Nowa część → Ze zdjęcia → zdjęcie aparatem (bez pytania o uprawnienia) i z galerii.
- [ ] Grubość wymagana (0,4–200); „Przetwórz” pokazuje obraz z obrysem i otworami, wymiary bbox, ostrzeżenia.
- [ ] Zmiana progu (wyłączenie Auto) przelicza obrys; brak markerów / część na krawędzi → czytelny błąd po polsku.
- [ ] Płytka o znanych wymiarach: błąd ≤ 0,5 mm po korekcji grubości (ew. skala z wymiaru referencyjnego).
- [ ] Edytor obrysu: skala referencyjna, obróć 90°, lustro, dodanie otworu dotknięciem, edycja średnicy, zamiana konturu na otwór,
      fazki; „Zapisz” → szczegóły części.

## Szablony i eksport
- [ ] Wszystkie 5 szablonów: walidacja, podgląd, tabela cech; zapis i edycja.
- [ ] Eksport STL i 3MF (plaster i pełna część) → plik otwiera się w Snapmaker Orca / innym slicerze; wymiary zgodne.
- [ ] „Zapisz plik…” i „Udostępnij” działają; w historii prób pojawia się nowa pozycja; status części → Testowanie.

## Wyniki prób i uczenie
- [ ] Próba → „za ciasno o 0,2 mm” dla otworu (także z notatki) → bias otworów PLA −0,25 → −0,3833; ponowny eksport ma większy otwór CAD.
- [ ] Przełącznik „Uwzględnij” w Kompensacji przelicza wartość; ręczne nadpisanie i „Przywróć domyślne”.
- [ ] Kalibracja: eksport kuponu, wpisanie 5 pomiarów, podsumowanie zmian.

## Obudowa
- [ ] Ekran główny → „Obudowa” → wybór „Wg wymiarów”: 50 × 30, wysokość 15 → podgląd z góry (ścianki, wnęka 51 × 31, obrys przerywany);
      komunikat o ręcznej wysokości i portach widoczny; dno 55 × 35 × 18,6, pokrywa 55 × 35 × 3,6.
- [ ] „Eksport…” → 3MF: w slicerze dwa obiekty obok siebie, pokrywa płaską stroną na stole, zakładką do góry, bez podpór.
- [ ] Eksport STL: udostępnienie dwóch plików (`..-base_full_N.stl`, `..-lid_full_N.stl`); „Zapisz plik…” pyta dwa razy; po anulowaniu brak próby w historii.
- [ ] „Plasterek”: mały narożnik 25 × 25 (dno + pokrywa z zakładką); wydrukuj i sprawdź, czy zakładka wchodzi suwliwie (bez luzu zbyt dużego); wpisz wynik próby.
- [ ] Wycięcie (np. Północ, pozycja 25, szerokość 10, wysokość 5, środek 6) pojawia się czerwonym polem na podglądzie i w wydruku.
- [ ] Tryb „Śruby”: dla prostokąta słupki jako uszy na zewnątrz (ostrzeżenie); zwiększ luz do 6 mm → słupki wewnątrz. Wkręt M3 trzyma się w otworze 2,5 mm.
- [ ] Ze zdjęcia: arkusz → zdjęcie → edytor obrysu → „Zapisz” → ekran Obudowa z obrysem i wysokością z pola „grubość”; część w bibliotece ma nazwę obudowy.
- [ ] Obudowa większa niż 270 mm i obiekt < 10 mm → ostrzeżenia; filament: PLA (wnętrze) / ASA (przełącznik „na zewnątrz”).
