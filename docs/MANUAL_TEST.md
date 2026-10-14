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
