/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment.verifier;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.deployment.SlotContainerLogStreamer;
import io.harness.delegate.task.azure.appservice.deployment.context.SlotContainerDeploymentVerifierContext;

import lombok.Getter;

@OwnedBy(CDP)
public class SlotContainerDeploymentStatusVerifier extends SlotStatusVerifier {
  @Getter private final SlotContainerLogStreamer logStreamer;

  public SlotContainerDeploymentStatusVerifier(SlotContainerDeploymentVerifierContext context) {
    super(context.getLogCallback(), context.getSlotName(), context.getAzureWebClient(),
        context.getAzureWebClientContext(), null);
    this.logStreamer = context.getLogStreamer();
  }

  @Override
  public boolean hasReachedSteadyState() {
    logStreamer.readContainerLogs();
    return logStreamer.isSuccess();
  }
  @Override
  public String getSteadyState() {
    return null;
  }

  @Override
  public boolean operationFailed() {
    return logStreamer.failed();
  }

  @Override
  public String getErrorMessage() {
    return logStreamer.getErrorLog();
  }
}
