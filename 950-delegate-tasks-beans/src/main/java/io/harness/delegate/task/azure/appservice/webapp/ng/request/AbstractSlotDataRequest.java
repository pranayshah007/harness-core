/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.ng.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@Data
@OwnedBy(CDP)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractSlotDataRequest extends AbstractWebAppTaskRequest {
  @Expression(ALLOW_SECRETS) private AppSettingsFile startupCommand;
  @Expression(ALLOW_SECRETS) private AppSettingsFile applicationSettings;
  @Expression(ALLOW_SECRETS) private AppSettingsFile connectionStrings;
  @Expression(ALLOW_SECRETS) private AzureArtifactConfig artifact;
  private Integer timeoutIntervalInMin;

  public abstract Set<String> getPrevExecUserAddedAppSettingNames();
  public abstract Set<String> getPrevExecUserAddedConnStringNames();
  public abstract boolean isPrevExecUserChangedStartupCommand();
  public boolean isCleanDeployment() {
    return false;
  }
  protected AbstractSlotDataRequest(String accountId, CommandUnitsProgress commandUnitsProgress,
      AzureWebAppInfraDelegateConfig infrastructure, AppSettingsFile startupCommand,
      AppSettingsFile applicationSettings, AppSettingsFile connectionStrings, AzureArtifactConfig artifact,
      Integer timeoutIntervalInMin) {
    super(accountId, commandUnitsProgress, infrastructure);
    this.startupCommand = startupCommand;
    this.applicationSettings = applicationSettings;
    this.connectionStrings = connectionStrings;
    this.artifact = artifact;
    this.timeoutIntervalInMin = timeoutIntervalInMin;
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

    addSettingsFileDecryptionDetails(startupCommand, decryptionDetails);
    addSettingsFileDecryptionDetails(applicationSettings, decryptionDetails);
    addSettingsFileDecryptionDetails(connectionStrings, decryptionDetails);
  }

  @Override
  protected void populateRequestCapabilities(
      List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator) {
    if (startupCommand != null) {
      capabilities.addAll(startupCommand.fetchRequiredExecutionCapabilities(maskingEvaluator));
    }

    if (applicationSettings != null) {
      capabilities.addAll(applicationSettings.fetchRequiredExecutionCapabilities(maskingEvaluator));
    }

    if (connectionStrings != null) {
      capabilities.addAll(connectionStrings.fetchRequiredExecutionCapabilities(maskingEvaluator));
    }
  }

  private static void addSettingsFileDecryptionDetails(
      AppSettingsFile settingsFile, List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> decryptionDetails) {
    if (settingsFile != null && settingsFile.isEncrypted()) {
      decryptionDetails.add(Pair.of(settingsFile.getEncryptedFile(), settingsFile.getEncryptedDataDetails()));
    }
  }
}
