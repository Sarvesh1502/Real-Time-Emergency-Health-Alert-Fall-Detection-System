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
}
