package com.example.alert.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {
  @Value("${twilio.accountSid:}")
  private String sidProp;
  @Value("${twilio.authToken:}")
  private String tokenProp;
  @Value("${twilio.from:}")
  private String fromProp;
  @Value("${alert.to:}")
  private String toProp;

  private String get(String envKey, String propVal) {
    String env = System.getenv(envKey);
    if (propVal != null && !propVal.isBlank()) return propVal;
    return env != null ? env : "";
  }

  private boolean ensureInit() {
    String sid = get("TWILIO_ACCOUNT_SID", sidProp);
    String token = get("TWILIO_AUTH_TOKEN", tokenProp);
    if (sid.isBlank() || token.isBlank()) return false;
    try {
      Twilio.init(sid, token);
      return true;
    } catch (Exception e) {
      System.out.println("[SMS] Twilio init failed: " + e.getMessage());
      return false;
    }
  }

  public boolean send(String text) {
    String from = get("TWILIO_FROM", fromProp);
    String to = get("ALERT_TO", toProp);
    if (from.isBlank() || to.isBlank()) {
      System.out.println("[SMS] Missing FROM/TO. Skipping SMS.");
      return false;
    }
    if (!ensureInit()) return false;
    try {
      Message.creator(new PhoneNumber(to), new PhoneNumber(from), text).create();
      System.out.println("[SMS] Sent to " + to);
      return true;
    } catch (Exception e) {
      System.out.println("[SMS] Send failed: " + e.getMessage());
      return false;
    }
  }
}
