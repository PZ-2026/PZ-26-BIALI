# Środowisko Fullstack (Docker Compose)

## 1. Uruchom bazę danych ORAZ backend Spring Boot

Otwórz terminal w głównym katalogu projektu (PZ-26-BIALI) i uruchom:

```powershell
docker compose up -d --build
```

To uruchomi PostgreSQL na porcie `5432` oraz automatycznie wykona skrypt `baza+data.sql` przy pierwszym starcie pustej bazy.

## 2. Sprawdz logi (opcjonalnie)

```powershell
docker logs -f fitmanager-postgres
```

## 3. Uruchom backend

Konfiguracja aplikacji jest juz gotowa pod:

- DB: `fitmanager`
- user: `postgres`
- haslo: `postgres`
- host: `localhost:5432`

Po prostu odpal backend:

```powershell
.\gradlew.bat bootRun
```

## 4. Gdy zmieniasz SQL i chcesz inicjalizacje od nowa

Skrypt init wykona sie tylko przy tworzeniu nowego wolumenu. Aby zresetowac baze:

```powershell
docker compose down -v
docker compose up -d
```

## 5. Szybki test logowania

Endpoint:

- `POST /api/auth/login`

Przykladowe dane z `baza+data.sql`:

- `admin@fitmanager.pl` / `Admin123`
- `trener@fitmanager.pl` / `Trener123`
- `klient@fitmanager.pl` / `Haslo123`

Uwaga:

- W SQL sa placeholdery `hashed_pwd_*`.
- Backend przy starcie wykrywa placeholdery i automatycznie zamienia je na hashe BCrypt dla hasel powyzej.
