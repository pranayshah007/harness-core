/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.nexus;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusValidationParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.nexus.NexusTaskParams;
import io.harness.delegate.beans.nexus.NexusTaskParams.TaskType;
import io.harness.delegate.beans.nexus.NexusTaskResponse;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Slf4j
public class NexusDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private SecretDecryptionService decryptionService;
  @Inject private TimeLimiter timeLimiter;
  @Inject NexusMapper nexusMapper;
  @Inject NGErrorHelper ngErrorHelper;
  @Inject NexusValidationHandler nexusValidationHandler;

  public NexusDelegateTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    NexusTaskParams taskParams = (NexusTaskParams) parameters;
    final NexusConnectorDTO nexusConfig = taskParams.getNexusConnectorDTO();
    final List<EncryptedDataDetail> encryptionDetails = taskParams.getEncryptedDataDetails();
    decryptionService.decrypt(nexusConfig.getAuth().getCredentials(), encryptionDetails);
    final TaskType taskType = taskParams.getTaskType();
    switch (taskType) {
      case VALIDATE:
        return validateNexusServer(nexusConfig, encryptionDetails);
      default:
        throw new InvalidRequestException("No task found for " + taskType.name());
    }
  }

  private NexusTaskResponse validateNexusServer(
      NexusConnectorDTO nexusRequest, List<EncryptedDataDetail> encryptedDataDetails) {
    final NexusValidationParams nexusValidationParams = NexusValidationParams.builder()
                                                            .encryptedDataDetails(encryptedDataDetails)
                                                            .nexusConnectorDTO(nexusRequest)
                                                            .build();
    ConnectorValidationResult connectorValidationResult =
        nexusValidationHandler.validate(nexusValidationParams, getAccountId());
    connectorValidationResult.setDelegateId(getDelegateId());
    return NexusTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
