/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.jobs;

import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class AnalysisOrchestrationJob implements Handler<AnalysisOrchestrator> {
  @Inject private OrchestrationService orchestrationService;
  @Override
  public void handle(AnalysisOrchestrator entity) {
    orchestrationService.orchestrate(entity);
  }
}
