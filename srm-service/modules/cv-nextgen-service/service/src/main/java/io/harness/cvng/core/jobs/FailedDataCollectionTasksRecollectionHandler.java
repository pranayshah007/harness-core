/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.impl.SLIDataCollectionTaskServiceImpl;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class FailedDataCollectionTasksRecollectionHandler
    implements MongoPersistenceIterator.Handler<EntityUnavailabilityStatuses> {
  @Inject private DataCollectionTaskService dataCollectionTaskService;

  @Inject private VerificationTaskService verificationTaskService;

  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;

  @Inject SLIDataCollectionTaskServiceImpl sliDataCollectionTaskService;

  @Inject Clock clock;

  @Override
  public void handle(EntityUnavailabilityStatuses entity) {
    long endTime = entity.getEndTime();
    String sliId =
        verificationTaskService.getSLIVerificationTaskId(entity.getAccountId(), entity.getEntityIdentifier());
    ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.get(sliId);
    Optional<DataCollectionTask> dataCollectionTaskOptional =
        Optional.ofNullable(dataCollectionTaskService.getFirstDataCollectionTaskWithStatusAfterStartTime(
            sliId, DataCollectionExecutionStatus.SUCCESS, Instant.ofEpochSecond(endTime)));
    if (dataCollectionTaskOptional.isPresent()) {
      sliDataCollectionTaskService.createRestoreTask(serviceLevelIndicator,
          Instant.ofEpochSecond(entity.getStartTime()), Instant.ofEpochSecond(entity.getEndTime()));
    }
  }
}
