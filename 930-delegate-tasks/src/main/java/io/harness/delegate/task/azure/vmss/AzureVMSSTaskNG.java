/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.vmss;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.azure.vmss.handler.AzureVMSSRequestHandler;
import io.harness.delegate.task.azure.vmss.ng.request.AzureVMSSTaskRequest;
import io.harness.delegate.task.azure.vmss.ng.response.AzureVMSSRequestResponse;
import io.harness.delegate.task.azure.vmss.ng.response.AzureVMSSTaskRequestResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jose4j.lang.JoseException;

@Slf4j
@OwnedBy(CDP)
public class AzureVMSSTaskNG extends AbstractDelegateRunnableTask {
  @Inject private Map<String, AzureVMSSRequestHandler<? extends AzureVMSSTaskRequest>> azureVMSSRequestHandlerMap;
  @Inject private SecretDecryptionService decryptionService;

  public AzureVMSSTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public AzureVMSSTaskRequestResponse run(TaskParameters parameters) throws IOException, JoseException {
    if (!(parameters instanceof AzureVMSSTaskRequest)) {
      throw new InvalidArgumentsException(Pair.of("parameters",
          format("Invalid parameters type [%s], expected: [%s]", parameters.getClass().getSimpleName(),
              AzureVMSSTaskRequest.class.getSimpleName())));
    }

    final AzureVMSSTaskRequest taskRequest = (AzureVMSSTaskRequest) parameters;
    log.info("Starting task execution for request type: {}", taskRequest.getRequestType());
    final CommandUnitsProgress commandUnitsProgress = taskRequest.getCommandUnitsProgress() != null
        ? taskRequest.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();
    final AzureVMSSRequestHandler<? extends AzureVMSSTaskRequest> requestHandler =
        azureVMSSRequestHandlerMap.get(taskRequest.getRequestType().name());

    try {
      requireNonNull(requestHandler, "No request handler implemented for type: " + taskRequest.getRequestType().name());
      decryptTaskRequest(taskRequest);
      AzureVMSSRequestResponse requestResponse = requestHandler.handleRequest(taskRequest);
      return AzureVMSSTaskRequestResponse.builder()
          .commandUnitsProgress(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .requestResponse(requestResponse)
          .build();
    } catch (Exception e) {
      Exception processedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error(
          "Exception in processing azure webp app request type {}", taskRequest.getRequestType(), processedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), processedException);
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  private void decryptTaskRequest(AzureVMSSTaskRequest taskRequest) {
    taskRequest.fetchDecryptionDetails().forEach(decryptableEntity -> {
      decryptionService.decrypt(decryptableEntity.getKey(), decryptableEntity.getValue());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(decryptableEntity.getKey(), decryptableEntity.getValue());
    });
  }
}
