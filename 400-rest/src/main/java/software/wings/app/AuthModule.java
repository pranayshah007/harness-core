/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.service.impl.DelegateAuthServiceImpl;
import io.harness.service.intfc.DelegateAuthService;

import software.wings.service.impl.AuthServiceImpl;
import software.wings.service.intfc.AuthService;

import com.google.inject.AbstractModule;

@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class AuthModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(AuthService.class).to(AuthServiceImpl.class);
    bind(DelegateAuthService.class).to(DelegateAuthServiceImpl.class);
  }
}
