/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.bamboo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.bamboo.BambooConnectionTaskResponse;
import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.beans.connector.bamboo.BambooTestConnectionTaskParams;
import io.harness.delegate.beans.connector.bamboo.BambooValidationParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
public class BambooTestConnectionDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private BambooValidationHandler bambooValidationHandler;

  public BambooTestConnectionDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public BambooConnectionTaskResponse run(TaskParameters parameters) {
    BambooTestConnectionTaskParams bambooTestConnectionTaskParams = (BambooTestConnectionTaskParams) parameters;
    BambooConnectorDTO bambooConnectorDTO = bambooTestConnectionTaskParams.getBambooConnectorDTO();
    final BambooValidationParams bambooValidationParams =
        BambooValidationParams.builder()
            .encryptionDataDetails(bambooTestConnectionTaskParams.getEncryptionDetails())
            .bambooConnectorDTO(bambooConnectorDTO)
            .build();
    ConnectorValidationResult bambooConnectorValidationResult =
        bambooValidationHandler.validate(bambooValidationParams, getAccountId());
    bambooConnectorValidationResult.setDelegateId(getDelegateId());
    return BambooConnectionTaskResponse.builder().connectorValidationResult(bambooConnectorValidationResult).build();
  }

  @Override
  public BambooConnectionTaskResponse run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
