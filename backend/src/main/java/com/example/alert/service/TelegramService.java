package com.example.alert.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

  /**
   * Sends a message to the configured Telegram chat
   * @param text The message text to send
   * @return true if message was sent successfully, false otherwise
   */
  public boolean sendMessage(String text) {
    // Input validation
    if (text == null || text.trim().isEmpty()) {
      System.out.println("[Telegram] Error: Message text cannot be empty");
      return false;
    }

    String botToken = getBotToken();
    String chatId = getChatId();
    
    // Check configuration
    if (botToken.isBlank() || chatId.isBlank()) {
      System.err.println("[Telegram] Error: Missing bot token or chat ID. " +
          "Please check your configuration.");
      System.err.println("[Telegram] Bot Token: " + 
          (botToken.isBlank() ? "[MISSING]" : "[SET]"));
      System.err.println("[Telegram] Chat ID: " + 
          (chatId.isBlank() ? "[MISSING]" : chatId));
      return false;
    }

    try {
      String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
      System.out.println("[Telegram] Sending message to chat " + chatId);
      
      // Prepare request
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("chat_id", chatId);
      form.add("text", text);
      form.add("parse_mode", "HTML");

      HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
      
      // Log request (without sensitive data)
      System.out.println("[Telegram] Sending request to: " + 
          url.replace(botToken, "***REDACTED***"));
      
      // Send request
      ResponseEntity<String> response = rest.postForEntity(url, request, String.class);
      
      // Log response status
      System.out.println("[Telegram] Response status: " + response.getStatusCode());
      
      if (response.getStatusCode().is2xxSuccessful()) {
        System.out.println("[Telegram] Message sent successfully");
        return true;
      } else {
        System.err.println("[Telegram] Failed to send message. Status: " + 
            response.getStatusCode() + ", Body: " + response.getBody());
        return false;
      }
      
    } catch (Exception e) {
      System.err.println("[Telegram] Error sending message: " + e.getMessage());
      e.printStackTrace();
      return false;
    }
  }
}
