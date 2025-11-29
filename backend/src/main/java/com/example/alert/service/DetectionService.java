package com.example.alert.service;

import com.example.alert.model.Alert;
import com.example.alert.model.Event;
import com.example.alert.repo.AlertRepository;
import com.example.alert.repo.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DetectionService {
  private final MLService ml;
  private final AlertRepository alertRepo;
  private final EventRepository eventRepo;
  private final TelegramService telegramService;
  private volatile long suppressUntilMs = 0L; // cooldown to avoid spamming

  @Value("${alert.confirm.silentMs:10000}")
  private long confirmSilentMs;
  @Value("${alert.confirm.modalMs:10000}")
  private long confirmModalMs;

  public DetectionService(MLService ml, AlertRepository alertRepo, EventRepository eventRepo, TelegramService telegramService, SmsService smsService) {
    this.ml = ml;
    this.alertRepo = alertRepo;
    this.eventRepo = eventRepo;
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
    boolean ruleHit = accelMag > 16.0 || gyroMag > 120.0; // relaxed thresholds

    // ML score (stubbed)
    double mlScore = ml.predictFallProbability(e.getAx(), e.getAy(), e.getAz(), e.getGx(), e.getGy(), e.getGz());

    // Device-Drop heuristic: try to down-weight events that look like a phone drop
    boolean dropLike = isLikelyDeviceDrop(e);

    // Debug log metrics each event (compact)
    try {
      System.out.println("[DETECT] ts=%d aMag=%.2f gMag=%.2f ml=%.2f ctx=%s dropLike=%s".formatted(
          e.getTimestamp(), accelMag, gyroMag, mlScore, String.valueOf(e.getContext()), String.valueOf(dropLike))
      );
    } catch (Exception ignore) {}

    // Hybrid decision: rule OR (moderate rule + ML high)
    boolean alert = (ruleHit || (accelMag > 13.0 && mlScore > 0.55) || (gyroMag > 80.0 && mlScore > 0.55));
    // If it looks like a device drop, require a stronger signal (softened)
    if (alert && dropLike) {
      // keep some guard but allow stronger-but-common signals
      alert = ((mlScore > 0.65) || ruleHit) && accelMag > 14.5;
    }

    if (alert) {
      System.out.println("[DETECT] decision=ALERT ruleHit=" + ruleHit + " mlScore=" + "%.2f".formatted(mlScore));
      Alert a = new Alert();
      a.setTimestamp(e.getTimestamp());
      a.setReason("Hybrid detection: rule=" + ruleHit + ", mlScore=" + "%.2f".formatted(mlScore) + (dropLike? ", dropLike=true":""));
      a.setLat(e.getLat());
      a.setLng(e.getLng());
      long now = System.currentTimeMillis();
      // Adaptive confirmation timings (very strong -> immediate modal; human/surface -> shorter silent, longer modal)
      String ctx = e.getContext() == null ? "" : e.getContext();
      boolean humanLikely = ctx.contains("in_hand") || ctx.contains("moving");
      boolean surfaceLikely = ctx.contains("face_down") || ctx.contains("still_side") || ctx.contains("in_pocket");
      boolean veryStrong = accelMag > 20.0 || (mlScore > 0.80 && (accelMag > 16.0 || gyroMag > 110.0));

      long silentMs = confirmSilentMs;   // default
      long modalMs  = confirmModalMs;    // default

      if (veryStrong && !dropLike) {
        // Very strong and not drop-like -> show immediately, give user longer to respond
        silentMs = 0L;
        modalMs = Math.max(confirmModalMs, 30_000L);
      } else if (humanLikely) {
        // Likely being carried -> shorten silent and extend modal
        silentMs = Math.min(confirmSilentMs, 5_000L);
        modalMs = Math.max(confirmModalMs, 25_000L);
      } else if (dropLike || surfaceLikely) {
        // Looks like surface drop -> shorter silent but still allow user time
        silentMs = Math.min(confirmSilentMs, 5_000L);
        modalMs = Math.max(confirmModalMs, 20_000L);
      }

      a.setStatus("PENDING_SILENT");
      a.setConfirmStartsAt(now + Math.max(0, silentMs));
      a.setExpiryAt(a.getConfirmStartsAt() + Math.max(1_000L, modalMs));
      alertRepo.save(a);
      System.out.println("[ALERT] Pending (silent) for possible fall at ts=" + a.getTimestamp());
      return a;
    }
    System.out.println("[DETECT] decision=NO_ALERT ruleHit=" + ruleHit + " mlScore=" + "%.2f".formatted(mlScore));
    return null;
  }

  // Heuristic device-drop detector using last ~4s of events and current context
  private boolean isLikelyDeviceDrop(Event current) {
    try {
      long cutoff = current.getTimestamp() - 4000L;
      List<Event> recent = eventRepo.recent30().stream()
          .filter(ev -> ev.getTimestamp() >= cutoff && ev.getTimestamp() <= current.getTimestamp())
          .collect(Collectors.toList());
      if (recent.isEmpty()) return false;

      // Compute features: peak impact, post-impact stillness, orientation/context hints
      double peakA = 0, postVarA = 0, postVarG = 0;
      int idxImpact = -1;
      for (int i = 0; i < recent.size(); i++) {
        Event ev = recent.get(i);
        double aMag = Math.sqrt(ev.getAx()*ev.getAx() + ev.getAy()*ev.getAy() + ev.getAz()*ev.getAz());
        if (aMag > peakA) { peakA = aMag; idxImpact = i; }
      }
      if (idxImpact >= 0) {
        // measure variance over ~1.5s after impact (approx 3 samples since we send at 2Hz)
        int start = Math.min(idxImpact + 1, recent.size());
        int end = Math.min(start + 4, recent.size());
        int n = Math.max(0, end - start);
        if (n >= 2) {
          double[] a = new double[n];
          double[] g = new double[n];
          for (int j = 0; j < n; j++) {
            Event ev = recent.get(start + j);
            double aMag = Math.sqrt(ev.getAx()*ev.getAx() + ev.getAy()*ev.getAy() + ev.getAz()*ev.getAz());
            double gMag = Math.sqrt(ev.getGx()*ev.getGx() + ev.getGy()*ev.getGy() + ev.getGz()*ev.getGz());
            a[j] = aMag; g[j] = gMag;
          }
          postVarA = variance(a);
          postVarG = variance(g);
        }
      }

      boolean highImpact = peakA > 18.0; // strong spike
      boolean quicklyStill = postVarA < 0.4 && postVarG < 25.0; // settles quickly
      boolean contextSuggestsSurface = current.getContext() != null && (
          current.getContext().contains("face_down") || current.getContext().contains("still_side")
      );

      // Likely drop if a strong impact followed by quick stillness and surface-like context
      return highImpact && quicklyStill && contextSuggestsSurface;
    } catch (Exception ex) {
      return false;
    }
  }

  private static double variance(double[] arr) {
    if (arr == null || arr.length < 2) return 0.0;
    double m = 0; for (double v: arr) m += v; m /= arr.length;
    double s = 0; for (double v: arr) { double d=v-m; s += d*d; }
    return s/arr.length;
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
