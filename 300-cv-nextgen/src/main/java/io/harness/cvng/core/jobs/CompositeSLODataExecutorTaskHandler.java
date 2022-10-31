package io.harness.cvng.core.jobs;

import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

public class CompositeSLODataExecutorTaskHandler
    implements MongoPersistenceIterator.Handler<AbstractServiceLevelObjective> {
  @Inject OrchestrationService orchestrationService;
  @Inject VerificationTaskService verificationTaskService;
  @Inject CompositeSLORecordService sloRecordService;
  @Inject Clock clock;

  @Override
  public void handle(AbstractServiceLevelObjective serviceLevelObjectiveV2) {
    CompositeServiceLevelObjective compositeServiceLevelObjective =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2;
    String sloVerificationTaskId = verificationTaskService.getCompositeSLOVerificationTaskId(
        compositeServiceLevelObjective.getAccountId(), compositeServiceLevelObjective.getUuid());
    CompositeSLORecord lastSLORecord =
        sloRecordService.getLatestCompositeSLORecord(compositeServiceLevelObjective.getUuid());
    LocalDateTime currentLocalDate =
        LocalDateTime.ofInstant(clock.instant(), compositeServiceLevelObjective.getZoneOffset());
    Instant startTimeForCurrentRange = compositeServiceLevelObjective.getCurrentTimeRange(currentLocalDate)
                                           .getStartTime(compositeServiceLevelObjective.getZoneOffset());
    Instant createdAtTime = Instant.ofEpochMilli(compositeServiceLevelObjective.getCreatedAt());
    Instant startTime = (startTimeForCurrentRange.isAfter(createdAtTime)) ? startTimeForCurrentRange : createdAtTime;
    if (lastSLORecord != null) {
      startTime = Instant.ofEpochMilli(lastSLORecord.getEpochMinute());
    }
    orchestrationService.queueAnalysis(sloVerificationTaskId, startTime, Instant.now());
  }
}
