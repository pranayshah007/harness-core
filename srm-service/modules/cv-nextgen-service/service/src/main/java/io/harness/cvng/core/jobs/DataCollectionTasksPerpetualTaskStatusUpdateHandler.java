/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;

public class DataCollectionTasksPerpetualTaskStatusUpdateHandler
    implements MongoPersistenceIterator.Handler<DataCollectionTask> {
  @Inject DataCollectionTaskService dataCollectionTaskService;
  @Override
  public void handle(DataCollectionTask entity) {
    dataCollectionTaskService.updatePerpetualTaskStatus(entity);
  }
}
