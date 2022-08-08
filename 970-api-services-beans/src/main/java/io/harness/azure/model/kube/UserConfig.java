/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure.model.kube;

import static io.harness.azure.model.AzureConstants.KUBECFG_AUTH_PROVIDER;
import static io.harness.azure.model.AzureConstants.KUBECFG_CLIENT_CERTIFICATE_DATA;
import static io.harness.azure.model.AzureConstants.KUBECFG_CLIENT_KEY_DATA;

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
public class UserConfig {
  @JsonProperty(KUBECFG_CLIENT_CERTIFICATE_DATA) private String clientCertificateData;
  @JsonProperty(KUBECFG_CLIENT_KEY_DATA) private String clientKeyData;
  @JsonProperty(KUBECFG_AUTH_PROVIDER) private AuthProvider authProvider;
}
