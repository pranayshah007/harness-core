/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.ng.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppRequestType;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureArtifactType;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.tuple.Pair;

@Data
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = true)
public class AzureWebAppRollbackRequest extends AbstractWebAppTaskRequest {
  private AzureAppServicePreDeploymentData preDeploymentData;
  private AzureArtifactConfig artifact;
  private Integer timeoutIntervalInMin;
  private String targetSlot;
  private AzureArtifactType azureArtifactType;
  private boolean cleanDeployment;

  @Builder
  public AzureWebAppRollbackRequest(String accountId, AzureAppServicePreDeploymentData preDeploymentData,
      CommandUnitsProgress commandUnitsProgress, AzureWebAppInfraDelegateConfig infrastructure,
      AzureArtifactConfig artifact, Integer timeoutIntervalInMin, String targetSlot,
      AzureArtifactType azureArtifactType, boolean cleanDeployment) {
    super(accountId, commandUnitsProgress, infrastructure);
    this.preDeploymentData = preDeploymentData;
    this.artifact = artifact;
    this.timeoutIntervalInMin = timeoutIntervalInMin;
    this.targetSlot = targetSlot;
    this.azureArtifactType = azureArtifactType;
    this.cleanDeployment = cleanDeployment;
  }

  @Override
  protected void populateDecryptionDetails(List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> decryptionDetails) {
    AzureArtifactConfig artifactConfig = getArtifact();
    if (artifactConfig != null && artifactConfig.getConnectorConfig() != null) {
      List<DecryptableEntity> decryptableEntities = artifactConfig.getConnectorConfig().getDecryptableEntities();
      if (decryptableEntities != null) {
        for (DecryptableEntity decryptableEntity : decryptableEntities) {
          decryptionDetails.add(Pair.of(decryptableEntity, artifactConfig.getEncryptedDataDetails()));
        }
      }
    }
  }

  @Override
  public AzureWebAppRequestType getRequestType() {
    return AzureWebAppRequestType.ROLLBACK;
  }
}
