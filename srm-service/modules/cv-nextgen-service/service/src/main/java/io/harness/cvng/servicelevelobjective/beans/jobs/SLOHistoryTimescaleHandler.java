/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.jobs;

import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLOTimeScaleService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;

public class SLOHistoryTimescaleHandler implements MongoPersistenceIterator.Handler<AbstractServiceLevelObjective> {
  @Inject SLOTimeScaleService sloTimeScaleService;
  @Override
  public void handle(AbstractServiceLevelObjective serviceLevelObjective) {
    sloTimeScaleService.insertSLOHistory(serviceLevelObjective);
  }
}
