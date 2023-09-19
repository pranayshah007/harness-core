/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.aws.codecommit;

import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;

@Value
@Builder
public class AwsCodeCommitApiTaskParams
    extends ConnectorCapabilityBaseHelper implements ExecutionCapabilityDemander, TaskParameters {
  static final String AWS_URL = "https://aws.amazon.com/";

  AwsCodeCommitApiParams apiParams;

  AwsCodeCommitConnectorDTO awsCodeCommitConnectorDTO;
  List<EncryptedDataDetail> encryptedDataDetails;

  @NotEmpty AwsCodeCommitRequestType requestType;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    capabilityList.add(
        HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(AWS_URL, maskingEvaluator));
    if (awsCodeCommitConnectorDTO != null) {
      populateDelegateSelectorCapability(capabilityList, awsCodeCommitConnectorDTO.getDelegateSelectors(), awsCodeCommitConnectorDTO.getConnectorType());
    }
    return capabilityList;
  }
}
