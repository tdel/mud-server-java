package app.domain.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

// Table L2 H5 par niveau d'une classe (lvlUpgainData de stats/chars/baseStats) :
// PV et PM de base, régénération de PV et de PM par tick de 3 s. Chaque classe
// et chaque profession a la sienne (<vitals> de data/classes/<nom>.xml) : celle
// d'une profession reprend celle de ses parents jusqu'à son niveau d'accès, puis
// monte plus vite (un Warrior niveau 40 a 1044 PV de base, un Human Fighter
// resté sans profession 637).
public final class ClassVitals {

    private static final XmlMapper XML_MAPPER = new XmlMapper();

    public record Level(double hp, double mp, double hpRegen, double mpRegen) {
    }

    private final NavigableMap<Integer, Level> byLevel;

    private ClassVitals(NavigableMap<Integer, Level> byLevel) {
        this.byLevel = byLevel;
    }

    // Valeurs au niveau `level` (celles du dernier niveau tabulé au-delà).
    public Level at(int level) {
        var entry = byLevel.floorEntry(level);
        return entry != null ? entry.getValue() : byLevel.firstEntry().getValue();
    }

    static ClassVitals load(String resource) {
        try (InputStream in = ClassVitals.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Ressource introuvable : " + resource);
            }
            Root root = XML_MAPPER.readValue(in, Root.class);
            if (root.vitals() == null || root.vitals().level() == null || root.vitals().level().isEmpty()) {
                throw new IllegalStateException("<vitals> absent de " + resource);
            }
            NavigableMap<Integer, Level> byLevel = new TreeMap<>();
            for (LevelXml level : root.vitals().level()) {
                byLevel.put(level.val(), new Level(level.hp(), level.mp(), level.hpRegen(), level.mpRegen()));
            }
            return new ClassVitals(byLevel);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + resource, e);
        }
    }

    // Les <skill> de l'arbre et les autres blocs de la classe sont lus ailleurs.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Root(VitalsXml vitals) {
    }

    private record VitalsXml(
            @JacksonXmlProperty(localName = "level") @JacksonXmlElementWrapper(useWrapping = false) List<LevelXml> level) {
    }

    private record LevelXml(@JacksonXmlProperty(isAttribute = true) int val,
            @JacksonXmlProperty(isAttribute = true) double hp, @JacksonXmlProperty(isAttribute = true) double mp,
            @JacksonXmlProperty(isAttribute = true) double hpRegen,
            @JacksonXmlProperty(isAttribute = true) double mpRegen) {
    }
}
