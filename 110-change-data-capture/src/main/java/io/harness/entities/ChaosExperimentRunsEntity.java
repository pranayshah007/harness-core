/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.ChangeHandler;
import io.harness.changehandlers.ChaosExperimentRunsChangeDataHandler;
import io.harness.entities.subscriptions.ChaosExperimentRuns;

import com.google.inject.Inject;

public class ChaosExperimentRunsEntity implements CDCEntity<ChaosExperimentRuns> {
  @Inject private ChaosExperimentRunsChangeDataHandler handler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    return handler;
  }

  @Override
  public Class<ChaosExperimentRuns> getSubscriptionEntity() {
    return ChaosExperimentRuns.class;
  }
}
