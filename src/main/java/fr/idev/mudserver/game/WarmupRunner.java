package fr.idev.mudserver.game;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Un {@link ApplicationRunner} s'exécute après le rafraîchissement du contexte
 * (DI complète) mais avant la publication de {@code ApplicationReadyEvent} —
 * donc mécaniquement avant {@code TelnetServer.start()}, qui écoute cet
 * événement. Cette garantie Spring Boot documentée évite d'avoir à coordonner
 * deux listeners du même événement via {@code @Order}. L'ordre des appels
 * ci-dessous est significatif : {@code warmRoomItems()} dépend du contenu déjà
 * chargé par {@code warmRooms()} (les rooms) et {@code warmItemTemplates()}
 * (les templates), voir {@link ItemService#warmRoomItems}.
 */
@Component
@ConditionalOnProperty(prefix = "app.telnet", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WarmupRunner implements ApplicationRunner {

    private final RoomService roomService;
    private final ItemService itemService;
    private final RaceService raceService;
    private final ClassService classService;
    private final LevelService levelService;

    public WarmupRunner(RoomService roomService, ItemService itemService, RaceService raceService,
            ClassService classService, LevelService levelService) {
        this.roomService = roomService;
        this.itemService = itemService;
        this.raceService = raceService;
        this.classService = classService;
        this.levelService = levelService;
    }

    @Override
    public void run(ApplicationArguments args) {
        roomService.warmRooms();
        itemService.warmItemTemplates();
        itemService.warmRoomItems(roomService.allRooms());
        raceService.warmRaceBonuses();
        classService.warmClassHitDice();
        levelService.warmXpThresholds();
    }
}
