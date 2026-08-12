package fr.idev.mudserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import fr.idev.mudserver.game.ItemTemplateService;
import fr.idev.mudserver.game.actor.LevelService;
import fr.idev.mudserver.game.actor.MonsterService;
import fr.idev.mudserver.game.WorldTemplateService;

@SpringBootApplication
public class ServerApplication {

    private static final Logger log = LoggerFactory.getLogger(ServerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.telnet", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner warmupRunner(ItemTemplateService itemTemplateService, LevelService levelService,
            MonsterService monsterService, WorldTemplateService worldTemplateService) {
        return args -> {
            long start = System.currentTimeMillis();
            log.info("startup.warmup_started");
            itemTemplateService.warmItemTemplates();
            worldTemplateService.warmWorldTemplates(itemTemplateService.templateSummariesById());
            monsterService.warmMonsterTemplates(itemTemplateService.templateIds());
            levelService.warmXpThresholds();
            log.info("startup.warmup_completed durationMs={}", System.currentTimeMillis() - start);
        };
    }
}
