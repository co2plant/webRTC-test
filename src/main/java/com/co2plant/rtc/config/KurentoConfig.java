package com.co2plant.rtc.config;

import org.kurento.client.KurentoClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.beans.factory.annotation.Value;

@Configuration
public class KurentoConfig {

    @Value("${kurento.client.url}")
    private String kurentoRawUrl;

    @Bean
    public KurentoClient kurentoClient() {
        return KurentoClient.create(kurentoRawUrl);
    }
}
