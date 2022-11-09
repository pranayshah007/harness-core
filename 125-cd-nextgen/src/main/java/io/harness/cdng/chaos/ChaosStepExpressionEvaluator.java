/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.chaos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.chaos.ChaosStepNotifyData.ChaosStepNotifyDataKeys;
import io.harness.expression.EngineExpressionEvaluator;

@OwnedBy(HarnessTeam.PIPELINE)
public class ChaosStepExpressionEvaluator extends EngineExpressionEvaluator {
  private final ChaosStepNotifyData chaosStepNotifyData;

  public ChaosStepExpressionEvaluator(ChaosStepNotifyData chaosStepNotifyData) {
    super(null);
    this.chaosStepNotifyData = chaosStepNotifyData;
  }

  @Override
  protected void initialize() {
    super.initialize();
    this.addToContext(ChaosStepNotifyDataKeys.phase, chaosStepNotifyData.getPhase());
    this.addToContext(ChaosStepNotifyDataKeys.resiliencyScore, chaosStepNotifyData.getResiliencyScore());
    this.addToContext(ChaosStepNotifyDataKeys.faultsPassed, chaosStepNotifyData.getFaultsPassed());
    this.addToContext(ChaosStepNotifyDataKeys.faultsFailed, chaosStepNotifyData.getFaultsFailed());
    this.addToContext(ChaosStepNotifyDataKeys.faultsAwaited, chaosStepNotifyData.getFaultsAwaited());
    this.addToContext(ChaosStepNotifyDataKeys.faultsStopped, chaosStepNotifyData.getFaultsStopped());
    this.addToContext(ChaosStepNotifyDataKeys.faultsNa, chaosStepNotifyData.getFaultsNa());
    this.addToContext(ChaosStepNotifyDataKeys.totalFaults, chaosStepNotifyData.getTotalFaults());
  }
}
