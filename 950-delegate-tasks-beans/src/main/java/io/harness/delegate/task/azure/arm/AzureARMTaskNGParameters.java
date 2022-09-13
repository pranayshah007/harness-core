/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
public class AzureARMTaskNGParameters extends AzureResourceCreationTaskNGParameters {
  AppSettingsFile templateBody;
  AppSettingsFile parametersBody;
  ARMScopeType deploymentScope;
  AzureDeploymentMode deploymentMode;
  String managementGroupId;
  String subscriptionId;
  String resourceGroupName;

  String deploymentDataLocation;

  String deploymentName;

  private boolean rollback; // TODO

  @Builder
  public AzureARMTaskNGParameters(String accountId, AzureARMTaskType taskType, AzureConnectorDTO connectorDTO,
      AppSettingsFile templateBody, AppSettingsFile parametersBody, ARMScopeType scopeType,
      AzureDeploymentMode deploymentMode, String managementGroupId, String subscriptionId, String resourceGroupName,
      String deploymentDataLocation, @NotNull List<EncryptedDataDetail> encryptedDataDetails, String deploymentName,
      boolean rollback, long timeoutInMs, CommandUnitsProgress commandUnitsProgress) {
    super(accountId, taskType, connectorDTO, encryptedDataDetails, timeoutInMs, commandUnitsProgress);
    this.templateBody = templateBody;
    this.parametersBody = parametersBody;
    this.deploymentScope = scopeType;
    this.deploymentMode = deploymentMode;
    this.managementGroupId = managementGroupId;
    this.subscriptionId = subscriptionId;
    this.resourceGroupName = resourceGroupName;
    this.deploymentDataLocation = deploymentDataLocation;
    this.deploymentName = deploymentName;
    this.rollback = rollback;
  }

  @Override
  public List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> fetchDecryptionDetails() {
    List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> decryptionDetails = new ArrayList<>();
    List<DecryptableEntity> decryptableEntities = this.azureConnectorDTO.getDecryptableEntities();

    decryptFiles(templateBody, decryptionDetails);
    decryptFiles(parametersBody, decryptionDetails);

    decryptableEntities.forEach(
        decryptableEntity -> decryptionDetails.add(Pair.of(decryptableEntity, encryptedDataDetails)));
    return decryptionDetails;
  }
}
