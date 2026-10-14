# Notatki implementacyjne

Szczegółowe decyzje i odstępstwa: `docs/DECISIONS.md` (zgodnie z DESIGN.md). Tu tylko ustalenia od użytkownika.

- Domyślny profil drukarki przy pierwszym uruchomieniu: **Snapmaker U1** (FDM, toolchanger, dysza 0,4 mm, warstwa 0,2 mm,
  stopa słonia 0,2 mm; ustawiony jako aktywny). Pole druku ok. 270×270×270 mm jest z pamięci użytkownika i oznaczone w kodzie
  (`ProfileRepository.DEFAULT_BUILD_VOLUME_MM`) jako DO WERYFIKACJI; trafia tylko do notatki profilu, nie do obliczeń.
  Kompensacje nadal uczone per drukarka + filament, priory PLA/PETG z DESIGN.md. Eksport 3MF/STL bez zmian (Snapmaker Orca).
