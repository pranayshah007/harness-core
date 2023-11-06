/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.ChangeHandler;
import io.harness.changehandlers.ChaosExperimentsTagsChangeDataHandler;
import io.harness.changehandlers.ChaosHubsChangeDataHandler;
import io.harness.entities.subscriptions.ChaosHubs;

import com.google.inject.Inject;

public class ChaosHubsEntity implements CDCEntity<ChaosHubs> {
  @Inject private ChaosHubsChangeDataHandler handler;
  @Inject private ChaosExperimentsTagsChangeDataHandler tagsHandler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    if ("ChaosExperimentsTags".equalsIgnoreCase(handlerClass)) {
      return tagsHandler;
    }
    return handler;
  }

  @Override
  public Class<ChaosHubs> getSubscriptionEntity() {
    return ChaosHubs.class;
  }
}
