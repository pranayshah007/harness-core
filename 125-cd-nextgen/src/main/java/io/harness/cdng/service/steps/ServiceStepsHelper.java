/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.UnsupportedOperationException;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class ServiceStepsHelper {
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private OutcomeService outcomeService;

  public NGLogCallback getServiceLogCallback(Ambiance ambiance) {
    return getServiceLogCallback(ambiance, false);
  }

  public NGLogCallback getServiceLogCallback(Ambiance ambiance, boolean shouldOpenStream) {
    return new NGLogCallback(logStreamingStepClientFactory, prepareServiceAmbiance(ambiance), null, shouldOpenStream);
  }

  private Ambiance prepareServiceAmbiance(Ambiance ambiance) {
    List<Level> levels = ambiance.getLevelsList();
    for (int i = levels.size() - 1; i >= 0; i--) {
      Level level = levels.get(i);
      if (ServiceConfigStep.STEP_TYPE.equals(level.getStepType())
          || ServiceSectionStep.STEP_TYPE.equals(level.getStepType())) {
        return AmbianceUtils.clone(ambiance, i + 1);
      }
    }
    throw new UnsupportedOperationException("Not inside service step or one of it's children");
  }

  public List<Outcome> getChildrenOutcomes(Map<String, ResponseData> responseDataMap) {
    List<StepOutcomeRef> outcomeRefs = new ArrayList<>();
    for (ResponseData responseData : responseDataMap.values()) {
      if (!(responseData instanceof StepResponseNotifyData)) {
        continue;
      }

      StepResponseNotifyData stepResponseNotifyData = (StepResponseNotifyData) responseData;
      if (EmptyPredicate.isNotEmpty(stepResponseNotifyData.getStepOutcomeRefs())) {
        outcomeRefs.addAll(stepResponseNotifyData.getStepOutcomeRefs());
      }
    }

    if (EmptyPredicate.isEmpty(outcomeRefs)) {
      return Collections.emptyList();
    }

    Set<String> runtimeIds = new HashSet<>();
    outcomeRefs.forEach(or -> runtimeIds.add(or.getInstanceId()));
    return outcomeService.fetchOutcomes(new ArrayList<>(runtimeIds));
  }
}
