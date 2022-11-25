/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompositeSLODataExecutorTaskHandler
    implements MongoPersistenceIterator.Handler<AbstractServiceLevelObjective> {
  @Inject OrchestrationService orchestrationService;
  @Inject VerificationTaskService verificationTaskService;

  @Override
  public void handle(AbstractServiceLevelObjective serviceLevelObjectiveV2) {
    CompositeServiceLevelObjective compositeServiceLevelObjective =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2;
    String sloVerificationTaskId = verificationTaskService.getCompositeSLOVerificationTaskId(
        compositeServiceLevelObjective.getAccountId(), compositeServiceLevelObjective.getUuid());
    orchestrationService.queueAnalysisWithoutEventPublish(
        sloVerificationTaskId, compositeServiceLevelObjective.getAccountId(), Instant.now(), Instant.now());
  }
}
