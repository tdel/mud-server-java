package app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.dataformat.xml.XmlMapper;

@Configuration
public class XmlMapperConfig {

    @Bean
    public XmlMapper xmlMapper() {
        return new XmlMapper();
    }
}
