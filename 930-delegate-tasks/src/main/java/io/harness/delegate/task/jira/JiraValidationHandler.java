/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jira;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.jira.JiraValidationParams;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.jira.JiraActionNG;

import com.google.inject.Inject;
import java.util.Collections;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
public class JiraValidationHandler implements ConnectorValidationHandler {
  @Inject JiraTaskNGHelper jiraTaskNGHelper;
  @Inject NGErrorHelper ngErrorHelper;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    JiraValidationParams serviceNowValidationParams = (JiraValidationParams) connectorValidationParams;
    try {
      jiraTaskNGHelper.getJiraTaskResponse(JiraTaskNGParameters.builder()
                                               .jiraConnectorDTO(serviceNowValidationParams.getJiraConnectorDTO())
                                               .encryptionDetails(serviceNowValidationParams.getEncryptedDataDetails())
                                               .action(JiraActionNG.VALIDATE_CREDENTIALS)
                                               .build());
      return ConnectorValidationResult.builder()
          .status(ConnectivityStatus.SUCCESS)
          .testedAt(System.currentTimeMillis())
          .build();
    } catch (Exception e) {
      return ConnectorValidationResult.builder()
          .status(ConnectivityStatus.FAILURE)
          .errors(Collections.singletonList(ngErrorHelper.createErrorDetail(e.getMessage())))
          .errorSummary(ngErrorHelper.getErrorSummary(e.getMessage()))
          .testedAt(System.currentTimeMillis())
          .build();
    }
  }
}
