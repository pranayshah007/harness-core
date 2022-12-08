/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.authenticationsettings.impl.AuthenticationSettingsService;
import io.harness.ng.authenticationsettings.impl.AuthenticationSettingsServiceImpl;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClientFactory;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

@OwnedBy(PL)
public class AuthenticationSettingsModule extends AbstractModule {
  private final ServiceHttpClientConfig managerClientConfig;
  private final String managerServiceSecret;

  public AuthenticationSettingsModule(ServiceHttpClientConfig managerClientConfig, String managerServiceSecret) {
    this.managerClientConfig = managerClientConfig;
    this.managerServiceSecret = managerServiceSecret;
  }

  @Override
  protected void configure() {
    bind(AuthSettingsManagerClient.class).toProvider(AuthSettingsManagerClientFactory.class).in(Scopes.SINGLETON);
    bind(AuthenticationSettingsService.class).to(AuthenticationSettingsServiceImpl.class);
  }

  @Provides
  private AuthSettingsManagerClientFactory getAuthSettingsManagerClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new AuthSettingsManagerClientFactory(
        managerClientConfig, managerServiceSecret, new ServiceTokenGenerator(), kryoConverterFactory);
  }
}
