/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice;

import static io.harness.azure.model.AzureConstants.SLOT_NAME_BLANK_ERROR_MSG;
import static io.harness.azure.model.AzureConstants.WEB_APP_NAME_BLANK_ERROR_MSG;

import io.harness.annotation.RecasterAlias;
import io.harness.delegate.beans.azure.appservicesettings.AzureAppServiceApplicationSettingDTO;
import io.harness.delegate.beans.azure.appservicesettings.AzureAppServiceConnectionStringDTO;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters.AzureAppServiceTaskType;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

@Data
@Builder
@RecasterAlias("io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData")
public class AzureAppServicePreDeploymentData {
  @NotBlank(message = WEB_APP_NAME_BLANK_ERROR_MSG) private String appName;
  @NotBlank(message = SLOT_NAME_BLANK_ERROR_MSG) private String slotName;
  private String startupCommand;
  private double trafficWeight;
  private String deploymentProgressMarker;
  private Map<String, AzureAppServiceApplicationSettingDTO> appSettingsToRemove;
  private Map<String, AzureAppServiceApplicationSettingDTO> appSettingsToAdd;
  private Map<String, AzureAppServiceConnectionStringDTO> connStringsToRemove;
  private Map<String, AzureAppServiceConnectionStringDTO> connStringsToAdd;
  private Map<String, AzureAppServiceApplicationSettingDTO> dockerSettingsToAdd;
  private String imageNameAndTag;
  private AzureAppServiceTaskType failedTaskType;
}
