/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.jobs;

import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;

import com.google.inject.Inject;

public class SLONotificationHandler implements Handler<ServiceLevelObjective> {
  @Inject private ServiceLevelObjectiveService serviceLevelObjectiveService;

  @Override
  public void handle(ServiceLevelObjective serviceLevelObjective) {
    serviceLevelObjectiveService.handleNotification(serviceLevelObjective);
  }
}
