package com.example.alert.service;

import com.example.alert.model.Alert;
import com.example.alert.model.Event;
import com.example.alert.repo.AlertRepository;
import org.springframework.stereotype.Service;

@Service
public class DetectionService {
  private final MLService ml;
  private final AlertRepository alertRepo;

  public DetectionService(MLService ml, AlertRepository alertRepo) {
    this.ml = ml;
    this.alertRepo = alertRepo;
  }

  public Alert process(Event e) {
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
      alertRepo.save(a);

      // Mock SMS / notification
      System.out.println("[ALERT] Possible fall detected at " + a.getTimestamp() + " lat=" + a.getLat() + ", lng=" + a.getLng());
      System.out.println("[SMS] (mock) Sending SMS to caregiver: 'Possible fall detected!' ");
      return a;
    }
    return null;
  }
}
