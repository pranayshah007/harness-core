/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jenkins;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsTestConnectionTaskParams;
import io.harness.delegate.beans.connector.jenkins.JenkinsTestConnectionTaskResponse;
import io.harness.delegate.beans.connector.jenkins.JenkinsValidationParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Slf4j
public class JenkinsTestConnectionDelegateTask extends AbstractDelegateRunnableTask {
  private static final String EMPTY_STR = "";
  @Inject private JenkinsValidationHandler jenkinsValidationHandler;

  public JenkinsTestConnectionDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public JenkinsTestConnectionTaskResponse run(TaskParameters parameters) {
    JenkinsTestConnectionTaskParams jenkinsTestConnectionTaskParams = (JenkinsTestConnectionTaskParams) parameters;
    JenkinsConnectorDTO jenkinsConnectorDTO = jenkinsTestConnectionTaskParams.getJenkinsConnector();
    final JenkinsValidationParams jenkinsValidationParams =
        JenkinsValidationParams.builder()
            .encryptionDataDetails(jenkinsTestConnectionTaskParams.getEncryptionDetails())
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .build();
    ConnectorValidationResult jenkinsConnectorValidationResult =
        jenkinsValidationHandler.validate(jenkinsValidationParams, getAccountId());
    jenkinsConnectorValidationResult.setDelegateId(getDelegateId());
    return JenkinsTestConnectionTaskResponse.builder()
        .connectorValidationResult(jenkinsConnectorValidationResult)
        .build();
  }

  @Override
  public JenkinsTestConnectionTaskResponse run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
