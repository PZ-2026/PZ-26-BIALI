# Używamy oficjalnego obrazu z Javą 17
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Kopiujemy wszystkie pliki backendu
COPY . .

# Nadajemy uprawnienia do uruchomienia wrappera gradle
RUN chmod +x gradlew

# Wystawiamy port 8080 i uruchamiamy aplikację
EXPOSE 8080
CMD ["./gradlew", "bootRun", "--no-daemon"]