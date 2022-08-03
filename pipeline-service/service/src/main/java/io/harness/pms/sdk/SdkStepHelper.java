/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.datastructures.EphemeralCacheService;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.pipeline.CommonStepInfo;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class SdkStepHelper {
  public static final String SDK_STEP_SET_NAME = "sdkStepsVisibleInUI";
  @Inject PmsSdkInstanceService pmsSdkInstanceService;
  @Inject CommonStepInfo commonStepInfo;
  @Inject EphemeralCacheService ephemeralCacheService;

  public Set<String> getAllStepVisibleInUI() {
    if (ephemeralCacheService.getDistributedSet(SDK_STEP_SET_NAME).isEmpty()) {
      Set<String> allSteps = new HashSet<>();
      for (Set<SdkStep> sdkSteps : pmsSdkInstanceService.getSdkSteps().values()) {
        allSteps.addAll(sdkSteps.stream()
                            .filter(SdkStep::getIsPartOfStepPallete)
                            .map(sdkStep -> sdkStep.getStepType().getType())
                            .collect(Collectors.toList()));
      }
      allSteps.addAll(commonStepInfo.getCommonSteps("").stream().map(StepInfo::getType).collect(Collectors.toList()));
      ephemeralCacheService.getDistributedSet(SDK_STEP_SET_NAME).addAll(allSteps);
      return allSteps;
    }
    return ephemeralCacheService.getDistributedSet(SDK_STEP_SET_NAME);
  }
}
