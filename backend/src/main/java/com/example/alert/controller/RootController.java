package com.example.alert.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/")
public class RootController {

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> root() {
    return Map.of(
      "message", "Fall Detection API running",
      "health", "/api/health",
      "alerts", "/api/alerts",
      "events_recent", "/api/events/recent"
    );
  }

  @GetMapping(path = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> error() {
    return Map.of(
      "error", "Not Found",
      "hint", "Use /api/health, /api/alerts, /api/events/recent or the frontend at http://localhost:5500"
    );
  }
}
