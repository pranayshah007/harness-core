/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.request;

import static io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters.AzureAppServiceTaskType.SLOT_SETUP;
import static io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters.AzureAppServiceType.WEB_APP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;
import io.harness.expression.Expression;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AzureWebAppSlotSetupParameters extends AzureAppServiceTaskParameters {
  private String slotName;
  private String targetSlotName;
  @Expression(ALLOW_SECRETS) private List<AzureAppServiceApplicationSetting> applicationSettings;
  @Expression(ALLOW_SECRETS) private List<AzureAppServiceConnectionString> connectionStrings;
  private String imageName;
  private String imageTag;
  ConnectorConfigDTO connectorConfigDTO;
  List<EncryptedDataDetail> encryptedDataDetails;
  AzureRegistryType azureRegistryType;
  @Expression(ALLOW_SECRETS) private String startupCommand;
  private boolean isBasicDeployment;

  @Builder
  public AzureWebAppSlotSetupParameters(String appId, String accountId, String activityId, String subscriptionId,
      String resourceGroupName, String webAppName, String slotName, String targetSlotName, String imageName,
      String imageTag, String commandName, Integer timeoutIntervalInMin, ConnectorConfigDTO connectorConfigDTO,
      List<EncryptedDataDetail> encryptedDataDetails, AzureRegistryType azureRegistryType,
      List<AzureAppServiceApplicationSetting> applicationSettings,
      List<AzureAppServiceConnectionString> connectionStrings, String startupCommand, boolean isBasicDeployment) {
    super(appId, accountId, activityId, subscriptionId, resourceGroupName, webAppName, commandName,
        timeoutIntervalInMin, SLOT_SETUP, WEB_APP);
    this.slotName = slotName;
    this.targetSlotName = targetSlotName;
    this.imageName = imageName;
    this.imageTag = imageTag;
    this.connectorConfigDTO = connectorConfigDTO;
    this.encryptedDataDetails = encryptedDataDetails;
    this.azureRegistryType = azureRegistryType;
    this.applicationSettings = applicationSettings;
    this.connectionStrings = connectionStrings;
    this.startupCommand = startupCommand;
    this.isBasicDeployment = isBasicDeployment;
  }
}
