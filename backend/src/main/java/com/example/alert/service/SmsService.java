package com.example.alert.service;

import org.springframework.stereotype.Service;

@Service
public class SmsService {
  // SMS is disabled/no-op in this prototype. Kept for DI compatibility.
  public boolean send(String text) {
    System.out.println("[SMS] No-op (disabled). Message: " + text);
    return false;
  }
}
