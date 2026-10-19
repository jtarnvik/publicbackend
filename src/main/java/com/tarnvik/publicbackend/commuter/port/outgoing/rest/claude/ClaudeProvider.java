package com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RefusalStopDetails;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.tarnvik.publicbackend.commuter.model.domain.DeviationResponse;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.dto.ClaudeDeviationResponse;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.mapper.ClaudeDeviationResponseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ClaudeProvider {
  private static final String DEVIATION_PROMPT = """
    Tolka detta avvikelsemeddelande från Stockholms Lokaltrafik. En avvikelse syftar typiskt på skillnader mot tidtabell eller något problem med tillgängligheten på en station eller hållplats, men även andra godtyckliga avvikelse kan också förekomma.
    ---
    %s
    ---
    Tolkningen skall besvaras enligt mönstret:
       from - datumet som avvikelsen börjar gälla
       to - datumet som avvikelsen slutar
       accessibility - avvikelsen gäller tillgänglighet, tex att en hiss är trasig eller lagas eller att en rulltrappa inte är i drift eller avvikelse i hur
       delays - avvikelsen avser förseninger
       cancelations - avvikelsen avser inställda avgångar
       duringCommute - avvikelsen avser tider under den typiska morgonpendling 07:00-08:30 eller kvällspendlingen 15:30-18:00. Pendling sker endast måndag till fredag.
       duringWeekend - Avvikelse sker mellan 21:00 fredag till 06:00 måndag morgon
       duringNight - Avvikelsen sker mellan 21:00 till 06:00
       importance - viktighetsgrad, en fyra-gradig skala, LOW/MEDIUM/HIGH/UNKNOWN. Low exempelvis mindre påverkan, mindre förseingar eller enstaka inställda avgångar. Medium exempelvis större påverkan, större förseningar eller flertal inställda avgångar. High exempelvis stor påverkar, flera avgångar i rad som är inställda, generella framkomlighetsproblem pga tex väder eller trafik. UNKNOWN om viktighetsgraden inte kan avgöras.
       interpretationNotes - Förklara kortfattat hur du kom fram till de olika egenkapernas värde.
    Det är möjligt och troligt att all information inte finns i den avvikelse som skall tolkas.
    Om en egenskap inte kan tolkas ut ut meddelandet skall du inte gissa utan då skall den egenskapen utelämnas ut svaret.
    Detta gäller även datum. Finns inga specifika datum i avvikelsen skall även dessa utelämnas och inte anges i svaret.
    Utelämnade egenskaper och datum skall sättas till null i svaret.
    """;

  private final AnthropicClient client;
  private final ClaudeDeviationResponseMapper mapper;

  public ClaudeProvider(@Value("${anthropic.apiKey}") String apiKey, ClaudeDeviationResponseMapper mapper) {
    this.client = AnthropicOkHttpClient.builder()
      .apiKey(apiKey)
      .build();
    this.mapper = mapper;
  }

  public DeviationResponse interpretDeviation(String deviation) {
    String prompt = DEVIATION_PROMPT.formatted(deviation);

    // Thinking is on by default and counts toward maxTokens, so the cap leaves room for it as well as the JSON.
    StructuredMessageCreateParams<ClaudeDeviationResponse> params = MessageCreateParams.builder()
      .model(Model.CLAUDE_HAIKU_5_5)
      .maxTokens(4096)
      .addUserMessage(prompt)
      .outputConfig(StructuredOutputConfig.<ClaudeDeviationResponse>builder()
        .format(ClaudeDeviationResponse.class)
        .effort(OutputConfig.Effort.LOW)
        .build())
      .build();

    StructuredMessage<ClaudeDeviationResponse> response = client.messages().create(params);
    verifyStopReason(response);

    // The response may begin with thinking blocks, so the answer is the first text block, not the first block.
    ClaudeDeviationResponse claudeResponse = response.content().stream()
      .flatMap(block -> block.text().stream())
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("Claude response contained no text block"))
      .text();
    return mapper.toDeviationResponse(claudeResponse);
  }

  private static void verifyStopReason(StructuredMessage<ClaudeDeviationResponse> response) {
    StopReason stopReason = response.stopReason().orElse(null);
    if (StopReason.REFUSAL.equals(stopReason)) {
      String category = response.stopDetails()
        .flatMap(RefusalStopDetails::category)
        .map(Object::toString)
        .orElse("unknown");
      throw new IllegalStateException("Claude declined to interpret the deviation, category " + category);
    }
    if (StopReason.MAX_TOKENS.equals(stopReason)) {
      throw new IllegalStateException("Claude response was cut off at max tokens");
    }
  }
}
