/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.rancher;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@OwnedBy(HarnessTeam.CDP)
@Data
@SuperBuilder
public class RancherTestConnectionTaskParams implements ConnectorValidationParams, ExecutionCapabilityDemander {
  RancherConnectorDTO rancherConnectorDTO;
  List<EncryptedDataDetail> encryptedDataDetails;
  String connectorName;

  @Override
  public ConnectorType getConnectorType() {
    return ConnectorType.RANCHER;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return RancherTaskCapabilityHelper.fetchRequiredExecutionCapabilities(rancherConnectorDTO, maskingEvaluator);
  }
}
