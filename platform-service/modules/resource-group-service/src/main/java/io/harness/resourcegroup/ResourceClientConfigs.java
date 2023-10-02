/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.secret.ConfigSecret;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResourceClientConfigs {
  @JsonProperty("ng-manager") @ConfigSecret ServiceConfig ngManager;
  @JsonProperty("manager") @ConfigSecret ServiceConfig manager;
  @JsonProperty("pipeline-service") @ConfigSecret ServiceConfig pipelineService;
  @JsonProperty("resourceGroup") @ConfigSecret ServiceConfig resourceGroupService;
  @JsonProperty("template-service") @ConfigSecret ServiceConfig templateService;
  @JsonProperty("gitops-service") @ConfigSecret ServiceConfig gitopsService;
  @JsonProperty("ce-nextgen") @ConfigSecret ServiceConfig ceNextGen;

  @JsonProperty("code") @ConfigSecret ServiceConfig code;

  @Value
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class ServiceConfig {
    String baseUrl;
    @ConfigSecret String secret;
  }
}
