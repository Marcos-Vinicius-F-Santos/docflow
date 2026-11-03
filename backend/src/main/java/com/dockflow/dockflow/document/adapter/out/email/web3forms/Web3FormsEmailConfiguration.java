package com.dockflow.dockflow.document.adapter.out.email.web3forms;

import com.dockflow.dockflow.document.port.out.email.DocumentEmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/** Backend-only Web3Forms configuration. Values are supplied by the environment. */
@Configuration(proxyBeanMethods = false)
public class Web3FormsEmailConfiguration {

    @Bean
    DocumentEmailSender documentEmailSender(
        @Value("${docflow.notification.email.web3forms-access-key:}") String accessKey,
        @Value("${docflow.notification.email.web3forms-url:https://api.web3forms.com/submit}") String endpoint
    ) {
        return new Web3FormsEmailSender(accessKey, URI.create(endpoint));
    }
}
