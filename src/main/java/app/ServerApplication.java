package app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import app.game.WorldInstanceService;
import app.game.catalog.ItemSetCatalog;
import app.game.catalog.ItemTemplateCatalog;
import app.game.catalog.LevelCatalog;
import app.game.catalog.MonsterCatalog;
import app.game.catalog.PassiveSkillCatalog;
import app.game.catalog.SkillCatalog;
import app.game.catalog.WorldTemplateCatalog;

@SpringBootApplication
public class ServerApplication {

    private static final Logger log = LoggerFactory.getLogger(ServerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }

    // Ordre significatif : les sorts doivent être chargés avant les item templates,
    // car ItemTemplateCatalog dénormalise les grantedSkillIds d'items/*.xml en
    // objets
    // ActiveSkill via SkillCatalog.getById dès le chargement (armes/armures
    // magiques
    // octroyant un sort à l'équipement). Les item templates doivent ensuite être
    // chargées avant materializeDefaultWorld(), puisque la création de personnage
    // (WorldInstance.createCharacter) apprend les sorts de niveau 1 via
    // SkillCatalogHolder dès la création du monde par défaut.
    @Bean
    public ApplicationRunner warmupRunner(ItemTemplateCatalog itemTemplateCatalog, ItemSetCatalog itemSetCatalog,
            SkillCatalog skillCatalog, PassiveSkillCatalog passiveSkillCatalog, LevelCatalog levelCatalog,
            MonsterCatalog monsterCatalog, WorldTemplateCatalog worldTemplateCatalog,
            WorldInstanceService worldInstanceService) {
        return args -> {
            long start = System.currentTimeMillis();
            log.info("startup.warmup_started");
            skillCatalog.warmSkills();
            passiveSkillCatalog.warmPassiveSkills();
            itemTemplateCatalog.warmItemTemplates();
            itemSetCatalog.warmItemSets();
            worldTemplateCatalog.warmWorldTemplates();
            monsterCatalog.warmMonsterTemplates();
            levelCatalog.warmXpThresholds();
            worldInstanceService.materializeDefaultWorld();
            log.info("startup.warmup_completed durationMs={}", System.currentTimeMillis() - start);
        };
    }
}
