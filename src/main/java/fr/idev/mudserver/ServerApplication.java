package fr.idev.mudserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import fr.idev.mudserver.game.MovementTicker;
import fr.idev.mudserver.game.catalog.ItemTemplateCatalog;
import fr.idev.mudserver.game.catalog.LevelCatalog;
import fr.idev.mudserver.game.catalog.MonsterCatalog;
import fr.idev.mudserver.game.catalog.WorldTemplateCatalog;

@SpringBootApplication
public class ServerApplication {

    private static final Logger log = LoggerFactory.getLogger(ServerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.telnet", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner warmupRunner(ItemTemplateCatalog itemTemplateCatalog, LevelCatalog levelCatalog,
            MonsterCatalog monsterCatalog, WorldTemplateCatalog worldTemplateCatalog) {
        return args -> {
            long start = System.currentTimeMillis();
            log.info("startup.warmup_started");
            itemTemplateCatalog.warmItemTemplates();
            worldTemplateCatalog.warmWorldTemplates();
            monsterCatalog.warmMonsterTemplates();
            levelCatalog.warmXpThresholds();
            log.info("startup.warmup_completed durationMs={}", System.currentTimeMillis() - start);
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.telnet", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner movementTickerRunner(MovementTicker movementTicker) {
        return args -> movementTicker.start();
    }
}
