/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jira.connection;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.jira.JiraConnectionTaskParams;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.jira.connection.JiraTestConnectionTaskNGResponse;
import io.harness.delegate.beans.connector.jira.connection.JiraTestConnectionTaskNGResponse.JiraTestConnectionTaskNGResponseBuilder;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.jira.JiraTaskNGHelper;
import io.harness.delegate.task.jira.JiraTaskNGParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HintException;
import io.harness.jira.JiraActionNG;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
public class JiraTestConnectionTaskNG extends AbstractDelegateRunnableTask {
  @Inject private JiraTaskNGHelper jiraTaskNGHelper;

  public JiraTestConnectionTaskNG(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("This method is deprecated");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    JiraConnectionTaskParams jiraConnectionTaskParams = (JiraConnectionTaskParams) parameters;
    JiraConnectorDTO connectorDTO = jiraConnectionTaskParams.getJiraConnectorDTO();

    JiraTestConnectionTaskNGResponseBuilder responseBuilder = JiraTestConnectionTaskNGResponse.builder();
    try {
      jiraTaskNGHelper.getJiraTaskResponse(JiraTaskNGParameters.builder()
                                               .jiraConnectorDTO(connectorDTO)
                                               .action(JiraActionNG.VALIDATE_CREDENTIALS)
                                               .encryptionDetails(jiraConnectionTaskParams.getEncryptionDetails())
                                               .build());
      responseBuilder.canConnect(true);
    } catch (HintException ex) {
      throw ex;
    } catch (Exception ex) {
      responseBuilder.canConnect(false).errorMessage(ExceptionUtils.getMessage(ex));
    }
    return responseBuilder.build();
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
