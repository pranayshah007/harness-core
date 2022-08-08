/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.model;

import io.harness.azure.AzureEnvironmentType;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString(exclude = {"key", "cert"})
public class AzureConfig {
  String tenantId;
  String clientId;
  char[] key;
  byte[] cert;
  private AzureEnvironmentType azureEnvironmentType;
  @Builder.Default AzureAuthenticationType azureAuthenticationType = AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET;
}
