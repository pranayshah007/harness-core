package io.harness.ccm.commons.beans.config;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CE)
public class GenAIServiceConfig {
  @JsonProperty(value = "model") private String model;
  @JsonProperty(value = "temperature") private Float temperature;
  @JsonProperty(value = "maxDecodeSteps") private Integer maxDecodeSteps;
  @JsonProperty(value = "topP") private Float topP;
  @JsonProperty(value = "topK") private Integer topK;
  @JsonProperty(value = "apiEndpoint") private String apiEndpoint;
  @JsonProperty(value = "genAIServiceSecret") private String serviceSecret;
}
