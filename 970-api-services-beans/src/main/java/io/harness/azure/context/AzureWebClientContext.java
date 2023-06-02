/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.context;

import static io.harness.azure.model.AzureConstants.WEB_APP_NAME_BLANK_ERROR_MSG;

import io.harness.azure.model.AzureConfig;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.hibernate.validator.constraints.NotBlank;

@Data
@EqualsAndHashCode(callSuper = true)
public class AzureWebClientContext extends AzureClientContext {
  @NotBlank(message = WEB_APP_NAME_BLANK_ERROR_MSG) private String appName;

  @Builder
  AzureWebClientContext(@NonNull AzureConfig azureConfig, @NonNull String subscriptionId,
      @NonNull String resourceGroupName, @NotBlank(message = WEB_APP_NAME_BLANK_ERROR_MSG) String appName,
      boolean extendedReadTimeout) {
    super(azureConfig, subscriptionId, resourceGroupName, extendedReadTimeout);
    this.appName = appName;
  }
}
