/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment.context;

import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.delegate.task.azure.appservice.deployment.StreamPackageDeploymentLogsTask;
import io.harness.logging.LogCallback;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

public class SlotDeploymentVerifierContext extends StatusVerifierContext {
  @Getter private final StreamPackageDeploymentLogsTask logStreamer;

  @Builder
  public SlotDeploymentVerifierContext(@NonNull LogCallback logCallback, @NonNull String slotName,
      @NonNull AzureWebClient azureWebClient, @NonNull AzureWebClientContext azureWebClientContext,
      @NonNull StreamPackageDeploymentLogsTask logStreamer) {
    super(logCallback, slotName, azureWebClient, azureWebClientContext, null);
    this.logStreamer = logStreamer;
  }
}
