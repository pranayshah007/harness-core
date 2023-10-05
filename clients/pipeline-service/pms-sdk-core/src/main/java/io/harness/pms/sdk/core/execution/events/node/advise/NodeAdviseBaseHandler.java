/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.sdk.core.execution.events.node.advise;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviseEvent;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.registries.AdviserRegistry;

import com.google.common.base.Preconditions;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public interface NodeAdviseBaseHandler {
  default AdviserResponse handleAdviseEvent(AdviseEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    Preconditions.checkArgument(isNotBlank(nodeExecutionId), "nodeExecutionId is null or empty");

    AdviserRegistry adviserRegistry = getAdviserRegistry();
    AdviserResponse adviserResponse = null;
    for (AdviserObtainment obtainment : event.getAdviserObtainmentsList()) {
      Adviser adviser = adviserRegistry.obtain(obtainment.getType());
      AdvisingEvent advisingEvent = AdvisingEvent.builder()
                                        .ambiance(event.getAmbiance())
                                        .failureInfo(event.getFailureInfo())
                                        .isPreviousAdviserExpired(event.getIsPreviousAdviserExpired())
                                        .retryIds(event.getRetryIdsList())
                                        .toStatus(event.getToStatus())
                                        .fromStatus(event.getFromStatus())
                                        .adviserParameters(obtainment.getParameters().toByteArray())
                                        .build();
      if (adviser.canAdvise(advisingEvent)) {
        adviserResponse = adviser.onAdviseEvent(advisingEvent);
        if (adviserResponse != null) {
          break;
        }
      }
    }
    return adviserResponse;
  }

  AdviserRegistry getAdviserRegistry();
}
