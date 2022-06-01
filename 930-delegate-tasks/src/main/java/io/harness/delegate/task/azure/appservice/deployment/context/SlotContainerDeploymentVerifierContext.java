/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment.context;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureServiceCallBack;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.deployment.SlotContainerLogStreamer;
import io.harness.logging.LogCallback;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@OwnedBy(CDP)
public class SlotContainerDeploymentVerifierContext extends StatusVerifierContext {
  @Getter private final SlotContainerLogStreamer logStreamer;

  @Builder
  public SlotContainerDeploymentVerifierContext(@NonNull LogCallback logCallback, @NonNull String slotName,
      @NonNull AzureWebClient azureWebClient, @NonNull AzureWebClientContext azureWebClientContext,
      AzureServiceCallBack restCallBack, SlotContainerLogStreamer logStreamer) {
    super(logCallback, slotName, azureWebClient, azureWebClientContext, restCallBack);
    this.logStreamer = logStreamer;
  }
}
