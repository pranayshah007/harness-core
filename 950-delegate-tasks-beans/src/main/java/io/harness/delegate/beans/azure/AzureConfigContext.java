/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.azure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureHostConnectionType;
import io.harness.azure.model.AzureOSType;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.filesystem.LazyAutoCloseableWorkingDirectory;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AzureConfigContext {
  private String taskId;
  private AzureConnectorDTO azureConnector;
  private List<EncryptedDataDetail> encryptedDataDetails;
  private LazyAutoCloseableWorkingDirectory certificateWorkingDirectory;
  private String subscriptionId;
  private String resourceGroup;
  private String webAppName;
  private String containerRegistry;
  private String repository;
  private String cluster;
  private String namespace;
  private boolean useClusterAdminCredentials;
  private AzureOSType azureOSType;
  private AzureHostConnectionType azureHostConnectionType;
  private Map<String, String> tags;
}
