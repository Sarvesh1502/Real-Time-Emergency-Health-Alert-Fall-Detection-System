package com.example.alert.model;

import jakarta.persistence.*;

@Entity
@Table(name = "events")
public class Event {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private long timestamp;
  private double ax; private double ay; private double az;
  private double gx; private double gy; private double gz;
  private Double lat; private Double lng;
  private String context;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public long getTimestamp() { return timestamp; }
  public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
  public double getAx() { return ax; }
  public void setAx(double ax) { this.ax = ax; }
  public double getAy() { return ay; }
  public void setAy(double ay) { this.ay = ay; }
  public double getAz() { return az; }
  public void setAz(double az) { this.az = az; }
  public double getGx() { return gx; }
  public void setGx(double gx) { this.gx = gx; }
  public double getGy() { return gy; }
  public void setGy(double gy) { this.gy = gy; }
  public double getGz() { return gz; }
  public void setGz(double gz) { this.gz = gz; }
  public Double getLat() { return lat; }
  public void setLat(Double lat) { this.lat = lat; }
  public Double getLng() { return lng; }
  public void setLng(Double lng) { this.lng = lng; }
  public String getContext() { return context; }
  public void setContext(String context) { this.context = context; }
}
