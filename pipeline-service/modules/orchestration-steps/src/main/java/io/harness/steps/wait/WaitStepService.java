/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.wait;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.wait.WaitStepInstance;

import java.util.Optional;

@OwnedBy(PIPELINE)
public interface WaitStepService {
  WaitStepInstance save(WaitStepInstance waitStepInstance);
  Optional<WaitStepInstance> findByNodeExecutionId(String nodeExecutionId);
  void markAsFailOrSuccess(String planExecutionId, String nodeExecutionId, WaitStepAction waitStepAction);
  void updatePlanStatus(String planExecutionId, String nodeExecutionId);

  WaitStepInstance getWaitStepExecutionDetails(String nodeExecutionId);
}
