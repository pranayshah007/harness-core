/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppSlotDeploymentRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;

@OwnedBy(CDP)
public class AzureWebAppSlotDeploymentRequestHandler
    extends AzureWebAppRequestHandler<AzureWebAppSlotDeploymentRequest> {
  @Override
  protected AzureWebAppRequestResponse execute(AzureWebAppSlotDeploymentRequest taskRequest, AzureConfig azureConfig,
      AzureLogCallbackProvider logCallbackProvider) {
    throw new UnsupportedOperationException("Request handler not implemented");
  }

  @Override
  protected Class<AzureWebAppSlotDeploymentRequest> getRequestType() {
    return AzureWebAppSlotDeploymentRequest.class;
  }
}
