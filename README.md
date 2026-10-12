# Label Scanner

Natywna aplikacja na Androida (Kotlin + Jetpack Compose). Na podstawie zdjęcia tabliczki znamionowej
urządzenia (zrobionego aparatem, wybranego z galerii lub udostępnionego do aplikacji) znajduje **model**
i **numer seryjny**. OCR działa lokalnie na urządzeniu (Google ML Kit), bez wysyłania zdjęć do sieci.

## Struktura
- `parser/` – czysty moduł Kotlin/JVM: `LabelParser` szuka wartości przy słowach „MODEL”, „SERIAL NO.”, „S/N”,
  „Typ”, „Nr seryjny” itd. (w tej samej linii, po prawej albo pod etykietą). Ma testy jednostkowe.
- `app/` – aplikacja: `TextScanner` (ML Kit → linie z ramkami → `LabelParser`) i interfejs w `MainActivity`.

## Budowanie
Wymaga Android Studio (lub Android SDK) i JDK 17.

    ./gradlew :parser:test          # testy parsera
    ./gradlew :app:assembleDebug    # APK w app/build/outputs/apk/debug/

Przykład: dla tabliczki Mitsubishi Electric aplikacja zwraca model `MSZ-EF25VE3W` i numer seryjny `7013350 T`.
