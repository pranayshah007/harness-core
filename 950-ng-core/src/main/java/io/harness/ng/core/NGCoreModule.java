/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.activityhistory.impl.NGActivityServiceImpl;
import io.harness.ng.core.activityhistory.impl.NGActivitySummaryServiceImpl;
import io.harness.ng.core.activityhistory.service.NGActivityService;
import io.harness.ng.core.activityhistory.service.NGActivitySummaryService;

import com.google.inject.AbstractModule;
import java.util.concurrent.atomic.AtomicReference;

@OwnedBy(HarnessTeam.PL)
public class NGCoreModule extends AbstractModule {
  private static final AtomicReference<NGCoreModule> instanceRef = new AtomicReference<>();

  public static NGCoreModule getInstance() {
    if (instanceRef.get() == null) {
      instanceRef.compareAndSet(null, new NGCoreModule());
    }
    return instanceRef.get();
  }

  @Override
  protected void configure() {
    super.configure();
    bind(NGActivityService.class).to(NGActivityServiceImpl.class);
    bind(NGActivitySummaryService.class).to(NGActivitySummaryServiceImpl.class);
  }
}
