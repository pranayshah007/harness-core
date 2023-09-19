/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awscodecommitconnector;

import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AwsCodeCommitCapabilityHelper extends ConnectorCapabilityBaseHelper {
  private static final String AWS_URL = "https://aws.amazon.com/";

  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ConnectorConfigDTO connectorConfigDTO, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    AwsCodeCommitConnectorDTO awsCodeCommitConnectorDTO = (AwsCodeCommitConnectorDTO) connectorConfigDTO;
    AwsCodeCommitAuthenticationDTO authentication = awsCodeCommitConnectorDTO.getAuthentication();
    if (authentication.getAuthType() == AwsCodeCommitAuthType.HTTPS) {
      capabilityList.add(
          HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(AWS_URL, maskingEvaluator));
    }
    populateDelegateSelectorCapability(capabilityList, awsCodeCommitConnectorDTO.getDelegateSelectors(), awsCodeCommitConnectorDTO.getConnectorType());
    return capabilityList;
  }
}
