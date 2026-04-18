package com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class PushoverProvider {
  private final String apiToken;
  private final String userKey;
  private final RestClient restClient;

  public PushoverProvider(
      @Value("${app.pushover.api-token}") String apiToken,
      @Value("${app.pushover.user-key}") String userKey) {
    this.apiToken = apiToken;
    this.userKey = userKey;
    this.restClient = RestClient.builder().baseUrl("https://api.pushover.net").build();
  }

  public void sendDeniedLoginNotification(String email, String name) {
    sendNotification("Nekad inloggning", name + " (" + email + ") nekades åtkomst.");
  }

  public void sendAccessRequestNotification(String name, String email, String message) {
    String body = name + " (" + email + ") ansöker om åtkomst.";
    if (message != null && !message.isBlank()) {
      body += "\n" + message;
    }
    sendNotification("Åtkomstbegäran", body);
  }

  public void sendAiInterpretationErrorNotification(String hashPrefix, int errorCount) {
    sendNotification("AI-tolkningsfel", "Avvikelse (hash: " + hashPrefix + "…) har misslyckats " + errorCount + " gånger. Tolkning låst i 24 timmar.");
  }

  public void sendGtfsPipelineErrorNotification(String phase, String errorMessage) {
    sendNotification("GTFS pipeline error", "Phase: " + phase + "\n" + errorMessage);
  }

  public void sendTestNotification() {
    sendNotification("SL Dashboard test", "Testmeddelande från SL Dashboard.");
  }

  private void sendNotification(String title, String message) {
    try {
      var body = new PushoverRequest(apiToken, userKey, title, message);
      restClient.post()
          .uri("/1/messages.json")
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (Exception e) {
      log.error("Failed to send Pushover notification: {}", e.getMessage());
    }
  }

  private record PushoverRequest(String token, String user, String title, String message) {}
}
