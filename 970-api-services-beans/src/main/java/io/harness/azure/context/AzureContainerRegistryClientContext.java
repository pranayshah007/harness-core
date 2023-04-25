/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.context;

import io.harness.azure.model.AzureConfig;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@Data
@EqualsAndHashCode(callSuper = true)
public class AzureContainerRegistryClientContext extends AzureClientContext {
  @NonNull private String registryName;

  @Builder
  AzureContainerRegistryClientContext(@NonNull AzureConfig azureConfig, @NonNull String subscriptionId,
      @NonNull String resourceGroupName, @NonNull String registryName) {
    super(azureConfig, subscriptionId, resourceGroupName, false);
    this.registryName = registryName;
  }
}
