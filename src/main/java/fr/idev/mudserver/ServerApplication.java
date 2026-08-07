package fr.idev.mudserver;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import fr.idev.mudserver.game.actor.ClassService;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.actor.LevelService;
import fr.idev.mudserver.game.actor.MonsterService;
import fr.idev.mudserver.game.actor.NpcService;
import fr.idev.mudserver.game.actor.RaceService;
import fr.idev.mudserver.game.RoomService;

/**
 * Un {@link ApplicationRunner} s'exécute après le rafraîchissement du contexte
 * (DI complète) mais avant la publication de {@code ApplicationReadyEvent} —
 * donc mécaniquement avant {@code TelnetServer.start()}, qui écoute cet
 * événement. Cette garantie Spring Boot documentée évite d'avoir à coordonner
 * deux listeners du même événement via {@code @Order}. L'ordre des appels
 * ci-dessous est significatif : {@code warmRoomItems()} dépend du contenu déjà
 * chargé par {@code warmRooms()} (les rooms) et {@code warmItemTemplates()}
 * (les templates), voir {@link ItemService#warmRoomItems}. Même raison pour
 * {@code npcService.warmNpcs} : place ses instances dans les rooms déjà
 * chargées. {@code monsterService.warmMonsters} dépend lui aussi de
 * {@code warmRooms()} — les points de spawn eux-mêmes viennent maintenant de
 * {@code data/rooms.json} (voir {@code Room#getMonsterSpawns()}),
 * {@code data/monsters.json} ne portant plus que les templates — <em>et</em> de
 * {@code warmItemTemplates()}, appelé avant lui plutôt qu'après comme les
 * autres : ses tables de butin (voir {@code data/monsters.json}) référencent
 * des identifiants d'{@code ItemTemplate}, validés au chargement contre
 * {@link ItemService#templateIds()} pour échouer tôt sur un UUID invalide
 * plutôt qu'au premier drop en jeu. {@code npcService.warmNpcs} reçoit
 * désormais lui aussi {@link ItemService#templateNamesById()}, pour la même
 * raison — et pour dénormaliser le nom de chaque article vendu sur
 * {@code GameNpcSeller.NpcShopEntry} : le catalogue boutique d'un PNJ marchand
 * (voir {@code data/npcs.json}) est validé au démarrage plutôt qu'au premier
 * achat en jeu.
 *
 * <p>
 * Le {@code @ConditionalOnProperty} ci-dessous est porté par la
 * <em>méthode</em> {@code @Bean}, jamais par la classe
 * {@code ServerApplication} elle-même. {@code ServerApplication} est la source
 * primaire passée à {@code SpringApplication.run()} : un {@code @Conditional}
 * au niveau classe y serait évalué avant même le
 * {@code @ComponentScan}/{@code @EnableAutoConfiguration} hérités de
 * {@code @SpringBootApplication}, donc si la condition échouait — ce qui arrive
 * dans tous les tests, {@code app.telnet.enabled=false} dans
 * {@code src/test/resources/application.yml} — aucun bean de l'application ne
 * serait enregistré et tous les {@code @SpringBootTest} casseraient avec un
 * contexte vide. Un {@code @Conditional} sur une méthode {@code @Bean} n'a pas
 * ce problème : il n'écarte que ce bean précis, une fois la classe de
 * configuration déjà acceptée par Spring — c'est pour ça qu'il reste ici plutôt
 * que sur {@code ServerApplication}.
 */
@SpringBootApplication
public class ServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.telnet", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner warmupRunner(RoomService roomService, ItemService itemService, RaceService raceService,
            ClassService classService, LevelService levelService, MonsterService monsterService,
            NpcService npcService) {
        return args -> {
            roomService.warmRooms();
            itemService.warmItemTemplates();
            monsterService.warmMonsters(roomService.allRooms(), itemService.templateIds());
            npcService.warmNpcs(roomService.allRooms(), itemService.templateNamesById());
            itemService.warmRoomItems(roomService.allRooms());
            raceService.warmRaceBonuses();
            classService.warmClassDefinitions();
            levelService.warmXpThresholds();
        };
    }
}
