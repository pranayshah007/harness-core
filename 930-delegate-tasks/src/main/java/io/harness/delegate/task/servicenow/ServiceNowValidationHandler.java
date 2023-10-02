/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.servicenow.ServiceNowValidationParams;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.servicenow.ServiceNowActionNG;

import com.google.inject.Inject;
import java.util.Collections;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
public class ServiceNowValidationHandler implements ConnectorValidationHandler {
  @Inject ServiceNowTaskNgHelper serviceNowTaskNgHelper;
  @Inject NGErrorHelper ngErrorHelper;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    ServiceNowValidationParams serviceNowValidationParams = (ServiceNowValidationParams) connectorValidationParams;
    try {
      serviceNowTaskNgHelper.getServiceNowResponse(
          ServiceNowTaskNGParameters.builder()
              .serviceNowConnectorDTO(serviceNowValidationParams.getServiceNowConnectorDTO())
              .encryptionDetails(serviceNowValidationParams.getEncryptedDataDetails())
              .action(ServiceNowActionNG.VALIDATE_CREDENTIALS)
              .build(),
          null);
      return ConnectorValidationResult.builder()
          .status(ConnectivityStatus.SUCCESS)
          .testedAt(System.currentTimeMillis())
          .build();
    } catch (Exception se) {
      return ConnectorValidationResult.builder()
          .status(ConnectivityStatus.FAILURE)
          .errors(Collections.singletonList(ngErrorHelper.createErrorDetail(se.getMessage())))
          .errorSummary(ngErrorHelper.getErrorSummary(se.getMessage()))
          .testedAt(System.currentTimeMillis())
          .build();
    }
  }
}
