/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.scm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.GitCapabilityHelper;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class ScmBatchGetFileTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  List<GetFileTaskParamsPerConnector> getFileTaskParamsPerConnectorList;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    getFileTaskParamsPerConnectorList.forEach(getFileTaskParamsPerConnector -> {
      GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(
          getFileTaskParamsPerConnector.getConnectorDecryptionParams().getScmConnector());
      executionCapabilities.addAll(GitCapabilityHelper.fetchRequiredExecutionCapabilitiesSimpleCheck(gitConfigDTO));
    });
    return executionCapabilities;
  }
}
