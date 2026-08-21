package fr.idev.mudserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.game.catalog.ItemTemplateCatalog;
import fr.idev.mudserver.game.catalog.LevelCatalog;
import fr.idev.mudserver.game.catalog.MonsterCatalog;
import fr.idev.mudserver.game.catalog.SpellCatalog;
import fr.idev.mudserver.game.catalog.WorldTemplateCatalog;

@SpringBootApplication
public class ServerApplication {

    private static final Logger log = LoggerFactory.getLogger(ServerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }

    // Ordre significatif : les item templates doivent être chargées avant les sorts
    // (même famille de données statiques), et les sorts avant
    // materializeDefaultWorld()
    // puisque la création de personnage (WorldInstance.createCharacter) apprend les
    // sorts de niveau 1 via SpellCatalogHolder dès la création du monde par défaut.
    @Bean
    public ApplicationRunner warmupRunner(ItemTemplateCatalog itemTemplateCatalog, SpellCatalog spellCatalog,
            LevelCatalog levelCatalog, MonsterCatalog monsterCatalog, WorldTemplateCatalog worldTemplateCatalog,
            WorldInstanceService worldInstanceService) {
        return args -> {
            long start = System.currentTimeMillis();
            log.info("startup.warmup_started");
            itemTemplateCatalog.warmItemTemplates();
            spellCatalog.warmSpells();
            worldTemplateCatalog.warmWorldTemplates();
            monsterCatalog.warmMonsterTemplates();
            levelCatalog.warmXpThresholds();
            worldInstanceService.materializeDefaultWorld();
            log.info("startup.warmup_completed durationMs={}", System.currentTimeMillis() - start);
        };
    }
}
