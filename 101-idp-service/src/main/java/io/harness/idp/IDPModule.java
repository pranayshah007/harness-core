/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp;

import com.google.inject.AbstractModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.secretmanager.SecretManager;
import io.harness.idp.secretmanager.SecretManagerImpl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IDPModule extends AbstractModule {
  private final io.harness.idp.IDPConfiguration appConfig;
  public IDPModule(io.harness.idp.IDPConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    bind(SecretManager.class).to(SecretManagerImpl.class);
  }
}
