package app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.xml.XmlMapper;

@Configuration
public class XmlMapperConfig {

    // FAIL_ON_UNKNOWN_PROPERTIES désactivé car skills.xml mélange <skill> et
    // <passive> sous la même racine : SkillCatalog/PassiveSkillCatalog ne lisent
    // chacun que leur propre type d'élément et ignorent l'autre.
    @Bean
    public XmlMapper xmlMapper() {
        return XmlMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }
}
