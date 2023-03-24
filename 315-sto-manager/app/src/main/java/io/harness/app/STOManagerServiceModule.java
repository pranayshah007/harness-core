/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.impl.STOYamlSchemaServiceImpl;
import io.harness.app.intfc.STOYamlSchemaService;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.STO)
public class STOManagerServiceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(STOYamlSchemaService.class).to(STOYamlSchemaServiceImpl.class).in(Singleton.class);
  }
}
