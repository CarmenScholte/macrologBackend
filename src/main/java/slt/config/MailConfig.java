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
@ConfigurationProperties("mail")
public class MailConfig {

    private String username;
    private String password;
    private String host;
    private String port;

    @PostConstruct
    public void configLoaded() {
        log.info("Mail configured " + host);
    }
}
