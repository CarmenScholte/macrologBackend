package slt.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Slf4j
@Data
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties("spring.datasource")
public class DatabaseConfig {

    private String url;
    private String username;
    private String password;

    @PostConstruct
    public void configLoaded() {
        log.info("Database configured " + username);
    }
}
