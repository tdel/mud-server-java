package fr.idev.mudserver;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Pattern "singleton container" : le conteneur est démarré une seule fois pour toute la JVM
 * de test et jamais arrêté explicitement (JUnit/Ryuk s'en chargent à la fin). Sans ça,
 * {@code @Container}/{@code @Testcontainers} redémarre un conteneur (donc un nouveau port
 * publié) à chaque classe de test, mais le contexte Spring mis en cache par
 * {@code @SpringBootTest} continue de pointer vers l'ancien port — la classe suivante essaie
 * alors de se connecter à un conteneur déjà arrêté ("Connection refused" après 30s de timeout
 * HikariCP).
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
