/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import io.harness.eula.service.EulaService;
import io.harness.eula.service.impl.EulaServiceImpl;

import com.google.inject.AbstractModule;

public class EulaModule extends AbstractModule {
  NextGenConfiguration appConfig;

  public EulaModule(NextGenConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    bind(EulaService.class).to(EulaServiceImpl.class);
  }
}
