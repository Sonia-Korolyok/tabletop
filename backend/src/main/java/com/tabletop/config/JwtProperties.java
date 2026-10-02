package com.tabletop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tabletop.jwt")
public record JwtProperties(String secret, long ttlMinutes) {
}
