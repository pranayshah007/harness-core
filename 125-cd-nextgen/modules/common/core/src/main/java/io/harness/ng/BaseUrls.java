/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.PL)
public class BaseUrls {
  @JsonProperty("currentGenUiUrl") String currentGenUiUrl;
  @JsonProperty("nextGenUiUrl") String nextGenUiUrl;
  @JsonProperty("nextGenAuthUiUrl") String nextGenAuthUiUrl;
  @JsonProperty("webhookBaseUrl") String webhookBaseUrl;
  @JsonProperty("ngManagerScmBaseUrl") String ngManagerScmBaseUrl;
}
