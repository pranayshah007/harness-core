/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatus;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;
import java.time.Instant;

public class RestoreDataCollectionTaskRecalculationHandler
    implements MongoPersistenceIterator.Handler<EntityUnavailabilityStatuses> {
  @Inject EntityUnavailabilityStatusesService entityUnavailabilityStatusesService;

  @Inject OrchestrationService orchestrationService;

  @Inject VerificationTaskService verificationTaskService;

  @Override
  public void handle(EntityUnavailabilityStatuses entity) {
    EntityUnavailabilityStatuses entityUnavailabilityStatuses =
        entityUnavailabilityStatusesService.getMinStartTimeInstanceWithStatus(
            ProjectParams.builder()
                .accountIdentifier(entity.getAccountId())
                .orgIdentifier(entity.getOrgIdentifier())
                .projectIdentifier(entity.getProjectIdentifier())
                .build(),
            entity.getEntityType(), entity.getEntityIdentifier(), EntityUnavailabilityStatus.DATA_RECOLLECTION_PASSED);
    if (entityUnavailabilityStatuses.getStartTime() == entity.getStartTime()) {
      String verificationTaskId =
          verificationTaskService.getSLIVerificationTaskId(entity.getAccountId(), entity.getEntityIdentifier());
      orchestrationService.queueAnalysis(AnalysisInput.builder()
                                             .verificationTaskId(verificationTaskId)
                                             .startTime(Instant.ofEpochSecond(entity.getStartTime()))
                                             .endTime(DateTimeUtils.roundDownTo5MinBoundary(Instant.now()))
                                             .isSLORestoreTask(true)
                                             .build());
    }
  }
}
