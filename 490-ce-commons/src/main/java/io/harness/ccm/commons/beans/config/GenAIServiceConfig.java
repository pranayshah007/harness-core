/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

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
