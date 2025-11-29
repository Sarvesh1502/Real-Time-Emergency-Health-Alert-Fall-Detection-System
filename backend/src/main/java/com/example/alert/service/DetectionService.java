package com.example.alert.service;

import com.example.alert.model.Alert;
import com.example.alert.model.Event;
import com.example.alert.repo.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class DetectionService {
  private final MLService ml;
  private final AlertRepository alertRepo;
  private final TelegramService telegramService;
  private volatile long suppressUntilMs = 0L; // cooldown to avoid spamming

  public DetectionService(MLService ml, AlertRepository alertRepo, TelegramService telegramService, SmsService smsService) {
    this.ml = ml;
    this.alertRepo = alertRepo;
    this.telegramService = telegramService;
  }

  public Alert process(Event e) {
    long nowTs = System.currentTimeMillis();
    if (nowTs < suppressUntilMs) {
      return null; // in cooldown: ignore new alerts
    }
    // Rule-based quick check: high accel or gyro
    double accelMag = Math.sqrt(e.getAx()*e.getAx() + e.getAy()*e.getAy() + e.getAz()*e.getAz());
    double gyroMag = Math.sqrt(e.getGx()*e.getGx() + e.getGy()*e.getGy() + e.getGz()*e.getGz());
    boolean ruleHit = accelMag > 18.0 || gyroMag > 150.0; // simple thresholds

    // ML score (stubbed)
    double mlScore = ml.predictFallProbability(e.getAx(), e.getAy(), e.getAz(), e.getGx(), e.getGy(), e.getGz());

    // Hybrid decision: rule OR (moderate rule + ML high)
    boolean alert = ruleHit || (accelMag > 14.0 && mlScore > 0.6) || (gyroMag > 90.0 && mlScore > 0.6);

    if (alert) {
      Alert a = new Alert();
      a.setTimestamp(e.getTimestamp());
      a.setReason("Hybrid detection: rule=" + ruleHit + ", mlScore=" + String.format("%.2f", mlScore));
      a.setLat(e.getLat());
      a.setLng(e.getLng());
      long now = System.currentTimeMillis();
      a.setStatus("PENDING_SILENT");
      a.setConfirmStartsAt(now + 10_000); // first 10s: silent grace
      a.setExpiryAt(a.getConfirmStartsAt() + 10_000); // next 10s: modal window, then auto-send
      alertRepo.save(a);
      System.out.println("[ALERT] Pending (silent) for possible fall at ts=" + a.getTimestamp());
      return a;
    }
    return null;
  }

  @Scheduled(fixedDelay = 1000)
  @Transactional
  public void checkPendingAlerts() {
    long now = System.currentTimeMillis();
    // Transition from silent to confirm phase
    List<Alert> toConfirm = alertRepo.findByStatusAndConfirmStartsAtLessThanEqual("PENDING_SILENT", now);
    for (Alert a : toConfirm) {
      if (a.getConfirmStartsAt() != null && now >= a.getConfirmStartsAt()) {
        a.setStatus("PENDING_CONFIRM");
        alertRepo.save(a);
      }
    }
    // Auto-send after confirm window elapsed
    List<Alert> toSend = alertRepo.findByStatusAndExpiryAtLessThanEqual("PENDING_CONFIRM", now);
    for (Alert a : toSend) {
      sendTelegramAlert(a);
      a.setStatus("SENT");
      alertRepo.save(a);
      // enter cooldown to avoid spamming on continuous motion
      suppressUntilMs = Math.max(suppressUntilMs, now + 20_000);
    }
  }

  public void confirmAlert(Long alertId, boolean isOkay) {
    alertRepo.findById(alertId).ifPresent(a -> {
      if (!"PENDING_SILENT".equals(a.getStatus()) && !"PENDING_CONFIRM".equals(a.getStatus())) return;
      if (isOkay) {
        // user said they are okay -> cancel
        a.setStatus("CANCELLED");
        alertRepo.save(a);
        System.out.println("[ALERT] Cancelled by user for id=" + a.getId());
        // short cooldown to prevent immediate re-trigger
        suppressUntilMs = Math.max(suppressUntilMs, System.currentTimeMillis() + 10_000);
      } else {
        // user said NOT okay -> send immediately
        sendTelegramAlert(a);
        a.setStatus("SENT");
        alertRepo.save(a);
        System.out.println("[ALERT] Confirmed emergency by user for id=" + a.getId());
        // longer cooldown since we escalated
        suppressUntilMs = Math.max(suppressUntilMs, System.currentTimeMillis() + 20_000);
      }
    });
  }

  private void sendTelegramAlert(Alert a) {
    try {
      String ts = String.valueOf(a.getTimestamp());
      String text = "🚨 Fall detected\n" +
          "Time: " + ts + "\n" +
          "Reason: " + a.getReason() + "\n" +
          "Location: " + a.getLat() + ", " + a.getLng() + "\n" +
          "Map: https://maps.google.com/?q=" + a.getLat() + "," + a.getLng();
      // Telegram only
      telegramService.sendMessage(text);
    } catch (Exception ex) {
      System.out.println("[Telegram] send failed: " + ex.getMessage());
    }
  }
}
