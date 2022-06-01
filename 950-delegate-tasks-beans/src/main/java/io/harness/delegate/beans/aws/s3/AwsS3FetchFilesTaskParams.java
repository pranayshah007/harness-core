/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.aws.s3;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
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

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@OwnedBy(HarnessTeam.CDP)
public class AwsS3FetchFilesTaskParams
    extends ConnectorTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  List<AwsS3FetchFileDelegateConfig> fetchFileDelegateConfigs;
  @Builder.Default private boolean shouldOpenLogStream = true;
  private boolean closeLogStream;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    fetchFileDelegateConfigs.forEach(awsS3FetchFileDelegateConfig
        -> executionCapabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
            awsS3FetchFileDelegateConfig.getAwsConnector(), maskingEvaluator)));
    return executionCapabilities;
  }
}
