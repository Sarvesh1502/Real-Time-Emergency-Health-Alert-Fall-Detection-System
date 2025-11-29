package com.example.alert.controller;

import com.example.alert.model.Alert;
import com.example.alert.model.Event;
import com.example.alert.repo.AlertRepository;
import com.example.alert.repo.EventRepository;
import com.example.alert.service.DetectionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EventController {
  private final EventRepository eventRepo;
  private final AlertRepository alertRepo;
  private final DetectionService detectionService;

  public EventController(EventRepository eventRepo, AlertRepository alertRepo, DetectionService detectionService) {
    this.eventRepo = eventRepo;
    this.alertRepo = alertRepo;
    this.detectionService = detectionService;
  }

  @GetMapping({"", "/"})
  public Map<String, Object> apiRoot() {
    return Map.of(
      "message", "Fall Detection API",
      "health", "/api/health",
      "alerts", "/api/alerts",
      "events_recent", "/api/events/recent"
    );
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "ok");
  }

  @PostMapping("/events")
  public Map<String, Object> postEvent(@RequestBody Map<String, Object> payload) {
    Event e = new Event();
    e.setTimestamp(((Number)payload.getOrDefault("timestamp", System.currentTimeMillis())).longValue());

    Map<String, Object> accel = (Map<String, Object>) payload.get("accel");
    Map<String, Object> gyro = (Map<String, Object>) payload.get("gyro");

    if (accel != null) {
      e.setAx(((Number)accel.getOrDefault("x", 0)).doubleValue());
      e.setAy(((Number)accel.getOrDefault("y", 0)).doubleValue());
      e.setAz(((Number)accel.getOrDefault("z", 0)).doubleValue());
    }
    if (gyro != null) {
      e.setGx(((Number)gyro.getOrDefault("x", 0)).doubleValue());
      e.setGy(((Number)gyro.getOrDefault("y", 0)).doubleValue());
      e.setGz(((Number)gyro.getOrDefault("z", 0)).doubleValue());
    }

    Object lat = payload.get("lat");
    Object lng = payload.get("lng");
    e.setLat(lat instanceof Number ? ((Number)lat).doubleValue() : null);
    e.setLng(lng instanceof Number ? ((Number)lng).doubleValue() : null);
    Object ctx = payload.get("context");
    if (ctx instanceof String) e.setContext((String) ctx);

    eventRepo.save(e);

    Alert alert = detectionService.process(e);

    if (alert != null) {
      return Map.of(
        "saved", true,
        "alert", true,
        "alertId", alert.getId(),
        "status", alert.getStatus(),
        "confirmStartsAt", alert.getConfirmStartsAt(),
        "expiryAt", alert.getExpiryAt()
      );
    }
    return Map.of("saved", true, "alert", false);
  }

  @GetMapping("/alerts")
  public List<Alert> alerts() {
    return alertRepo.recent30();
  }

  @PostMapping("/alerts/{id}/confirm")
  public Map<String, Object> confirmAlert(@PathVariable("id") Long id, @RequestParam("ok") boolean ok) {
    detectionService.confirmAlert(id, ok);
    return Map.of("ok", true);
  }

  @GetMapping("/events/recent")
  public List<Event> recentEvents() {
    return eventRepo.recent30();
  }
}
