# FitLab

Hobbystyczna aplikacja na Androida do odtwarzania i drukowania 3D (FDM) zgubionych lub złamanych części tak, żeby
pasowały za pierwszym lub drugim razem: zdjęcie na arkuszu z markerami ArUco albo szablon parametryczny, edycja wymiarów,
wydruk próbny („plaster”), uczenie kompensacji per drukarka + filament, eksport STL/3MF.

Pełny projekt: [docs/DESIGN.md](docs/DESIGN.md). Lista zadań: [docs/TASKS.md](docs/TASKS.md).
Odstępstwa i decyzje: [docs/DECISIONS.md](docs/DECISIONS.md), [docs/IMPLEMENTATION_NOTES.md](docs/IMPLEMENTATION_NOTES.md).
Ręczna lista kontrolna: [docs/MANUAL_TEST.md](docs/MANUAL_TEST.md).

## Budowanie

- Rdzeń JVM (geometria, kompensacja, szablony, eksport, wizja; bez Androida): `./gradlew -p geometry test`
- Aplikacja (wymaga Android SDK i dostępu do Google Maven): `./gradlew :app:assembleDebug`

Struktura: `geometry/core` (czysty Kotlin), `geometry/vision` (OpenCV), `app` (Android, Compose, Room).
