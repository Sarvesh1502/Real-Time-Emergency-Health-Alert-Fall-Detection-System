package com.example.alert.model;

import jakarta.persistence.*;

@Entity
@Table(name = "alerts")
public class Alert {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private long timestamp;
  private String reason;
  private Double lat; private Double lng;
  private String status; // PENDING_SILENT, PENDING_CONFIRM, SENT, CANCELLED
  private Long confirmStartsAt; // epoch ms when modal should appear
  private Long expiryAt; // epoch ms when auto-send should occur

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public long getTimestamp() { return timestamp; }
  public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public Double getLat() { return lat; }
  public void setLat(Double lat) { this.lat = lat; }
  public Double getLng() { return lng; }
  public void setLng(Double lng) { this.lng = lng; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Long getConfirmStartsAt() { return confirmStartsAt; }
  public void setConfirmStartsAt(Long confirmStartsAt) { this.confirmStartsAt = confirmStartsAt; }
  public Long getExpiryAt() { return expiryAt; }
  public void setExpiryAt(Long expiryAt) { this.expiryAt = expiryAt; }
}
