/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;
import com.google.inject.Singleton;
@Singleton
public class ChangeSourceDemoHandler implements MongoPersistenceIterator.Handler<ChangeSource> {
  @Inject private ChangeSourceService changeSourceService;
  @Override
  public void handle(ChangeSource entity) {
    changeSourceService.generateDemoData(entity);
  }
}
