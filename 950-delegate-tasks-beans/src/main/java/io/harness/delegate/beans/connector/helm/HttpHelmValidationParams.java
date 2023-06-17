/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.helm;

import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class HttpHelmValidationParams
    extends ConnectorTaskParams implements ConnectorValidationParams, ExecutionCapabilityDemander {
  HttpHelmConnectorDTO httpHelmConnectorDTO;
  List<EncryptedDataDetail> encryptionDataDetails;
  String connectorName;
  boolean ignoreResponseCode;

  @Override
  public ConnectorType getConnectorType() {
    return ConnectorType.HTTP_HELM_REPO;
  }

  @Override
  public String getConnectorName() {
    return connectorName;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return HttpHelmCapabilityHelper.fetchRequiredExecutionCapabilities(
        httpHelmConnectorDTO, maskingEvaluator, ignoreResponseCode);
  }
}
