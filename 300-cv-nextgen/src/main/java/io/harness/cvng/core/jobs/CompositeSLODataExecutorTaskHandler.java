package io.harness.cvng.core.jobs;

import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;
import java.time.Instant;

public class CompositeSLODataExecutorTaskHandler
    implements MongoPersistenceIterator.Handler<AbstractServiceLevelObjective> {
  @Inject OrchestrationService orchestrationService;
  @Inject VerificationTaskService verificationTaskService;
  @Inject CompositeSLORecordService sloRecordService;

  @Override
  public void handle(AbstractServiceLevelObjective serviceLevelObjectiveV2) {
    String verificationId = verificationTaskService.getCompositeSLOVerificationTaskId(
        serviceLevelObjectiveV2.getAccountId(), serviceLevelObjectiveV2.getUuid());
    CompositeSLORecord lastSLORecord = sloRecordService.getLatestCompositeSLORecord(serviceLevelObjectiveV2.getUuid());
    Instant startTime = Instant.ofEpochMilli(serviceLevelObjectiveV2.getCreatedAt());
    if (lastSLORecord != null) {
      startTime = Instant.ofEpochMilli(lastSLORecord.getEpochMinute());
    }
    orchestrationService.queueAnalysis(verificationId, startTime, Instant.now());
  }
}
