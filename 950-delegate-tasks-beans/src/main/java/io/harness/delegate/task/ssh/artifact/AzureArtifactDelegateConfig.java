/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ssh.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsInternalConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AzureArtifactDelegateConfig implements SshWinRmArtifactDelegateConfig, NestedAnnotationResolver {
  String identifier;
  ConnectorInfoDTO connectorDTO;
  String project;
  String feed;
  String scope;
  String packageType;
  String packageId;
  String packageName;
  String version;
  String versionRegex;
  String type;
  String image;
  String imagePullSecret;
  List<EncryptedDataDetail> encryptedDataDetails;

  @Override
  public String getIdentifier() {
    return identifier;
  }

  @Override
  public SshWinRmArtifactType getArtifactType() {
    return SshWinRmArtifactType.AZURE;
  }

  @Override
  public String getArtifactPath() {
    return packageName;
  }

  public static AzureArtifactsInternalConfig toInternalConfig(
      AzureArtifactDelegateConfig azureArtifactDelegateConfig, String decryptedToken) {
    AzureArtifactsConnectorDTO connectorDTO =
        (AzureArtifactsConnectorDTO) azureArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    return AzureArtifactsInternalConfig.builder()
        .feed(azureArtifactDelegateConfig.getFeed())
        .project(azureArtifactDelegateConfig.getProject())
        .packageId(azureArtifactDelegateConfig.getPackageId())
        .registryUrl(connectorDTO.getAzureArtifactsUrl())
        .token(decryptedToken)
        .build();
  }
}
