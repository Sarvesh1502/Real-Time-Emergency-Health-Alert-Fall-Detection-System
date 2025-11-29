package com.example.alert.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class TelegramService {

  @Value("${telegram.botToken:}")
  private String botTokenProp;

  @Value("${telegram.chatId:}")
  private String chatIdProp;

  private final RestTemplate rest = new RestTemplate();

  private String getBotToken() {
    String env = System.getenv("TELEGRAM_BOT_TOKEN");
    return (botTokenProp != null && !botTokenProp.isBlank()) ? botTokenProp : (env == null ? "" : env);
  }

  private String getChatId() {
    String env = System.getenv("TELEGRAM_CHAT_ID");
    return (chatIdProp != null && !chatIdProp.isBlank()) ? chatIdProp : (env == null ? "" : env);
  }

  public boolean sendMessage(String text) {
    String botToken = getBotToken();
    String chatId = getChatId();
    if (botToken.isBlank() || chatId.isBlank()) {
      System.out.println("[Telegram] Missing bot token or chat id. Skipping send.");
      return false;
    }
    try {
      String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("chat_id", chatId);
      form.add("text", text);

      HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(form, headers);
      rest.postForEntity(url, req, String.class);
      System.out.println("[Telegram] Message sent.");
      return true;
    } catch (Exception e) {
      System.out.println("[Telegram] Failed to send: " + e.getMessage());
      return false;
    }
  }
}
