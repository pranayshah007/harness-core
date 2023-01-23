/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.integration;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.AbstractModule;
import java.util.concurrent.atomic.AtomicReference;

@OwnedBy(CDC)
public class CDPipelineIntegrationModule extends AbstractModule {
  private static final AtomicReference<CDPipelineIntegrationModule> instanceRef = new AtomicReference<>();

  public static CDPipelineIntegrationModule getInstance() {
    if (instanceRef.get() == null) {
      instanceRef.compareAndSet(null, new CDPipelineIntegrationModule());
    }
    return instanceRef.get();
  }

  @Override
  protected void configure() {}
}
