/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.pdcconnector;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.utils.PhysicalDataCenterConstants.DEFAULT_SSH_PORT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.ExpressionEvaluator;

import java.util.List;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@OwnedBy(CDP)
@Data
@SuperBuilder
public class PhysicalDataCenterConnectorValidationParams
    implements ConnectorValidationParams, ExecutionCapabilityDemander {
  PhysicalDataCenterConnectorDTO physicalDataCenterConnectorDTO;
  String connectorName;

  @Override
  public ConnectorType getConnectorType() {
    return ConnectorType.PDC;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return PhysicalDataCenterConnectorCapabilityHelper.fetchRequiredExecutionCapabilities(
        physicalDataCenterConnectorDTO, maskingEvaluator, DEFAULT_SSH_PORT);
  }
}
