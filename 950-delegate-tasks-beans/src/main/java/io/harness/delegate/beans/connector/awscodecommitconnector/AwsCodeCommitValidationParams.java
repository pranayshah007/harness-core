/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awscodecommitconnector;

import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class AwsCodeCommitValidationParams
    extends ConnectorCapabilityBaseHelper implements ConnectorValidationParams, ExecutionCapabilityDemander {
  private final String AWS_URL = "https://aws.amazon.com/";
  private AwsCodeCommitConnectorDTO awsConnector;
  private List<EncryptedDataDetail> encryptionDetails;
  private String connectorName;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    capabilityList.add(
        HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(AWS_URL, maskingEvaluator));
    populateDelegateSelectorCapability(capabilityList, awsConnector.getDelegateSelectors(), awsConnector.getConnectorType());
    return capabilityList;
  }

  @Override
  public ConnectorType getConnectorType() {
    return ConnectorType.CODECOMMIT;
  }
}
