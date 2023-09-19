/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.IRSA;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.MANUAL_CREDENTIALS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class AwsCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ConnectorConfigDTO connectorConfigDTO, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorConfigDTO;
    AwsCredentialDTO credential = awsConnectorDTO.getCredential();
    if (credential.getAwsCredentialType() == MANUAL_CREDENTIALS) {
      final String AWS_URL = "https://aws.amazon.com/";
      capabilityList.add(
          HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(AWS_URL, maskingEvaluator));
    } else if (credential.getAwsCredentialType() != INHERIT_FROM_DELEGATE
        && credential.getAwsCredentialType() != IRSA) {
      throw new UnknownEnumTypeException("AWS Credential Type", String.valueOf(credential.getAwsCredentialType()));
    }
    populateDelegateSelectorCapability(capabilityList, awsConnectorDTO.getDelegateSelectors(), awsConnectorDTO.getConnectorType());
    return capabilityList;
  }
}
