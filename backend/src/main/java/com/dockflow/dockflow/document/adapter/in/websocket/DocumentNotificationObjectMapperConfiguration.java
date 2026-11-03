package com.dockflow.dockflow.document.adapter.in.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies the JSON mapper used by the provider-neutral WebSocket contract. */
@Configuration(proxyBeanMethods = false)
public class DocumentNotificationObjectMapperConfiguration {

    @Bean
    ObjectMapper documentNotificationObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
