/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.gcpsecretmanagerconnector;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.gcpsecretmanager.GcpSecretManagerConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PL)
@Value
@Builder
public class GcpSecretManagerValidationParams implements ConnectorValidationParams, ExecutionCapabilityDemander {
  private GcpSecretManagerConnectorDTO gcpSecretManagerConnectorDTO;
  private String connectorName;
  public static final String ENCRYPTION_SERVICE_URL = "https://secretmanager.googleapis.com/v1/";
  @Override
  public ConnectorType getConnectorType() {
    return ConnectorType.GCP_SECRET_MANAGER;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities =
        new ArrayList<>(Arrays.asList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
            ENCRYPTION_SERVICE_URL, maskingEvaluator)));
    populateDelegateSelectorCapability(executionCapabilities, gcpSecretManagerConnectorDTO.getDelegateSelectors(), getConnectorType());
    return executionCapabilities;
  }
}