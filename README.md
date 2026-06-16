# FitManager — System do zarządzania siłownią

Aplikacja do obsługi siłowni: karnety, płatności, trenerzy i klienci, rezerwacje zajęć oraz panel administracyjny z raportami. Składa się z backendu REST (Spring Boot) i aplikacji mobilnej na Androida (Kotlin + Jetpack Compose).

---

## Zespół BIALI

- Kacper Kowalski
- Jakub Kwaśniak
- Olaf Słowik
- Piotr Gajda

---

## Opis

**FitManager** to system informatyczny wspierający codzienną pracę siłowni. Użytkownicy logują się do aplikacji mobilnej z rolą **ADMIN**, **TRAINER** lub **CLIENT**. Backend udostępnia API REST zabezpieczone tokenem JWT.

Administrator zarządza użytkownikami, typami karnetów i przypisaniami trener–klient, a także ma wgląd w statystyki. Trener obsługuje podopiecznych, plany i sesje. Klient kupuje karnety, rezerwuje treningi i śledzi postępy.

---

## Funkcjonalności

### Wspólne

- Rejestracja i logowanie (JWT)
- Profil użytkownika (`/api/me`)

### Klient (CLIENT)

- Przegląd i zakup karnetów
- Doładowanie salda konta
- Rezerwacja sesji treningowych
- Wybór trenera / wynajem trenera
- Śledzenie postępów i raport postępów (PDF)

### Trener (TRAINER)

- Lista podopiecznych
- Plany treningowe i sesje
- Podgląd postępów klientów
- Raport postępów klienta (PDF)

### Administrator (ADMIN)

- CRUD użytkowników (admin, trener, klient)
- Zarządzanie typami karnetów
- Przypisywanie klientów do trenerów
- Wykresy i statystyki (`/api/admin/charts/data`)
- Generowanie raportu PDF (`/api/admin/reports/users/pdf`):
  - przegląd karnetów (aktywne, wygasające w 14 dni, klienci bez karnetu)
  - przychody z ostatnich 30 dni
  - sprzedaż wg typu karnetu
  - przypisania trener → klienci
  - klienci bez przypisanego trenera
  - ostatnie płatności
  - pełna lista użytkowników

---

## Technologie

| Warstwa            | Technologie                                                            |
| ------------------ | ---------------------------------------------------------------------- |
| **Backend**        | Java 17, Spring Boot 3.1, Spring Security, Spring Data JPA, JWT (jjwt) |
| **Baza danych**    | PostgreSQL 15                                                          |
| **PDF**            | OpenPDF                                                                |
| **Android**        | Kotlin, Jetpack Compose, Retrofit, Coroutines                          |
| **Testy backendu** | JUnit 5, Mockito                                                       |
| **Narzędzia**      | Gradle, Docker Compose (opcjonalnie)                                   |

---

## Struktura projektu

```
PZ-26-BIALI/
├── backend/                 # API REST (Spring Boot)
│   ├── src/main/java/       # kod aplikacji
│   ├── src/main/resources/  # schema.sql, data.sql, application.properties
│   ├── src/test/            # testy JUnit
│   └── strona.http          # przykładowe requesty HTTP
├── FitManager/              # aplikacja Android
│   └── app/                 # moduł główny (Compose UI, sieć)
├── baza+data.sql            # skrypt inicjalizacji bazy (Docker)
├── docker-compose.yml       # PostgreSQL + backend w kontenerach
└── README.md
```

---

## Wymagania

### Backend (lokalnie)

- **JDK 17**
- **PostgreSQL** (lokalnie lub przez Docker)
- **Gradle** (wrapper w projekcie — `./gradlew`)

### Android

- **Android Studio** (aktualna wersja)
- **Android SDK** — min. API 24
- Emulator lub telefon w tej samej sieci Wi‑Fi co komputer z backendem

---

## Uruchomienie (baza + backend + Android)

### Opcja A — Docker Compose (baza + backend)

Z katalogu głównego repozytorium:

```powershell
docker compose up -d
```

- PostgreSQL: `localhost:5432`, baza `fitmanager`, user/hasło: `postgres` / `postgres`
- Backend: `http://localhost:8080`

### Opcja B — Backend lokalnie (Gradle)

1. Uruchom PostgreSQL i utwórz bazę `fitmanager` (albo użyj Dockera tylko na bazę).

2. Domyślna konfiguracja w `backend/src/main/resources/application.properties`:
   - URL: `jdbc:postgresql://localhost:5432/fitmanager`
   - user: `postgres`, hasło: `postgres`

3. Uruchom backend:

```powershell
cd backend
./gradlew bootRun
```

Przy starcie Spring ładuje `schema.sql` i `data.sql` (struktura + dane testowe).

API: **http://localhost:8080**

### Android

1. Otwórz folder `FitManager/` w Android Studio.
2. Sprawdź adres API w `FitManager/app/build.gradle.kts`:
   - **Emulator:** `http://10.0.2.2:8080/` (localhost komputera)
   - **Telefon fizyczny:** ustaw IP komputera w Wi‑Fi, np. `http://192.168.x.x:8080/`
3. Zbuduj i uruchom aplikację (Run).
4. Zaloguj się kontem testowym (np. admin).

---

## Testy

### Wszystkie testy backendu

```powershell
cd backend
./gradlew test
```

Windows (CMD):

```cmd
cd backend
gradlew.bat clean test
```
