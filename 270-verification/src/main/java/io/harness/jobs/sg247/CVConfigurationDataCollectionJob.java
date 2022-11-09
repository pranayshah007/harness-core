/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jobs.sg247;

import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.service.intfc.ContinuousVerificationService;

import software.wings.verification.CVConfiguration;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CVConfigurationDataCollectionJob implements Handler<CVConfiguration> {
  @Inject private ContinuousVerificationService continuousVerificationService;

  @Override
  public void handle(CVConfiguration cvConfiguration) {
    log.info("Executing APM & Logs Data collection for {}", cvConfiguration.getUuid());
    continuousVerificationService.triggerDataCollection(cvConfiguration);
  }
}
