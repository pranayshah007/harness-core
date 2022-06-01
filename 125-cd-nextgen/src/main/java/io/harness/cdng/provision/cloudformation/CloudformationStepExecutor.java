/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.cloudformation.beans.CloudFormationCreateStackPassThroughData;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;

@OwnedBy(CDP)
public interface CloudformationStepExecutor {
  TaskChainResponse executeCloudformationTask(Ambiance ambiance, StepElementParameters stepParameters,
      CloudformationTaskNGParameters parameters, CloudFormationCreateStackPassThroughData passThroughData);
}