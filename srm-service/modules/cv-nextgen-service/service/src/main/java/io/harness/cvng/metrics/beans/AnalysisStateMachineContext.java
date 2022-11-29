/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.metrics.beans;

import io.harness.cvng.statemachine.beans.AnalysisState.StateType;
import io.harness.metrics.AutoMetricContext;

public class AnalysisStateMachineContext extends AutoMetricContext {
  public AnalysisStateMachineContext(String accountId, StateType analysisStateType) {
    put("accountId", accountId);
    put("analysisStateType", analysisStateType.name());
  }
}
