/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.variable.services.VariableService;
import io.harness.ng.core.variable.services.impl.VariableServiceImpl;

import com.google.inject.AbstractModule;

@OwnedBy(PL)
public class NgVariableModule extends AbstractModule {
  NextGenConfiguration appConfig;

  public NgVariableModule(NextGenConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    bind(NextGenConfiguration.class).toInstance(appConfig);
    bind(VariableService.class).to(VariableServiceImpl.class);
  }
}
