/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure.model.kube;

import static io.harness.azure.model.AzureConstants.KUBECFG_API_SERVER_ID;
import static io.harness.azure.model.AzureConstants.KUBECFG_CLIENT_ID;
import static io.harness.azure.model.AzureConstants.KUBECFG_CONFIG_MODE;
import static io.harness.azure.model.AzureConstants.KUBECFG_TENANT_ID;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.CDP)
public class AuthProviderConfig {
  @JsonProperty(KUBECFG_API_SERVER_ID) private String apiServerId;
  @JsonProperty(KUBECFG_CLIENT_ID) private String clientId;
  @JsonProperty(KUBECFG_CONFIG_MODE) private String configMode;
  private String environment;
  @JsonProperty(KUBECFG_TENANT_ID) private String tenantId;
}
