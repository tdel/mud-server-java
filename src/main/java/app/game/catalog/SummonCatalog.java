package app.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import app.domain.actor.CreatureRace;
import app.domain.actor.template.SummonTemplate;
import app.domain.actor.template.SummonTemplate.SummonLevel;
import app.domain.actor.template.SummonTemplate.SummonSpecial;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

// Serviteurs invocables, chargés une fois à la construction du bean (données
// statiques, aucune dépendance aux autres catalogues).
@Service
public class SummonCatalog {

    private static final Logger log = LoggerFactory.getLogger(SummonCatalog.class);

    private static final String RESOURCE = "/data/summons/summons.xml";

    private final Map<String, SummonTemplate> templates = new ConcurrentHashMap<>();

    public SummonCatalog(XmlMapper xmlMapper) {
        try (InputStream in = getClass().getResourceAsStream(RESOURCE)) {
            Document document = xmlMapper.readValue(in, Document.class);
            for (SummonXml summon : document.summon()) {
                List<SummonLevel> levels = summon.level().stream()
                        .map(l -> new SummonLevel(l.level(), l.hp(), l.pAtk(), l.mAtk(), l.pDef(), l.mDef())).toList();
                templates.put(summon.id(), new SummonTemplate(summon.id(), summon.name(), summon.race(), summon.model(),
                        summon.special(), summon.lifetime(), summon.speed(), summon.atkSpd(), levels));
            }
            log.info("summon.templates_loaded count={}", templates.size());
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + RESOURCE, e);
        }
    }

    public Optional<SummonTemplate> findById(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    private record Document(
            @JacksonXmlProperty(localName = "summon") @JacksonXmlElementWrapper(useWrapping = false) List<SummonXml> summon) {
    }

    private record SummonXml(@JacksonXmlProperty(isAttribute = true) String id, String name, CreatureRace race,
            String model, SummonSpecial special, int lifetime, double speed, int atkSpd,
            @JacksonXmlProperty(localName = "level") @JacksonXmlElementWrapper(useWrapping = false) List<LevelXml> level) {
    }

    private record LevelXml(@JacksonXmlProperty(isAttribute = true) int id,
            @JacksonXmlProperty(isAttribute = true) int level, @JacksonXmlProperty(isAttribute = true) double hp,
            @JacksonXmlProperty(isAttribute = true) double pAtk, @JacksonXmlProperty(isAttribute = true) double mAtk,
            @JacksonXmlProperty(isAttribute = true) double pDef, @JacksonXmlProperty(isAttribute = true) double mDef) {
    }
}
