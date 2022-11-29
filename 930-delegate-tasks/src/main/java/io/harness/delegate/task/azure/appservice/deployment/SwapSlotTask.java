/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment;

import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.logging.LogCallback;

import com.azure.core.http.rest.Response;
import java.nio.ByteBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@OwnedBy(HarnessTeam.CDP)
public class SwapSlotTask implements Runnable {
  private final String sourceSlotName;
  private final String targetSlotName;
  private final AzureWebClient azureWebClient;
  private final AzureWebClientContext webClientContext;
  private final LogCallback slotSwapLogCallback;

  public SwapSlotTask(String sourceSlotName, String targetSlotName, AzureWebClient azureWebClient,
      AzureWebClientContext webClientContext, LogCallback slotSwapLogCallback) {
    this.sourceSlotName = sourceSlotName;
    this.targetSlotName = targetSlotName;
    this.azureWebClient = azureWebClient;
    this.webClientContext = webClientContext;
    this.slotSwapLogCallback = slotSwapLogCallback;
  }

  @Override
  public void run() {
    slotSwapLogCallback.saveExecutionLog(format(
        "Sending request for swapping source slot: [%s] with target slot: [%s]", sourceSlotName, targetSlotName));
    Mono<Response<Flux<ByteBuffer>>> responseMono =
        azureWebClient.swapDeploymentSlotsAsync(webClientContext, sourceSlotName, targetSlotName);
    Response<Flux<ByteBuffer>> response = responseMono.block();
    if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
      slotSwapLogCallback.saveExecutionLog("Swapping request returned successfully", INFO);
    } else {
      slotSwapLogCallback.saveExecutionLog("Swap slot failed", ERROR, FAILURE);
    }
  }
}
