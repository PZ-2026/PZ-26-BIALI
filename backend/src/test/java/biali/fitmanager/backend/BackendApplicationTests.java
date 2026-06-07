package biali.fitmanager.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lekki test jednostkowy klasy startowej bez uruchamiania kontekstu Spring ani bazy danych.
 */
@DisplayName("Test klasy startowej aplikacji")
class BackendApplicationTests {

    /**
     * Sprawdza, że klasa {@link BackendApplication} istnieje i można utworzyć jej instancję
     * bez ładowania kontekstu aplikacji.
     */
    @Test
    @DisplayName("Klasa BackendApplication jest dostepna")
    void applicationClassIsAvailable() {
        BackendApplication application = assertDoesNotThrow(BackendApplication::new);
        assertNotNull(application);
    }
}
