/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

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
import io.harness.delegate.task.azure.arm.handlers.AzureResourceCreationAbstractTaskHandler;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.delegate.task.azure.common.AzureLogCallbackProviderFactory;
import io.harness.exception.UnexpectedTypeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.secret.SecretSanitizerThreadLocal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jose4j.lang.JoseException;

@OwnedBy(CDP)
@Slf4j
public class AzureResourceCreationTaskNG extends AbstractDelegateRunnableTask {
  @Inject private Map<AzureARMTaskType, AzureResourceCreationAbstractTaskHandler> handlerMap;
  @Inject protected AzureLogCallbackProviderFactory logCallbackProviderFactory;
  @Inject protected SecretDecryptionService secretDecryptionService;

  public AzureResourceCreationTaskNG(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    log.info("Started executing the Azure ARM/BP NG task");
    AzureResourceCreationTaskNGParameters taskNGParameters = (AzureResourceCreationTaskNGParameters) parameters;
    decryptSecrets(taskNGParameters);
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    AzureLogCallbackProvider logCallback = getLogCallback(commandUnitsProgress);
    if (!handlerMap.containsKey(taskNGParameters.getAzureARMTaskType())) {
      throw new UnexpectedTypeException(
          String.format("No handler found for task type %s", taskNGParameters.getAzureARMTaskType()));
    }
    AzureResourceCreationAbstractTaskHandler handler = handlerMap.get(taskNGParameters.getAzureARMTaskType());
    try {
      AzureResourceCreationTaskNGResponse response =
          handler.executeTask(taskNGParameters, getDelegateId(), getTaskId(), logCallback);
      if (response.getCommandExecutionStatus().equals(CommandExecutionStatus.SUCCESS)) {
        logCallback.obtainLogCallback("Create").saveExecutionLog("Sucess", INFO, CommandExecutionStatus.SUCCESS);
      } else {
        logCallback.obtainLogCallback("Create").saveExecutionLog("Failure", ERROR, CommandExecutionStatus.FAILURE);
      }
      response.setUnitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
      return response;
    } catch (Exception e) {
      throw new TaskNGDataException(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), e);
    }
  }
  // TODO: check if this is how is been suppose to work
  public AzureLogCallbackProvider getLogCallback(CommandUnitsProgress commandUnitsProgress) {
    return logCallbackProviderFactory.createNg(getLogStreamingTaskClient(), commandUnitsProgress);
  }

  private void decryptSecrets(AzureResourceCreationTaskNGParameters taskNGParameters) {
    List<DecryptableEntity> decryptableEntities = taskNGParameters.azureConnectorDTO.getDecryptableEntities();
    List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> decryptionDetails = new ArrayList<>();
    decryptableEntities.forEach(decryptableEntity
        -> decryptionDetails.add(Pair.of(decryptableEntity, taskNGParameters.getEncryptedDataDetails())));
    decryptionDetails.forEach(decryptableEntityListPair -> {
      secretDecryptionService.decrypt(decryptableEntityListPair.getKey(), decryptableEntityListPair.getValue());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          decryptableEntityListPair.getKey(), decryptableEntityListPair.getValue());
    });
  }
}
