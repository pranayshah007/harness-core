/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.util.Objects.requireNonNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.azure.appservice.webapp.handler.AzureWebAppRequestHandler;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppTaskRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTaskResponse;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.delegate.task.azure.common.AzureLogCallbackProviderFactory;
import io.harness.delegate.utils.TaskExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.secret.SecretSanitizerThreadLocal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.lang.JoseException;

@OwnedBy(CDP)
@Slf4j
public class AzureWebAppTaskNG extends AbstractDelegateRunnableTask {
  @Inject
  private Map<String, AzureWebAppRequestHandler<? extends AzureWebAppTaskRequest>> azureWebAppTaskTypeToRequestHandler;
  @Inject private AzureLogCallbackProviderFactory logCallbackProviderFactory;
  @Inject private SecretDecryptionService decryptionService;

  public AzureWebAppTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public AzureWebAppTaskResponse run(TaskParameters parameters) throws IOException, JoseException {
    AzureWebAppTaskRequest azureWebAppTaskRequest = (AzureWebAppTaskRequest) parameters;
    CommandUnitsProgress commandUnitsProgress = azureWebAppTaskRequest.getCommandUnitsProgress() != null
        ? azureWebAppTaskRequest.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();

    log.info("Starting task execution for request type: {}", azureWebAppTaskRequest.getRequestType());

    AzureWebAppRequestHandler<? extends AzureWebAppTaskRequest> requestHandler =
        azureWebAppTaskTypeToRequestHandler.get(azureWebAppTaskRequest.getRequestType().name());
    AzureLogCallbackProvider logCallbackProvider =
        logCallbackProviderFactory.createNg(getLogStreamingTaskClient(), commandUnitsProgress);

    try {
      decryptRequest(azureWebAppTaskRequest);
      requireNonNull(
          requestHandler, "No request handler implemented for type: " + azureWebAppTaskRequest.getRequestType());
      AzureWebAppRequestResponse requestResponse =
          requestHandler.handleRequest(azureWebAppTaskRequest, logCallbackProvider);
      return AzureWebAppTaskResponse.builder()
          .requestResponse(requestResponse)
          .commandUnitsProgress(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception e) {
      Exception processedException = ExceptionMessageSanitizer.sanitizeException(e);
      TaskExceptionUtils.handleExceptionCommandUnits(
          commandUnitsProgress, logCallbackProvider::obtainLogCallback, processedException);
      log.error("Exception in processing azure webp app request type {}", azureWebAppTaskRequest.getRequestType(),
          processedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), processedException);
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  private void decryptRequest(AzureWebAppTaskRequest webAppTaskRequest) {
    if (webAppTaskRequest.getInfrastructure() != null) {
      AzureWebAppInfraDelegateConfig infrastructure = webAppTaskRequest.getInfrastructure();
      List<EncryptedDataDetail> encryptedDataDetails = infrastructure.getEncryptionDataDetails();
      List<DecryptableEntity> decryptableEntities = infrastructure.getDecryptableEntities();
      for (DecryptableEntity decryptable : decryptableEntities) {
        decryptionService.decrypt(decryptable, encryptedDataDetails);
      }
    }
  }
}
