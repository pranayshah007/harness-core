/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.oidc.config.OidcConfigurationUtility;
import io.harness.oidc.gcp.GcpOidcTokenUtility;
import io.harness.oidc.jwks.OidcJwksUtility;
import io.harness.oidc.rsa.OidcRsaKeyService;

import com.google.inject.AbstractModule;

@OwnedBy(PL)
public class NGOidcModule extends AbstractModule {
  String oidcConfigFile;

  public NGOidcModule(NextGenConfiguration appConfig) {
    if (appConfig.getOidcConfigPath().isEmpty() || appConfig.getOidcConfigPath() == null) {
      oidcConfigFile = System.getProperty("user.dir") + "/120-ng-manager";
    } else {
      oidcConfigFile = appConfig.getOidcConfigPath();
    }
    oidcConfigFile = oidcConfigFile + "/oidc_config.json";
  }

  @Override
  protected void configure() {
    bind(OidcConfigurationUtility.class).toInstance(new OidcConfigurationUtility(oidcConfigFile));
    bind(OidcJwksUtility.class).toInstance(new OidcJwksUtility());
    bind(OidcRsaKeyService.class).toInstance(new OidcRsaKeyService());
    bind(GcpOidcTokenUtility.class).toInstance(new GcpOidcTokenUtility());
  }
}
