/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.pdcconnector;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.utils.PhysicalDataCenterConstants.DEFAULT_HOST_SOCKET_TIMEOUT_MS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@OwnedBy(CDP)
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class HostConnectivityTaskParams
    extends ConnectorTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  private String hostName;
  private int port;
  @Builder.Default private int socketTimeout = DEFAULT_HOST_SOCKET_TIMEOUT_MS;
  ConnectorType connectorType;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability(capabilityList, delegateSelectors, connectorType);
    return capabilityList;
  }
}
