/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.chaos;

import io.harness.persistence.HPersistence;

import com.google.inject.AbstractModule;
import java.util.concurrent.atomic.AtomicReference;

public class ChaosModule extends AbstractModule {
  private static final AtomicReference<ChaosModule> instanceRef = new AtomicReference<>();

  public static ChaosModule getInstance() {
    if (instanceRef.get() == null) {
      instanceRef.compareAndSet(null, new ChaosModule());
    }
    return instanceRef.get();
  }

  @Override
  protected void configure() {
    bind(ChaosService.class).to(ChaosServiceImpl.class);
  }

  private void registerRequiredBindings() {
    requireBinding(HPersistence.class);
  }
}
