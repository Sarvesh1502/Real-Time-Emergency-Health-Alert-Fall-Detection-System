package com.example.alert.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {
  @Bean
  public CorsFilter corsFilter() {
    CorsConfiguration cfg = new CorsConfiguration();
    // Allow ngrok domain and localhost for development
    cfg.setAllowedOrigins(Arrays.asList(
      "https://nonhierarchically-unabased-giancarlo.ngrok-free.dev",
      "http://localhost:5500"
    ));
    cfg.setAllowCredentials(true);
    cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(Arrays.asList("*"));
    // Explicitly expose headers that might be needed by the frontend
    cfg.setExposedHeaders(Arrays.asList(
      "Access-Control-Allow-Origin",
      "Access-Control-Allow-Credentials",
      "Content-Type",
      "Authorization"
    ));
    // Set max age to 1 hour (in seconds)
    cfg.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cfg);
    return new CorsFilter(source);
  }
}
