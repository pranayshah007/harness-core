/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.advisers.manualIntervention.ManualInterventionAdviserWithRollback;
import io.harness.advisers.nextstep.NextStageAdviser;
import io.harness.advisers.nextstep.NextStepAdviser;
import io.harness.advisers.retry.RetryAdviserWithRollback;
import io.harness.advisers.rollback.OnFailRollbackAdviser;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.proceedwithdefault.ProceedWithDefaultValueAdviser;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class PipelineServiceUtilAdviserRegistrar {
  public Map<AdviserType, Class<? extends Adviser>> getEngineAdvisers() {
    Map<AdviserType, Class<? extends Adviser>> advisersMap = new HashMap<>();
    advisersMap.put(RetryAdviserWithRollback.ADVISER_TYPE, RetryAdviserWithRollback.class);
    advisersMap.put(OnFailRollbackAdviser.ADVISER_TYPE, OnFailRollbackAdviser.class);
    advisersMap.put(ManualInterventionAdviserWithRollback.ADVISER_TYPE, ManualInterventionAdviserWithRollback.class);
    advisersMap.put(NextStepAdviser.ADVISER_TYPE, NextStepAdviser.class);
    advisersMap.put(NextStageAdviser.ADVISER_TYPE, NextStageAdviser.class);
    advisersMap.put(ProceedWithDefaultValueAdviser.ADVISER_TYPE, ProceedWithDefaultValueAdviser.class);

    return advisersMap;
  }
}
