package com.example.taxbackend.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    @Bean
    public TomcatServletWebServerFactory tomcatFactory() {
        return new TomcatServletWebServerFactory() {
            @Override
            protected void customizeConnector(Connector connector) {
                super.customizeConnector(connector);
                connector.setProperty("maxParameterCount", "10000"); // For query params
                connector.setProperty("maxPostSize", "10485760"); // For POST body (10MB)
                connector.setProperty("fileCountLimit", "50"); // **Adjust your file count limit here**
            }
        };
    }
}
