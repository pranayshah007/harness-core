/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.ChangeHandler;
import io.harness.changehandlers.ChaosExperimentRunsChangeDataHandler;
import io.harness.changehandlers.ChaosExperimentsTagsChangeDataHandler;
import io.harness.changehandlers.ChaosInfrastructuresChangeDataHandler;
import io.harness.entities.subscriptions.ChaosExperimentRuns;
import io.harness.entities.subscriptions.ChaosInfrastructures;

import com.google.inject.Inject;

public class ChaosInfrastructuresEntity implements CDCEntity<ChaosInfrastructures> {
  @Inject private ChaosInfrastructuresChangeDataHandler handler;
  @Inject private ChaosExperimentsTagsChangeDataHandler tagsHandler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    if ("ChaosExperimentsTags".equalsIgnoreCase(handlerClass)) {
      return tagsHandler;
    }
    return handler;
  }

  @Override
  public Class<ChaosInfrastructures> getSubscriptionEntity() {
    return ChaosInfrastructures.class;
  }
}
