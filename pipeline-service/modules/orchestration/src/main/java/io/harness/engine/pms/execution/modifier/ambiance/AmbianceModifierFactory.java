/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.pms.execution.modifier.ambiance;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.steps.StepCategory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
public class AmbianceModifierFactory {
  private final StageLevelAmbianceModifier stageLevelAmbianceModifier;
  @Inject
  public AmbianceModifierFactory(StageLevelAmbianceModifier stageLevelAmbianceModifier) {
    this.stageLevelAmbianceModifier = stageLevelAmbianceModifier;
  }

  public AmbianceModifier obtainModifier(StepCategory stepCategory) {
    switch (stepCategory) {
      case STAGE:
        return stageLevelAmbianceModifier;
      default:
        return null;
    }
  }
}
