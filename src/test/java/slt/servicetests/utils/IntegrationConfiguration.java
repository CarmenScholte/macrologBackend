package slt.servicetests.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import slt.service.GoogleMailService;

@Profile("test")
@Configuration
@Slf4j
public class IntegrationConfiguration {

    @Bean
    @Primary
    public GoogleMailService mailService() {
        log.debug("Creating mock mail service");
        return new MyMockedMailService(null,null,null);
    }
}