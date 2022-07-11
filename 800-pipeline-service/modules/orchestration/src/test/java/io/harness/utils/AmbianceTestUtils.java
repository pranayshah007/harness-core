/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;

@Deprecated
public class AmbianceTestUtils {
  public static final String ACCOUNT_ID = generateUuid();
  public static final String APP_ID = generateUuid();
  public static final String PLAN_EXECUTION_ID = generateUuid();
  public static final String PHASE_RUNTIME_ID = generateUuid();
  public static final String PHASE_SETUP_ID = generateUuid();
  public static final String SECTION_RUNTIME_ID = generateUuid();
  public static final String SECTION_SETUP_ID = generateUuid();

  public static Ambiance buildAmbiance() {
    Level phaseLevel =
        Level.newBuilder()
            .setRuntimeId(PHASE_RUNTIME_ID)
            .setSetupId(PHASE_SETUP_ID)
            .setStepType(StepType.newBuilder().setType("DEPLOY_PHASE").setStepCategory(StepCategory.STEP).build())
            .setGroup("PHASE")
            .build();
    Level sectionLevel =
        Level.newBuilder()
            .setRuntimeId(SECTION_RUNTIME_ID)
            .setSetupId(SECTION_SETUP_ID)
            .setStepType(StepType.newBuilder().setType("DEPLOY_SECTION").setStepCategory(StepCategory.STEP).build())
            .setGroup("SECTION")
            .build();
    List<Level> levels = new ArrayList<>();
    levels.add(phaseLevel);
    levels.add(sectionLevel);
    return Ambiance.newBuilder()
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .putAllSetupAbstractions(ImmutableMap.of("accountId", ACCOUNT_ID, "appId", APP_ID))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234)
        .build();
  }
}
