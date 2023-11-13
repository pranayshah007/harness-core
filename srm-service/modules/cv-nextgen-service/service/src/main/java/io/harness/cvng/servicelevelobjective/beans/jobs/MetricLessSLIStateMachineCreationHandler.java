/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.jobs;

import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;
import java.time.Clock;
import java.util.Optional;

public class MetricLessSLIStateMachineCreationHandler
    implements MongoPersistenceIterator.Handler<ServiceLevelIndicator> {
  @Inject OrchestrationService orchestrationService;
  @Inject VerificationTaskService verificationTaskService;
  @Inject Clock clock;
  @Override
  public void handle(ServiceLevelIndicator serviceLevelIndicator) {
    Optional<String> sliVerificationTaskId = verificationTaskService.getSLIVerificationTaskId(
        serviceLevelIndicator.getAccountId(), serviceLevelIndicator.getUuid());
    if (sliVerificationTaskId.isPresent()) {
      AnalysisInput analysisInput = AnalysisInput.builder()
                                        .verificationTaskId(sliVerificationTaskId.get())
                                        .startTime(clock.instant())
                                        .endTime(clock.instant())
                                        .build();
      orchestrationService.queueAnalysis(analysisInput);
    }
  }
}
