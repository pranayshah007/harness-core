/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.execution.StageExecutionInstanceInfo;
import io.harness.delegate.cdng.execution.StepExecutionInstanceInfo;

@OwnedBy(CDP)
public interface StageExecutionInstanceInfoRepositoryCustom {
  StageExecutionInstanceInfo append(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineExecutionId, String stageExecutionId, StepExecutionInstanceInfo stepExecutionInstanceInfo);
}
